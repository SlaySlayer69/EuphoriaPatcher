package com.euphoriapatches.euphoria_patcher.fabric.mixin;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.fabric.FabricModLoaderSpecifics;
import com.euphoriapatches.euphoria_patcher.util.ModLoaderSpecifics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.main.Main.class)
public class EuphoriaPatcherMixin {
    @Inject(method = "main([Ljava/lang/String;)V", at = @At("HEAD"))
    private static void onGameStart(String[] args, CallbackInfo ci) {
        FabricModLoaderSpecifics fabricSpecifics = new FabricModLoaderSpecifics();
        ModLoaderSpecifics.setInstance(fabricSpecifics);

        if(ModLoaderSpecifics.serverCheckStatic()) return;
        new EuphoriaPatcher();
    }
}
