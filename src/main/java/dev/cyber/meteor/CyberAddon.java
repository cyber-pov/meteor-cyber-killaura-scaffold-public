package dev.cyber.meteor;

import dev.cyber.meteor.modules.combat.KillAuraPlus;
import dev.cyber.meteor.modules.movement.ScaffoldPlus;
import dev.cyber.meteor.utils.rotation.RotationManager;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class CyberAddon extends MeteorAddon {
    @Override
    public void onInitialize() {
        MeteorClient.EVENT_BUS.subscribe(RotationManager.INSTANCE);
        Modules.get().add(new KillAuraPlus());
        Modules.get().add(new ScaffoldPlus());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CyberCategories.CYBER);
    }

    @Override
    public String getPackage() {
        return "dev.cyber.meteor";
    }

    @Override
    public String getWebsite() {
        return null;
    }

    @Override
    public GithubRepo getRepo() {
        return null;
    }
}
