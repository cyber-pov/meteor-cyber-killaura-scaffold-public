package dev.cyber.meteor.modules.player;

import dev.cyber.meteor.CyberCategories;
import java.util.List;
import java.util.function.Predicate;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Xray;
import meteordevelopment.meteorclient.systems.modules.world.InfinityMiner;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BambooBlock;
import net.minecraft.block.BambooShootBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ToolComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShearsItem;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.BlockPos;

public class AutoToolPlus
extends Module {
    private final SettingGroup sgGeneral;
    private final SettingGroup sgWhitelist;
    private final Setting<ToolSelector> toolSelector;
    private final Setting<EnchantPreference> prefer;
    private final Setting<Boolean> silkTouchForEnderChest;
    private final Setting<Boolean> fortuneForOresCrops;
    private final Setting<Boolean> antiBreak;
    private final Setting<Integer> breakDurability;
    private final Setting<Double> minScoreGain;
    private final Setting<ListMode> listMode;
    private final Setting<List<Item>> whitelist;
    private final Setting<List<Item>> blacklist;
    private final Setting<Integer> staticSlot;

    public AutoToolPlus() {
        super(CyberCategories.CYBER, "auto-tool+", "Automatically switches to the most effective mining tool.", new String[]{"auto-tool-plus", "autotoolplus"});
        this.sgGeneral = this.settings.getDefaultGroup();
        this.sgWhitelist = this.settings.createGroup("Whitelist");
        this.toolSelector = (Setting<dev.cyber.meteor.modules.player.AutoToolPlus.ToolSelector>) this.sgGeneral.add((new EnumSetting.Builder<ToolSelector>()).name("tool-selector").description("How the slot is selected.").defaultValue(ToolSelector.Dynamic).build());
        this.prefer = this.sgGeneral.add((Setting)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("prefer")).description("Either to prefer Silk Touch, Fortune, or none.")).defaultValue(EnchantPreference.Fortune)).build());
        this.silkTouchForEnderChest = this.sgGeneral.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("silk-touch-for-ender-chest")).description("Mines Ender Chests only with the Silk Touch enchantment.")).defaultValue(true)).build());
        this.fortuneForOresCrops = this.sgGeneral.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("fortune-for-ores-and-crops")).description("Mines ores and crops only with the Fortune enchantment.")).defaultValue(false)).build());
        this.antiBreak = this.sgGeneral.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("anti-break")).description("Stops using tools that are near breaking.")).defaultValue(true)).build());
        this.breakDurability = this.sgGeneral.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("anti-break-percentage")).description("Durability percentage at which tools are avoided.")).defaultValue(10)).range(1, 100).sliderRange(1, 100).visible(() -> this.antiBreak.get())).build());
        this.minScoreGain = this.sgGeneral.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("min-score-gain")).description("Minimum relative score increase required to switch. 0 = always choose best.")).defaultValue(0.0).min(0.0).sliderMax(0.5).build());
        this.listMode = this.sgWhitelist.add((Setting)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("list-mode")).description("Selection mode.")).defaultValue(ListMode.Blacklist)).build());
        this.whitelist = this.sgWhitelist.add((Setting)((ItemListSetting.Builder)((ItemListSetting.Builder)((ItemListSetting.Builder)new ItemListSetting.Builder().name("whitelist")).description("Allowed tools.")).visible(() -> this.listMode.get() == ListMode.Whitelist)).filter(AutoToolPlus::isTool).build());
        this.blacklist = this.sgWhitelist.add((Setting)((ItemListSetting.Builder)((ItemListSetting.Builder)((ItemListSetting.Builder)new ItemListSetting.Builder().name("blacklist")).description("Disallowed tools.")).visible(() -> this.listMode.get() == ListMode.Blacklist)).filter(AutoToolPlus::isTool).build());
        this.staticSlot = (Setting<Integer>) this.sgGeneral.add((new IntSetting.Builder()).defaultValue(0).name("static-slot").description("The hotbar slot used in static mode.").range(0, 8).sliderRange(0, 8).build());
    }

    @EventHandler(priority=100)
    private void onStartBreakingBlock(StartBreakingBlockEvent event) {
        ItemStack after;
        boolean needSwitch;
        if (Modules.get().isActive(InfinityMiner.class)) {
            return;
        }
        if (this.mc.player.isCreative()) {
            return;
        }
        BlockState state = this.mc.world.getBlockState(event.blockPos);
        if (!BlockUtils.canBreak((BlockPos)event.blockPos, (BlockState)state)) {
            return;
        }
        ItemStack current = this.mc.player.getMainHandStack();
        int currentSlot = this.mc.player.getInventory().getSelectedSlot();
        int bestSlot = -1;
        double bestScore = -1.0;

        if (this.toolSelector.get() == ToolSelector.Static) {
            bestSlot = Math.max(0, Math.min((Integer) this.staticSlot.get(), 8));
            ItemStack staticStack = this.mc.player.getInventory().getStack(bestSlot);
            bestScore = AutoToolPlus.getScore(staticStack, state, (Boolean)this.silkTouchForEnderChest.get(), (Boolean)this.fortuneForOresCrops.get(), (EnchantPreference)(this.prefer.get()), this::isAllowedByList);
            if (bestScore < 0.0) bestSlot = -1;
        } else {
            for (int i = 0; i < 9; ++i) {
                double score;
                ItemStack stack = this.mc.player.getInventory().getStack(i);
                if (!isCandidate(stack, i) || !((score = AutoToolPlus.getScore(stack, state, (Boolean)this.silkTouchForEnderChest.get(), (Boolean)this.fortuneForOresCrops.get(), (EnchantPreference)(this.prefer.get()), s -> !this.shouldStopUsing((ItemStack)s))) > bestScore)) continue;
                bestScore = score;
                bestSlot = i;
            }
        }

        double currentScore = AutoToolPlus.getScore(current, state, (Boolean)this.silkTouchForEnderChest.get(), (Boolean)this.fortuneForOresCrops.get(), (EnchantPreference)(this.prefer.get()), s -> !this.shouldStopUsing((ItemStack)s));
        if (this.toolSelector.get() == ToolSelector.Static) {
            needSwitch = bestSlot != -1 && bestSlot != currentSlot;
        } else {
            needSwitch = bestSlot != -1 && bestSlot != currentSlot && (bestScore > currentScore * (1.0 + (Double)this.minScoreGain.get()) || this.shouldStopUsing(current) || !AutoToolPlus.isTool(current));
        }
        if (needSwitch) InvUtils.swap(bestSlot, false);
        if (this.shouldStopUsing(after = this.mc.player.getMainHandStack()) && AutoToolPlus.isTool(after)) {
            this.mc.options.attackKey.setPressed(false);
            event.cancel();
        }
    }

    private boolean shouldStopUsing(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageable()) {
            return false;
        }
        int remaining = stack.getMaxDamage() - stack.getDamage();
        double threshold = (double)stack.getMaxDamage() * ((double)((Integer)this.breakDurability.get()).intValue() / 100.0);
        return (Boolean)this.antiBreak.get() != false && (double)remaining < threshold;
    }

    private boolean isCandidate(ItemStack stack, int slot) {
        return isAllowedByList(stack);
    }

    private boolean isAllowedByList(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (this.listMode.get() == ListMode.Whitelist) return ((List)this.whitelist.get()).contains(stack.getItem());
        if (this.listMode.get() == ListMode.Blacklist) return !((List)this.blacklist.get()).contains(stack.getItem());
        return true;
    }

    public static double getScore(ItemStack stack, BlockState state, boolean silkTouchEnderChest, boolean fortuneOre, EnchantPreference preference, Predicate<ItemStack> allowed) {
        if (!allowed.test(stack) || !AutoToolPlus.isTool(stack)) {
            return -1.0;
        }
        if (!(stack.isSuitableFor(state) || stack.isIn(ItemTags.SWORDS) && (state.getBlock() instanceof BambooBlock || state.getBlock() instanceof BambooShootBlock) || stack.getItem() instanceof ShearsItem && (state.getBlock() instanceof LeavesBlock || state.isIn(BlockTags.WOOL)))) {
            return -1.0;
        }
        if (silkTouchEnderChest && state.isOf(Blocks.ENDER_CHEST) && !Utils.hasEnchantment((ItemStack)stack, (RegistryKey)Enchantments.SILK_TOUCH)) {
            return -1.0;
        }
        if (fortuneOre && AutoToolPlus.isFortunable(state.getBlock()) && !Utils.hasEnchantment((ItemStack)stack, (RegistryKey)Enchantments.FORTUNE)) {
            return -1.0;
        }
        double score = 0.0;
        score += (double)(stack.getMiningSpeedMultiplier(state) * 1000.0f);
        score += (double)Utils.getEnchantmentLevel((ItemStack)stack, (RegistryKey)Enchantments.UNBREAKING);
        score += (double)Utils.getEnchantmentLevel((ItemStack)stack, (RegistryKey)Enchantments.EFFICIENCY);
        score += (double)Utils.getEnchantmentLevel((ItemStack)stack, (RegistryKey)Enchantments.MENDING);
        if (preference == EnchantPreference.Fortune) {
            score += (double)Utils.getEnchantmentLevel((ItemStack)stack, (RegistryKey)Enchantments.FORTUNE);
        }
        if (preference == EnchantPreference.SilkTouch) {
            score += (double)Utils.getEnchantmentLevel((ItemStack)stack, (RegistryKey)Enchantments.SILK_TOUCH);
        }
        if (stack.isIn(ItemTags.SWORDS) && (state.getBlock() instanceof BambooBlock || state.getBlock() instanceof BambooShootBlock)) {
            score += (double)(9000.0f + ((ToolComponent)stack.get(DataComponentTypes.TOOL)).getSpeed(state) * 1000.0f);
        }
        return score;
    }

    public static boolean isTool(Item item) {
        return AutoToolPlus.isTool(item.getDefaultStack());
    }

    public static boolean isTool(ItemStack stack) {
        return stack.isIn(ItemTags.AXES) || stack.isIn(ItemTags.HOES) || stack.isIn(ItemTags.PICKAXES) || stack.isIn(ItemTags.SHOVELS) || stack.getItem() instanceof ShearsItem;
    }

    private static boolean isFortunable(Block block) {
        if (block == Blocks.ANCIENT_DEBRIS) {
            return false;
        }
        return Xray.ORES.contains(block) || block instanceof CropBlock;
    }

    public static enum EnchantPreference {
        None,
        Fortune,
        SilkTouch;

    }

    public static enum ToolSelector {
        Dynamic,
        Static
    }

    public static enum ListMode {
        Whitelist,
        Blacklist;

    }
}
