package mc.euphoria_patches.euphoria_patcher.util;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Monitors the presence of potato.png in shaderpacks and triggers reloads when it changes
 */
public class PotatoFileMonitor {
    private static final Map<String, Boolean> potatoCache = new HashMap<>();
    private static volatile String currentShaderpackPath = null;
    private static volatile Boolean lastKnownState = null;
    private static volatile Boolean initialState = null; // Track the initial state
    private static volatile int monitorLogCount = 0; // Counter for monitoring logs
    private static Thread potatoMonitorThread = null;
    private static volatile boolean running = false;
    
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
        
        // Check cache first
        if (potatoCache.containsKey(pathKey)) {
            boolean cachedValue = potatoCache.get(pathKey);
            debugLog("Using cached value for " + pathKey + ": " + cachedValue);
            return cachedValue;
        }
        
        // Not in cache, perform actual check
        boolean exists = performPotatoCheck(shaderpackPath);
        potatoCache.put(pathKey, exists);
        debugLog("Cached new value for " + pathKey + ": " + exists);
        
        return exists;
    }
    
    /**
     * Updates the current shaderpack being monitored
     * @param shaderpackPath The new shaderpack path
     */
    public static void setCurrentShaderpack(Path shaderpackPath) {
        if (shaderpackPath == null) {
            currentShaderpackPath = null;
            lastKnownState = null;
            initialState = null;
            monitorLogCount = 0;
            return;
        }
        
        String pathKey = shaderpackPath.toString();
        
        // Check if we switched to a new shaderpack
        if (!pathKey.equals(currentShaderpackPath)) {
            debugLog("Shaderpack changed from " + currentShaderpackPath + " to " + pathKey);
            currentShaderpackPath = pathKey;
            boolean potatoExists = checkPotatoExists(shaderpackPath);
            lastKnownState = potatoExists;
            initialState = potatoExists;
            monitorLogCount = 0;
            debugLog("Initial potato.png state for new shaderpack: " + initialState);
        }
    }
    
    /**
     * Starts the background monitoring thread
     */
    public static void startMonitoring() {
        if (running) {
            debugLog("Monitoring already running");
            return;
        }
        
        running = true;
        potatoMonitorThread = new Thread(() -> {
            debugLog("Started potato.png monitoring thread");
            
            while (running) {
                try {
                    // Only monitor if we have a shaderpack and it initially had potato.png
                    // Continue monitoring even after removal to detect when it comes back
                    if (currentShaderpackPath != null && initialState != null && initialState) {
                        Path shaderpackPath = new java.io.File(currentShaderpackPath).toPath();
                        boolean currentState = performPotatoCheck(shaderpackPath);
                        
                        // If state changed, update cache and trigger reload
                        if (currentState != lastKnownState) {
                            debugLog("Potato.png state changed from " + lastKnownState + " to " + currentState);
                            potatoCache.put(currentShaderpackPath, currentState);
                            lastKnownState = currentState;
                            monitorLogCount = 0;
                            
                            if (currentState) {
                                debugLog("potato.png has returned - triggering shader reload");
                            } else {
                                debugLog("potato.png has been removed - triggering shader reload");
                            }
                            
                            IrisReloadManager.findAndScheduleReload();
                        } else if (monitorLogCount < 6) {
                            monitorLogCount++;
                            if (currentState) {
                                debugLog("Monitoring: potato.png is present (" + monitorLogCount + "/6)");
                            } else {
                                debugLog("Monitoring: potato.png is absent (" + monitorLogCount + "/6)");
                            }
                        } else if (monitorLogCount == 6) {
                            monitorLogCount++;
                            debugLog("Monitoring: potato.png state stable, no changes detected, will continue monitoring silently.");
                        }
                    }
                    
                    // Check every 1 second
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    debugLog("Monitoring thread interrupted");
                    break;
                } catch (Exception e) {
                    debugLog("Error in monitoring thread: " + e.getMessage());
                }
            }
            
            debugLog("Stopped potato.png monitoring thread");
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
    }
    
    /**
     * Performs the actual file system check for potato.png
     */
    private static boolean performPotatoCheck(Path shaderpackPath) {
        String potatoRelativePath = "shaders/lib/textures/potato.png";
        
        try {
            // Check if it's a directory
            if (Files.isDirectory(shaderpackPath)) {
                Path potatoPath = shaderpackPath.resolve(potatoRelativePath);
                boolean exists = Files.exists(potatoPath);
                return exists;
            }
            
            // Check if it's a ZIP file
            if (Files.isRegularFile(shaderpackPath) && 
                shaderpackPath.toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                
                try (ZipFile zipFile = new ZipFile(shaderpackPath.toFile())) {
                    ZipEntry entry = zipFile.getEntry(potatoRelativePath);
                    boolean exists = entry != null;
                    debugLog("ZIP shaderpack: potato.png " + (exists ? "exists" : "does not exist"));
                    return exists;
                } catch (IOException e) {
                    debugLog("Error reading ZIP file: " + e.getMessage());
                    return false;
                }
            }
            
            debugLog("Shaderpack is neither a directory nor a ZIP file");
            return false;
        } catch (Exception e) {
            debugLog("Exception checking for potato.png: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Clears the cache for a specific shaderpack
     */
    public static void clearCache(String shaderpackPath) {
        if (potatoCache.remove(shaderpackPath) != null) {
            debugLog("Cleared cache for " + shaderpackPath);
        }
    }
    
    /**
     * Clears the entire cache
     */
    public static void clearAllCache() {
        potatoCache.clear();
        currentShaderpackPath = null;
        lastKnownState = null;
        initialState = null;
        monitorLogCount = 0;
        debugLog("Cleared all cache");
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
        
        // Update the current shaderpack being monitored
        setCurrentShaderpack(shaderpackPath);
        
        // Start monitoring if not already running
        if (!running) {
            startMonitoring();
        }
        
        boolean potatoExists = checkPotatoExists(shaderpackPath);
        return !potatoExists;
    }
}