package com.euphoriapatches.euphoria_patcher.forge.mixin;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.integration.iris.IrisReloadManager;
import com.euphoriapatches.euphoria_patcher.util.ModLoaderSpecifics;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Debug(export = true)
@Mixin(Minecraft.class)
@Pseudo
public class ReloadShadersOnDimensionChangeMixin {
    @Unique
    private static String euphoriaPatcher$lastDimension = null;
    @Unique
    private static String euphoriaPatcher$getInstanceMethod = null; // Cached method name

    @Unique
    private static void euphoriaPatcher$debugLog(String message) {
        EuphoriaLogger.debugLog("[ReloadShadersOnDimensionChangeMixin] " + message);
    }

    /**
     * This injects at the end of the setLevel method, which is called when changing dimensions
     * Using simple name to let Mixin resolve all overloads
     */
    @Inject(
        method = {
                "setLevel",
                "setLevel(Lnet/minecraft/client/multiplayer/ClientLevel;)V",
                "setLevel(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/client/gui/screens/ReceivingLevelScreen$Reason;)V",
                "m_91156_(Lnet/minecraft/client/multiplayer/ClientLevel;)V",
                "func_71403_a(Lnet/minecraft/client/world/ClientWorld;)V"
        },
        at = @At("RETURN"),
        require = 0,
        remap = false
    )
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
            Class<?> minecraftClass = Minecraft.class;
            Object mcInstance = null;

            // Use cached method if available
            if (euphoriaPatcher$getInstanceMethod != null) {
                mcInstance = minecraftClass.getMethod(euphoriaPatcher$getInstanceMethod).invoke(null);
            } else {
                // First time - find which method works
                String[] getInstanceMethods = {"m_91087_", "getInstance", "func_71410_x"};
                for (String methodName : getInstanceMethods) {
                    try {
                        mcInstance = minecraftClass.getMethod(methodName).invoke(null);
                        euphoriaPatcher$getInstanceMethod = methodName; // Cache for next time
                        euphoriaPatcher$debugLog("Cached getInstance method: " + methodName);
                        break;
                    } catch (NoSuchMethodException e) {
                        // Try next method
                    }
                }
            }

            if (mcInstance != null) {
                minecraftClass.getMethod("execute", Runnable.class).invoke(mcInstance, (Runnable) () -> {
                    IrisReloadManager.findAndScheduleReload();
                    euphoriaPatcher$debugLog("Scheduled shader reload after dimension change");
                });
            }
        } catch (Exception e) {
            EuphoriaPatcher.log(2, 0, "Error scheduling shader reload: " + e.getMessage());
        }
    }
}
