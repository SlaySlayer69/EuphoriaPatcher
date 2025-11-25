package mc.euphoria_patches.euphoria_patcher.neoforge.mixin;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import mc.euphoria_patches.euphoria_patcher.logging.EuphoriaLogger;
import mc.euphoria_patches.euphoria_patcher.integration.iris.IrisReloadManager;
import mc.euphoria_patches.euphoria_patcher.util.ModLoaderSpecifics;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class ReloadShadersOnDimensionChangeMixin {
    @Unique
    private static String euphoriaPatcher$lastDimension = null;

    @Unique
    private static void euphoriaPatcher$debugLog(String message) {
        EuphoriaLogger.debugLog("[ReloadShadersOnDimensionChangeMixin] " + message);
    }

    /**
     * This injects at the end of the setLevel method, which is called when changing dimensions
     */
    @Inject(method = "setLevel", at = @At("RETURN"))
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
        
        // Check if dimension changed
        if (!currentDimension.equals(euphoriaPatcher$lastDimension)) {
            euphoriaPatcher$debugLog("!!! DIMENSION CHANGED: " + euphoriaPatcher$lastDimension + " -> " + currentDimension + " !!!");
            euphoriaPatcher$lastDimension = currentDimension;
            
            // Use IrisReloadManager to handle the reload
            try {
                Minecraft.getInstance().execute(() -> {
                    IrisReloadManager.findAndScheduleReload();
                    euphoriaPatcher$debugLog("Scheduled shader reload after dimension change");
                });
            } catch (Exception e) {
                EuphoriaPatcher.log(2, 0, "Error scheduling shader reload: " + e.getMessage());
            }
        }
    }
}