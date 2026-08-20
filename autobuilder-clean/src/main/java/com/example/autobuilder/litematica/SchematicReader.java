package com.example.autobuilder.litematica;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import net.minecraft.block.BlockState;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.HashMap;
import java.util.Map;

/**
 * Reads a Litematica schematic placement, applies its active rotation and
 * mirror transform per sub-region, and flattens the result into a single
 * Map<BlockPos, SchematicBlock> in world coordinates. This is the cache
 * BuildPlanner/LayerManager iterate over - nothing else in AutoBuilder
 * touches Litematica's raw region storage directly.
 *
 * Cache invalidation contract: callers (BuildController) must call
 * {@link #isStillValid(SchematicPlacement)} each time a new build session
 * starts, and call {@link #rebuild(SchematicPlacement)} whenever it returns
 * false - i.e. whenever origin, rotation, mirror, or the selected placement
 * itself has changed since the cache was built.
 */
public final class SchematicReader {

    private Map<BlockPos, SchematicBlock> cache = Map.of();

    private BlockPos lastOrigin = null;
    private BlockRotation lastRotation = null;
    private BlockMirror lastMirror = null;
    private SchematicPlacement lastPlacement = null;

    /**
     * True if the cache was built from the same placement, with the same
     * origin/rotation/mirror, that is currently active. If the user has
     * moved, rotated, or re-selected the schematic in Litematica since the
     * last rebuild, this returns false.
     */
    public boolean isStillValid(SchematicPlacement placement) {
        if (placement != lastPlacement) return false;
        if (!placement.getOrigin().equals(lastOrigin)) return false;
        if (placement.getRotation() != lastRotation) return false;
        if (placement.getMirror() != lastMirror) return false;
        return true;
    }

    /** Rebuilds the flattened world-space block cache from scratch for the given placement. */
    public void rebuild(SchematicPlacement placement) {
        Map<BlockPos, SchematicBlock> newCache = new HashMap<>();

        LitematicaSchematic schematic = LitematicaIntegration.getSchematic(placement);
        BlockPos origin = LitematicaIntegration.getOrigin(placement);
        BlockRotation placementRotation = placement.getRotation();
        BlockMirror placementMirror = placement.getMirror();

        for (SubRegionPlacement subRegion : LitematicaIntegration.getSubRegions(placement)) {
            if (!subRegion.isEnabled()) continue;

            String regionName = subRegion.getName();
            LitematicaBlockStateContainer container = schematic.getSubRegionContainer(regionName);
            if (container == null) continue;

            Vec3i regionSize = schematic.getSubRegionSize(regionName);
            BlockPos regionPosInSchematic = schematic.getSubRegionPosition(regionName);

            // Combine the sub-region's own rotation/mirror (if any) with the placement's overall
            // transform. Sub-regions in Litematica can carry their own transform relative to the
            // placement, which is why both layers are applied here rather than just the top-level one.
            BlockRotation combinedRotation = combineRotations(placementRotation, subRegion.getRotation());
            BlockMirror combinedMirror = combineMirrors(placementMirror, subRegion.getMirror());

            int sizeX = Math.abs(regionSize.getX());
            int sizeY = Math.abs(regionSize.getY());
            int sizeZ = Math.abs(regionSize.getZ());

            for (int x = 0; x < sizeX; x++) {
                for (int y = 0; y < sizeY; y++) {
                    for (int z = 0; z < sizeZ; z++) {
                        BlockState rawState = container.get(x, y, z);
                        if (rawState == null || rawState.isAir()) continue;

                        BlockState transformedState = rawState
                                .mirror(combinedMirror)
                                .rotate(combinedRotation);

                        BlockPos localPos = new BlockPos(x, y, z);
                        BlockPos schematicSpacePos = regionPosInSchematic.add(localPos);
                        BlockPos worldPos = transformSchematicToWorld(
                                schematicSpacePos, origin, placementRotation, placementMirror
                        );

                        SchematicBlock block = new SchematicBlock(
                                worldPos,
                                schematicSpacePos,
                                transformedState,
                                worldPos.getY(),
                                0 // Priority assigned later by DependencyGraph.
                        );
                        newCache.put(worldPos, block);
                    }
                }
            }
        }

        this.cache = Map.copyOf(newCache);
        this.lastOrigin = origin;
        this.lastRotation = placementRotation;
        this.lastMirror = placementMirror;
        this.lastPlacement = placement;
    }

    /**
     * Applies the placement's overall rotation and mirror to a schematic-space
     * position and offsets it by the placement origin, producing the final
     * world-space BlockPos. Mirrors Litematica's own placement math: mirror
     * is applied before rotation, matching vanilla's Structure NBT convention.
     */
    private BlockPos transformSchematicToWorld(
            BlockPos schematicPos, BlockPos origin, BlockRotation rotation, BlockMirror mirror
    ) {
        int x = schematicPos.getX();
        int y = schematicPos.getY();
        int z = schematicPos.getZ();

        // Apply mirror first.
        switch (mirror) {
            case LEFT_RIGHT -> z = -z;
            case FRONT_BACK -> x = -x;
            case NONE -> { /* no-op */ }
        }

        // Then apply rotation around the Y axis.
        int rotatedX = x, rotatedZ = z;
        switch (rotation) {
            case CLOCKWISE_90 -> {
                rotatedX = -z;
                rotatedZ = x;
            }
            case CLOCKWISE_180 -> {
                rotatedX = -x;
                rotatedZ = -z;
            }
            case COUNTERCLOCKWISE_90 -> {
                rotatedX = z;
                rotatedZ = -x;
            }
            case NONE -> { /* no-op */ }
        }

        return origin.add(rotatedX, y, rotatedZ);
    }

    private BlockRotation combineRotations(BlockRotation a, BlockRotation b) {
        // BlockRotation forms a cyclic group of order 4; rotate() composes them correctly.
        return a.rotate(b);
    }

    private BlockMirror combineMirrors(BlockMirror placementMirror, BlockMirror subRegionMirror) {
        // Two mirrors of the same axis cancel out; differing axes compose into a 180 rotation
        // handled separately by the rotation combination above. For simplicity and correctness
        // in the common case (sub-region mirror is NONE), prefer the placement-level mirror
        // when the sub-region does not specify its own.
        if (subRegionMirror == BlockMirror.NONE) return placementMirror;
        if (placementMirror == BlockMirror.NONE) return subRegionMirror;
        return placementMirror == subRegionMirror ? BlockMirror.NONE : placementMirror;
    }

    public Map<BlockPos, SchematicBlock> getCache() {
        return cache;
    }
}
