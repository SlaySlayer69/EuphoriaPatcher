package com.euphoriapatches.euphoria_patcher.forge.mixin;

import com.euphoriapatches.euphoria_patcher.integration.uniforms.OptifineUniformBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.euphoriapatches.euphoria_patcher.forge.mixin.EuphoriaMixinPlugin.OPTIFINE_SHADERS_CLASS;

// OptiFine has no mod-facing custom uniform API. Its own built-in uniforms (worldTime,
// frameCounter, ...) get pushed to the currently bound program from setProgramUniforms, once per
// program switch - see OptifineUniformBridge for why we piggyback on that same call rather than
// a separate per-frame hook.
@Pseudo
@Mixin(targets = OPTIFINE_SHADERS_CLASS, remap = false)
public class OptifineShadersUniformsMixin {

    @Inject(
            method = "setProgramUniforms(Lnet/optifine/shaders/ProgramStage;)V",
            at = @At("TAIL"),
            remap = false,
            require = 0
    )
    private static void onSetProgramUniforms(@Coerce Object programStage, CallbackInfo ci) {
        OptifineUniformBridge.updateEuphoriaUniforms();
    }
}
