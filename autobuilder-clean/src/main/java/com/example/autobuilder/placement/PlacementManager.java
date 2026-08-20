package com.example.autobuilder.placement;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;

/**
 * Executes an already-solved PlacementTarget: selects the correct hotbar
 * slot for the required item, constructs a real BlockHitResult, and
 * dispatches it through the normal client interaction manager exactly as a
 * manual right-click would - meaning the server sees a legitimate,
 * indistinguishable-from-manual interact packet.
 */
public final class PlacementManager {

    /**
     * Attempts to select the hotbar slot containing enough of the required
     * item. Returns false (and does not touch the current slot) if the item
     * is not present in the hotbar - InventoryManager is responsible for
     * shuffling items into the hotbar before this is called.
     */
    public boolean selectHotbarSlotFor(ClientPlayerEntity player, net.minecraft.item.Item requiredItem) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (!stack.isEmpty() && stack.getItem() == requiredItem) {
                player.getInventory().setSelectedSlot(slot);
                return true;
            }
        }
        return false;
    }

    /**
     * Executes the placement: applies sneak state if required, dispatches
     * the interactBlock packet with the solved hit vector/face, and
     * triggers the arm swing animation. Returns the ActionResult from the
     * interaction manager so PlacementVerifier can decide whether to expect
     * a resulting block-state change.
     */
    public ActionResult executePlacement(MinecraftClient client, PlacementTarget target) {
        ClientPlayerEntity player = client.player;
        ClientPlayerInteractionManager interactionManager = client.interactionManager;
        if (player == null || interactionManager == null) {
            return ActionResult.FAIL;
        }

        boolean wasSneaking = player.isSneaking();
        if (target.requiresSneak() && !wasSneaking) {
            player.setSneaking(true);
        }

        BlockHitResult hitResult = new BlockHitResult(
                target.hitVec(),
                target.clickFace(),
                target.neighborPos(),
                false
        );

        ActionResult result = interactionManager.interactBlock(player, Hand.MAIN_HAND, hitResult);

        if (result.isAccepted()) {
            player.swingHand(Hand.MAIN_HAND);
        }

        if (target.requiresSneak() && !wasSneaking) {
            player.setSneaking(false);
        }

        return result;
    }
}
