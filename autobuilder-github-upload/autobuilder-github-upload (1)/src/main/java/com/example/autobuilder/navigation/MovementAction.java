package com.example.autobuilder.navigation;

/**
 * The kind of physical action required to traverse a single path edge.
 * MovementController inspects this per-waypoint to decide which Input
 * flags (jumping, sneaking, sprinting) to raise.
 */
public enum MovementAction {
    /** Flat horizontal movement, cardinal direction, no elevation change. */
    WALK,

    /** Flat horizontal movement along a diagonal, no elevation change. */
    WALK_DIAGONAL,

    /** Climb exactly one block of elevation via a jump. */
    JUMP_UP,

    /** Descend 1-3 blocks of elevation onto solid, non-hazardous ground. */
    DROP_DOWN,

    /** Jump across a horizontal gap of more than one block (with or without elevation change). */
    SPRINT_JUMP,

    /** Vertical movement climbing a ladder or vine. */
    ASCEND_LADDER,

    /** Vertical movement descending a ladder or vine. */
    DESCEND_LADDER
}
