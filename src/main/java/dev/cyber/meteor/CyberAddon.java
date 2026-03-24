package dev.cyber.meteor;

import dev.cyber.meteor.commands.CPosCommand;
import dev.cyber.meteor.modules.world.NukerPlus;
import dev.cyber.meteor.commands.SPingCommand;
import dev.cyber.meteor.modules.combat.AutoWeaponPlus;
import dev.cyber.meteor.modules.combat.KillAuraPlus;
import dev.cyber.meteor.modules.combat.TriggerBot;
import dev.cyber.meteor.modules.movement.ScaffoldPlus;
import dev.cyber.meteor.modules.movement.SprintPlus;
import dev.cyber.meteor.modules.player.AutoEatPlus;
import dev.cyber.meteor.modules.player.AutoChat;
import dev.cyber.meteor.modules.player.AutoReconnectPlus;
import dev.cyber.meteor.modules.player.AutoToolPlus;
import dev.cyber.meteor.modules.player.Mc265322Fix;
import dev.cyber.meteor.modules.render.ItemID;
import dev.cyber.meteor.modules.render.OreSim;
import dev.cyber.meteor.modules.render.OreReveal;
import dev.cyber.meteor.utils.rotation.RotationManager;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class CyberAddon extends MeteorAddon {
    public void onInitialize() {
        Commands.add(new CPosCommand());
        Commands.add(new SPingCommand());
        MeteorClient.EVENT_BUS.subscribe(RotationManager.INSTANCE);
        Modules.get().add(new TriggerBot());
        Modules.get().add(new KillAuraPlus());
        Modules.get().add(new AutoWeaponPlus());
        Modules.get().add(new ItemID());
        Modules.get().add(new AutoEatPlus());
        Modules.get().add(new AutoChat());
        Modules.get().add(new AutoReconnectPlus());
        Modules.get().add(new AutoToolPlus());
        Modules.get().add(new Mc265322Fix());
        Modules.get().add(new SprintPlus());
        Modules.get().add(new ScaffoldPlus());
        Modules.get().add(new OreSim());
        Modules.get().add(new OreReveal());
        Modules.get().add(new NukerPlus());
    }

    public void onRegisterCategories() {
        Modules.registerCategory(CyberCategories.CYBER);
    }

    public String getPackage() {
        return "dev.cyber.meteor";
    }

    public String getWebsite() {
        return null;
    }

    public GithubRepo getRepo() {
        return null;
    }
}
