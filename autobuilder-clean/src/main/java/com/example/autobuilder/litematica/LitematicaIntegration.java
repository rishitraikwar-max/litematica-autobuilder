package com.example.autobuilder.litematica;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import fi.dy.masa.litematica.selection.AreaSelection;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Optional;

/**
 * Thin wrapper around Litematica's own DataManager / SchematicPlacementManager
 * APIs. This class deliberately does NOT duplicate or reimplement any of
 * Litematica's schematic-reading logic - it only reads the placement that
 * the user already has active in the Litematica GUI and exposes it in a
 * form SchematicReader can consume.
 *
 * BUILD NOTE: the exact package/class names below (fi.dy.masa.litematica.*)
 * reflect Litematica's structure as of recent public releases. If the
 * 26.2-0.28.4 jar renames or relocates any of these classes, the compiler
 * will report exactly which import fails - fix it by checking Litematica's
 * source on GitHub for that tag and adjusting the import + call sites here.
 * The logic and shape of this class should not need to change even if a
 * class name does.
 */
public final class LitematicaIntegration {

    private LitematicaIntegration() {
    }

    /** Returns the currently selected/active schematic placement in Litematica's GUI, if any. */
    public static Optional<SchematicPlacement> getActivePlacement() {
        SchematicPlacement placement = DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement();
        return Optional.ofNullable(placement);
    }

    /** Returns the underlying LitematicaSchematic data (raw block/region storage) for a placement. */
    public static LitematicaSchematic getSchematic(SchematicPlacement placement) {
        return placement.getSchematic();
    }

    /** World-space origin position of the schematic placement (the anchor point set in-game). */
    public static BlockPos getOrigin(SchematicPlacement placement) {
        return placement.getOrigin();
    }

    /** All sub-region placements belonging to this schematic placement (most schematics have exactly one). */
    public static List<SubRegionPlacement> getSubRegions(SchematicPlacement placement) {
        return List.copyOf(placement.getSubRegionPlacements());
    }

    /**
     * The bounding-box selection covering every sub-region of this
     * placement, in world coordinates. Used by LayerManager to determine
     * the full Y range that needs to be processed.
     */
    public static AreaSelection getWorldSelection(SchematicPlacement placement) {
        return placement.getSelection(false);
    }

    /**
     * True if this placement (or its schematic contents) is currently
     * enabled/rendered in Litematica. AutoBuilder should not attempt to
     * build a placement the user has disabled.
     */
    public static boolean isEnabled(SchematicPlacement placement) {
        return placement.isEnabled();
    }
}
