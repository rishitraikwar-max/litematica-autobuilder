package com.example.autobuilder.config;

/**
 * Central, mutable configuration store for AutoBuilder.
 *
 * This is intentionally a plain class with public mutable fields rather than
 * a record, since MaLiLib's config GUI system binds directly to mutable
 * fields via reflection-friendly getters/setters. If/when a MaLiLib config
 * screen is wired up, each field below maps to one config option.
 */
public final class AutoBuilderConfig {

    // ---------------------------------------------------------------
    // Reach & placement
    // ---------------------------------------------------------------

    /** Minimum allowed distance (meters) from eye position to block face when placing. */
    public double minPlacementReach = 2.0;

    /** Maximum allowed distance (meters) from eye position to block face when placing. */
    public double maxPlacementReach = 4.2;

    /** Absolute vanilla interaction reach ceiling; standing positions may never exceed this. */
    public double absoluteReachCap = 4.5;

    /** Minimum standing-position search radius around a target block, in blocks. */
    public int standSearchRadiusMin = 1;

    /** Maximum standing-position search radius around a target block, in blocks. */
    public int standSearchRadiusMax = 3;

    // ---------------------------------------------------------------
    // Movement speeds
    // ---------------------------------------------------------------

    /** Multiplier applied to forward/strafe impulse magnitude (1.0 = normal walk). */
    public float movementSpeedMultiplier = 1.0f;

    /** Whether sprinting is permitted during long-distance repathing moves. */
    public boolean allowSprint = true;

    /** Distance (blocks) to the next waypoint below which sprint is disabled for precision. */
    public double sprintDisableDistance = 3.0;

    /** Radius (meters) around a waypoint considered "arrived" (dead-zone). */
    public double waypointDeadZone = 0.25;

    /** Horizontal distance (meters) to a block boundary at which a scheduled jump is triggered. */
    public double jumpTriggerDistance = 0.4;

    // ---------------------------------------------------------------
    // Rotation
    // ---------------------------------------------------------------

    /** Maximum angular velocity, in degrees per tick, for camera rotation. */
    public double maxAngularVelocityDegPerTick = 12.0;

    /** Maximum angular acceleration, in degrees per tick^2, for camera rotation easing. */
    public double maxAngularAccelDegPerTick2 = 3.0;

    /** Yaw error (degrees) inside which the camera is considered "locked" for placement. */
    public double yawLockToleranceDeg = 2.5;

    /** Pitch error (degrees) inside which the camera is considered "locked" for placement. */
    public double pitchLockToleranceDeg = 2.5;

    // ---------------------------------------------------------------
    // Safety
    // ---------------------------------------------------------------

    /** Maximum fall distance (blocks) considered non-hazardous without water below. */
    public int maxSafeFallDistance = 3;

    /** Ticks of near-zero horizontal displacement before StuckDetector flags a stall. */
    public int stuckDetectionTicks = 15;

    /** Horizontal displacement (meters) per tick below which movement counts as "not moving". */
    public double stuckDisplacementThreshold = 0.05;

    /** Keybind translation key for the emergency-stop hotkey (default: K). */
    public String emergencyStopKeyTranslationKey = "key.autobuilder.emergency_stop";

    // ---------------------------------------------------------------
    // Debug
    // ---------------------------------------------------------------

    public boolean debugRenderPath = true;
    public boolean debugRenderTargetBlock = true;
    public boolean debugRenderLayerBounds = true;
    public boolean debugLogStateTransitions = false;
    public boolean debugLogPathfinding = false;

    // ---------------------------------------------------------------
    // Layer processing order
    // ---------------------------------------------------------------

    public enum LayerOrder {
        LOWEST_TO_HIGHEST,
        HIGHEST_TO_LOWEST,
        RANGE
    }

    public LayerOrder layerOrder = LayerOrder.LOWEST_TO_HIGHEST;

    /** Inclusive Y range used only when layerOrder == RANGE. */
    public int rangeStartY = 0;
    public int rangeEndY = 0;

    private static AutoBuilderConfig instance;

    public static AutoBuilderConfig getInstance() {
        if (instance == null) {
            instance = new AutoBuilderConfig();
        }
        return instance;
    }

    private AutoBuilderConfig() {
    }
}
