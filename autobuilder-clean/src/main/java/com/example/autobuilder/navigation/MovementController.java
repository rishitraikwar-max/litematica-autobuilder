package com.example.autobuilder.navigation;

import com.example.autobuilder.config.AutoBuilderConfig;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Converts the active Path's current waypoint into per-tick movement
 * impulses. This class NEVER calls setPos/teleport/setVelocity - it only
 * computes forwardImpulse/leftImpulse/jumping values, which are applied to
 * the vanilla input pipeline by {@link com.example.autobuilder.mixin.KeyboardInputMixin}
 * exactly like a real key press would be.
 *
 * One MovementController instance is owned by BuildController and persists
 * across the MOVING state's ticks for a given path.
 */
public final class MovementController {

    /**
     * Snapshot of the impulses that should be applied this tick. Immutable
     * value passed from computeTickImpulse() to the mixin via
     * AutoBuilderClient's per-tick bridge.
     */
    public record ImpulseState(float forwardImpulse, float leftImpulse, boolean jumping, boolean sprinting) {
        public static final ImpulseState NONE = new ImpulseState(0f, 0f, false, false);
    }

    private final AutoBuilderConfig config;

    public MovementController(AutoBuilderConfig config) {
        this.config = config;
    }

    /**
     * Computes this tick's movement impulse for the given player and active
     * path. Also advances the path's waypoint cursor when the dead-zone
     * radius is reached, and returns ImpulseState.NONE (with the path left
     * unmodified) if the path is already complete.
     */
    public ImpulseState computeTickImpulse(ClientPlayerEntity player, Path path) {
        if (path == null || path.isComplete()) {
            return ImpulseState.NONE;
        }

        Vec3d playerPos = player.getPos();
        Vec3d waypoint = path.getCurrentWaypoint();

        double dx = waypoint.x - playerPos.x;
        double dz = waypoint.z - playerPos.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        if (horizontalDistance < config.waypointDeadZone) {
            path.advance();
            if (path.isComplete()) {
                return ImpulseState.NONE;
            }
            waypoint = path.getCurrentWaypoint();
            dx = waypoint.x - playerPos.x;
            dz = waypoint.z - playerPos.z;
            horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        }

        // deltaTheta: angle between the player's forward vector and the
        // direction to the waypoint, in the player's local movement frame.
        // atan2(dz, dx) gives the world-space bearing to the waypoint;
        // subtracting (yaw + 90 degrees) rotates it into player-forward-relative space,
        // matching vanilla's movementForward/movementSideways convention.
        double targetBearing = Math.atan2(dz, dx);
        double playerForwardRad = Math.toRadians(player.getYaw() + 90.0);
        double deltaTheta = targetBearing - playerForwardRad;
        // Normalize to [-PI, PI] so cos/sin below reflect the shortest turn direction.
        deltaTheta = normalizeRadians(deltaTheta);

        float forwardImpulse = (float) Math.cos(deltaTheta) * config.movementSpeedMultiplier;
        float leftImpulse = (float) Math.sin(deltaTheta) * config.movementSpeedMultiplier;

        boolean jumping = shouldJump(path, player, horizontalDistance);
        boolean sprinting = config.allowSprint && horizontalDistance > config.sprintDisableDistance
                && path.getCurrentAction() != MovementAction.JUMP_UP;

        return new ImpulseState(forwardImpulse, leftImpulse, jumping, sprinting);
    }

    private boolean shouldJump(Path path, ClientPlayerEntity player, double horizontalDistanceToWaypoint) {
        MovementAction action = path.getCurrentAction();
        if (action != MovementAction.JUMP_UP && action != MovementAction.SPRINT_JUMP) {
            return false;
        }
        if (!player.isOnGround()) {
            return false; // Already airborne from a previous jump trigger; don't re-trigger.
        }
        // Trigger the jump once horizontal distance to the block boundary we're stepping up
        // into is within the configured threshold - not the waypoint center, but the near
        // face of the target block, which is (distance - 0.5) from the centerline waypoint.
        double distanceToBoundary = horizontalDistanceToWaypoint - 0.5;
        return distanceToBoundary <= config.jumpTriggerDistance;
    }

    private static double normalizeRadians(double angle) {
        double twoPi = Math.PI * 2.0;
        angle = angle % twoPi;
        if (angle > Math.PI) angle -= twoPi;
        if (angle < -Math.PI) angle += twoPi;
        return angle;
    }
}
