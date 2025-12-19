package com.euphoriapatches.euphoria_patcher.fabric.mixin;

import com.euphoriapatches.euphoria_patcher.integration.iris.IrisReloadManager;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Debug(export = true)
@Mixin(MinecraftClient.class)
@Pseudo
public class ClientTickMixinYarn {
    @Inject(
            method = {
                    "tick",
                    "tick()V",
                    "runTick",
                    "runTick()V",
                    "m_91398_()V"
            },
            at = @At("HEAD"),
            require = 0, remap = false)
    private void onClientTick(CallbackInfo ci) {
        IrisReloadManager.checkPendingReload();
    }
}
