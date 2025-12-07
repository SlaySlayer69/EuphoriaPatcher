package mc.euphoria_patches.euphoria_patcher.integration.iris;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import mc.euphoria_patches.euphoria_patcher.logging.EuphoriaLogger;

/**
 * Centralizes all functionality related to Iris shader reloading
 */
public class IrisReloadManager {
    private static volatile boolean pendingReload = false;
    private static volatile Class<?> pendingIrisClass = null;

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[IrisReloadManager] " + message);
    }

    /**
     * Attempts to find the Iris class from known possible locations
     * @return The Iris class if found, null otherwise
     */
    public static Class<?> findIrisClass() {
        // Try both possible Iris class locations
        try {
            Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
            debugLog("Found Iris class at net.irisshaders.iris.Iris");
            return irisClass;
        } catch (ClassNotFoundException e1) {
            debugLog("Iris class not found at net.irisshaders.iris.Iris, trying alternative location");
            try {
                Class<?> irisClass = Class.forName("net.coderbot.iris.Iris");
                debugLog("Found Iris class at net.coderbot.iris.Iris");
                return irisClass;
            } catch (ClassNotFoundException e2) {
                // Iris isn't installed, this is fine - just log to debug
                debugLog("Iris not found - this is normal if Iris isn't installed");
                return null;
            }
        }
    }

    /**
     * Schedules an Iris shader reload to happen on the next game tick
     * @param irisClass The Iris class to use for reloading
     */
    public static void scheduleReload(Class<?> irisClass) {
        if (irisClass != null) {
            debugLog("Scheduling shader reload");
            pendingIrisClass = irisClass;
            pendingReload = true;
        } else {
            debugLog("Cannot schedule reload - Iris class is null");
        }
    }

    /**
     * Convenience method to find and schedule a reload in one step
     */
    public static void findAndScheduleReload() {
        Class<?> irisClass = findIrisClass();
        if (irisClass != null) {
            scheduleReload(irisClass);
        }
    }

    /**
     * Checks if there's a pending reload and executes it if there is
     * This should be called from the main game thread
     */
    public static void checkPendingReload() {
        if (pendingReload && pendingIrisClass != null) {
            try {
                debugLog("Processing pending shader reload on main thread");
                pendingIrisClass.getMethod("reload").invoke(null);
                debugLog("Successfully reloaded shaders");
            } catch (Exception e) {
                EuphoriaPatcher.log(2, 0, "Error reloading Iris shaders: " + e.getMessage());
            } finally {
                pendingReload = false;
                pendingIrisClass = null;
            }
        }
    }

    /**
     * Checks if Iris is installed
     * @return true if Iris is installed, false otherwise
     */
    public static boolean isIrisInstalled() {
        return findIrisClass() != null;
    }
}
