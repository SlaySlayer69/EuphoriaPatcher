package mc.euphoria_patches.euphoria_patcher.neoforge.mixin;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import mc.euphoria_patches.euphoria_patcher.util.EuphoriaLogger;
import mc.euphoria_patches.euphoria_patcher.util.IrisReloadManager;
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
    private static String lastDimension = null;

    @Unique
    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ReloadShadersOnDimensionChangeMixin] " + message);
    }

    /**
     * This injects at the end of the setLevel method, which is called when changing dimensions
     */
    @Inject(method = "setLevel", at = @At("RETURN"))
    private void onDimensionChange(CallbackInfo ci) {
        debugLog("### EUPHORIA DIMENSION DETECTION - setLevel called ###");
        
        // Get current dimension
        String currentDimension = ModLoaderSpecifics.getCurrentDimensionStatic();
        
        // First-time initialization
        if (lastDimension == null) {
            lastDimension = currentDimension;
            debugLog("Initial dimension set to: " + currentDimension);
            return;
        }
        
        // Check if dimension changed
        if (!currentDimension.equals(lastDimension)) {
            debugLog("!!! DIMENSION CHANGED: " + lastDimension + " -> " + currentDimension + " !!!");
            lastDimension = currentDimension;
            
            // Use IrisReloadManager to handle the reload
            try {
                Minecraft.getInstance().execute(() -> {
                    IrisReloadManager.findAndScheduleReload();
                    debugLog("Scheduled shader reload after dimension change");
                });
            } catch (Exception e) {
                EuphoriaPatcher.log(2, 0, "Error scheduling shader reload: " + e.getMessage());
            }
        }
    }
}