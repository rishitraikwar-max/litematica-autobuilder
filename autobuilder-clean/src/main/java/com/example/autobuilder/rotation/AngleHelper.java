package com.example.autobuilder.rotation;

import net.minecraft.util.math.Vec3d;

public final class AngleHelper {

    private AngleHelper() {
    }

    /** Normalizes an angle in degrees to the range [-180, 180). */
    public static float normalizeDegrees(float angle) {
        angle = angle % 360.0f;
        if (angle >= 180.0f) angle -= 360.0f;
        if (angle < -180.0f) angle += 360.0f;
        return angle;
    }

    /** Shortest signed angular delta (degrees) to rotate `from` into `to`, in range [-180, 180). */
    public static float shortestDelta(float from, float to) {
        return normalizeDegrees(to - from);
    }

    /**
     * Converts a world-space direction vector (eye position -> target
     * position) into vanilla yaw/pitch degrees, matching the convention
     * used by Entity.getRotationVector().
     *
     * Returns a float[2] = { yaw, pitch }.
     */
    public static float[] vectorToRotation(Vec3d from, Vec3d to) {
        Vec3d diff = to.subtract(from);
        double horizontalDistance = Math.sqrt(diff.x * diff.x + diff.z * diff.z);

        float yaw = (float) (Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(diff.y, horizontalDistance)));

        return new float[]{normalizeDegrees(yaw), pitch};
    }

    /** Clamps pitch to vanilla's allowed camera range. */
    public static float clampPitch(float pitch) {
        return Math.max(-90.0f, Math.min(90.0f, pitch));
    }
}
