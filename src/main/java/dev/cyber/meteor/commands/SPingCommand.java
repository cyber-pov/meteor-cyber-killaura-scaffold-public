package dev.cyber.meteor.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.command.CommandSource;

public class SPingCommand extends Command {
    public SPingCommand() {
        super("sping", "Show current server ping in ms.", "ping");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            if (mc.player == null) {
                error("Player is not available.");
                return SINGLE_SUCCESS;
            }

            ClientPlayNetworkHandler network = mc.getNetworkHandler();
            if (network == null) {
                error("Not connected to a server.");
                return SINGLE_SUCCESS;
            }

            PlayerListEntry entry = network.getPlayerListEntry(mc.player.getUuid());
            if (entry == null) {
                error("Failed to read ping.");
                return SINGLE_SUCCESS;
            }

            int ping = entry.getLatency();
            info("Server ping: %d ms", ping);
            return SINGLE_SUCCESS;
        });
    }
}

