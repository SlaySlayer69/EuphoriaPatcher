package mc.euphoria_patches.euphoria_patcher.mixin;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import mc.euphoria_patches.euphoria_patcher.util.ModLoaderSpecifics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.main.Main")
public class EuphoriaPatcherMixin {
    @Inject(method = "main([Ljava/lang/String;)V", at = @At("HEAD"))
    private static void onGameStart(String[] args, CallbackInfo ci) {
        if(ModLoaderSpecifics.serverCheck()) return;
        new EuphoriaPatcher();
    }
}