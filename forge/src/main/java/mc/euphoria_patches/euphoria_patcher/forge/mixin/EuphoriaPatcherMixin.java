package mc.euphoria_patches.euphoria_patcher.forge.mixin;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import mc.euphoria_patches.euphoria_patcher.forge.ForgeModLoaderSpecifics;
import mc.euphoria_patches.euphoria_patcher.util.ModLoaderSpecifics;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Debug(export = true)
@Mixin(value = net.minecraft.client.main.Main.class, remap = false)
public class EuphoriaPatcherMixin {
    @Inject(method = "main([Ljava/lang/String;)V", at = @At("HEAD"))
    private static void onGameStart(String[] args, CallbackInfo ci) {
        ForgeModLoaderSpecifics forgeSpecifics = new ForgeModLoaderSpecifics();
        ModLoaderSpecifics.setInstance(forgeSpecifics);

        if(ModLoaderSpecifics.serverCheckStatic()) return;
        new EuphoriaPatcher();
    }
}
