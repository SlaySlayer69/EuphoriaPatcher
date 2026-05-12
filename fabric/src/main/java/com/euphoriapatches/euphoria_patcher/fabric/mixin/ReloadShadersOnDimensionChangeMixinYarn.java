package com.euphoriapatches.euphoria_patcher.fabric.mixin;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.integration.iris.IrisReloadManager;
import com.euphoriapatches.euphoria_patcher.util.mod.ModLoaderSpecifics;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class ReloadShadersOnDimensionChangeMixinYarn {
    @Unique
    private static String euphoriaPatcher$lastDimension = null;

    @Unique
    private static void euphoriaPatcher$debugLog(String message) {
        EuphoriaLogger.debugLog("[ReloadShadersOnDimensionChangeMixin] " + message);
    }

    /**
     * This injects at the end of the setWorld method, which is called when changing dimensions
     */
    @Inject(
            method = {
                "setWorld",
                "method_1481"
            },
            at = @At("RETURN"))
    private void onDimensionChange(CallbackInfo ci) {
        euphoriaPatcher$debugLog("### EUPHORIA DIMENSION DETECTION - setWorld called ###");

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
            MinecraftClient.getInstance().execute(() -> {
                IrisReloadManager.findAndScheduleReload();
                euphoriaPatcher$debugLog("Scheduled shader reload after dimension change");
            });
        } catch (Exception e) {
            EuphoriaPatcher.log(2, 0, "Error scheduling shader reload: " + e.getMessage());
        }
    }
}
