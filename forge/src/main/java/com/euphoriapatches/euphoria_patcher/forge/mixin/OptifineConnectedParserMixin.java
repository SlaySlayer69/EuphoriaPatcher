package com.euphoriapatches.euphoria_patcher.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;

@Pseudo
@Mixin(targets = EuphoriaMixinPlugin.OPTIFINE_CONNECTED_PARSER_CLASS, remap = false)
public class OptifineConnectedParserMixin {
    @Unique
    private void euphoriaPatcher$debugLog(String message) {
        EuphoriaLogger.debugLog("[OptifineConnectedParserMixin] " + message);
    }

    @Redirect(method = "parseBlockPart", at = @At(value = "INVOKE", target = "warn(Ljava/lang/String;)V"))
    private void euphoriaPatcher$redirectWarnBlockPart(@Coerce Object instance, String message) {
        euphoriaPatcher$debugLog(message);
    }

    @Redirect(method = "parseItems", at = @At(value = "INVOKE", target = "warn(Ljava/lang/String;)V", ordinal = 0))
    private void euphoriaPatcher$redirectWarnItems1(@Coerce Object instance, String message) {
        euphoriaPatcher$debugLog(message);
    }

    @Redirect(method = "parseItems", at = @At(value = "INVOKE", target = "warn(Ljava/lang/String;)V", ordinal = 1))
    private void euphoriaPatcher$redirectWarnItems2(@Coerce Object instance, String message) {
        euphoriaPatcher$debugLog(message);
    }

    @Redirect(method = "parseEntities", at = @At(value = "INVOKE", target = "warn(Ljava/lang/String;)V", ordinal = 0))
    private void euphoriaPatcher$redirectWarnEntities1(@Coerce Object instance, String message) {
        euphoriaPatcher$debugLog(message);
    }

    @Redirect(method = "parseEntities", at = @At(value = "INVOKE", target = "warn(Ljava/lang/String;)V", ordinal = 1))
    private void euphoriaPatcher$redirectWarnEntities2(@Coerce Object instance, String message) {
        euphoriaPatcher$debugLog(message);
    }
}
