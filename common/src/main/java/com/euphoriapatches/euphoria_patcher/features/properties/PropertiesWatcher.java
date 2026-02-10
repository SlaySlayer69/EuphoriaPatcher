package com.euphoriapatches.euphoria_patcher.features.properties;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.integration.ShaderLoader;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.services.ShaderDetector;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Monitors shader loader config file and current shader's properties directory for changes.
 * Automatically detects shader changes and triggers property merging.
 */
public class PropertiesWatcher {

    private static volatile String currentShaderpackPath = null;
    private static Thread configWatcherThread = null;
    private static Thread propertiesWatcherThread = null;
    private static volatile boolean running = false;
    private static WatchService configWatchService = null;
    private static WatchService propertiesWatchService = null;
    private static final Map<WatchKey, Path> propertiesWatchKeys = new ConcurrentHashMap<>();

    // Track last modification times to debounce rapid file changes
    private static final Map<Path, Long> lastModificationTimes = new ConcurrentHashMap<>();
    private static final long DEBOUNCE_DELAY_MS = 1000; // 1 second debounce

    // Track last merge time to avoid duplicate merges
    private static volatile long lastMergeTime = 0;
    private static final long MERGE_COOLDOWN_MS = 2000; // 2 seconds between merges

    /**
     * Start monitoring both the shader loader config and the current shader's properties
     */
    public static void startWatcher() {
        if (running) {
            debugLog("Watcher already running - stopping old threads first");
            stopMonitoring();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // Ignore
            }
        }

        running = true;
        debugLog("Starting PropertiesWatcher");

        // Start the config file watcher thread
        startConfigWatcher();

        // Initialize current shader and start properties watcher if applicable
        updateCurrentShader();
    }

    /**
     * Start watching the shader loader config file for changes
     */
    private static void startConfigWatcher() {
        configWatcherThread = new Thread(() -> {
            debugLog("Started config file monitoring thread");

            try {
                Path configPath = ShaderLoader.getShaderLoaderConfigPath();
                if (configPath == null || !Files.exists(configPath)) {
                    debugLog("No shader loader config file found, cannot monitor");
                    return;
                }

                configWatchService = FileSystems.getDefault().newWatchService();
                Path configDir = configPath.getParent();
                WatchKey configKey = configDir.register(configWatchService, ENTRY_MODIFY);

                debugLog("Registered WatchService on config directory: " + configDir);

                while (running) {
                    try {
                        WatchKey key = configWatchService.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS);

                        if (key == null) {
                            continue;
                        }

                        for (WatchEvent<?> event : key.pollEvents()) {
                            WatchEvent.Kind<?> kind = event.kind();

                            if (kind == OVERFLOW) {
                                continue;
                            }

                            @SuppressWarnings("unchecked")
                            WatchEvent<Path> ev = (WatchEvent<Path>) event;
                            Path filename = ev.context();
                            Path changedFile = configDir.resolve(filename);

                            // Check if the changed file is our config file
                            if (Files.isSameFile(changedFile, configPath)) {
                                debugLog("Config file changed, checking for shader change");

                                // Small delay to ensure config write is complete
                                try {
                                    Thread.sleep(500);
                                } catch (InterruptedException e) {
                                    // Ignore
                                }

                                updateCurrentShader();
                            }
                        }

                        boolean valid = key.reset();
                        if (!valid) {
                            debugLog("Config WatchKey no longer valid");
                            break;
                        }

                    } catch (InterruptedException e) {
                        if (running) {
                            debugLog("Config watcher interrupted");
                        }
                        break;
                    } catch (Exception e) {
                        debugLog("Error in config watcher: " + e.getMessage());
                        debugLog(EuphoriaLogger.getStackTrace(e));
                    }
                }

            } catch (IOException e) {
                debugLog("Failed to create config WatchService: " + e.getMessage());
                debugLog(EuphoriaLogger.getStackTrace(e));
            } finally {
                if (configWatchService != null) {
                    try {
                        configWatchService.close();
                    } catch (IOException e) {
                        debugLog("Error closing config watch service: " + e.getMessage());
                    }
                }
            }

            debugLog("Stopped config file monitoring thread");
        }, "PropertiesWatcher-Config");

        configWatcherThread.setDaemon(true);
        configWatcherThread.start();
    }

    /**
     * Update the current shader and restart properties monitoring if needed
     */
    private static void updateCurrentShader() {
        Path shaderpackPath = ShaderLoader.getCurrentShaderpackPath();

        if (shaderpackPath == null) {
            debugLog("No current shaderpack detected");
            stopPropertiesWatcher();
            currentShaderpackPath = null;
            return;
        }

        String pathKey = shaderpackPath.toString();

        // Check if we switched to a new shaderpack
        if (!pathKey.equals(currentShaderpackPath)) {
            debugLog("Shaderpack changed to: " + pathKey);

            // Stop monitoring the old shaderpack's properties
            if (currentShaderpackPath != null) {
                debugLog("Stopping monitoring for previous shaderpack");
                stopPropertiesWatcher();
            }

            currentShaderpackPath = pathKey;

            // Check if this is an Euphoria Patches shader
            EuphoriaPatcher instance = EuphoriaPatcher.getInstance();
            if (instance == null) {
                debugLog("EuphoriaPatcher instance not available yet");
                return;
            }

            ShaderDetector shaderDetector = instance.getShaderDetector();
            if (!shaderDetector.isEuphoriaPatchesShader(shaderpackPath)) {
                debugLog("Shaderpack is not an Euphoria Patches shader, skipping monitoring");
                return;
            }

            // Check if properties directory exists
            Path propertiesDir = shaderpackPath.resolve("shaders/properties");
            if (!Files.exists(propertiesDir) || !Files.isDirectory(propertiesDir)) {
                debugLog("Properties directory does not exist, skipping monitoring");
                return;
            }

            debugLog("Euphoria Patches shader with properties directory detected");

            // Perform initial merge
            performMerge(shaderpackPath);

            // Start monitoring if it's a directory
            if (Files.isDirectory(shaderpackPath)) {
                debugLog("Starting properties monitoring for directory shaderpack");
                startPropertiesWatcher(shaderpackPath);
            } else {
                debugLog("Shaderpack is not a directory, skipping continuous monitoring");
            }
        }
    }

    /**
     * Start monitoring the properties directory for changes
     */
    private static void startPropertiesWatcher(Path shaderpackPath) {
        propertiesWatcherThread = new Thread(() -> {
            debugLog("Started properties monitoring thread for: " + shaderpackPath);

            try {
                monitorPropertiesDirectory(shaderpackPath);
            } catch (Exception e) {
                debugLog("Error in properties monitoring thread: " + e.getMessage());
                debugLog(EuphoriaLogger.getStackTrace(e));
            }

            debugLog("Stopped properties monitoring thread");
        }, "PropertiesWatcher-Properties");

        propertiesWatcherThread.setDaemon(true);
        propertiesWatcherThread.start();
    }

    /**
     * Stop the properties watcher thread only
     */
    private static void stopPropertiesWatcher() {
        if (propertiesWatcherThread != null) {
            debugLog("Stopping properties watcher thread");
            propertiesWatcherThread.interrupt();
            try {
                propertiesWatcherThread.join(2000);
            } catch (InterruptedException e) {
                debugLog("Interrupted while waiting for properties watcher to stop");
            }
            propertiesWatcherThread = null;
        }

        // Cancel all properties watch keys
        for (WatchKey key : propertiesWatchKeys.keySet()) {
            key.cancel();
        }
        propertiesWatchKeys.clear();

        if (propertiesWatchService != null) {
            try {
                propertiesWatchService.close();
            } catch (IOException e) {
                debugLog("Error closing properties watch service: " + e.getMessage());
            }
            propertiesWatchService = null;
        }

        lastModificationTimes.clear();
    }

    /**
     * Stop monitoring both config and properties
     */
    public static void stopMonitoring() {
        if (!running) {
            return;
        }

        debugLog("Stopping all monitoring threads");
        running = false;

        // Stop config watcher
        if (configWatcherThread != null) {
            configWatcherThread.interrupt();
            try {
                configWatcherThread.join(2000);
            } catch (InterruptedException e) {
                debugLog("Interrupted while waiting for config watcher to stop");
            }
            configWatcherThread = null;
        }

        if (configWatchService != null) {
            try {
                configWatchService.close();
            } catch (IOException e) {
                debugLog("Error closing config watch service: " + e.getMessage());
            }
            configWatchService = null;
        }

        // Stop properties watcher
        stopPropertiesWatcher();

        currentShaderpackPath = null;
    }

    /**
     * Monitor the properties directory using WatchService
     */
    private static void monitorPropertiesDirectory(Path shaderpackPath) {
        Path propertiesDir = shaderpackPath.resolve("shaders/properties");

        if (!Files.exists(propertiesDir) || !Files.isDirectory(propertiesDir)) {
            debugLog("Properties directory does not exist, cannot monitor");
            return;
        }

        try {
            propertiesWatchService = FileSystems.getDefault().newWatchService();

            // Register the properties directory and all subdirectories
            registerDirectoryTree(propertiesDir);

            debugLog("Registered WatchService on properties directory and subdirectories");

            while (running && propertiesWatcherThread != null && !propertiesWatcherThread.isInterrupted()) {
                try {
                    WatchKey key = propertiesWatchService.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS);

                    if (key == null) {
                        continue;
                    }

                    Path dir = propertiesWatchKeys.get(key);
                    if (dir == null) {
                        debugLog("WatchKey not in map, skipping");
                        key.reset();
                        continue;
                    }

                    boolean shouldMerge = false;

                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();

                        if (kind == OVERFLOW) {
                            continue;
                        }

                        @SuppressWarnings("unchecked")
                        WatchEvent<Path> ev = (WatchEvent<Path>) event;
                        Path filename = ev.context();
                        Path fullPath = dir.resolve(filename);

                        debugLog("Detected " + kind.name() + " for: " + fullPath);

                        // Handle directory creation - register new directory for watching
                        if (kind == ENTRY_CREATE && Files.isDirectory(fullPath)) {
                            debugLog("New directory created, registering: " + fullPath);
                            registerDirectoryTree(fullPath);
                        }

                        // Check if this is a .properties file or a directory change
                        if (fullPath.toString().endsWith(".properties") || Files.isDirectory(fullPath)) {
                            // Update modification time
                            long currentTime = System.currentTimeMillis();
                            Long lastModTime = lastModificationTimes.get(fullPath);

                            if (lastModTime == null || (currentTime - lastModTime) > DEBOUNCE_DELAY_MS) {
                                lastModificationTimes.put(fullPath, currentTime);
                                shouldMerge = true;
                                debugLog("Change detected in properties structure, will trigger merge");
                            } else {
                                debugLog("Change detected but within debounce period, skipping");
                            }
                        }
                    }

                    boolean valid = key.reset();
                    if (!valid) {
                        debugLog("WatchKey no longer valid, removing");
                        propertiesWatchKeys.remove(key);
                    }

                    // Perform merge if needed (with cooldown)
                    if (shouldMerge) {
                        long currentTime = System.currentTimeMillis();
                        if ((currentTime - lastMergeTime) > MERGE_COOLDOWN_MS) {
                            // Small delay to ensure file write is complete
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                // Ignore
                            }

                            debugLog("Triggering properties merge");
                            performMerge(shaderpackPath);
                            lastMergeTime = currentTime;
                        } else {
                            debugLog("Merge request within cooldown period, skipping");
                        }
                    }

                } catch (InterruptedException e) {
                    debugLog("Properties watcher interrupted");
                    break;
                } catch (Exception e) {
                    debugLog("Error processing properties watch events: " + e.getMessage());
                    debugLog(EuphoriaLogger.getStackTrace(e));
                }
            }

        } catch (IOException e) {
            debugLog("Failed to create properties WatchService: " + e.getMessage());
            debugLog(EuphoriaLogger.getStackTrace(e));
        }
    }

    /**
     * Register a directory and all its subdirectories with the watch service
     */
    private static void registerDirectoryTree(Path directory) throws IOException {
        // Register the directory itself
        registerDirectory(directory);

        // Walk the tree and register all subdirectories
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(Files::isDirectory)
                 .filter(path -> !path.equals(directory)) // Skip the root as it's already registered
                 .forEach(path -> {
                     try {
                         registerDirectory(path);
                     } catch (IOException e) {
                         debugLog("Failed to register directory: " + path + " - " + e.getMessage());
                     }
                 });
        }
    }

    /**
     * Register a single directory with the watch service
     */
    private static void registerDirectory(Path directory) throws IOException {
        WatchKey key = directory.register(propertiesWatchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        propertiesWatchKeys.put(key, directory);
        debugLog("Registered directory: " + directory);
    }

    /**
     * Perform the actual merge operation
     */
    private static void performMerge(Path shaderpackPath) {
        try {
            Path propertiesDir = shaderpackPath.resolve("shaders/properties");
            Path targetFile = shaderpackPath.resolve("shaders/block.properties");

            debugLog("Initiating merge: " + propertiesDir + " -> " + targetFile);

            boolean success = PropertiesMerger.mergeProperties(propertiesDir, targetFile);

            if (success) {
                debugLog("Properties merge completed successfully");
                EuphoriaPatcher.log(0, "Auto-merged properties files into block.properties");
            } else {
                debugLog("Properties merge failed");
            }

        } catch (Exception e) {
            debugLog("Error during merge: " + e.getMessage());
            debugLog(EuphoriaLogger.getStackTrace(e));
        }
    }

    /**
     * Get the current shaderpack path being monitored
     * @return The current shaderpack path, or null if none is being monitored
     */
    public static Path getCurrentShaderpackPath() {
        return currentShaderpackPath != null ? Paths.get(currentShaderpackPath) : null;
    }

    /**
     * Check if monitoring is currently active
     * @return true if monitoring is running, false otherwise
     */
    public static boolean isMonitoring() {
        return running;
    }

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[PropertiesWatcher] " + message);
    }
}
