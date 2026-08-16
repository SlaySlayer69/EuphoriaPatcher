package com.euphoriapatches.euphoria_patcher.features.shader_settings;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.integration.ShaderLoader;
import com.euphoriapatches.euphoria_patcher.services.ShaderDetector;
import com.euphoriapatches.euphoria_patcher.util.VersionComparator;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * Utility class for converting shader settings files for Iris shaders.
 */
public class SettingsConverterUtil {
    private SettingsConverterUtil() {
    }

    /**
     * Checks if settings conversions should run for a shaderpack. Returns {@code true} only if the shader's
     * Euphoria Patches version is >= the current patch version to prevent corrupting older settings layouts.
     */
    public static boolean shouldConvertSettings(Path shaderPath) {
        if (shaderPath == null) {
            return false;
        }

        EuphoriaPatcher patcher = EuphoriaPatcher.getInstance();
        ShaderDetector detector = patcher != null ? patcher.getShaderDetector() : null;
        if (detector == null || !detector.isEuphoriaPatchesShader(shaderPath)) {
            return false;
        }

        String shaderVersion = detector.getEuphoriaPatchesVersionFromShader(shaderPath);
        if (shaderVersion == null) {
            return false;
        }

        return VersionComparator.isNewerOrEqual(shaderVersion, EuphoriaPatcher.PATCH_VERSION.replace("_", ""));
    }

    /**
     * Whether settings conversions should run for the currently active shaderpack.
     */
    public static boolean shouldNotConvertCurrentShaderSettings() {
        return !shouldConvertSettings(ShaderLoader.getCurrentShaderpackPath());
    }

    /**
     * Converts settings lines and returns a new {@link InputStream}.
     */
    public static InputStream convertToInputStreamAndUpdateSettings(List<String> lines) {
        List<String> convertedLines = ShaderSettingsConverter.convertLines(lines);
        String content = String.join("\n", convertedLines);
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.ISO_8859_1)); // ISO_8859_1 to match the original file encoding
    }
}
