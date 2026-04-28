package com.euphoriapatches.euphoria_patcher.fabric.mixin;

import com.euphoriapatches.euphoria_patcher.integration.iris.IrisColortexAdder;
import com.google.common.collect.ImmutableSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Set;

@Pseudo
@Mixin(targets = EuphoriaMixinPlugin.IRIS_PACK_RENDER_TARGETS_CLASS, remap = false)
public class IrisModernPackRenderTargetDirectivesMixin {

    @Mutable
    @Shadow
    @Final
    private static Set<Integer> BASELINE_SUPPORTED_RENDER_TARGETS;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void euphoriaPatcher$appendTargets(CallbackInfo ci) {
        ImmutableSet.Builder<Integer> builder = ImmutableSet.builder();
        builder.addAll(BASELINE_SUPPORTED_RENDER_TARGETS);
        IrisColortexAdder.appendAdditionalRenderTargets(builder);
        BASELINE_SUPPORTED_RENDER_TARGETS = builder.build();
    }
}
