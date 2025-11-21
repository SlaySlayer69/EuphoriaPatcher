package mc.euphoria_patches.euphoria_patcher.neoforge.mixin;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import mc.euphoria_patches.euphoria_patcher.neoforge.NeoforgeModLoaderSpecifics;
import mc.euphoria_patches.euphoria_patcher.util.ModLoaderSpecifics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.main.Main")
public class EuphoriaPatcherMixin {
    @Inject(method = "main([Ljava/lang/String;)V", at = @At("HEAD"))
    private static void onGameStart(String[] args, CallbackInfo ci) {
        NeoforgeModLoaderSpecifics neoforgeSpecifics = new NeoforgeModLoaderSpecifics();
        ModLoaderSpecifics.setInstance(neoforgeSpecifics);

        if(ModLoaderSpecifics.serverCheckStatic()) return;
        new EuphoriaPatcher();
    }
}