package com.euphoriapatches.euphoria_patcher.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;

@Pseudo
@Mixin(targets = EuphoriaMixinPlugin.OPTIFINE_BLOCK_ALIASES_CLASS, remap = false)
public class OptifineBlockAliasesMixin {
    @Unique
    private static void euphoriaPatcher$debugLog(String message) {
        EuphoriaLogger.debugLog("[OptifineBlockAliasesMixin] " + message);
    }

    @Redirect(method = "loadBlockAliases", at = @At(value = "INVOKE", target = "Lnet/optifine/Config;warn(Ljava/lang/String;)V", ordinal = 0))
    private static void euphoriaPatcher$redirectWarnLoadBlockAliases0(String message) {
        euphoriaPatcher$debugLog(message);
    }

    @Redirect(method = "loadBlockAliases", at = @At(value = "INVOKE", target = "Lnet/optifine/Config;warn(Ljava/lang/String;)V", ordinal = 1))
    private static void euphoriaPatcher$redirectWarnLoadBlockAliases1(String message) {
        euphoriaPatcher$debugLog(message);
    }

    @Redirect(method = "loadBlockAliases", at = @At(value = "INVOKE", target = "Lnet/optifine/Config;warn(Ljava/lang/String;)V", ordinal = 2))
    private static void euphoriaPatcher$redirectWarnLoadBlockAliases2(String message) {
        euphoriaPatcher$debugLog(message);
    }
}
