package mc.euphoria_patches.euphoria_patcher.neoforge.mixin;

import mc.euphoria_patches.euphoria_patcher.util.IrisReloadManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Create a new mixin class
@Mixin(Minecraft.class)
public class ClientTickMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void onClientTick(CallbackInfo ci) {
        IrisReloadManager.checkPendingReload();
    }
}