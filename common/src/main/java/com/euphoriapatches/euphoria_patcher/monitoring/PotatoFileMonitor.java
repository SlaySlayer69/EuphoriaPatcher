package com.euphoriapatches.euphoria_patcher.monitoring;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.integration.iris.IrisReloadManager;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.services.ShaderDetector;
import com.euphoriapatches.euphoria_patcher.util.ArchiveOperations;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Monitors the presence of potato.png in shaderpacks and triggers reloads when it changes
 */
public class PotatoFileMonitor {
    // Per-shaderpack state tracking
    private static final Map<String, Boolean> shaderpackStates = new HashMap<>();
    // Track initial state to know if shaderpack ever had potato.png
    private static final Map<String, Boolean> shaderpackInitialStates = new HashMap<>();
    private static volatile String currentShaderpackPath = null;
    private static Thread potatoMonitorThread = null;
    private static volatile boolean running = false;
    private static WatchService watchService = null;
    private static WatchKey watchKey = null;

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[PotatoFileMonitor] " + message);
    }

    /**
     * Checks if the current shaderpack has potato.png
     * @param shaderpackPath The path to the current shaderpack
     * @return true if potato.png exists, false otherwise
     */
    public static boolean checkPotatoExists(Path shaderpackPath) {
        if (shaderpackPath == null) {
            debugLog("Shaderpack path is null");
            return false;
        }

        String pathKey = shaderpackPath.toString();
        Boolean initialState = shaderpackInitialStates.get(pathKey);

        // If this shaderpack initially didn't have potato.png, use cached result (always false)
        // This avoids expensive file system checks for the majority of shaderpacks
        if (initialState != null && !initialState) {
            debugLog("Using cached result for shaderpack that never had potato.png");
            return false;
        }

        // For shaderpacks that have/had potato.png, always perform fresh check
        // This detects changes made while using other shaderpacks
        boolean exists = performPotatoCheck(shaderpackPath);
        debugLog("Fresh check for potato.png: " + exists);

        return exists;
    }

    /**
     * Updates the current shaderpack being monitored
     * @param shaderpackPath The new shaderpack path
     */
    public static void setCurrentShaderpack(Path shaderpackPath) {
        if (shaderpackPath == null) {
            // Stop monitoring when no shaderpack is active
            stopMonitoring();
            currentShaderpackPath = null;
            return;
        }

        String pathKey = shaderpackPath.toString();

        // Check if we switched to a new shaderpack
        if (!pathKey.equals(currentShaderpackPath)) {
            debugLog("Shaderpack changed from " + currentShaderpackPath + " to " + pathKey);

            // Stop monitoring the old shaderpack
            if (running) {
                debugLog("Stopping monitoring for previous shaderpack");
                stopMonitoring();
            }

            currentShaderpackPath = pathKey;

            // Check current state and compare with last known state for this shaderpack
            boolean currentState = checkPotatoExists(shaderpackPath);
            Boolean lastKnownState = shaderpackStates.get(pathKey);

            // Track initial state on first encounter
            if (!shaderpackInitialStates.containsKey(pathKey)) {
                shaderpackInitialStates.put(pathKey, currentState);
                debugLog("Recording initial potato.png state for " + pathKey + ": " + currentState);
            }

            if (!shaderpackInitialStates.get(pathKey)) return; // Never had potato.png, no need to track further

            if (lastKnownState != null && lastKnownState != currentState) {
                debugLog("State changed for " + pathKey + " while it was not active: " + lastKnownState + " -> " + currentState);
                if (currentState) {
                    debugLog("potato.png has been added - triggering shader reload");
                } else {
                    debugLog("potato.png has been removed - triggering shader reload");
                }
                IrisReloadManager.findAndScheduleReload();
            } else if (lastKnownState == null) {
                debugLog("First time loading this shaderpack, potato.png state: " + currentState);
            } else {
                debugLog("No state change detected, potato.png state: " + currentState);
            }

            // Update the state for this shaderpack
            shaderpackStates.put(pathKey, currentState);

            // Start monitoring for directories if they have potato.png
            if (Files.isDirectory(shaderpackPath) && currentState) {
                debugLog("Directory shaderpack with potato.png detected - starting continuous monitoring");
                startMonitoring();
            } else if (Files.isDirectory(shaderpackPath)) {
                debugLog("Directory shaderpack without potato.png - no monitoring needed");
            } else {
                debugLog("ZIP shaderpack detected - no continuous monitoring needed");
            }
        }
    }

    /**
     * Starts the background monitoring thread (only for directory shaderpacks)
     */
    public static void startMonitoring() {
        if (running) {
            debugLog("Monitoring already running - stopping old thread first");
            stopMonitoring();
            // Give the old thread time to clean up
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // Ignore
            }
        }

        // Only start monitoring for directory shaderpacks
        if (currentShaderpackPath == null) {
            debugLog("No shaderpack path set, skipping monitoring");
            return;
        }

        Path shaderpackPath = new java.io.File(currentShaderpackPath).toPath();

        if (!Files.isDirectory(shaderpackPath)) {
            debugLog("Shaderpack is not a directory, skipping continuous monitoring");
            return;
        }

        // Only monitor if it currently has potato.png
        Boolean currentState = shaderpackStates.get(currentShaderpackPath);
        if (currentState == null || !currentState) {
            debugLog("Shaderpack doesn't have potato.png currently, skipping monitoring");
            return;
        }

        running = true;
        // Capture the path at thread creation time to avoid race conditions
        final String monitoringPath = currentShaderpackPath;

        potatoMonitorThread = new Thread(() -> {
            debugLog("Started potato.png monitoring thread for: " + monitoringPath);

            try {
                monitorDirectoryShaderpack(shaderpackPath);
            } catch (Exception e) {
                debugLog("Error in monitoring thread: " + e.getMessage());
            }

            debugLog("Stopped potato.png monitoring thread for: " + monitoringPath);
        }, "PotatoFileMonitor");

        potatoMonitorThread.setDaemon(true);
        potatoMonitorThread.start();
    }

    /**
     * Stops the background monitoring thread
     */
    public static void stopMonitoring() {
        if (!running) {
            return;
        }

        debugLog("Stopping monitoring thread");
        running = false;

        if (potatoMonitorThread != null) {
            potatoMonitorThread.interrupt();
            try {
                potatoMonitorThread.join(2000); // Wait up to 2 seconds
            } catch (InterruptedException e) {
                debugLog("Interrupted while waiting for monitor thread to stop");
            }
            potatoMonitorThread = null;
        }

        watchKey = null;
        watchService = null;
    }

    /**
     * Performs the actual file system check for potato.png
     */
    private static boolean performPotatoCheck(Path shaderpackPath) {
        String potatoRelativePath = "shaders/lib/textures/potato.png";

        EuphoriaPatcher instance = EuphoriaPatcher.getInstance();
        ShaderDetector shaderDetector = instance.getShaderDetector();

        if (!shaderDetector.isEuphoriaPatchesShader(shaderpackPath)) {
            debugLog("Shaderpack is not recognized as Euphoria Patches shader");
            return false;
        }

        try {
            // Check if it's a directory
            if (Files.isDirectory(shaderpackPath)) {
                Path potatoPath = shaderpackPath.resolve(potatoRelativePath);
                return Files.exists(potatoPath);
            }

            // Check if it's a ZIP file
            if (Files.isRegularFile(shaderpackPath) &&
                shaderpackPath.toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                boolean exists = ArchiveOperations.fileExistsInZip(shaderpackPath, potatoRelativePath);
                debugLog("ZIP shaderpack: potato.png " + (exists ? "exists" : "does not exist"));
                return exists;
            }

            debugLog("Shaderpack is neither a directory nor a ZIP file");
            return false;
        } catch (Exception e) {
            debugLog("Exception checking for potato.png: " + e.getMessage());
            return false;
        }
    }

    /**
     * Monitors a directory-based shaderpack using WatchService for efficiency
     */
    private static void monitorDirectoryShaderpack(Path shaderpackPath) {
        debugLog("Using WatchService for directory monitoring");

        // Store the path we're monitoring to detect if it changes
        final String monitoredPath = shaderpackPath.toString();

        try {
            // Create WatchService
            watchService = FileSystems.getDefault().newWatchService();

            // Watch the directory containing potato.png
            Path texturesDir = shaderpackPath.resolve("shaders/lib/textures");

            if (!Files.exists(texturesDir)) {
                debugLog("Textures directory doesn't exist, cannot monitor");
                return;
            }

            watchKey = texturesDir.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
            debugLog("Registered WatchService on: " + texturesDir);

            while (running) {
                try {
                    // Check if the shaderpack has changed - if so, exit this monitoring thread
                    if (!monitoredPath.equals(currentShaderpackPath)) {
                        debugLog("Shaderpack changed during monitoring, stopping old monitor");
                        break;
                    }

                    // Wait for key to be signaled (blocks until event occurs)
                    WatchKey key = watchService.poll(1, java.util.concurrent.TimeUnit.SECONDS);

                    if (key == null) {
                        continue; // No events, check if still running
                    }

                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();

                        if (kind == OVERFLOW) {
                            debugLog("WatchService overflow event");
                            continue;
                        }

                        @SuppressWarnings("unchecked")
                        WatchEvent<Path> ev = (WatchEvent<Path>) event;
                        Path filename = ev.context();

                        // Check if the event is for potato.png, and we're still monitoring the same pack
                        if (filename.toString().equals("potato.png") && monitoredPath.equals(currentShaderpackPath)) {
                            debugLog("Detected potato.png change: " + kind.name());
                            handlePotatoStateChange(shaderpackPath);
                        }
                    }

                    // Reset the key
                    boolean valid = key.reset();
                    if (!valid) {
                        debugLog("WatchKey no longer valid");
                        break;
                    }

                } catch (InterruptedException e) {
                    debugLog("WatchService interrupted");
                    break;
                } catch (Exception e) {
                    debugLog("Error in WatchService loop: " + e.getMessage());
                }
            }

        } catch (IOException e) {
            debugLog("Failed to create WatchService: " + e.getMessage());
        } finally {
            // Clean up WatchService resources for this monitoring thread
            if (watchKey != null) {
                watchKey.cancel();
            }
            if (watchService != null) {
                try {
                    watchService.close();
                } catch (IOException e) {
                    debugLog("Error closing WatchService: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Handles the state change of potato.png presence
     */
    private static void handlePotatoStateChange(Path shaderpackPath) {
        boolean currentState = performPotatoCheck(shaderpackPath);
        String pathKey = shaderpackPath.toString();
        Boolean previousState = shaderpackStates.get(pathKey);

        debugLog("Potato.png state changed from " + previousState + " to " + currentState);
        shaderpackStates.put(pathKey, currentState);

        if (currentState) {
            debugLog("potato.png has returned - triggering shader reload");
        } else {
            debugLog("potato.png has been removed - triggering shader reload");
        }

        IrisReloadManager.findAndScheduleReload();
    }

    /**
     * Gets whether the current shaderpack should have the POTATO_REMOVED define
     * @param shaderpackPath The current shaderpack path
     * @return true if POTATO_REMOVED define should be added, false otherwise
     */
    public static boolean shouldAddPotatoRemovedDefine(Path shaderpackPath) {
        if (shaderpackPath == null) {
            return false;
        }

        setCurrentShaderpack(shaderpackPath);

        boolean potatoExists = checkPotatoExists(shaderpackPath);
        return !potatoExists;
    }
}
