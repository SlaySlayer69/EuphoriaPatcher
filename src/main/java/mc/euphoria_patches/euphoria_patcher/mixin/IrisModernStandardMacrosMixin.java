package mc.euphoria_patches.euphoria_patcher.mixin;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import mc.euphoria_patches.euphoria_patcher.util.UpdateChecker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.ArrayList;
import java.util.List;

// Use @Pseudo to tell the mixin processor the class isn't available at compile time
@Pseudo
@Mixin(targets = "net.irisshaders.iris.gl.shader.StandardMacros", remap = false)
public class IrisModernStandardMacrosMixin {

    @Unique
    private static int injectCount = 0;
    @Unique
    private static boolean injectedOnce = false;

    @Shadow(remap = false)
    private static void define(List<?> defines, String key) {}

    @Shadow(remap = false)
    private static void define(List<?> defines, String key, String value) {}

    @Inject(
            method = "createStandardEnvironmentDefines",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/iris/gl/shader/StandardMacros;define(Ljava/util/List;Ljava/lang/String;Ljava/lang/String;)V",
                    ordinal = 1,
                    remap = false
            ),
            locals = LocalCapture.CAPTURE_FAILHARD,
            require = 0,
            remap = false
    )
    private static void addEuphoriaDefine(CallbackInfoReturnable<?> cir, ArrayList<?> standardDefines) {
        try {
            injectCount++;

            if (EuphoriaPatcher.isSpacEagle()) define(standardDefines, "SPACEAGLE17");

            String currentVersion = formatVersion(EuphoriaPatcher.PATCH_VERSION);
            define(standardDefines, "CURRENT_EUPHORIA_PATCHES_VERSION", currentVersion);


            if (UpdateChecker.NEW_VERSION_AVAILABLE && EuphoriaPatcher.doUpdateChecking && EuphoriaPatcher.doDisplayShaderUpdateMessage && !injectedOnce) {
                define(standardDefines, "NEW_EUPHORIA_PATCHES_UPDATE");

                if (UpdateChecker.NEW_MOD_VERSION != null) {
                    String nextVersionFormatted = formatVersion(UpdateChecker.NEW_MOD_VERSION);
                    define(standardDefines, "NEXT_EUPHORIA_PATCHES_VERSION", nextVersionFormatted);
                }
            }

            if (injectCount == 1) {
                EuphoriaPatcher.log(0, "Added Euphoria Patches defines to Iris");
            }

            injectedOnce = true;
        } catch (Exception ignored) {
        }
    }
    @Unique
    private static String formatVersion(String version) {
        String[] versionParts = version.replace("_", "").split("\\.");
        StringBuilder versionBuilder = new StringBuilder();
        for (int i = 0; i < versionParts.length; i++) {
            versionBuilder.append("_").append(versionParts[i]);
            if (i < versionParts.length - 1) {
                versionBuilder.append(", _dot, ");
            }
        }
        return versionBuilder.toString();
    }
}