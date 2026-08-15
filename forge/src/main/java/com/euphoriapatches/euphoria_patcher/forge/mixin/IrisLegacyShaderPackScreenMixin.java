package com.euphoriapatches.euphoria_patcher.forge.mixin;

import com.euphoriapatches.euphoria_patcher.features.steganography.ShaderSteganography;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

// Make png shader settings files work with Iris, we decode the embedded config file if there is any and use iris importPackOptions to load it.
@Pseudo
@Mixin(targets = EuphoriaMixinPlugin.LEGACY_IRIS_SHADER_PACK_SCREEN_CLASS, remap = false)
public class IrisLegacyShaderPackScreenMixin {
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

    /**
     * Intercepts file drops on the shader selection list. If exactly one file is dropped (.txt or a PNG
     * carrying an embedded payload) while a shaderpack is active, applies it as settings via
     * {@code importPackOptions(Path)}. Unmatched drops fall back to standard Iris validation.
     */
    @Inject(
            method = "onPackListFilesDrop",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void euphoriaPatcher$onPackListFilesDrop(List<Path> paths, CallbackInfo ci) {
        if (paths == null || paths.size() != 1) {
            return;
        }

        Path path = paths.get(0);
        String lowerName = path.toString().toLowerCase(Locale.ROOT);
        boolean isTxt = lowerName.endsWith(".txt");
        boolean isPng = lowerName.endsWith(".png");
        if (!isTxt && !isPng) {
            return;
        }
        if (isPng && ShaderSteganography.decodeFromPngFile(path) == null) {
            return;
        }
        if (!euphoriaPatcher$isShaderCurrentlyActive()) {
            return;
        }

        try {
            Method importPackOptions = this.getClass().getMethod("importPackOptions", Path.class);
            importPackOptions.invoke(this, path);
            euphoriaPatcher$debugLog("Applied dropped settings file to the active shaderpack: " + path.getFileName());
            ci.cancel();
        } catch (Throwable t) {
            euphoriaPatcher$debugLog("Failed to apply dropped settings file via reflection, falling back to standard handling: " + t);
        }
    }

    @Unique
    private static boolean euphoriaPatcher$isShaderCurrentlyActive() {
        try {
            Class<?> irisClass = Class.forName("net.coderbot.iris.Iris");
            Method getCurrentPack = irisClass.getMethod("getCurrentPack");
            Object result = getCurrentPack.invoke(null);
            return result instanceof Optional && ((Optional<?>) result).isPresent();
        } catch (Throwable t) {
            return false;
        }
    }

    @Unique
    private static void euphoriaPatcher$debugLog(String message) {
        EuphoriaLogger.debugLog("[IrisLegacyShaderPackScreenMixin] " + message);
    }
}
