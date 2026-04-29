package com.euphoriapatches.euphoria_patcher.forge.mixin;

import com.euphoriapatches.euphoria_patcher.integration.DefineHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = EuphoriaMixinPlugin.OPTIFINE_SHADER_MACROS_CLASS, remap = false)
public class OptifineShaderMacrosMixin {

   @Inject(method = "getFixedMacroLines", at = @At("RETURN"), cancellable = true)
   private static void euphoriaPatcher$appendFixedMacroLines(CallbackInfoReturnable<String> original) {
      original.setReturnValue(DefineHelper.addEuphoriaDefines(original.getReturnValue(), DefineHelper.Target.OPTIFINE));
   }
}
