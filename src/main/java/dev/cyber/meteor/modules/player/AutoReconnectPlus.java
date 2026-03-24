package dev.cyber.meteor.modules.player;

import dev.cyber.meteor.CyberCategories;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.ServerConnectBeginEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;

import java.util.Locale;

public class AutoReconnectPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAutoJoin = settings.createGroup("Auto Join");
    private final SettingGroup sgScoreboard = settings.createGroup("Scoreboard Detect");

    private final Setting<String> targetServer = sgGeneral.add(new StringSetting.Builder()
        .name("target-server")
        .description("Only reconnect automatically when the last server address contains this text.")
        .defaultValue("play.freshsmp.fun")
        .build()
    );

    private final Setting<Double> reconnectDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("reconnect-delay")
        .description("Seconds to wait before reconnecting.")
        .defaultValue(3.0)
        .min(0.0)
        .decimalPlaces(1)
        .build()
    );

    private final Setting<Boolean> autoJoinEnabled = sgAutoJoin.add(new BoolSetting.Builder()
        .name("auto-join-enabled")
        .description("Send a command automatically after reconnecting.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> autoJoinCommand = sgAutoJoin.add(new StringSetting.Builder()
        .name("auto-join-command")
        .description("Command sent after reconnect. Example: /lifesteal")
        .defaultValue("/lifesteal")
        .build()
    );

    private final Setting<Double> autoJoinDelay = sgAutoJoin.add(new DoubleSetting.Builder()
        .name("auto-join-delay")
        .description("Seconds to wait after reconnecting before sending the auto-join command.")
        .defaultValue(5.0)
        .min(0.0)
        .decimalPlaces(1)
        .build()
    );

    private final Setting<Integer> autoJoinAttempts = sgAutoJoin.add(new meteordevelopment.meteorclient.settings.IntSetting.Builder()
        .name("auto-join-attempts")
        .description("How many times to retry the auto-join command before giving up.")
        .defaultValue(3)
        .min(1)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Double> autoJoinRetryDelay = sgAutoJoin.add(new DoubleSetting.Builder()
        .name("auto-join-retry-delay")
        .description("Seconds between auto-join retries.")
        .defaultValue(3.0)
        .min(0.5)
        .decimalPlaces(1)
        .build()
    );

    private final Setting<Boolean> notifyAutoJoin = sgAutoJoin.add(new BoolSetting.Builder()
        .name("notify-auto-join")
        .description("Show a local chat message when AutoReconnect+ sends or stops the auto-join command.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> scoreboardDetectEnabled = sgScoreboard.add(new BoolSetting.Builder()
        .name("scoreboard-detect-enabled")
        .description("Use the right-side scoreboard text to decide whether the auto-join command should be sent.")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> scoreboardReadyText = sgScoreboard.add(new StringSetting.Builder()
        .name("scoreboard-ready-text")
        .description("Optional. If set, this text must be found in the right-side scoreboard before the auto-join command can be sent.")
        .defaultValue("")
        .build()
    );

    private final Setting<String> scoreboardDoneText = sgScoreboard.add(new StringSetting.Builder()
        .name("scoreboard-done-text")
        .description("If this text is found in the right-side scoreboard, the auto-join command will not be sent.")
        .defaultValue("")
        .build()
    );

    private final Setting<Boolean> scoreboardCaseSensitive = sgScoreboard.add(new BoolSetting.Builder()
        .name("scoreboard-case-sensitive")
        .description("Require scoreboard text matches to use exact casing.")
        .defaultValue(false)
        .build()
    );

    private Pair<ServerAddress, ServerInfo> lastServerConnection;
    private int reconnectInTicks = -1;
    private int autoJoinInTicks = -1;
    private boolean reconnectTriggered;
    private boolean autoJoinQueued;
    private boolean autoJoinHandledForConnection;
    private int autoJoinAttemptsLeft;
    private boolean wasConnectedToTargetServer;

    public AutoReconnectPlus() {
        super(CyberCategories.CYBER, "auto-reconnect+", "Reconnects after disconnects and can run a custom join command automatically.");
    }

    @Override
    public void onDeactivate() {
        reconnectInTicks = -1;
        autoJoinInTicks = -1;
        reconnectTriggered = false;
        autoJoinQueued = false;
        autoJoinHandledForConnection = false;
        autoJoinAttemptsLeft = 0;
        wasConnectedToTargetServer = false;
    }

    @EventHandler
    private void onServerConnectBegin(ServerConnectBeginEvent event) {
        if (!matchesTarget(event.address)) return;

        lastServerConnection = new ObjectObjectImmutablePair<>(event.address, event.info);
        autoJoinHandledForConnection = false;

        if (autoJoinEnabled.get()) {
            autoJoinQueued = true;
            autoJoinInTicks = -1;
            autoJoinAttemptsLeft = autoJoinAttempts.get();
        } else {
            autoJoinQueued = false;
            autoJoinAttemptsLeft = 0;
        }

        reconnectTriggered = false;

        reconnectInTicks = -1;
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (!(event.screen instanceof DisconnectedScreen)) return;
        if (lastServerConnection == null) return;
        if (!matchesTarget(lastServerConnection.left())) return;

        reconnectInTicks = secondsToTicks(reconnectDelay.get());
        autoJoinInTicks = -1;
        reconnectTriggered = false;
        autoJoinQueued = false;
        autoJoinHandledForConnection = false;
        autoJoinAttemptsLeft = 0;
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        String message = event.getMessage().getString();
        if (message == null || message.isBlank()) return;

        if (message.contains("You are already queued for Lifesteal.")
            || message.contains("You are now queued for Lifesteal!")
            || message.contains("You are connecting to Lifesteal!")) {
            notifyStatus("auto-join complete");
            autoJoinQueued = false;
            autoJoinInTicks = -1;
            autoJoinHandledForConnection = true;
            autoJoinAttemptsLeft = 0;
            reconnectTriggered = false;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        boolean connectedToTargetServer = isConnectedToTargetServer();
        if (connectedToTargetServer && !wasConnectedToTargetServer) {
            autoJoinHandledForConnection = false;
            if (autoJoinEnabled.get()) {
                autoJoinQueued = true;
                autoJoinInTicks = -1;
                autoJoinAttemptsLeft = autoJoinAttempts.get();
                notifyStatus("detected target server join");
            }
        } else if (!connectedToTargetServer) {
            autoJoinHandledForConnection = false;
        }

        wasConnectedToTargetServer = connectedToTargetServer;

        if (reconnectInTicks >= 0) tickReconnect();
        if (autoJoinQueued) tickAutoJoin();
    }

    private void tickReconnect() {
        if (!(mc.currentScreen instanceof DisconnectedScreen)) {
            reconnectInTicks = -1;
            return;
        }

        if (reconnectInTicks-- > 0) return;

        reconnectInTicks = -1;
        reconnectTriggered = true;
        ConnectScreen.connect(new TitleScreen(), mc, lastServerConnection.left(), lastServerConnection.right(), false, null);
    }

    private void tickAutoJoin() {
        if (mc.player == null || mc.world == null) return;
        if (!isConnectedToTargetServer()) return;
        if (!autoJoinEnabled.get()) {
            autoJoinQueued = false;
            autoJoinInTicks = -1;
            return;
        }
        if (autoJoinHandledForConnection) {
            autoJoinQueued = false;
            autoJoinInTicks = -1;
            return;
        }
        if (scoreboardShowsDoneState()) {
            notifyStatus("auto-join blocked by scoreboard-done-text");
            autoJoinQueued = false;
            autoJoinInTicks = -1;
            autoJoinHandledForConnection = true;
            return;
        }
        if (!scoreboardAllowsAutoJoin()) return;

        if (autoJoinInTicks < 0) autoJoinInTicks = secondsToTicks(autoJoinDelay.get());
        if (autoJoinInTicks-- > 0) return;

        if (autoJoinAttemptsLeft <= 0) {
            notifyStatus("auto-join gave up");
            autoJoinQueued = false;
            autoJoinInTicks = -1;
            autoJoinHandledForConnection = true;
            return;
        }

        String command = autoJoinCommand.get().trim();
        if (!command.isEmpty()) {
            ChatUtils.sendPlayerMsg(command);
            autoJoinAttemptsLeft--;
            notifyStatus("sent " + command + " (" + autoJoinAttemptsLeft + " retries left)");
        }

        if (autoJoinAttemptsLeft <= 0) {
            autoJoinQueued = false;
            autoJoinInTicks = -1;
            autoJoinHandledForConnection = true;
        } else {
            autoJoinInTicks = secondsToTicks(autoJoinRetryDelay.get());
        }
    }

    private boolean isConnectedToTargetServer() {
        ServerInfo server = mc.getCurrentServerEntry();
        String target = targetServer.get().trim();
        return server != null && server.address != null && !target.isEmpty() && server.address.contains(target);
    }

    private boolean scoreboardAllowsAutoJoin() {
        if (!scoreboardDetectEnabled.get()) return true;

        String ready = scoreboardReadyText.get().trim();
        if (ready.isEmpty()) return true;

        return scoreboardContains(ready);
    }

    private boolean scoreboardShowsDoneState() {
        if (!scoreboardDetectEnabled.get()) return false;

        String done = scoreboardDoneText.get().trim();
        return !done.isEmpty() && scoreboardContains(done);
    }

    private boolean scoreboardContains(String needle) {
        String haystack = collectScoreboardText();
        if (haystack.isBlank()) return false;

        if (scoreboardCaseSensitive.get()) return haystack.contains(needle);

        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private String collectScoreboardText() {
        if (mc.world == null) return "";

        Scoreboard scoreboard = mc.world.getScoreboard();
        if (scoreboard == null) return "";

        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (objective == null) return "";

        StringBuilder builder = new StringBuilder();
        appendText(builder, objective.getDisplayName());

        for (ScoreboardEntry entry : scoreboard.getScoreboardEntries(objective)) {
            appendText(builder, entry.name());
            appendText(builder, entry.display());
        }

        return builder.toString();
    }

    private void appendText(StringBuilder builder, Text text) {
        if (text == null) return;
        if (!builder.isEmpty()) builder.append('\n');
        builder.append(text.getString());
    }

    private boolean matchesTarget(ServerAddress address) {
        String target = targetServer.get().trim();
        return address != null && !target.isEmpty() && address.getAddress().contains(target);
    }

    private int secondsToTicks(double seconds) {
        return Math.max(0, (int) Math.round(seconds * 20.0));
    }

    private void notifyStatus(String message) {
        if (!notifyAutoJoin.get()) return;
        ChatUtils.sendMsg(0, "AutoReconnect+", net.minecraft.util.Formatting.LIGHT_PURPLE, net.minecraft.util.Formatting.GRAY, message);
    }
}
