package mc.euphoria_patches.euphoria_patcher.services;

import mc.euphoria_patches.euphoria_patcher.logging.EuphoriaLogger;
import mc.euphoria_patches.euphoria_patcher.util.ArchiveOperations;

import java.nio.file.Path;

/**
 * Service for validating shader files through various verification methods
 */
public class ShaderValidator {
    
    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ShaderValidator] " + message);
    }

    /**
     * Validates a shader by extracting, re-archiving, and checking byte size
     * This is a heavy operation used to identify unpatched base shaders
     * 
     * @param path Path to the shader file or directory
     * @param filesScanned Current count of files scanned (for progress reporting)
     * @param totalFiles Total files to scan (for progress reporting)
     * @return true if the shader matches the expected byte size
     */
    public boolean validateByByteSize(Path path, int filesScanned, int totalFiles) {
        try {
            debugLog("Validating shader by byte size (" + filesScanned + "/" + totalFiles + "): " + path.getFileName());
            
            Path tempDir = ArchiveOperations.createTempDirectory();
            if (tempDir == null) {
                debugLog("Failed to create temp directory for byte size validation");
                return false;
            }
            debugLog("Created temp directory: " + tempDir);

            String baseName = path.getFileName().toString().replace(".zip", "");
            debugLog("Base name for extraction: " + baseName);

            // Extract if it's a zip file
            Path baseExtracted = tempDir.resolve(baseName);
            baseExtracted = ArchiveOperations.extract(path, baseExtracted, "extracting archive");
            if (baseExtracted == null) {
                debugLog("Failed to extract base for byte size validation");
                ArchiveOperations.deleteTempDirectory(tempDir);
                return false;
            }
            debugLog("Successfully extracted to: " + baseExtracted);

            // Archive for byte size comparison
            Path baseArchived = tempDir.resolve(baseName + ".tar");
            baseArchived = ArchiveOperations.archive(baseExtracted, baseArchived);
            if (baseArchived == null) {
                debugLog("Failed to archive base for byte size validation");
                ArchiveOperations.deleteTempDirectory(tempDir);
                return false;
            }
            debugLog("Successfully archived to: " + baseArchived);

            // Check byte size quietly
            boolean result = ArchiveOperations.verifyBaseArchiveQuiet(baseArchived);
            debugLog("Byte size verification result for " + path.getFileName() + ": " + result);

            // Clean up
            ArchiveOperations.deleteTempDirectory(tempDir);

            return result;
        } catch (Exception e) {
            debugLog("Exception during byte size validation: " + e.getMessage());
            return false;
        }
    }
}
