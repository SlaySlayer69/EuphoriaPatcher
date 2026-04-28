package com.euphoriapatches.euphoria_patcher.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;

@Pseudo
@Mixin(targets = EuphoriaMixinPlugin.OPTIFINE_ITEM_ALIASES_CLASS, remap = false)
public class OptifineItemAliasesMixin {
    @Unique
    private static void euphoriaPatcher$debugLog(String message) {
        EuphoriaLogger.debugLog("[OptifineItemAliasesMixin] " + message);
    }

    @Redirect(method = "loadItemAliases", at = @At(value = "INVOKE", target = "Lnet/optifine/Config;warn(Ljava/lang/String;)V", ordinal = 0))
    private static void euphoriaPatcher$redirectWarnLoadItemAliases0(String message) {
        euphoriaPatcher$debugLog(message);
    }

    @Redirect(method = "loadItemAliases", at = @At(value = "INVOKE", target = "Lnet/optifine/Config;warn(Ljava/lang/String;)V", ordinal = 1))
    private static void euphoriaPatcher$redirectWarnLoadItemAliases1(String message) {
        euphoriaPatcher$debugLog(message);
    }

    @Redirect(method = "loadItemAliases", at = @At(value = "INVOKE", target = "Lnet/optifine/Config;warn(Ljava/lang/String;)V", ordinal = 2))
    private static void euphoriaPatcher$redirectWarnLoadItemAliases2(String message) {
        euphoriaPatcher$debugLog(message);
    }
}
