package mc.euphoria_patches.euphoria_patcher.forge.mixin;

import mc.euphoria_patches.euphoria_patcher.integration.iris.IrisReloadManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Debug(export = true)
@Mixin(Minecraft.class)
@Pseudo
public class ClientTickMixin {
    @Inject(
            method = {
                    "tick",
                    "runTick",
                    "runTick()V",
                    "m_91398_()V",
                    "func_195542_b(Z)V"
            },
            at = @At("HEAD"),
            require = 0, remap = false)
    private void onClientTick(CallbackInfo ci) {
        IrisReloadManager.checkPendingReload();
    }
}