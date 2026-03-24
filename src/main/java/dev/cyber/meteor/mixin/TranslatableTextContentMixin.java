package dev.cyber.meteor.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cyber.meteor.modules.player.Mc265322Fix;
import dev.cyber.meteor.utils.protection.ExploitContext;
import dev.cyber.meteor.utils.protection.VanillaKeyRegistry;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Language;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks mod translation keys from being resolved in exploit contexts (sign / anvil).
 *
 * Two-stage approach (mirrors OpSec):
 *   1. @Inject HEAD of updateTranslations() — invalidates languageCache so the method
 *      always re-runs in exploit context and our @WrapOperation can fire.
 *      Without this, a previously-cached resolution would be reused unchanged.
 *
 *   2. @WrapOperation on Language.get(key, fallback) inside updateTranslations() —
 *      non-vanilla keys are replaced with their fallback value (the raw key name).
 *      The server receives nothing that reveals the installed mod set.
 */
@Mixin(TranslatableTextContent.class)
public abstract class TranslatableTextContentMixin {

    @Shadow @Final private String key;
    @Shadow private Language languageCache;

    /** Force cache miss in exploit context so the @WrapOperation below fires. */
    @Inject(method = "updateTranslations", at = @At("HEAD"))
    private void cyber$invalidateCache(CallbackInfo ci) {
        if (!ExploitContext.isActive()) return;
        if (VanillaKeyRegistry.isVanillaTranslationKey(key)) return;
        this.languageCache = null;
    }

    /**
     * Replace non-vanilla translation resolution with the fallback value.
     * Called for Language.get(key, fallback) (non-null fallback branch) inside updateTranslations().
     */
    @WrapOperation(
        method = "updateTranslations",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/Language;get(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"
        )
    )
    private String cyber$blockModTranslation(
        Language instance, String translationKey, String fallback, Operation<String> original
    ) {
        if (!ExploitContext.isActive()) return original.call(instance, translationKey, fallback);
        if (VanillaKeyRegistry.isVanillaTranslationKey(translationKey)) {
            return original.call(instance, translationKey, fallback);
        }

        Mc265322Fix guard = Modules.get().get(Mc265322Fix.class);
        if (guard == null || !guard.isProtectionEnabled(ExploitContext.getSource())) {
            return original.call(instance, translationKey, fallback);
        }

        guard.notifyBlocked(translationKey, ExploitContext.getSource());
        return fallback;
    }

    /**
     * Replace non-vanilla translation resolution for the no-fallback branch.
     * Called for Language.get(key) (null fallback branch) inside updateTranslations().
     * Server probes often omit the fallback — this path was previously unguarded.
     */
    @WrapOperation(
        method = "updateTranslations",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/Language;get(Ljava/lang/String;)Ljava/lang/String;"
        )
    )
    private String cyber$blockModTranslationNoFallback(
        Language instance, String translationKey, Operation<String> original
    ) {
        if (!ExploitContext.isActive()) return original.call(instance, translationKey);
        if (VanillaKeyRegistry.isVanillaTranslationKey(translationKey)) {
            return original.call(instance, translationKey);
        }

        Mc265322Fix guard = Modules.get().get(Mc265322Fix.class);
        if (guard == null || !guard.isProtectionEnabled(ExploitContext.getSource())) {
            return original.call(instance, translationKey);
        }

        guard.notifyBlocked(translationKey, ExploitContext.getSource());
        // No fallback provided — return the raw key so the server sees nothing useful
        return translationKey;
    }
}
