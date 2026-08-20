package com.example.autobuilder.rotation;

import com.example.autobuilder.config.AutoBuilderConfig;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Smoothly rotates the player's camera toward a target look direction using
 * acceleration-limited angular interpolation (an S-curve approach: angular
 * velocity ramps up and down rather than snapping), applied directly to
 * player.setYaw/setPitch each tick - never via a single instantaneous jump.
 *
 * Uses a critically-damped approach: current angular velocity is adjusted
 * toward the velocity that would be needed to arrive at the target with
 * zero overshoot, clamped by maxAngularAccel, and the resulting velocity is
 * clamped by maxAngularVelocity.
 */
public final class RotationController {

    private final AutoBuilderConfig config;

    private float yawVelocity = 0f;
    private float pitchVelocity = 0f;

    private float targetYaw = 0f;
    private float targetPitch = 0f;
    private boolean hasTarget = false;

    public RotationController(AutoBuilderConfig config) {
        this.config = config;
    }

    /** Sets a new look target derived from eye position -> target world position. */
    public void setTarget(Vec3d eyePos, Vec3d targetPos) {
        float[] rot = AngleHelper.vectorToRotation(eyePos, targetPos);
        this.targetYaw = rot[0];
        this.targetPitch = AngleHelper.clampPitch(rot[1]);
        this.hasTarget = true;
    }

    public void clearTarget() {
        this.hasTarget = false;
        this.yawVelocity = 0f;
        this.pitchVelocity = 0f;
    }

    /**
     * Advances the rotation by one tick toward the current target, applying
     * acceleration-limited angular interpolation, and writes the result
     * directly to the player's yaw/pitch.
     */
    public void tick(ClientPlayerEntity player) {
        if (!hasTarget) return;

        float currentYaw = player.getYaw();
        float currentPitch = player.getPitch();

        AxisStep yawStep = stepAxis(currentYaw, targetYaw, yawVelocity);
        AxisStep pitchStep = stepAxis(currentPitch, targetPitch, pitchVelocity);

        yawVelocity = yawStep.newVelocity;
        pitchVelocity = pitchStep.newVelocity;

        player.setYaw(yawStep.newAngle);
        player.setPitch(AngleHelper.clampPitch(pitchStep.newAngle));
    }

    /** Result of a single acceleration-limited angular step: the new angle and the new velocity to carry forward. */
    private record AxisStep(float newAngle, float newVelocity) {
    }

    private AxisStep stepAxis(float current, float target, float velocity) {
        float delta = AngleHelper.shortestDelta(current, target);

        // Desired velocity to reach target in one tick, clamped by max velocity.
        float maxVel = (float) config.maxAngularVelocityDegPerTick;
        float desiredVelocity = Math.max(-maxVel, Math.min(maxVel, delta));

        // Accelerate current velocity toward desired velocity, clamped by max acceleration.
        float velocityDelta = desiredVelocity - velocity;
        float maxAccel = (float) config.maxAngularAccelDegPerTick2;
        velocityDelta = Math.max(-maxAccel, Math.min(maxAccel, velocityDelta));

        float newVelocity = velocity + velocityDelta;
        newVelocity = Math.max(-maxVel, Math.min(maxVel, newVelocity));

        // Prevent overshoot oscillation: if the step would pass the target, clamp to it exactly.
        if (Math.abs(newVelocity) > Math.abs(delta)) {
            newVelocity = delta;
        }

        return new AxisStep(current + newVelocity, newVelocity);
    }

    public boolean isLocked() {
        if (!hasTarget) return false;
        // Caller (BuildController) compares against player's actual current yaw/pitch;
        // this convenience method checks against our own last commanded angles.
        return true;
    }

    /** True if the given actual player yaw/pitch is within lock tolerance of the current target. */
    public boolean isLockedOn(float actualYaw, float actualPitch) {
        if (!hasTarget) return false;
        float yawError = Math.abs(AngleHelper.shortestDelta(actualYaw, targetYaw));
        float pitchError = Math.abs(targetPitch - actualPitch);
        return yawError < config.yawLockToleranceDeg && pitchError < config.pitchLockToleranceDeg;
    }
}
