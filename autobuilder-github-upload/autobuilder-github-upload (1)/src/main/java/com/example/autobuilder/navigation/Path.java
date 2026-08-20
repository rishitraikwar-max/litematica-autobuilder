package com.example.autobuilder.navigation;

import com.example.autobuilder.spatial.LineOfSight;
import com.example.autobuilder.spatial.VoxelWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a raw A* node list into a smoothed, centerline waypoint sequence
 * and tracks progression through it as the player moves. Smoothing uses
 * raycast-based "string pulling": starting from the current waypoint, it
 * greedily tries to skip ahead to the furthest waypoint with a clear direct
 * line, collapsing zig-zag A* output into fewer, straighter segments.
 */
public final class Path {

    private final List<Vec3d> waypoints;
    private final List<MovementAction> actions;
    private int currentIndex = 0;

    private Path(List<Vec3d> waypoints, List<MovementAction> actions) {
        this.waypoints = waypoints;
        this.actions = actions;
    }

    /**
     * Builds a smoothed Path from raw A* BlockPos output. rawActions must be
     * the same length as rawNodes and describes the action used to ARRIVE at
     * rawNodes[i] (rawActions[0] is unused/ignored, since the start node has
     * no arrival action).
     */
    public static Path fromNodes(VoxelWorld voxelWorld, List<BlockPos> rawNodes, List<MovementAction> rawActions) {
        List<Vec3d> centered = new ArrayList<>(rawNodes.size());
        for (BlockPos p : rawNodes) {
            centered.add(new Vec3d(p.getX() + 0.5, p.getY(), p.getZ() + 0.5));
        }

        List<Vec3d> smoothedPoints = new ArrayList<>();
        List<MovementAction> smoothedActions = new ArrayList<>();

        smoothedPoints.add(centered.get(0));
        smoothedActions.add(MovementAction.WALK); // Placeholder for start node; never consumed.

        int i = 0;
        while (i < centered.size() - 1) {
            int furthest = i + 1;
            // Never string-pull across a JUMP_UP, DROP_DOWN, or ladder action - those require
            // precise positioning at the transition point and must remain distinct waypoints.
            for (int j = i + 2; j < centered.size(); j++) {
                boolean crossesSpecialAction = false;
                for (int k = i + 1; k <= j; k++) {
                    MovementAction a = rawActions.get(k);
                    if (a == MovementAction.JUMP_UP || a == MovementAction.DROP_DOWN
                            || a == MovementAction.SPRINT_JUMP
                            || a == MovementAction.ASCEND_LADDER || a == MovementAction.DESCEND_LADDER) {
                        crossesSpecialAction = true;
                        break;
                    }
                }
                if (crossesSpecialAction) break;
                if (LineOfSight.isPathClear(voxelWorld.getWorld(), centered.get(i), centered.get(j))) {
                    furthest = j;
                } else {
                    break;
                }
            }
            smoothedPoints.add(centered.get(furthest));
            smoothedActions.add(rawActions.get(furthest));
            i = furthest;
        }

        return new Path(smoothedPoints, smoothedActions);
    }

    public boolean isComplete() {
        return currentIndex >= waypoints.size();
    }

    public Vec3d getCurrentWaypoint() {
        if (isComplete()) return null;
        return waypoints.get(currentIndex);
    }

    public MovementAction getCurrentAction() {
        if (isComplete()) return null;
        return actions.get(currentIndex);
    }

    /** Peek at the waypoint after the current one, or null if this is the last. Used for jump pre-triggering. */
    public Vec3d peekNextWaypoint() {
        int next = currentIndex + 1;
        if (next >= waypoints.size()) return null;
        return waypoints.get(next);
    }

    public MovementAction peekNextAction() {
        int next = currentIndex + 1;
        if (next >= actions.size()) return null;
        return actions.get(next);
    }

    /** Advances to the next waypoint. Called by MovementController once the dead-zone is reached. */
    public void advance() {
        currentIndex++;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public int size() {
        return waypoints.size();
    }

    public List<Vec3d> getAllWaypoints() {
        return waypoints;
    }
}
