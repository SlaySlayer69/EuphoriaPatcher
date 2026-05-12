package com.euphoriapatches.euphoria_patcher.monitoring;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.services.ShaderDetector;
import com.euphoriapatches.euphoria_patcher.io.ArchiveOperations;
import com.euphoriapatches.euphoria_patcher.util.shader.ShaderPropertyReader;

import java.nio.file.Path;

public class ShaderpacksWatcherUtils {

    private ShaderpacksWatcher shaderpacksWatcher;
    private static ShaderpacksWatcherUtils instance;

    public ShaderpacksWatcherUtils() {
        instance = this;
    }

    public static ShaderpacksWatcherUtils getInstance() {
        return instance;
    }

    public void startShaderpacksWatcher() {
        if (shaderpacksWatcher != null && shaderpacksWatcher.isRunning()) return;

        shaderpacksWatcher = ShaderpacksWatcher.createAndStart(this, true);
        if (shaderpacksWatcher != null) {
            EuphoriaPatcher.log(0, "Watching shaderpacks folder for changes...");
        }
    }

    public ShaderpacksWatcher getShaderpacksWatcher() {
        return shaderpacksWatcher;
    }

    private void stopShaderpacksWatcher() {
        if (shaderpacksWatcher != null) {
            shaderpacksWatcher.stopWatching();
        }
    }

    public void startWatcherAfterByteSizeFailure() {
        if (shaderpacksWatcher != null) {
            shaderpacksWatcher.resetAfterByeSizeFailure();
        } else {
            startShaderpacksWatcher();
        }
    }

    public synchronized boolean processNewShaderpack(Path baseFile) {
        try {
            EuphoriaPatcher.log(0, "Processing newly detected shader pack: " + baseFile.getFileName());

            EuphoriaPatcher instance = EuphoriaPatcher.getInstance();
            ShaderDetector shaderDetector = instance.getShaderDetector();

            // Create shader info object
            ShaderDetector.ShaderInfo shaderInfo = new ShaderDetector.ShaderInfo();
            shaderInfo.baseFile = baseFile;
            String name = baseFile.getFileName().toString();

            // Check if this is a newer dev version first - if so, just accept it
            if (shaderDetector.isNewerDevVersion(baseFile, shaderInfo)) {
                debugLog("Accepted newer dev version: " + baseFile.getFileName());

                stopShaderpacksWatcher();
                instance.thankYouMessage(baseFile, shaderInfo.styleUnbound, shaderInfo.styleReimagined, shaderInfo.installedDir, true);
                return true;
            }

            // Not a dev version, proceed with normal patching
            // Create temporary directory
            Path temp = ArchiveOperations.createTempDirectory();
            if (temp == null) return false;

            // Determine style from filename or common.glsl
            if (name.contains("Reimagined")) {
                shaderInfo.styleReimagined = true;
            } else if (name.contains("Unbound")) {
                shaderInfo.styleUnbound = true;
            } else {
                // If not clear from filename, check common.glsl
                String detectedStyle = ShaderPropertyReader.detectStyleFromCommonFile(baseFile, EuphoriaPatcher.COMMON_LOCATION);
                shaderInfo.styleReimagined = "Reimagined".equals(detectedStyle);
                shaderInfo.styleUnbound = "Unbound".equals(detectedStyle);
            }

            // Use the common method for patching
            boolean success = instance.completeShaderPatching(shaderInfo, temp);

            // Stop watching only if successful
            if (success) {
                stopShaderpacksWatcher();
            } else if (shaderpacksWatcher != null) {
                // Track this file as having an invalid byte size
                shaderpacksWatcher.trackInvalidByteSizeFile(baseFile.getFileName().toString());
            }

            return success;
        } catch (Exception e) {
            EuphoriaPatcher.log(3, "Error processing newly detected shader pack: " + e.getMessage());
            return false;
        }
    }

    private static void debugLog(String message){
        EuphoriaLogger.debugLog("[ShaderpacksWatcherUtils] " + message);
    }
}
