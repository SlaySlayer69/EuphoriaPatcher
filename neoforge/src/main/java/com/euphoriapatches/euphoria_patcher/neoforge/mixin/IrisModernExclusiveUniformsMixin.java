package com.euphoriapatches.euphoria_patcher.neoforge.mixin;

import com.euphoriapatches.euphoria_patcher.integration.Target;
import com.euphoriapatches.euphoria_patcher.integration.uniforms.UniformHelper;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.euphoriapatches.euphoria_patcher.neoforge.mixin.EuphoriaMixinPlugin.IRIS_EXCLUSIVE_UNIFORMS_CLASS;

@Pseudo
@Mixin(targets = IRIS_EXCLUSIVE_UNIFORMS_CLASS, remap = false)
public class IrisModernExclusiveUniformsMixin {

    @Inject(
            method = "addIrisExclusiveUniforms(Lnet/irisshaders/iris/gl/uniform/UniformHolder;Lnet/irisshaders/iris/uniforms/FrameUpdateNotifier;)V",
            at = @At("TAIL"),
            remap = false,
            require = 0
    )
    private static void onAddIrisExclusiveUniformsTwoArgs(@Coerce Object uniforms, @Coerce Object updateNotifier, CallbackInfo ci) {
        Target target = Target.IRIS_MODERN;
        euphoriaPatcher$debugLog("Adding Euphoria uniforms to " + target + " (2-arg variant)");
        UniformHelper.addEuphoriaUniforms(uniforms, target);
    }

    @Inject(
            method = "addIrisExclusiveUniforms(Lnet/irisshaders/iris/gl/uniform/UniformHolder;)V",
            at = @At("TAIL"),
            remap = false,
            require = 0
    )
    private static void onAddIrisExclusiveUniformsOneArg(@Coerce Object uniforms, CallbackInfo ci) {
        Target target = Target.IRIS_MODERN;
        euphoriaPatcher$debugLog("Adding Euphoria uniforms to " + target + " (1-arg variant)");
        UniformHelper.addEuphoriaUniforms(uniforms, target);
    }

    @Unique
    private static void euphoriaPatcher$debugLog(String message) {
        EuphoriaLogger.debugLog("[IrisModernExclusiveUniformsMixin] " + message);
    }
}
