package com.example.autobuilder.placement;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Everything PlacementManager needs to execute a single block placement.
 *
 * @param targetPos     The world position the new block should end up at.
 * @param neighborPos   The already-existing block to right-click against
 *                      (targetPos offset by the opposite of clickFace).
 * @param clickFace     Which face of neighborPos is being clicked.
 * @param hitVec        Exact point on that face to aim at, in absolute
 *                      world coordinates (not the [0,1]^3 local form) -
 *                      already resolved by PlacementPlanner/SpecialBlockHandler.
 * @param standingPos   The world position the player should stand at
 *                      (feet position) to execute this placement.
 * @param requiresSneak True if sneaking is required during placement (e.g.
 *                      to avoid triggering a container GUI, or to place
 *                      against a block with an interactive right-click like
 *                      a door or lever).
 */
public record PlacementTarget(
        BlockPos targetPos,
        BlockPos neighborPos,
        Direction clickFace,
        Vec3d hitVec,
        BlockPos standingPos,
        boolean requiresSneak
) {
}
