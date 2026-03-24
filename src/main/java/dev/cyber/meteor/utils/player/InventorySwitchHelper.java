package dev.cyber.meteor.utils.player;

import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import net.minecraft.item.ItemStack;

public final class InventorySwitchHelper {
    private InventorySwitchHelper() {
    }

    public static int prepareHotbarSlot(int slot) {
        if (slot == SlotUtils.OFFHAND) return slot;
        if (SlotUtils.isHotbar(slot)) return slot;
        if (!SlotUtils.isMain(slot)) return -1;

        int emptySlot = findEmptyHotbarSlot();
        if (emptySlot == -1) return -1;

        InvUtils.move().from(slot).toHotbar(emptySlot);
        return emptySlot;
    }

    public static int findEmptyHotbarSlot() {
        return InvUtils.find(ItemStack::isEmpty, SlotUtils.HOTBAR_START, SlotUtils.HOTBAR_END).slot();
    }
}
