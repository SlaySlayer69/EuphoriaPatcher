package mc.euphoria_patches.euphoria_patcher.fabric.mixin;

import mc.euphoria_patches.euphoria_patcher.integration.iris.IrisReloadManager;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class ClientTickMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void onClientTick(CallbackInfo ci) {
        IrisReloadManager.checkPendingReload();
    }
}
