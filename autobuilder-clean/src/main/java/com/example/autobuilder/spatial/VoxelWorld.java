package com.example.autobuilder.spatial;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * Thin caching facade over the live ClientWorld used by the pathfinder and
 * placement solver. Re-querying getBlockState/getCollisionShape on every A*
 * neighbor expansion is expensive when a single search can touch thousands
 * of nodes, so this layer memoizes walkability/hazard results per BlockPos
 * for the lifetime of a single pathfinding request.
 *
 * IMPORTANT: instances of this class are short-lived - one per pathfinding
 * request/tick batch - because block states can change (the player itself
 * is placing blocks!). Never cache a VoxelWorld across multiple ticks.
 */
public final class VoxelWorld {

    private final World world;
    private final int maxSafeFallDistance;

    private final Map<BlockPos, Boolean> walkabilityCache = new HashMap<>();
    private final Map<BlockPos, Boolean> hazardCache = new HashMap<>();
    private final Map<BlockPos, Boolean> headroomCache = new HashMap<>();
    private final Map<BlockPos, Boolean> solidTopCache = new HashMap<>();

    public VoxelWorld(World world, int maxSafeFallDistance) {
        this.world = world;
        this.maxSafeFallDistance = maxSafeFallDistance;
    }

    public World getWorld() {
        return world;
    }

    public BlockState getBlockState(BlockPos pos) {
        return world.getBlockState(pos);
    }

    public boolean isHazardous(BlockPos pos) {
        return hazardCache.computeIfAbsent(pos.toImmutable(),
                p -> BlockClassifier.isHazardous(world, p, world.getBlockState(p)));
    }

    public boolean hasSolidTopFace(BlockPos pos) {
        return solidTopCache.computeIfAbsent(pos.toImmutable(),
                p -> BlockClassifier.hasSolidTopFace(world, p, world.getBlockState(p)));
    }

    public boolean hasHeadroom(BlockPos feetPos) {
        return headroomCache.computeIfAbsent(feetPos.toImmutable(),
                p -> BlockClassifier.hasHeadroom(world, p));
    }

    public boolean isWalkable(BlockPos feetPos) {
        return walkabilityCache.computeIfAbsent(feetPos.toImmutable(),
                p -> BlockClassifier.isWalkable(world, p, maxSafeFallDistance));
    }

    public boolean isUnsafeFall(BlockPos feetPos) {
        return BlockClassifier.isUnsafeFall(world, feetPos, maxSafeFallDistance);
    }

    /**
     * Finds the first solid landing position below feetPos, scanning down up
     * to maxSafeFallDistance + 1 blocks. Returns null if no safe landing is
     * found within range (caller should treat this edge as impassable).
     */
    public BlockPos findDropLanding(BlockPos feetPos) {
        BlockPos.Mutable cursor = feetPos.mutableCopy();
        for (int dropped = 1; dropped <= maxSafeFallDistance + 1; dropped++) {
            cursor.setY(feetPos.getY() - dropped);
            BlockPos immutable = cursor.toImmutable();
            if (isHazardous(immutable)) {
                return null;
            }
            if (hasSolidTopFace(immutable)) {
                BlockPos landing = immutable.up();
                if (dropped <= maxSafeFallDistance && hasHeadroom(landing)) {
                    return landing;
                }
                return null;
            }
        }
        return null;
    }

    /** Clears all memoized results - call if the underlying world state is known to have changed. */
    public void invalidate() {
        walkabilityCache.clear();
        hazardCache.clear();
        headroomCache.clear();
        solidTopCache.clear();
    }
}
