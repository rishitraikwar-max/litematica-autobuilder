package com.example.autobuilder.placement;

import com.example.autobuilder.config.AutoBuilderConfig;
import com.example.autobuilder.spatial.LineOfSight;
import com.example.autobuilder.spatial.VoxelWorld;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

/**
 * Given a target block to place, finds a valid combination of:
 *   - which neighboring block to click against (and which face),
 *   - the exact hit point on that face,
 *   - a player standing position with solid footing and unobstructed line
 *     of sight to that hit point, within reach range, that does not
 *     intersect the block being placed.
 *
 * This is pure geometry/search - it does not execute anything itself.
 * PlacementManager consumes the resulting PlacementTarget.
 */
public final class PlacementPlanner {

    private final VoxelWorld voxelWorld;
    private final AutoBuilderConfig config;

    public PlacementPlanner(VoxelWorld voxelWorld, AutoBuilderConfig config) {
        this.voxelWorld = voxelWorld;
        this.config = config;
    }

    /**
     * Attempts to solve a full PlacementTarget for the given block state at
     * targetPos. Returns null if no valid standing position + click
     * combination could be found (caller should treat this as
     * "unreachable, try again later" rather than a hard failure).
     */
    public PlacementTarget solve(BlockPos targetPos, BlockState targetState, PlayerEntity player) {
        SpecialBlockHandler.ClickSolution solution = SpecialBlockHandler.solve(targetState, targetPos, player);

        Direction clickFace = solution.face();
        BlockPos neighborPos = targetPos.offset(clickFace.getOpposite());

        // Verify the neighbor actually has a solid face to click against. If not, the
        // dependency ordering upstream (DependencyGraph) should have deferred this block -
        // but we defensively bail out here rather than producing an invalid placement.
        BlockState neighborState = voxelWorld.getBlockState(neighborPos);
        if (neighborState.isAir() || neighborState.getCollisionShape(voxelWorld.getWorld(), neighborPos).isEmpty()) {
            return null;
        }

        Vec3d worldHitVec = new Vec3d(
                neighborPos.getX() + solution.localHitVec().x,
                neighborPos.getY() + solution.localHitVec().y,
                neighborPos.getZ() + solution.localHitVec().z
        );

        List<BlockPos> candidates = generateStandingCandidates(targetPos);
        candidates.sort(Comparator.comparingDouble(p -> distanceSquared(p, targetPos)));

        for (BlockPos standPos : candidates) {
            if (!isValidStandingPosition(standPos, targetPos)) continue;

            Vec3d eyePos = eyePositionFor(standPos, player);
            double distance = eyePos.distanceTo(worldHitVec);
            if (distance < config.minPlacementReach || distance > config.maxPlacementReach) continue;
            if (distance > config.absoluteReachCap) continue;

            if (!LineOfSight.hasClearLineOfSight(voxelWorld.getWorld(), eyePos, worldHitVec, player)) continue;

            boolean requiresSneak = requiresSneakToPlace(neighborPos);

            return new PlacementTarget(targetPos, neighborPos, clickFace, worldHitVec, standPos, requiresSneak);
        }

        return null;
    }

    /**
     * Generates candidate standing positions in a ring around the target
     * block, at radii from config.standSearchRadiusMin to
     * standSearchRadiusMax, across a range of Y offsets (-1 to +1) to allow
     * standing slightly above or below the target's layer.
     */
    private List<BlockPos> generateStandingCandidates(BlockPos targetPos) {
        List<BlockPos> candidates = new ArrayList<>();
        for (int radius = config.standSearchRadiusMin; radius <= config.standSearchRadiusMax; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    // Only consider the ring boundary at this radius, not the full filled square,
                    // to avoid re-testing positions already covered by a smaller radius.
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    for (int dy = -1; dy <= 1; dy++) {
                        candidates.add(targetPos.add(dx, dy, dz));
                    }
                }
            }
        }
        return candidates;
    }

    private boolean isValidStandingPosition(BlockPos standPos, BlockPos targetPos) {
        if (!voxelWorld.isWalkable(standPos)) return false;

        // The player's bounding box (0.6 x 1.8 x 0.6, centered on the block) must not
        // intersect the block being placed.
        Box playerBox = new Box(
                standPos.getX() + 0.2, standPos.getY(), standPos.getZ() + 0.2,
                standPos.getX() + 0.8, standPos.getY() + 1.8, standPos.getZ() + 0.8
        );
        Box targetBox = new Box(targetPos);
        return !playerBox.intersects(targetBox);
    }

    private Vec3d eyePositionFor(BlockPos standPos, PlayerEntity player) {
        double eyeHeight = player.getStandingEyeHeight();
        return new Vec3d(standPos.getX() + 0.5, standPos.getY() + eyeHeight, standPos.getZ() + 0.5);
    }

    private double distanceSquared(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * True if the neighbor block being clicked has its own right-click
     * interaction (doors, buttons, containers, etc.) that would trigger
     * instead of block placement unless the player is sneaking.
     */
    private boolean requiresSneakToPlace(BlockPos neighborPos) {
        BlockState state = voxelWorld.getBlockState(neighborPos);
        // A conservative heuristic: any block whose class overrides onUse is assumed
        // interactive. Rather than reflect on this at runtime, PlacementManager's
        // dispatch always sneaks when clicking a non-full-cube neighbor as a safe default;
        // this method exists as an explicit extension point for a more precise per-block
        // interactive-block registry if one is added later.
        return !state.isOpaqueFullCube(voxelWorld.getWorld(), neighborPos);
    }
}
