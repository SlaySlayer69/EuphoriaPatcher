package com.euphoriapatches.euphoria_patcher.fabric.mixin;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.integration.ShaderLoader;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = EuphoriaMixinPlugin.PHOTONICS_RAYTRACER_CLASS, remap = false)
public class PhotonicsRaytracerMixin {

    @Inject(method = "readShaderFile", at = @At("RETURN"), cancellable = true)
    private static void euphoriaPatcher$safeReturn(
            @Coerce Object path,
            boolean patchFile,
            CallbackInfoReturnable<String> cir
    ) {

        String result = cir.getReturnValue();
        boolean isEP = EuphoriaPatcher.getInstance().getShaderDetector().isEuphoriaPatchesShader(ShaderLoader.getCurrentShaderpackPath());

        if (result == null) {
            if (isEP) {
                euphoriaPatcher$debugLog("PhotonicsRaytracer readShaderFile returned null for path: " + path);
                euphoriaPatcher$debugLog("Returning empty string to prevent issues.");
                cir.setReturnValue("");
            }
        }
    }

    @Unique
    private static void euphoriaPatcher$debugLog(String message) {
        EuphoriaLogger.debugLog("[PhotonicsRaytracerMixin] " + message);
    }
}
