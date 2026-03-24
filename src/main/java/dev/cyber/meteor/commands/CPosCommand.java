package dev.cyber.meteor.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import java.util.Locale;

public class CPosCommand extends Command {
    public CPosCommand() {
        super("cpos", "Copy your current coordinates to clipboard.", "copypos");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            if (mc.player == null || mc.keyboard == null) {
                error("Player is not available.");
                return SINGLE_SUCCESS;
            }

            double x = mc.player.getX();
            double y = mc.player.getY();
            double z = mc.player.getZ();
            String coords = String.format(Locale.US, "%.3f %.3f %.3f", x, y, z);

            mc.keyboard.setClipboard(coords);
            info("Copied: %s", coords);
            return SINGLE_SUCCESS;
        });
    }
}

