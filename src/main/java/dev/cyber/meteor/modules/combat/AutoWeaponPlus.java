package dev.cyber.meteor.modules.combat;

import dev.cyber.meteor.CyberCategories;
import dev.cyber.meteor.utils.player.InventorySwitchHelper;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;

public class AutoWeaponPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Weapon> weapon = sgGeneral.add(new EnumSetting.Builder<Weapon>()
        .name("weapon")
        .description("What type of weapon to use.")
        .defaultValue(Weapon.Sword)
        .build()
    );

    private final Setting<Integer> threshold = sgGeneral.add(new IntSetting.Builder()
        .name("threshold")
        .description("If the non-preferred weapon produces this much damage this will favor it over your preferred weapon.")
        .defaultValue(4)
        .build()
    );

    private final Setting<Boolean> antiBreak = sgGeneral.add(new BoolSetting.Builder()
        .name("anti-break")
        .description("Prevents you from breaking your weapon.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> searchInventory = sgGeneral.add(new BoolSetting.Builder()
        .name("search-inventory")
        .description("Searches the full inventory for weapons, not only the hotbar.")
        .defaultValue(false)
        .build()
    );

    public AutoWeaponPlus() {
        super(CyberCategories.CYBER, "auto-weapon+", "Finds the best weapon to use, including inventory search.", "auto-weapon-plus", "autoweaponplus");
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (!(event.entity instanceof LivingEntity livingEntity)) return;

        int slot = getBestWeapon(livingEntity);
        if (slot == -1) return;

        int hotbarSlot = InventorySwitchHelper.prepareHotbarSlot(slot);
        if (hotbarSlot == -1) return;

        InvUtils.swap(hotbarSlot, false);
    }

    private int getBestWeapon(LivingEntity target) {
        int currentSlot = mc.player.getInventory().getSelectedSlot();
        int swordSlot = -1;
        int axeSlot = -1;
        double swordDamage = Double.NEGATIVE_INFINITY;
        double axeDamage = Double.NEGATIVE_INFINITY;

        int end = searchInventory.get() ? 35 : 8;

        for (int i = 0; i <= end; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty() || shouldSkip(stack, i)) continue;

            if (stack.isIn(ItemTags.SWORDS)) {
                double damage = DamageUtils.getAttackDamage(mc.player, target, stack);
                if (damage > swordDamage) {
                    swordDamage = damage;
                    swordSlot = i;
                }
            } else if (stack.getItem() instanceof AxeItem) {
                double damage = DamageUtils.getAttackDamage(mc.player, target, stack);
                if (damage > axeDamage) {
                    axeDamage = damage;
                    axeSlot = i;
                }
            }
        }

        return pickWeaponSlot(currentSlot, swordSlot, swordDamage, axeSlot, axeDamage);
    }

    private boolean shouldSkip(ItemStack stack, int slot) {
        if (antiBreak.get() && stack.isDamageable() && (stack.getMaxDamage() - stack.getDamage()) <= 10) return true;
        return slot > 8 && searchInventory.get() && InventorySwitchHelper.findEmptyHotbarSlot() == -1;
    }

    private int pickWeaponSlot(int currentSlot, int swordSlot, double swordDamage, int axeSlot, double axeDamage) {
        if (weapon.get() == Weapon.Sword) {
            if (swordSlot != -1 && (axeSlot == -1 || threshold.get() > axeDamage - swordDamage)) return swordSlot;
            if (axeSlot != -1) return axeSlot;
        } else {
            if (axeSlot != -1 && (swordSlot == -1 || threshold.get() > swordDamage - axeDamage)) return axeSlot;
            if (swordSlot != -1) return swordSlot;
        }

        return currentSlot;
    }

    public enum Weapon {
        Sword,
        Axe
    }
}
