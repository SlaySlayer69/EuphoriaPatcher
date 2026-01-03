package com.euphoriapatches.euphoria_patcher.util;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility for reading shader property files and extracting information
 */
public class ShaderPropertyReader {

    // Cache for shader style results: key = shader path string, value = style
    private static final Map<String, String> styleCache = new HashMap<>();

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ShaderPropertyReader] " + message);
    }

    /**
     * Determines shader style by reading the common.glsl file
     *
     * @param shaderPath Path to the shader file or directory
     * @param commonLocation Relative path to common.glsl within the shader (e.g., "shaders/common.glsl")
     * @return "Reimagined" or "Unbound" based on the SHADER_STYLE value, defaults to "Reimagined"
     */
    public static String detectStyleFromCommonFile(Path shaderPath, String commonLocation) {
        // Check cache first
        String cacheKey = shaderPath.toString();
        if (styleCache.containsKey(cacheKey)) {
            debugLog("Returning cached style for: " + shaderPath.getFileName());
            return styleCache.get(cacheKey);
        }

        Path tempDir = null;
        String detectedStyle = "Reimagined"; // Default fallback

        try {
            // Create temp directory
            tempDir = ArchiveOperations.createTempDirectory();
            if (tempDir == null) {
                debugLog("Could not create temp directory, returning default style");
                styleCache.put(cacheKey, detectedStyle);
                return detectedStyle;
            }

            String baseName = shaderPath.getFileName().toString().replace(".zip", "");

            // Extract if needed
            Path extractedPath;
            if (shaderPath.toString().endsWith(".zip")) {
                extractedPath = ArchiveOperations.extract(shaderPath, tempDir.resolve(baseName), "extracting archive");
                if (extractedPath == null) {
                    debugLog("Failed to extract shader, returning default style");
                    styleCache.put(cacheKey, detectedStyle);
                    return detectedStyle;
                }
            } else {
                extractedPath = shaderPath;
            }

            // Read the common.glsl file
            Path commonFile = extractedPath.resolve(commonLocation);
            if (Files.exists(commonFile)) {
                String content = FileUtils.readFileToString(commonFile.toFile(), "UTF-8");

                // Look for SHADER_STYLE definition
                if (content.contains("SHADER_STYLE 4")) {
                    debugLog("Detected Unbound style from common.glsl");
                    detectedStyle = "Unbound";
                } else if (content.contains("SHADER_STYLE 1") || content.contains("SHADER_STYLE")) {
                    debugLog("Detected Reimagined style from common.glsl");
                    detectedStyle = "Reimagined";
                }
            } else {
                debugLog("common.glsl file not found at: " + commonFile);
            }
        } catch (IOException e) {
            debugLog("Error reading common.glsl: " + e.getMessage());
        } finally {
            ArchiveOperations.deleteTempDirectory(tempDir);
        }

        debugLog("Caching and returning " + detectedStyle + " style");
        styleCache.put(cacheKey, detectedStyle);
        return detectedStyle;
    }

    /**
     * Clears the style cache. Useful if shaders are modified during runtime.
     */
    public static void clearCache() {
        styleCache.clear();
        debugLog("Style cache cleared");
    }
}
