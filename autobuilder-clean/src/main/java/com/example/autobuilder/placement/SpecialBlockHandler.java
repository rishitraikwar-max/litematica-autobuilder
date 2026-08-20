package com.example.autobuilder.placement;

import net.minecraft.block.*;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Solves the click geometry needed to make vanilla's block-placement logic
 * produce a specific target BlockState, for every block family whose
 * orientation depends on which face/sub-position the player clicks (as
 * opposed to blocks that always place the same way regardless of click
 * point).
 *
 * Each solver method returns a face+hitVec pair such that, if the player
 * right-clicks that exact point with the correct item selected, vanilla's
 * own BlockItem.getPlacementState() logic will independently arrive at the
 * same BlockState already computed by SchematicReader. This mod never
 * fabricates a BlockState directly - it only aims at the point that makes
 * vanilla produce it, preserving legitimate client-server placement flow.
 */
public final class SpecialBlockHandler {

    private SpecialBlockHandler() {
    }

    /** Result of solving orientation geometry: which face to click and where on that face. */
    public record ClickSolution(Direction face, Vec3d localHitVec) {
    }

    /**
     * Top-level dispatch: given a target BlockState and the neighbor block
     * it will be placed against, compute the click face and local hit
     * vector ([0,1]^3 within the neighbor's block space) required.
     * Falls back to a generic "click the top-center of the block below" for
     * any block family without special orientation logic.
     */
    public static ClickSolution solve(BlockState targetState, BlockPos targetPos, PlayerEntity player) {
        Block block = targetState.getBlock();

        if (block instanceof StairsBlock) {
            return solveStairs(targetState);
        }
        if (block instanceof SlabBlock) {
            return solveSlab(targetState);
        }
        if (block instanceof TrapdoorBlock) {
            return solveTrapdoor(targetState);
        }
        if (block instanceof PillarBlock) { // Logs, and other axis-aligned pillar blocks.
            return solvePillar(targetState);
        }
        if (block instanceof PistonBlock || block instanceof ObserverBlock) {
            return solveDirectional(targetState);
        }
        if (block instanceof StairsBlock == false && targetState.contains(Properties.HORIZONTAL_FACING)) {
            return solveHorizontalFacing(targetState);
        }

        return genericFallback();
    }

    private static ClickSolution solveStairs(BlockState state) {
        Direction facing = state.get(Properties.HORIZONTAL_FACING);
        boolean top = state.get(Properties.BLOCK_HALF) == BlockHalf.TOP;

        // Clicking the bottom half of a neighbor's face places a bottom stair;
        // clicking the top half places a top ("upside-down") stair. We aim at
        // the face opposite the desired facing direction so vanilla resolves
        // HORIZONTAL_FACING correctly (placement faces away from the clicked face).
        Direction clickFace = facing.getOpposite();
        double y = top ? 0.9 : 0.1;
        Vec3d hit = faceCenterWithY(clickFace, y);
        return new ClickSolution(clickFace, hit);
    }

    private static ClickSolution solveSlab(BlockState state) {
        SlabType type = state.get(Properties.SLAB_TYPE);
        if (type == SlabType.DOUBLE) {
            // A double slab is produced by clicking an existing single slab of the same
            // type again; simplest reliable approach is clicking the top face center,
            // which converts a bottom slab beneath it into a double when combined.
            return new ClickSolution(Direction.UP, new Vec3d(0.5, 1.0, 0.5));
        }
        Direction clickFace = type == SlabType.TOP ? Direction.UP : Direction.DOWN;
        double y = type == SlabType.TOP ? 1.0 : 0.0;
        return new ClickSolution(Direction.UP, new Vec3d(0.5, y, 0.5));
    }

    private static ClickSolution solveTrapdoor(BlockState state) {
        Direction facing = state.get(Properties.HORIZONTAL_FACING);
        boolean top = state.get(Properties.BLOCK_HALF) == BlockHalf.TOP;
        Direction clickFace = facing.getOpposite();
        double y = top ? 0.9 : 0.1;
        return new ClickSolution(clickFace, faceCenterWithY(clickFace, y));
    }

    private static ClickSolution solvePillar(BlockState state) {
        Direction.Axis axis = state.get(Properties.AXIS);
        // Clicking the face matching the desired axis produces that axis orientation
        // (e.g. clicking the top/bottom face of a neighbor places a vertical log).
        Direction clickFace = switch (axis) {
            case X -> Direction.EAST;
            case Y -> Direction.UP;
            case Z -> Direction.SOUTH;
        };
        return new ClickSolution(clickFace, new Vec3d(0.5, 0.5, 0.5));
    }

    private static ClickSolution solveDirectional(BlockState state) {
        Direction facing = state.contains(Properties.FACING)
                ? state.get(Properties.FACING)
                : state.get(Properties.HORIZONTAL_FACING);
        // These blocks face AWAY from the player at placement time, so the click
        // face should be the opposite of the desired facing.
        Direction clickFace = facing.getOpposite();
        return new ClickSolution(clickFace, new Vec3d(0.5, 0.5, 0.5));
    }

    private static ClickSolution solveHorizontalFacing(BlockState state) {
        Direction facing = state.get(Properties.HORIZONTAL_FACING);
        Direction clickFace = facing.getOpposite();
        return new ClickSolution(clickFace, new Vec3d(0.5, 0.5, 0.5));
    }

    private static ClickSolution genericFallback() {
        return new ClickSolution(Direction.UP, new Vec3d(0.5, 1.0, 0.5));
    }

    private static Vec3d faceCenterWithY(Direction face, double y) {
        return switch (face) {
            case UP, DOWN -> new Vec3d(0.5, y, 0.5);
            case NORTH -> new Vec3d(0.5, y, 0.0);
            case SOUTH -> new Vec3d(0.5, y, 1.0);
            case EAST -> new Vec3d(1.0, y, 0.5);
            case WEST -> new Vec3d(0.0, y, 0.5);
        };
    }
}
