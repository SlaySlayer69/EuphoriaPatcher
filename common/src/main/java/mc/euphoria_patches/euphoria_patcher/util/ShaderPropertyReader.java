package mc.euphoria_patches.euphoria_patcher.util;

import mc.euphoria_patches.euphoria_patcher.logging.EuphoriaLogger;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility for reading shader property files and extracting information
 */
public class ShaderPropertyReader {
    
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
        Path tempDir = null;
        try {
            // Create temp directory
            tempDir = ArchiveOperations.createTempDirectory();
            if (tempDir == null) {
                debugLog("Could not create temp directory, returning default style");
                return "Reimagined"; // Default if we can't create temp dir
            }
            
            String baseName = shaderPath.getFileName().toString().replace(".zip", "");
            
            // Extract if needed
            Path extractedPath;
            if (shaderPath.toString().endsWith(".zip")) {
                extractedPath = ArchiveOperations.extract(shaderPath, tempDir.resolve(baseName), "extracting archive");
                if (extractedPath == null) {
                    debugLog("Failed to extract shader, returning default style");
                    return "Reimagined";
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
                    return "Unbound";
                } else if (content.contains("SHADER_STYLE 1") || content.contains("SHADER_STYLE")) {
                    debugLog("Detected Reimagined style from common.glsl");
                    return "Reimagined";
                }
            } else {
                debugLog("common.glsl file not found at: " + commonFile);
            }
        } catch (IOException e) {
            debugLog("Error reading common.glsl: " + e.getMessage());
        } finally {
            ArchiveOperations.deleteTempDirectory(tempDir);
        }
        
        debugLog("Returning default Reimagined style");
        return "Reimagined"; // Default fallback
    }
}
