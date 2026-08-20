package com.example.autobuilder.placement;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * After PlacementManager dispatches an interactBlock packet, the resulting
 * block-state change doesn't land on the client instantly - it depends on
 * server round-trip time. This class tracks a single pending placement and
 * reports success/failure/still-pending each tick until a timeout is
 * reached, at which point BuildController's RECOVERY_REPOSITION path
 * should be triggered.
 */
public final class PlacementVerifier {

    public enum Result {
        PENDING,
        CONFIRMED,
        FAILED_TIMEOUT,
        FAILED_MISMATCH
    }

    private BlockPos pendingPos;
    private BlockState expectedState;
    private int ticksWaited;
    private int timeoutTicks = 40; // 2 seconds at 20 tps - generous margin for server latency.

    public void beginTracking(BlockPos pos, BlockState expectedState) {
        this.pendingPos = pos;
        this.expectedState = expectedState;
        this.ticksWaited = 0;
    }

    public void setTimeoutTicks(int timeoutTicks) {
        this.timeoutTicks = timeoutTicks;
    }

    public boolean isTracking() {
        return pendingPos != null;
    }

    /**
     * Call once per tick while isTracking() is true. Compares the live
     * world block state at the tracked position against what was expected.
     * Only the block identity is required to match exactly; most
     * orientation properties are allowed to differ slightly from the exact
     * expected state (e.g. stair shape auto-updates based on neighbors
     * after placement) - an exact Block match with no properties changed by
     * neighbor updates is treated as CONFIRMED.
     */
    public Result tick(World world) {
        if (pendingPos == null) {
            return Result.PENDING;
        }

        BlockState actual = world.getBlockState(pendingPos);

        if (actual.getBlock() == expectedState.getBlock()) {
            reset();
            return Result.CONFIRMED;
        }

        ticksWaited++;
        if (ticksWaited >= timeoutTicks) {
            boolean stillAir = actual.isAir();
            reset();
            return stillAir ? Result.FAILED_TIMEOUT : Result.FAILED_MISMATCH;
        }

        return Result.PENDING;
    }

    private void reset() {
        pendingPos = null;
        expectedState = null;
        ticksWaited = 0;
    }
}
