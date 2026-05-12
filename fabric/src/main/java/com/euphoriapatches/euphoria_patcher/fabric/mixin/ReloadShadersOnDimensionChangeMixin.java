package com.euphoriapatches.euphoria_patcher.fabric.mixin;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.integration.iris.IrisReloadManager;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.mod.ModLoaderSpecifics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.Minecraft", remap = false)
public class ReloadShadersOnDimensionChangeMixin {
    @Unique
    private static String euphoriaPatcher$lastDimension = null;

    @Unique
    private static void euphoriaPatcher$debugLog(String message) {
        EuphoriaLogger.debugLog("[ReloadShadersOnDimensionChangeMixin] " + message);
    }

    /**
     * This injects at the end of the setWorld/setLevel method, which is called when changing dimensions
     */
    @Inject(
            method = {
                    "setLevel"
            },
            at = @At("RETURN"),
            require = 0,
            remap = false)
    private void onDimensionChange(CallbackInfo ci) {
        euphoriaPatcher$debugLog("### EUPHORIA DIMENSION DETECTION - setLevel called ###");

        // Get current dimension
        String currentDimension = ModLoaderSpecifics.getCurrentDimensionStatic();

        // First-time initialization
        if (euphoriaPatcher$lastDimension == null) {
            euphoriaPatcher$lastDimension = currentDimension;
            euphoriaPatcher$debugLog("Initial dimension set to: " + currentDimension);
            return;
        }

        // Always reload on dimension change to clear update notification define
        euphoriaPatcher$debugLog("!!! DIMENSION CHANGED: " + euphoriaPatcher$lastDimension + " -> " + currentDimension + " !!!");
        euphoriaPatcher$lastDimension = currentDimension;

        // Use IrisReloadManager to handle the reload
        try {
            // Try both possible Minecraft client class names
            Class<?> mcClientClass = Class.forName("net.minecraft.client.Minecraft");
            Object mcInstance = mcClientClass.getMethod("getInstance").invoke(null);
            mcClientClass.getMethod("execute", Runnable.class).invoke(mcInstance, (Runnable) () -> {
                IrisReloadManager.findAndScheduleReload();
                euphoriaPatcher$debugLog("Scheduled shader reload after dimension change");
            });
        } catch (Exception e) {
            EuphoriaPatcher.log(2, 0, "Error scheduling shader reload: " + e.getMessage());
        }
    }
}
