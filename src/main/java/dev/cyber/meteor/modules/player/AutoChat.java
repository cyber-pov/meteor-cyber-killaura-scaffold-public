package dev.cyber.meteor.modules.player;

import dev.cyber.meteor.CyberCategories;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoChat extends Module {
    private static final Pattern GUESS_NUMBER_PATTERN = Pattern.compile("Guess A Number:\\s*(-?\\d+)\\s*-\\s*(-?\\d+)");

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgGG = settings.createGroup("GG");
    private final SettingGroup sgChatGame = settings.createGroup("Chat Game");

    private final Setting<Integer> cooldownTicks = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown-ticks")
        .description("Minimum delay before AutoChat can send again.")
        .defaultValue(100)
        .min(0)
        .sliderRange(0, 1200)
        .build()
    );

    private final Setting<Boolean> ggEnabled = sgGG.add(new BoolSetting.Builder()
        .name("gg-enabled")
        .description("Respond to GG event announcements.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> ggTrigger = sgGG.add(new StringSetting.Builder()
        .name("gg-trigger")
        .description("Chat text that triggers the GG response.")
        .defaultValue("A GG WAVE HAS BEGUN!")
        .build()
    );

    private final Setting<String> ggResponse = sgGG.add(new StringSetting.Builder()
        .name("gg-response")
        .description("Message sent after the GG trigger is detected.")
        .defaultValue("GG")
        .build()
    );

    private final Setting<Integer> ggDelayTicks = sgGG.add(new IntSetting.Builder()
        .name("gg-delay-ticks")
        .description("Delay before sending the GG response.")
        .defaultValue(20)
        .min(0)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<Boolean> ggCaseSensitive = sgGG.add(new BoolSetting.Builder()
        .name("gg-case-sensitive")
        .description("Require the GG trigger text to match exact casing.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> chatGameEnabled = sgChatGame.add(new BoolSetting.Builder()
        .name("chat-game-enabled")
        .description("Answer simple chat game prompts automatically.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> chatGameDelayTicks = sgChatGame.add(new IntSetting.Builder()
        .name("chat-game-delay-ticks")
        .description("Delay before answering the chat game.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 40)
        .build()
    );

    private int sendInTicks = -1;
    private int cooldownLeft;
    private String pendingMessage;

    public AutoChat() {
        super(CyberCategories.CYBER, "auto-chat", "Handles simple automated chat actions such as GG events and chat games.");
    }

    @Override
    public void onDeactivate() {
        sendInTicks = -1;
        cooldownLeft = 0;
        pendingMessage = null;
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (mc.player == null || mc.world == null || cooldownLeft > 0) return;

        String message = event.getMessage().getString();
        if (message == null || message.isBlank()) return;

        if (ggEnabled.get() && matchesGGTrigger(message)) {
            queueMessage(ggResponse.get().trim(), ggDelayTicks.get());
            return;
        }

        if (chatGameEnabled.get()) {
            String answer = buildChatGameAnswer(message);
            if (answer != null) queueMessage(answer, chatGameDelayTicks.get());
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (cooldownLeft > 0) cooldownLeft--;
        if (sendInTicks < 0 || pendingMessage == null) return;
        if (mc.player == null || mc.world == null) return;

        if (sendInTicks-- > 0) return;

        ChatUtils.sendPlayerMsg(pendingMessage);
        pendingMessage = null;
        sendInTicks = -1;
    }

    private void queueMessage(String message, int delay) {
        if (message == null || message.isBlank()) return;

        pendingMessage = message;
        sendInTicks = delay;
        cooldownLeft = cooldownTicks.get();
    }

    private boolean matchesGGTrigger(String message) {
        String trigger = ggTrigger.get();
        if (trigger == null || trigger.isBlank()) return false;

        return ggCaseSensitive.get()
            ? message.contains(trigger)
            : message.toLowerCase(Locale.ROOT).contains(trigger.toLowerCase(Locale.ROOT));
    }

    private String buildChatGameAnswer(String message) {
        Matcher matcher = GUESS_NUMBER_PATTERN.matcher(message);
        if (!matcher.find()) return null;

        int first = Integer.parseInt(matcher.group(1));
        int second = Integer.parseInt(matcher.group(2));
        int min = Math.min(first, second);
        int max = Math.max(first, second);

        return Integer.toString(ThreadLocalRandom.current().nextInt(min, max + 1));
    }
}
