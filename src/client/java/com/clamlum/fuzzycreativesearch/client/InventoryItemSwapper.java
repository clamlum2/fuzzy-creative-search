package com.clamlum.fuzzycreativesearch.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class InventoryItemSwapper {

    private final Minecraft minecraft;

    public InventoryItemSwapper(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public int getTargetCreativeSlot() {
        assert minecraft.player != null;
        var inventory = minecraft.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inventory.getItem(i).isEmpty()) {
                return i + 36;
            }
        }
        return inventory.getSelectedSlot() + 36;
    }

    public boolean swapSurvival(Item item) {
        if (minecraft.player == null || minecraft.gameMode == null) return false;

        var player = minecraft.player;
        var inv = player.getInventory();

        int invIndex = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.getItem() == item) {
                invIndex = i;
                break;
            }
        }
        if (invIndex == -1) return false;

        if (invIndex < 9) {
            inv.setSelectedSlot(invIndex);
            return true;
        }

        int targetHotbar = inv.getSelectedSlot();

        int fromMenuSlotId = findMenuSlotIdForInventoryIndex(invIndex);
        int toMenuSlotId   = findMenuSlotIdForInventoryIndex(targetHotbar);
        System.out.println("from=" + fromMenuSlotId + " to=" + toMenuSlotId);
        if (fromMenuSlotId == -1 || toMenuSlotId == -1) return false;

        int containerId = player.containerMenu.containerId;

        click(containerId, fromMenuSlotId);
        click(containerId, toMenuSlotId);
        click(containerId, fromMenuSlotId);

        inv.setSelectedSlot(targetHotbar);
        return true;
    }

    private void click(int containerId, int slotId) {
        assert minecraft.player != null;
        assert minecraft.gameMode != null;
        minecraft.gameMode.handleContainerInput(
                containerId,
                slotId,
                0,
                ContainerInput.PICKUP,
                minecraft.player
        );
    }

    private int findMenuSlotIdForInventoryIndex(int inventoryIndex) {
        assert minecraft.player != null;
        var menu = minecraft.player.containerMenu;
        var inv  = minecraft.player.getInventory();

        for (int menuSlotId = 0; menuSlotId < menu.slots.size(); menuSlotId++) {
            var slot = menu.slots.get(menuSlotId);
            if (slot.container == inv && slot.index == inventoryIndex) {
                return menuSlotId;
            }
        }

        if (inventoryIndex < 9) {
            int hotbarIndex = inventoryIndex + 36;
            for (int menuSlotId = 0; menuSlotId < menu.slots.size(); menuSlotId++) {
                var slot = menu.slots.get(menuSlotId);
                if (slot.container == inv && slot.index == hotbarIndex) {
                    return menuSlotId;
                }
            }
        }

        return -1;
    }
}