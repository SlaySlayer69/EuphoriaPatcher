package com.euphoriapatches.euphoria_patcher.fabric.mixin;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.features.steganography.ShaderSteganography;
import com.euphoriapatches.euphoria_patcher.features.shader_settings.SettingsConverterUtil;
import com.euphoriapatches.euphoria_patcher.integration.ShaderLoader;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.services.ShaderDetector;
import com.euphoriapatches.euphoria_patcher.util.UserPersistentData;
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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        String decoded = null;
        if (settingFile.toString().toLowerCase(Locale.ROOT).endsWith(".png")) {
            decoded = ShaderSteganography.decodeFromPngFile(settingFile);
            if (decoded != null) {
                euphoriaPatcher$debugLog("Substituting decoded preset text for dropped PNG: " + settingFile.getFileName());
            }
        }

        if (SettingsConverterUtil.shouldNotConvertCurrentShaderSettings()) {
            if (decoded != null) {
                return new ByteArrayInputStream(decoded.getBytes(StandardCharsets.ISO_8859_1));
            }
            return Files.newInputStream(settingFile, options);
        }

        List<String> lines = decoded != null
                ? Arrays.asList(decoded.split("\\R", -1))
                : Files.readAllLines(settingFile, StandardCharsets.ISO_8859_1);
        euphoriaPatcher$debugLog("Applied settings conversions to imported settings file: " + settingFile.getFileName());
        return SettingsConverterUtil.convertToInputStreamAndUpdateSettings(lines);
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

    /**
     * Counts how often settings got changes, needs the Queue as Iris does no-op on applyChanges if no changes are pending
     */
    @Inject(method = "applyChanges", at = @At("HEAD"), remap = false, require = 0)
    private void euphoriaPatcher$countSettingsChange(CallbackInfo ci) {
        try {
            if (euphoriaPatcher$pendingOptionChangeCount() > 0 && euphoriaPatcher$isEuphoriaShaderActive()) {
                UserPersistentData.incrementTimesSettingsChanged();
            }
        } catch (Throwable t) {
            euphoriaPatcher$debugLog("Failed to count shader settings change: " + t);
        }
    }

    @Unique
    private static int euphoriaPatcher$pendingOptionChangeCount() {
        try {
            Class<?> irisClass = Class.forName("net.coderbot.iris.Iris");
            Object queue = irisClass.getMethod("getShaderPackOptionQueue").invoke(null);
            if (queue instanceof Map) {
                return ((Map<?, ?>) queue).size();
            }
        } catch (Throwable t) {
            euphoriaPatcher$debugLog("Could not read shader pack option queue: " + t);
        }
        return 0;
    }

    @Unique
    private static boolean euphoriaPatcher$isEuphoriaShaderActive() {
        try {
            ShaderDetector shaderDetector = EuphoriaPatcher.getInstance().getShaderDetector();
            return shaderDetector != null
                    && shaderDetector.isEuphoriaPatchesShader(ShaderLoader.getCurrentShaderpackPath());
        } catch (Throwable t) {
            euphoriaPatcher$debugLog("Could not determine active shader: " + t);
            return false;
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
