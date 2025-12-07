package mc.euphoria_patches.euphoria_patcher.forge.mixin;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import mc.euphoria_patches.euphoria_patcher.logging.EuphoriaLogger;
import mc.euphoria_patches.euphoria_patcher.integration.iris.IrisReloadManager;
import mc.euphoria_patches.euphoria_patcher.util.ModLoaderSpecifics;
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
