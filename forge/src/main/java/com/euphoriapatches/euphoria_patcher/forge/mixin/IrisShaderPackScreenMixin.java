package com.euphoriapatches.euphoria_patcher.forge.mixin;

import com.euphoriapatches.euphoria_patcher.features.steganography.ShaderSteganography;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Locale;

// Make png shader settings files work with Iris, we decode the embedded config file if there is any and use iris importPackOptions to load it.
@Pseudo
@Mixin(targets = EuphoriaMixinPlugin.IRIS_SHADER_PACK_SCREEN_CLASS, remap = false)
public class IrisShaderPackScreenMixin {
    @Redirect(
            method = "importPackOptions",
            at = @At(value = "INVOKE", target = "Ljava/nio/file/Files;newInputStream(Ljava/nio/file/Path;[Ljava/nio/file/OpenOption;)Ljava/io/InputStream;"),
            remap = false,
            require = 0
    )
    private InputStream euphoriaPatcher$redirectNewInputStream(Path settingFile, OpenOption[] options) throws IOException {
        if (settingFile.toString().toLowerCase(Locale.ROOT).endsWith(".png")) {
            String decoded = ShaderSteganography.decodeFromPngFile(settingFile);
            if (decoded != null) {
                euphoriaPatcher$debugLog("Substituting decoded preset text for dropped PNG: " + settingFile.getFileName());
                return new ByteArrayInputStream(decoded.getBytes(StandardCharsets.UTF_8));
            }
        }
        return Files.newInputStream(settingFile, options);
    }

    @Unique
    private static void euphoriaPatcher$debugLog(String message) {
        EuphoriaLogger.debugLog("[IrisShaderPackScreenMixin] " + message);
    }
}
