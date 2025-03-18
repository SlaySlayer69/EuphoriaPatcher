package mc.euphoria_patches.euphoria_patcher.mixin;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;
import java.util.ArrayList;

// Use @Pseudo to tell the mixin processor the class isn't available at compile time
@Pseudo
@Mixin(targets = "net.irisshaders.iris.gl.shader.StandardMacros", remap = false)
public class IrisStandardMacrosMixin {

    // Static flag to track if we've already added the define
    private static boolean defineAdded = false;

    // Shadow the define method we want to call
    @Shadow(remap = false)
    private static void define(List<?> defines, String key) {}

    @Inject(
            method = "createStandardEnvironmentDefines",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/iris/gl/shader/StandardMacros;define(Ljava/util/List;Ljava/lang/String;Ljava/lang/String;)V",
                    ordinal = 10,
                    remap = false
            ),
            locals = LocalCapture.CAPTURE_FAILHARD,
            require = 0,
            remap = false
    )
    private static void addEuphoriaDefine(CallbackInfoReturnable<?> cir, ArrayList<?> standardDefines) {
        try {
            // Check if we've already added the define or if we should skip
            if (defineAdded || !EuphoriaPatcher.isSpacEagle()) {
                return;
            }

            // Add the define
            define(standardDefines, "SPACEAGLE17");
            EuphoriaPatcher.log(0,"Successfully added SPACEAGLE17 define to Iris");

            // Mark that we've successfully added the define
            defineAdded = true;
        } catch (Exception ignored) {
        }
    }
}