package com.example.autobuilder.spatial;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Two complementary line-of-sight tools:
 *
 * 1. {@link #traceVoxels3D} - a pure 3D Bresenham/DDA voxel walk, used by
 *    the pathfinder's raycast string-pulling step where we only need "which
 *    block cells does this line pass through", not precise hit vectors.
 *
 * 2. {@link #raycastBlocks} - a thin wrapper over vanilla's ClipContext
 *    raycasting, used by PlacementPlanner where we need an exact hit
 *    position/face against real collision geometry.
 */
public final class LineOfSight {

    private LineOfSight() {
    }

    /**
     * Walks every integer voxel cell intersected by the segment from start
     * to end using a 3D DDA (supercover) algorithm, so no cell the line
     * passes through - even diagonally - is skipped. Used for "is this
     * straight line clear of solid blocks" checks during path smoothing.
     */
    public static List<BlockPos> traceVoxels3D(Vec3d start, Vec3d end) {
        List<BlockPos> cells = new ArrayList<>();

        double x0 = start.x, y0 = start.y, z0 = start.z;
        double x1 = end.x, y1 = end.y, z1 = end.z;

        int x = (int) Math.floor(x0);
        int y = (int) Math.floor(y0);
        int z = (int) Math.floor(z0);

        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;

        int stepX = Integer.signum((int) Math.signum(dx));
        int stepY = Integer.signum((int) Math.signum(dy));
        int stepZ = Integer.signum((int) Math.signum(dz));

        double tDeltaX = dx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double tDeltaY = dy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double tDeltaZ = dz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);

        double tMaxX = tDeltaX == Double.POSITIVE_INFINITY
                ? Double.POSITIVE_INFINITY
                : fracStep(x0, stepX) * tDeltaX;
        double tMaxY = tDeltaY == Double.POSITIVE_INFINITY
                ? Double.POSITIVE_INFINITY
                : fracStep(y0, stepY) * tDeltaY;
        double tMaxZ = tDeltaZ == Double.POSITIVE_INFINITY
                ? Double.POSITIVE_INFINITY
                : fracStep(z0, stepZ) * tDeltaZ;

        int targetX = (int) Math.floor(x1);
        int targetY = (int) Math.floor(y1);
        int targetZ = (int) Math.floor(z1);

        int maxSteps = 4096; // Safety bound against pathological/degenerate input.
        cells.add(new BlockPos(x, y, z));

        for (int i = 0; i < maxSteps; i++) {
            if (x == targetX && y == targetY && z == targetZ) {
                break;
            }
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    x += stepX;
                    tMaxX += tDeltaX;
                } else {
                    z += stepZ;
                    tMaxZ += tDeltaZ;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    y += stepY;
                    tMaxY += tDeltaY;
                } else {
                    z += stepZ;
                    tMaxZ += tDeltaZ;
                }
            }
            cells.add(new BlockPos(x, y, z));
        }

        return cells;
    }

    private static double fracStep(double coord, int step) {
        if (step > 0) {
            return 1.0 - (coord - Math.floor(coord));
        } else if (step < 0) {
            return coord - Math.floor(coord);
        }
        return Double.POSITIVE_INFINITY;
    }

    /**
     * True if every voxel cell between start and end is non-solid (ignoring
     * fluids), i.e. a straight line between the two points is unobstructed.
     * Used by string-pulling to decide whether two path waypoints can be
     * merged into a single straight segment.
     */
    public static boolean isPathClear(World world, Vec3d start, Vec3d end) {
        for (BlockPos cell : traceVoxels3D(start, end)) {
            BlockState state = world.getBlockState(cell);
            if (!state.getCollisionShape(world, cell).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Precise raycast against real block collision geometry using vanilla's
     * ClipContext, for placement-target verification (does the player
     * actually have an unobstructed, in-reach line to the block face they
     * intend to click).
     */
    public static BlockHitResult raycastBlocks(World world, Vec3d start, Vec3d end, Entity entity) {
        RaycastContext ctx = new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                entity
        );
        return world.raycast(ctx);
    }

    /** Convenience: true if the raycast from start to end hits nothing before reaching end. */
    public static boolean hasClearLineOfSight(World world, Vec3d start, Vec3d end, Entity entity) {
        BlockHitResult result = raycastBlocks(world, start, end, entity);
        return result.getType() == HitResult.Type.MISS;
    }
}
