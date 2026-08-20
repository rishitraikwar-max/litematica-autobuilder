package com.example.autobuilder.navigation;

import net.minecraft.util.math.BlockPos;

/**
 * A single node in the A* search graph. Mutable G/F cost fields are
 * intentional: A* repeatedly relaxes (updates) a node's cost as cheaper
 * paths to it are discovered, and allocating a new record on every relax
 * would generate significant garbage across large searches.
 */
public final class PathNode {

    public final BlockPos pos;

    /** The action used to reach this node from its current best-known parent. Mutable: see relax(). */
    public MovementAction actionFromParent;

    public PathNode parent;
    public double g; // Cost from start to this node.
    public double h; // Heuristic estimate from this node to goal.
    public double f; // g + h.

    /** Open/closed set membership bookkeeping, used by AStarPathfinder's binary heap. */
    public boolean closed = false;
    public int heapIndex = -1;

    public PathNode(BlockPos pos, MovementAction actionFromParent) {
        this.pos = pos;
        this.actionFromParent = actionFromParent;
    }

    /**
     * Updates this node's best-known parent, action, and cost. Called
     * whenever AStarPathfinder discovers a cheaper route to this node.
     */
    public void relax(PathNode newParent, double newG, MovementAction newAction) {
        this.parent = newParent;
        this.g = newG;
        this.f = this.g + this.h;
        this.actionFromParent = newAction;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof PathNode other)) return false;
        return pos.equals(other.pos);
    }

    @Override
    public int hashCode() {
        return pos.hashCode();
    }
}
