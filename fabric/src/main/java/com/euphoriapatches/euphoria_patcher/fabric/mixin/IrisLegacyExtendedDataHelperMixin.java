package com.euphoriapatches.euphoria_patcher.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = EuphoriaMixinPlugin.LEGACY_IRIS_EXTENDED_DATA_HELPER_CLASS, remap = false)
public class IrisLegacyExtendedDataHelperMixin {

    @ModifyVariable(method = "computeMidBlock", at = @At("HEAD"), ordinal = 0, argsOnly = true, remap = false, require = 0)
    private static int euphoriaPatcher$maskLocalPosX(int localPosX) {
        return localPosX & 0xFFFF;
    }

    @ModifyVariable(method = "computeMidBlock", at = @At("HEAD"), ordinal = 1, argsOnly = true, remap = false, require = 0)
    private static int euphoriaPatcher$maskLocalPosY(int localPosY) {
        return localPosY & 0xFFFF;
    }

    @ModifyVariable(method = "computeMidBlock", at = @At("HEAD"), ordinal = 2, argsOnly = true, remap = false, require = 0)
    private static int euphoriaPatcher$maskLocalPosZ(int localPosZ) {
        return localPosZ & 0xFFFF;
    }
}
