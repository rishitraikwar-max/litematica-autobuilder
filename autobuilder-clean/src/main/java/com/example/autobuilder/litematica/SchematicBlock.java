package com.example.autobuilder.litematica;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

/**
 * A single block from a loaded schematic, resolved into world-space
 * coordinates and ready for the builder/placement pipeline to consume.
 *
 * @param worldPos      Absolute world position this block should end up at.
 * @param schematicPos  Original position within the schematic's own local
 *                      coordinate space (pre-transform), kept for debugging
 *                      and for re-deriving relationships after a placement
 *                      transform changes.
 * @param state         Target BlockState, already rotated/mirrored to match
 *                      the schematic placement's active transform.
 * @param layer         The world Y-level this block belongs to, used by
 *                      LayerManager to group blocks for layer-by-layer building.
 * @param priority      Lower values are placed first within a layer. Used to
 *                      express structural dependency ordering (e.g. support
 *                      blocks before torches, walls before doors).
 */
public record SchematicBlock(
        BlockPos worldPos,
        BlockPos schematicPos,
        BlockState state,
        int layer,
        int priority
) {

    /** True if this entry represents "no block here" (air) and should be skipped by the builder. */
    public boolean isAir() {
        return state.isAir();
    }

    /** Returns a copy of this block with an updated priority, used by DependencyGraph during sorting. */
    public SchematicBlock withPriority(int newPriority) {
        return new SchematicBlock(worldPos, schematicPos, state, layer, newPriority);
    }
}
