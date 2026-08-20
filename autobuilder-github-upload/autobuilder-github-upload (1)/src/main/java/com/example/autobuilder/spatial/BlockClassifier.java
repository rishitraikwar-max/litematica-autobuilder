package com.example.autobuilder.spatial;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.FireBlock;
import net.minecraft.block.SweetBerryBushBlock;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

/**
 * Pure classification logic: given a block state / position, decide whether
 * it is walkable ground, hazardous, or passable air. No pathfinding logic
 * lives here - this is a set of stateless predicates that AStarPathfinder
 * and VoxelWorld both consume.
 */
public final class BlockClassifier {

    private BlockClassifier() {
    }

    /**
     * A block is "hazardous" if standing in it / falling into it can hurt or
     * kill the player, or if it would corrupt scheduled movement (e.g. lava,
     * fire, powder snow). Hazardous nodes are given effectively infinite
     * pathfinding cost (see AStarPathfinder.HAZARD_COST).
     */
    public static boolean isHazardous(World world, BlockPos pos, BlockState state) {
        Block block = state.getBlock();

        if (!state.getFluidState().isEmpty() && state.getFluidState().isIn(FluidTags.LAVA)) {
            return true;
        }
        if (block instanceof FireBlock) {
            return true;
        }
        if (block == Blocks.MAGMA_BLOCK) {
            return true;
        }
        if (block == Blocks.POWDER_SNOW) {
            return true;
        }
        if (block instanceof CampfireBlock) {
            return true;
        }
        if (block instanceof SweetBerryBushBlock) {
            return true;
        }
        if (block == Blocks.CACTUS) {
            return true;
        }
        if (block == Blocks.WITHER_ROSE) {
            return true;
        }
        return false;
    }

    /**
     * True if the block at pos has a full solid top face - i.e. the
     * collision shape's maximum Y bound reaches 1.0 across the full X/Z
     * extent. This is required for a block to count as valid "ground" for
     * the walkability solver.
     */
    public static boolean hasSolidTopFace(World world, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return false;
        }
        VoxelShape collision = state.getCollisionShape(world, pos);
        if (collision.isEmpty()) {
            return false;
        }
        Box bounds = collision.getBoundingBox();
        // Require the shape to reach the very top of the block space, and to
        // span (nearly) the full footprint - this rules out slabs placed as
        // bottom-half (max Y 0.5) and thin shapes like signs/torches.
        return bounds.maxY >= 0.999
                && (bounds.maxX - bounds.minX) >= 0.9
                && (bounds.maxZ - bounds.minZ) >= 0.9;
    }

    /**
     * True if a block position offers a clear 0.6m x 1.8m player hitbox
     * clearance: the block at pos and the block above it must both have no
     * colliding volume (empty or non-full shapes that a 0.6-wide hitbox
     * would not intersect).
     */
    public static boolean hasHeadroom(World world, BlockPos feetPos) {
        BlockPos headPos = feetPos.up();
        BlockState feetState = world.getBlockState(feetPos);
        BlockState headState = world.getBlockState(headPos);

        return isPassableForHitbox(world, feetPos, feetState)
                && isPassableForHitbox(world, headPos, headState);
    }

    private static boolean isPassableForHitbox(World world, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return true;
        }
        VoxelShape shape = state.getCollisionShape(world, pos);
        if (shape.isEmpty()) {
            return true;
        }
        // Anything with meaningful collision volume in the center column is
        // treated as blocking - a conservative but safe approximation of a
        // 0.6-wide hitbox check.
        Box bounds = shape.getBoundingBox();
        double centerMinX = 0.5 - 0.3;
        double centerMaxX = 0.5 + 0.3;
        double centerMinZ = 0.5 - 0.3;
        double centerMaxZ = 0.5 + 0.3;
        boolean overlapsX = bounds.minX < centerMaxX && bounds.maxX > centerMinX;
        boolean overlapsZ = bounds.minZ < centerMaxZ && bounds.maxZ > centerMinZ;
        return !(overlapsX && overlapsZ);
    }

    /**
     * Determines whether standing at feetPos and looking down would result
     * in a fall greater than maxSafeFallDistance blocks with no water to
     * cushion it. Walks downward from feetPos until solid ground or water is
     * found, or the safe-fall limit is exceeded.
     */
    public static boolean isUnsafeFall(World world, BlockPos feetPos, int maxSafeFallDistance) {
        BlockPos.Mutable cursor = feetPos.mutableCopy();
        for (int dropped = 1; dropped <= maxSafeFallDistance + 1; dropped++) {
            cursor.setY(feetPos.getY() - dropped);
            BlockState state = world.getBlockState(cursor);

            if (!state.getFluidState().isEmpty() && state.getFluidState().isIn(FluidTags.WATER)) {
                return false; // Water cushions the fall - safe regardless of depth so far.
            }
            if (isHazardous(world, cursor, state)) {
                return true;
            }
            if (hasSolidTopFace(world, cursor, state)) {
                // Landed within the allowed drop distance.
                return dropped > maxSafeFallDistance;
            }
        }
        // Nothing solid found within maxSafeFallDistance + 1 - treat as void/unsafe.
        return true;
    }

    /**
     * Full walkability predicate combining ground, headroom, and hazard
     * checks. feetPos is the position the player's feet would occupy.
     */
    public static boolean isWalkable(World world, BlockPos feetPos, int maxSafeFallDistance) {
        BlockPos groundPos = feetPos.down();
        BlockState groundState = world.getBlockState(groundPos);
        BlockState feetState = world.getBlockState(feetPos);

        if (isHazardous(world, feetPos, feetState) || isHazardous(world, groundPos, groundState)) {
            return false;
        }
        if (!hasSolidTopFace(world, groundPos, groundState)) {
            // No solid ground directly below - only acceptable if the safe
            // fall check passes at feetPos (i.e. handled explicitly by
            // drop-down edge generation in AStarPathfinder, not here).
            return false;
        }
        if (!hasHeadroom(world, feetPos)) {
            return false;
        }
        return true;
    }
}
