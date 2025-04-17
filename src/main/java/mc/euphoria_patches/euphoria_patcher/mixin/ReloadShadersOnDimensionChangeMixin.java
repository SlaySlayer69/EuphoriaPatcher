package mc.euphoria_patches.euphoria_patcher.mixin;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import mc.euphoria_patches.euphoria_patcher.util.EuphoriaLogger;
import mc.euphoria_patches.euphoria_patcher.util.ModLoaderSpecifics;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class ReloadShadersOnDimensionChangeMixin {
    @Unique
    private static String lastDimension = null;


    @Unique
    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ReloadShadersOnDimensionChangeMixin] " + message);
    }

    /**
     * This injects at the end of the setWorld method, which is called when changing dimensions
     */
    @Inject(method = "setWorld", at = @At("RETURN"))
    private void onDimensionChange(CallbackInfo ci) {
        debugLog("### EUPHORIA DIMENSION DETECTION - setWorld called ###");
        
        // Get current dimension
        String currentDimension = ModLoaderSpecifics.getCurrentDimension();
        
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
            
            // Schedule shader reload for next game tick
            try {
                MinecraftClient.getInstance().execute(() -> {
                    Class<?> irisClass = null;
                    
                    // Try both possible Iris class locations
                    try {
                        irisClass = Class.forName("net.irisshaders.iris.Iris");
                        debugLog("Found Iris class at net.irisshaders.iris.Iris");
                    } catch (ClassNotFoundException e1) {
                        try {
                            irisClass = Class.forName("net.coderbot.iris.Iris");
                            debugLog("Found Iris class at net.coderbot.iris.Iris");
                        } catch (ClassNotFoundException e2) {
                            // Iris isn't installed, this is fine - just log to debug
                            debugLog("Iris not found - this is normal if Iris isn't installed");
                            return;
                        }
                    }
                    
                    // Only attempt to reload if we found a valid Iris class
                    if (irisClass != null) {
                        try {
                            irisClass.getMethod("reload").invoke(null);
                            debugLog("Successfully reloaded shaders after dimension change");
                        } catch (Exception e) {
                            // This is an actual error since we found Iris but couldn't reload
                            EuphoriaPatcher.log(2, 0, "Error reloading Iris shaders: " + e.getMessage());
                            debugLog("Error details: " + e.getClass().getName() + ": " + e.getMessage());
                        }
                    }
                });
            } catch (Exception e) {
                EuphoriaPatcher.log(2,0, "Error scheduling shader reload: " + e.getMessage());
            }
        }
    }
}