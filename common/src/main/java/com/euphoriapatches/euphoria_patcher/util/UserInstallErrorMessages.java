package com.euphoriapatches.euphoria_patcher.util;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.integration.ShaderLoader;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.monitoring.ShaderpacksWatcher;

import java.nio.file.Path;

/**
 * Handles error messaging and recovery actions when shader validation fails
 */
public class UserInstallErrorMessages {

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ShaderMessageErrorHandler] " + message);
    }

    /**
     * Handles the case when no base shader is found during initial detection
     */
    public static void handleShaderNotFound(ShaderVersionComparator versionComparator) {
        debugLog("Handling shader not found scenario");

        // First check if an update is available
        if (UpdateChecker.isUpdateAvailable()) {
            EuphoriaPatcher.log(3, 8, "=== SHADER NOT FOUND ===");
            EuphoriaPatcher.log(3, 8, "Required: " + EuphoriaPatcher.BRAND_NAME + "Shaders " + EuphoriaPatcher.VERSION.replace("_", ""));
            EuphoriaPatcher.log(3, 8, "");
            EuphoriaPatcher.log(3, 8, "A newer version of " + EuphoriaPatcher.PATCH_NAME + " is available!");
            EuphoriaPatcher.log(3, 8, "SOLUTION: Update to version " + UpdateChecker.getNewModVersion() + " which may support newer shaders.");
            EuphoriaPatcher.log(3, 8, "Download from: " + EuphoriaPatcher.EP_DOWNLOAD_URL);
            copyLinkMessage();
        } else {
            // No update available, check shaderpacks folder for other versions
            Path newerVersion = versionComparator.findNewerComplementaryVersion();

            if (newerVersion != null) {
                // Newer shader version found in shaderpacks
                debugLog("Found newer shader version: " + newerVersion.getFileName());
                String fileName = newerVersion.getFileName().toString();
                String detectedVersion = versionComparator.getComplementaryVersionFromFileName(fileName);

                EuphoriaPatcher.log(3, 8, "=== VERSION MISMATCH ===");
                EuphoriaPatcher.log(3, 8, "Found shader: " + fileName + " (version " + detectedVersion + ")");
                EuphoriaPatcher.log(3, 8, "Required shader: " + EuphoriaPatcher.BRAND_NAME + "Shaders " + EuphoriaPatcher.VERSION);
                EuphoriaPatcher.log(3, 8, "You have a NEWER shader version than what this mod version supports.");
                EuphoriaPatcher.log(3, 8, "");
                EuphoriaPatcher.log(3, 8, "SOLUTION 1: Wait for a " + EuphoriaPatcher.PATCH_NAME + " update that supports version " + detectedVersion);
                EuphoriaPatcher.log(3, 8, "SOLUTION 2: Manually check if a newer " + EuphoriaPatcher.PATCH_NAME + " version is available.");
                EuphoriaPatcher.log(3, 8, "SOLUTION 3: Download the compatible shader version " + EuphoriaPatcher.VERSION);
            } else {
                // Check for older version
                Path highestOlderVersion = versionComparator.findHighestOlderComplementaryVersion();

                EuphoriaPatcher.log(3, 8, "=== SHADER NOT FOUND ===");
                EuphoriaPatcher.log(3, 8, "Required: " + EuphoriaPatcher.BRAND_NAME + "Shaders " + EuphoriaPatcher.VERSION.replace("_", ""));

                if (highestOlderVersion != null) {
                    EuphoriaPatcher.log(3, 8, "Found: " + highestOlderVersion.getFileName().toString());
                    EuphoriaPatcher.log(3, 8, "You have an older version installed.");
                    EuphoriaPatcher.log(3, 8, "");
                    EuphoriaPatcher.log(3, 8, "SOLUTION: Download and install " + EuphoriaPatcher.BRAND_NAME + "Shaders " + EuphoriaPatcher.VERSION.replace("_", ""));
                } else {
                    EuphoriaPatcher.log(3, 8, "");
                    EuphoriaPatcher.log(3, 8, "No " + EuphoriaPatcher.BRAND_NAME + " shader found in your shaderpacks folder.");
                    EuphoriaPatcher.log(3, 8, "");
                    EuphoriaPatcher.log(3, 8, "SOLUTION: Download " + EuphoriaPatcher.BRAND_NAME + "Shaders " + EuphoriaPatcher.VERSION.replace("_", ""));
                }

            }
            EuphoriaPatcher.log(3, 8, "Download from: " + EuphoriaPatcher.COMP_DOWNLOAD_URL);
            copyLinkMessage();
        }

        // Start watching for the shader to be added
        EuphoriaPatcher instance = EuphoriaPatcher.getInstance();
        if (instance != null) {
            instance.startShaderpacksWatcher();
        }
    }

    /**
     * Handles size mismatch errors with appropriate messaging based on the situation
     */
    public static void handleSizeMismatch(String fileName, String originalFileName, ShaderVersionComparator versionComparator) {
        debugLog("Handling size mismatch for file: " + fileName);
        if (versionComparator != null && versionComparator.isNewerShaderVersion(fileName)) {
            debugLog("Detected newer shader version");
            handleNewerVersionDetected(fileName, originalFileName, versionComparator);
        } else if (fileName.matches(EuphoriaPatcher.BRAND_NAME + ".*" + EuphoriaPatcher.VERSION + ".*")) {
            debugLog("Detected incomplete file with matching version");
            handleIncompleteFile(originalFileName);
        } else {
            debugLog("Detected wrong version");
            handleWrongVersion(originalFileName);
        }

        startWatcherAndTrackFile(fileName);
    }

    /**
     * Handles detection of a newer shader version than the mod supports
     */
    private static void handleNewerVersionDetected(String fileName, String originalFileName, ShaderVersionComparator versionComparator) {
        String detectedVersion = versionComparator.getComplementaryVersionFromFileName(fileName);

        EuphoriaPatcher.log(3, 8, "=== VERSION MISMATCH ===");
        EuphoriaPatcher.log(3, 8, "Found shader: " + originalFileName + " (version " + detectedVersion + ")");
        EuphoriaPatcher.log(3, 8, "Required shader: " + EuphoriaPatcher.BRAND_NAME + "Shaders " + EuphoriaPatcher.VERSION);
        EuphoriaPatcher.log(3, 8, "You have a NEWER shader version than what this mod version supports.");

        if (UpdateChecker.isUpdateAvailable()) {
            EuphoriaPatcher.log(3, 8, "");
            EuphoriaPatcher.log(3, 8, "SOLUTION: Update " + EuphoriaPatcher.PATCH_NAME + " to the latest version: " + UpdateChecker.getNewModVersion());
            EuphoriaPatcher.log(3, 8, "Download from: " + EuphoriaPatcher.EP_DOWNLOAD_URL);
            copyLinkMessage();
        } else {
            EuphoriaPatcher.log(3, 8, "");
            EuphoriaPatcher.log(3, 8, "SOLUTION 1: Wait for a " + EuphoriaPatcher.PATCH_NAME + " update that supports version " + detectedVersion);
            EuphoriaPatcher.log(3, 8, "SOLUTION 2: Download the compatible shader version " + EuphoriaPatcher.VERSION);
            EuphoriaPatcher.log(3, 8, "Download from: " + EuphoriaPatcher.COMP_DOWNLOAD_URL);
            copyLinkMessage();
        }
    }

    /**
     * Handles detection of a file that appears incomplete or modified
     */
    private static void handleIncompleteFile(String originalFileName) {
        EuphoriaPatcher.log(3, 8, "=== FILE VERIFICATION FAILED ===");
        EuphoriaPatcher.log(3, 8, "Shader file: " + originalFileName);
        EuphoriaPatcher.log(3, 8, "This file appears to be incomplete or has been modified.");
        EuphoriaPatcher.log(3, 8, "This can happen if the shader was manually edited or if it's from an unofficial source.");
        EuphoriaPatcher.log(3, 8, "");
        EuphoriaPatcher.log(3, 8, "SOLUTION: Re-download " + EuphoriaPatcher.BRAND_NAME + "Shaders " + EuphoriaPatcher.VERSION);
        EuphoriaPatcher.log(3, 8, "Download from: " + EuphoriaPatcher.COMP_DOWNLOAD_URL);
        copyLinkMessage();

    }

    /**
     * Handles detection of completely wrong shader version
     */
    private static void handleWrongVersion(String originalFileName) {
        EuphoriaPatcher.log(3, 8, "=== WRONG SHADER VERSION ===");
        EuphoriaPatcher.log(3, 8, "Found: " + originalFileName);
        EuphoriaPatcher.log(3, 8, "Required: " + EuphoriaPatcher.BRAND_NAME + "Shaders " + EuphoriaPatcher.VERSION);
        EuphoriaPatcher.log(3, 8, "");
        EuphoriaPatcher.log(3, 8, "SOLUTION: Download the correct shader version " + EuphoriaPatcher.VERSION);
        EuphoriaPatcher.log(3, 8, "Download from: " + EuphoriaPatcher.COMP_DOWNLOAD_URL);
        copyLinkMessage();
    }

    /**
     * Handles detection of test/dev/pre-release versions
     */
    public static void handleDevVersion(String fileName, String originalFileName) {
        EuphoriaPatcher.log(3, 8, "=== DEV VERSION DETECTED ===");
        EuphoriaPatcher.log(3, 8, "Found: " + originalFileName);
        EuphoriaPatcher.log(3, 8, "This appears to be a test, dev, or pre-release version.");
        EuphoriaPatcher.log(3, 8, "");
        EuphoriaPatcher.log(3, 8, "SOLUTION: Download the official " + EuphoriaPatcher.BRAND_NAME + " release version: " + EuphoriaPatcher.VERSION);
        EuphoriaPatcher.log(3, 8, "Download from: " + EuphoriaPatcher.COMP_DOWNLOAD_URL);
        copyLinkMessage();

        startWatcherAndTrackFile(fileName);
    }

    /**
     * Handles hash verification failures
     */
    public static void handleHashMismatch(String fileName, String originalFileName) {
        EuphoriaPatcher.log(3, 8, "=== FILE VERIFICATION FAILED ===");
        EuphoriaPatcher.log(3, 8, "Shader file: " + originalFileName);
        EuphoriaPatcher.log(3, 8, "This file appears to have been modified.");
        EuphoriaPatcher.log(3, 8, "This can happen if the shader was manually edited or if it's from an unofficial source.");
        EuphoriaPatcher.log(3, 8, "File size matches but content hash does not.");
        EuphoriaPatcher.log(3, 8, "");
        EuphoriaPatcher.log(3, 8, "SOLUTION: Download the original unmodified " + EuphoriaPatcher.BRAND_NAME + " shader");
        EuphoriaPatcher.log(3, 8, "Download from: " + EuphoriaPatcher.COMP_DOWNLOAD_URL);
        copyLinkMessage();

        startWatcherAndTrackFile(fileName);
    }

    public static void copyLinkMessage(){
        if (ModLoaderSpecifics.isInstance(ModLoaderSpecifics.FORGE_1_7_10) || ModLoaderSpecifics.isInstance(ModLoaderSpecifics.FORGE_LEGACY)) {
            EuphoriaPatcher.log(3, 8, "Copy the download link from " + EuphoriaLogger.ERROR_LOG_FILE_NAME + " in your shaderpacks folder.");
        } else if (ShaderLoader.getShaderLoader().equals(ShaderLoader.OPTIFINE)) {
            EuphoriaPatcher.log(3, 8, "The download link has been copied to your clipboard. Paste it in your browser.");
        }
    }

    /**
     * Starts the shader watcher and tracks the invalid file
     */
    private static void startWatcherAndTrackFile(String fileName) {
        EuphoriaPatcher.log(0, "Watching for the correct shader to be added...");

        EuphoriaPatcher instance = EuphoriaPatcher.getInstance();
        if (instance != null) {
            instance.startWatcherAfterByteSizeFailure();
            ShaderpacksWatcher watcher = instance.getShaderpacksWatcher();
            if (watcher != null) {
                watcher.trackInvalidByteSizeFile(fileName);
            }
        }
    }
}
