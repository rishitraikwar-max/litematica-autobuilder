package com.example.autobuilder.navigation;

import com.example.autobuilder.spatial.VoxelWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Pure, from-scratch 3D A* pathfinder. No third-party pathfinding
 * dependency of any kind - all graph construction, edge costing, and
 * search logic below is original.
 *
 * Coordinate system: every PathNode.pos represents the BlockPos the
 * player's FEET occupy (i.e. the block the player is standing on is
 * pos.down()).
 */
public final class AStarPathfinder {

    /** Cost multiplier applied to the Euclidean heuristic (admissible-ish; slightly greedy for speed). */
    private static final double HEURISTIC_WEIGHT = 1.1;

    private static final double COST_FLAT_CARDINAL = 1.0;
    private static final double COST_FLAT_DIAGONAL = 1.414;
    private static final double COST_STEP_UP = 2.0;
    private static final double COST_DROP_BASE = 1.5;
    private static final double COST_DROP_PER_BLOCK = 0.5;

    /** Effectively-infinite cost used to mark hazardous / disallowed edges without special-casing them. */
    private static final double HAZARD_COST = Double.MAX_VALUE / 2.0;

    private static final int[][] CARDINAL_OFFSETS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };
    private static final int[][] DIAGONAL_OFFSETS = {
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private final VoxelWorld voxelWorld;
    private final int maxSearchNodes;
    private final int maxDropDistance;

    public AStarPathfinder(VoxelWorld voxelWorld, int maxSearchNodes, int maxDropDistance) {
        this.voxelWorld = voxelWorld;
        this.maxSearchNodes = maxSearchNodes;
        this.maxDropDistance = maxDropDistance;
    }

    /** Blacklisted nodes (e.g. from StuckDetector) that must never be expanded into during this search. */
    public List<BlockPos> findPath(BlockPos start, BlockPos goal, java.util.Set<BlockPos> blacklist) {
        Map<BlockPos, PathNode> allNodes = new HashMap<>();
        PriorityQueue<PathNode> openSet = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));

        PathNode startNode = new PathNode(start, null);
        startNode.g = 0;
        startNode.h = heuristic(start, goal);
        startNode.f = startNode.h;
        allNodes.put(start, startNode);
        openSet.add(startNode);

        int visited = 0;

        while (!openSet.isEmpty() && visited < maxSearchNodes) {
            PathNode current = openSet.poll();
            if (current.closed) {
                continue; // Stale queue entry from an earlier relax; skip.
            }
            current.closed = true;
            visited++;

            if (current.pos.equals(goal)) {
                return reconstructPath(current);
            }

            for (Edge edge : generateEdges(current.pos, blacklist)) {
                if (edge.cost >= HAZARD_COST) {
                    continue;
                }
                PathNode neighbor = allNodes.computeIfAbsent(edge.to, p -> new PathNode(p, edge.action));
                if (neighbor.closed) {
                    continue;
                }

                double tentativeG = current.g + edge.cost;
                boolean isNewNode = neighbor.parent == null && !neighbor.pos.equals(start);
                if (isNewNode || tentativeG < neighbor.g) {
                    if (neighbor.h == 0 && !neighbor.pos.equals(goal)) {
                        neighbor.h = heuristic(neighbor.pos, goal);
                    }
                    neighbor.relax(current, tentativeG, edge.action);
                    openSet.add(neighbor); // Duplicate entries are filtered via the `closed` check above.
                }
            }
        }

        return List.of(); // No path found within the node budget.
    }

    private double heuristic(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz) * HEURISTIC_WEIGHT;
    }

    private List<BlockPos> reconstructPath(PathNode goalNode) {
        List<BlockPos> path = new ArrayList<>();
        PathNode cursor = goalNode;
        while (cursor != null) {
            path.add(cursor.pos);
            cursor = cursor.parent;
        }
        java.util.Collections.reverse(path);
        return path;
    }

    /** A candidate transition from one node to an adjacent one, with its computed cost and action type. */
    private record Edge(BlockPos to, double cost, MovementAction action) {
    }

    private List<Edge> generateEdges(BlockPos from, java.util.Set<BlockPos> blacklist) {
        List<Edge> edges = new ArrayList<>();

        // --- Flat cardinal walks ---
        for (int[] off : CARDINAL_OFFSETS) {
            BlockPos to = from.add(off[0], 0, off[1]);
            if (blacklist.contains(to)) continue;
            if (voxelWorld.isWalkable(to)) {
                edges.add(new Edge(to, COST_FLAT_CARDINAL, MovementAction.WALK));
            }
        }

        // --- Flat diagonal walks (corner-cutting prevention: both adjacent orthogonals must be clear) ---
        for (int[] off : DIAGONAL_OFFSETS) {
            BlockPos to = from.add(off[0], 0, off[1]);
            if (blacklist.contains(to)) continue;
            BlockPos ortho1 = from.add(off[0], 0, 0);
            BlockPos ortho2 = from.add(0, 0, off[1]);
            if (voxelWorld.isWalkable(to) && voxelWorld.hasHeadroom(ortho1) && voxelWorld.hasHeadroom(ortho2)) {
                edges.add(new Edge(to, COST_FLAT_DIAGONAL, MovementAction.WALK_DIAGONAL));
            }
        }

        // --- Step up (1 block), cardinal only ---
        for (int[] off : CARDINAL_OFFSETS) {
            BlockPos to = from.add(off[0], 1, off[1]);
            if (blacklist.contains(to)) continue;
            // Requires 2 blocks of vertical headroom above BOTH the starting position and the landing position.
            boolean startHeadroom = voxelWorld.hasHeadroom(from) && voxelWorld.hasHeadroom(from.up());
            boolean landHeadroom = voxelWorld.hasHeadroom(to) && voxelWorld.hasHeadroom(to.up());
            if (voxelWorld.isWalkable(to) && startHeadroom && landHeadroom) {
                edges.add(new Edge(to, COST_STEP_UP, MovementAction.JUMP_UP));
            }
        }

        // --- Drop down (1-3 blocks), cardinal only ---
        for (int[] off : CARDINAL_OFFSETS) {
            BlockPos edgePos = from.add(off[0], 0, off[1]);
            if (blacklist.contains(edgePos)) continue;
            // Only attempt a drop if the immediately adjacent cell is NOT walkable at the same level
            // (i.e. there's actually a ledge here) - otherwise this duplicates the flat-walk edge.
            if (voxelWorld.isWalkable(edgePos)) continue;

            BlockPos landing = voxelWorld.findDropLanding(edgePos);
            if (landing == null) continue;

            int deltaY = from.getY() - landing.getY();
            if (deltaY < 1 || deltaY > maxDropDistance) continue;
            if (blacklist.contains(landing)) continue;

            double cost = COST_DROP_BASE + (COST_DROP_PER_BLOCK * deltaY);
            edges.add(new Edge(landing, cost, MovementAction.DROP_DOWN));
        }

        return edges;
    }
}
