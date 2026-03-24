package dev.cyber.meteor.modules.render;

import dev.cyber.meteor.CyberCategories;
import meteordevelopment.meteorclient.events.game.ItemStackTooltipEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ItemID
extends Module {
    public ItemID() {
        super(CyberCategories.CYBER, "item-id", "Shows the hovered item's registry ID in tooltips.", new String[]{"itemid"});
    }

    @EventHandler
    private void onItemStackTooltip(ItemStackTooltipEvent event) {
        String id = Registries.ITEM.getId(event.itemStack().getItem()).toString();
        event.appendEnd((Text)Text.literal((String)("ID: " + id)).formatted(Formatting.DARK_GRAY));
    }
}

