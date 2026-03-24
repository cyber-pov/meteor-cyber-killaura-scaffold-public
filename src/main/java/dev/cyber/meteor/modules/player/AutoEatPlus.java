package dev.cyber.meteor.modules.player;

import dev.cyber.meteor.CyberCategories;
import dev.cyber.meteor.modules.combat.KillAuraPlus;
import dev.cyber.meteor.modules.combat.TriggerBot;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import meteordevelopment.meteorclient.events.entity.player.ItemUseCrosshairTargetEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.AnchorAura;
import meteordevelopment.meteorclient.systems.modules.combat.BedAura;
import meteordevelopment.meteorclient.systems.modules.combat.CrystalAura;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.systems.modules.player.AutoGap;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.List;
import java.util.function.BiPredicate;

public class AutoEatPlus extends Module {
    @SuppressWarnings("unchecked")
    private static final Class<? extends Module>[] COMBAT_MODULES = new Class[] {
        KillAura.class,
        KillAuraPlus.class,
        TriggerBot.class,
        CrystalAura.class,
        AnchorAura.class,
        BedAura.class
    };

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgThreshold = settings.createGroup("Threshold");

    public final Setting<List<Item>> blacklist = sgGeneral.add(new ItemListSetting.Builder()
        .name("blacklist")
        .description("Which items to not eat.")
        .defaultValue(
            Items.ENCHANTED_GOLDEN_APPLE,
            Items.GOLDEN_APPLE,
            Items.CHORUS_FRUIT,
            Items.POISONOUS_POTATO,
            Items.PUFFERFISH,
            Items.CHICKEN,
            Items.ROTTEN_FLESH,
            Items.SPIDER_EYE,
            Items.SUSPICIOUS_STEW
        )
        .filter(item -> item.getComponents().get(DataComponentTypes.FOOD) != null)
        .build()
    );

    private final Setting<Boolean> pauseCombatModules = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-combat-modules")
        .description("Pauses combat modules while eating.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseBaritone = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-baritone")
        .description("Pauses Baritone while eating.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> searchInventory = sgGeneral.add(new BoolSetting.Builder()
        .name("search-inventory")
        .description("Searches the full inventory for food, not only the hotbar.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Priority> prioritise = sgGeneral.add(new EnumSetting.Builder<Priority>()
        .name("food-priority")
        .description("Which aspect of the food to prioritise selecting for.")
        .defaultValue(Priority.Saturation)
        .build()
    );

    private final Setting<ThresholdMode> thresholdMode = sgThreshold.add(new EnumSetting.Builder<ThresholdMode>()
        .name("threshold-mode")
        .description("The threshold mode to trigger auto eat.")
        .defaultValue(ThresholdMode.Any)
        .build()
    );

    private final Setting<Double> healthThreshold = sgThreshold.add(new DoubleSetting.Builder()
        .name("health-threshold")
        .description("The level of health you eat at.")
        .defaultValue(10)
        .range(1, 19)
        .sliderRange(1, 19)
        .visible(() -> thresholdMode.get() != ThresholdMode.Hunger)
        .build()
    );

    private final Setting<Integer> hungerThreshold = sgThreshold.add(new IntSetting.Builder()
        .name("hunger-threshold")
        .description("The level of hunger you eat at.")
        .defaultValue(16)
        .range(1, 19)
        .sliderRange(1, 19)
        .visible(() -> thresholdMode.get() != ThresholdMode.Health)
        .build()
    );

    public boolean eating;
    private int slot;
    private int prevSlot;

    private final List<Class<? extends Module>> wasCombatModule = new ReferenceArrayList<>();
    private boolean wasBaritone;

    public AutoEatPlus() {
        super(CyberCategories.CYBER, "auto-eat+", "Automatically eats food and pauses combat modules.", "auto-eat-plus", "autoeatplus");
    }

    @Override
    public void onDeactivate() {
        if (eating) stopEating();
    }

    @EventHandler(priority = EventPriority.LOW)
    private void onTick(TickEvent.Pre event) {
        if (Modules.get().get(AutoGap.class).isEating()) return;

        if (eating) {
            if (!shouldEat()) {
                stopEating();
                return;
            }

            if (mc.player.getInventory().getStack(slot).get(DataComponentTypes.FOOD) == null) {
                int newSlot = findSlot();
                if (newSlot == -1) {
                    stopEating();
                    return;
                }

                if (!changeSlot(newSlot)) {
                    stopEating();
                    return;
                }
            }

            eat();
            return;
        }

        if (shouldEat()) startEating();
    }

    @EventHandler
    private void onItemUseCrosshairTarget(ItemUseCrosshairTargetEvent event) {
        if (eating) event.target = null;
    }

    public boolean isEating() {
        return isActive() && eating;
    }

    public boolean shouldEat() {
        boolean healthLow = mc.player.getHealth() <= healthThreshold.get();
        boolean hungerLow = mc.player.getHungerManager().getFoodLevel() <= hungerThreshold.get();
        if (!thresholdMode.get().test(healthLow, hungerLow)) return false;

        slot = findSlot();
        if (slot == -1) return false;

        ItemStack stack = slot == SlotUtils.OFFHAND ? mc.player.getOffHandStack() : mc.player.getInventory().getStack(slot);
        FoodComponent food = stack.get(DataComponentTypes.FOOD);
        if (food == null) return false;

        return mc.player.getHungerManager().isNotFull() || food.canAlwaysEat();
    }

    private void startEating() {
        prevSlot = mc.player.getInventory().getSelectedSlot();
        eat();

        wasCombatModule.clear();
        if (pauseCombatModules.get()) {
            for (Class<? extends Module> klass : COMBAT_MODULES) {
                Module module = Modules.get().get(klass);
                if (module != null && module.isActive()) {
                    wasCombatModule.add(klass);
                    module.toggle();
                }
            }
        }

        if (pauseBaritone.get() && PathManagers.get().isPathing() && !wasBaritone) {
            wasBaritone = true;
            PathManagers.get().pause();
        }
    }

    private void eat() {
        if (!changeSlot(slot)) return;
        setPressed(true);
        if (!mc.player.isUsingItem()) Utils.rightClick();
        eating = true;
    }

    private void stopEating() {
        if (prevSlot != SlotUtils.OFFHAND) changeSlot(prevSlot);
        setPressed(false);
        eating = false;

        if (pauseCombatModules.get()) {
            for (Class<? extends Module> klass : COMBAT_MODULES) {
                if (wasCombatModule.contains(klass)) {
                    Module module = Modules.get().get(klass);
                    if (module != null) module.enable();
                }
            }
        }

        if (pauseBaritone.get() && wasBaritone) {
            wasBaritone = false;
            PathManagers.get().resume();
        }
    }

    private void setPressed(boolean pressed) {
        mc.options.useKey.setPressed(pressed);
    }

    private boolean changeSlot(int slot) {
        if (slot == SlotUtils.OFFHAND) {
            this.slot = SlotUtils.OFFHAND;
            return true;
        }

        if (SlotUtils.isHotbar(slot)) {
            InvUtils.swap(slot, false);
            this.slot = slot;
            return true;
        }

        int emptySlot = InvUtils.find(ItemStack::isEmpty, SlotUtils.HOTBAR_START, SlotUtils.HOTBAR_END).slot();
        if (emptySlot == -1) return false;

        InvUtils.move().from(slot).toHotbar(emptySlot);
        InvUtils.swap(emptySlot, false);
        this.slot = emptySlot;
        return true;
    }

    private int findSlot() {
        Item offHandItem = mc.player.getOffHandStack().getItem();
        FoodComponent offHandFood = offHandItem.getComponents().get(DataComponentTypes.FOOD);
        if (offHandFood != null && !blacklist.get().contains(offHandItem)) return SlotUtils.OFFHAND;

        int slot = findBestFood(SlotUtils.HOTBAR_START, SlotUtils.HOTBAR_END);
        if (slot != -1) return slot;

        if (searchInventory.get()) return findBestFood(SlotUtils.MAIN_START, SlotUtils.MAIN_END);
        return -1;
    }

    private int findBestFood(int start, int end) {
        int best = -1;
        float bestValue = -1;

        for (int i = start; i <= end; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            FoodComponent food = stack.get(DataComponentTypes.FOOD);
            if (food == null) continue;

            Item item = stack.getItem();
            if (blacklist.get().contains(item)) continue;

            float value = prioritise.get().value(food);
            if (value > bestValue) {
                bestValue = value;
                best = i;
            }
        }

        return best;
    }

    public enum ThresholdMode {
        Health((health, hunger) -> health),
        Hunger((health, hunger) -> hunger),
        Any((health, hunger) -> health || hunger),
        Both((health, hunger) -> health && hunger);

        private final BiPredicate<Boolean, Boolean> predicate;

        ThresholdMode(BiPredicate<Boolean, Boolean> predicate) {
            this.predicate = predicate;
        }

        public boolean test(boolean health, boolean hunger) {
            return predicate.test(health, hunger);
        }
    }

    public enum Priority {
        Combined,
        Hunger,
        Saturation;

        public float value(FoodComponent food) {
            return switch (this) {
                case Combined -> food.nutrition() + food.saturation();
                case Hunger -> food.nutrition();
                case Saturation -> food.saturation();
            };
        }
    }
}
