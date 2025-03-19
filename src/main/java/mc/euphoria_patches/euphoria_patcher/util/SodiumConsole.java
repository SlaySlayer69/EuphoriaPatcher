package mc.euphoria_patches.euphoria_patcher.util;

import net.minecraft.text.Text;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class SodiumConsole {
    private static boolean initialized = false;
    private static boolean sodiumAvailable = false;
    private static Object consoleSink = null;
    private static Method logMessageMethod = null;
    private static Class<?> messageLevelClass = null;
    private static Object infoLevel = null;
    private static Object warnLevel = null;
    private static Object severeLevel = null;
    private static boolean useDoubleForFadeTimer = false;
    private static boolean newSignature = false;

    // Known Sodium package paths
    private static final List<String[]> KNOWN_SODIUM_PATHS = Arrays.asList(
            new String[] {
                    "me.jellysquid.mods.sodium.client.gui.console.Console",
                    "me.jellysquid.mods.sodium.client.gui.console.ConsoleSink",
                    "me.jellysquid.mods.sodium.client.gui.console.message.MessageLevel"
            },
            new String[] {
                    "net.caffeinemc.mods.sodium.client.console.Console",
                    "net.caffeinemc.mods.sodium.client.console.ConsoleSink",
                    "net.caffeinemc.mods.sodium.client.console.message.MessageLevel"
            },
            new String[] {
                    "me.jellysquid.mods.sodium.client.console.Console",
                    "me.jellysquid.mods.sodium.client.console.ConsoleSink",
                    "me.jellysquid.mods.sodium.client.console.message.MessageLevel"
            }
        );

        private static void initialize() {
            if (initialized) return;
            initialized = true;

            // Try all known Sodium paths
            for (String[] paths : KNOWN_SODIUM_PATHS) {
                if (tryInitialize(paths[0], paths[1], paths[2])) {
                    return;
                }
            }
        }

        private static boolean tryInitialize(String consolePath, String consoleSinkPath, String messageLevelPath) {
            try {
                // Get Console instance
                Class<?> consoleClass = Class.forName(consolePath);
                Method instanceMethod = consoleClass.getMethod("instance");
                consoleSink = instanceMethod.invoke(null);

                // Get MessageLevel class and constants
                messageLevelClass = Class.forName(messageLevelPath);
                infoLevel = messageLevelClass.getField("INFO").get(null);
                warnLevel = messageLevelClass.getField("WARN").get(null);
                severeLevel = messageLevelClass.getField("SEVERE").get(null);

                // Find suitable method signature
                Class<?> consoleSinkClass = Class.forName(consoleSinkPath);

                // Try each known method signature
                if (tryMethodSignature(consoleSinkClass, "logMessage", String.class, boolean.class, double.class)) {
                    useDoubleForFadeTimer = true;
                    newSignature = true;
                } else if (tryMethodSignature(consoleSinkClass, "logMessage", Text.class, double.class)) {
                    useDoubleForFadeTimer = true;
                    newSignature = false;
                } else if (tryMethodSignature(consoleSinkClass, "logMessage", Text.class, int.class)) {
                    useDoubleForFadeTimer = false;
                    newSignature = false;
                } else if (tryMethodSignature(consoleSinkClass, "add", Text.class, int.class)) {
                    useDoubleForFadeTimer = false;
                    newSignature = false;
                } else {
                    return false;
                }

                sodiumAvailable = true;
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        private static boolean tryMethodSignature(Class<?> consoleSinkClass, String methodName, Class<?>... paramTypes) {
            try {
                // First parameter is always MessageLevel
                Class<?>[] fullParamTypes = new Class<?>[paramTypes.length + 1];
                fullParamTypes[0] = messageLevelClass;
                System.arraycopy(paramTypes, 0, fullParamTypes, 1, paramTypes.length);

                logMessageMethod = consoleSinkClass.getMethod(methodName, fullParamTypes);
                return true;
            } catch (NoSuchMethodException e) {
                return false;
            }
        }

        public static boolean isSodiumAvailable() {
            if (!initialized) initialize();
            return sodiumAvailable;
        }

        public static void logMessage(int level, int messageFadeTimer, String message) {
            if (!initialized) initialize();
            if (!sodiumAvailable) return;

            try {
                Object messageLevel = getMessageLevel(level);

                if (newSignature) {
                    // Newest format (1.21+): MessageLevel, String, boolean, double
                    double fadeTimerDouble = messageFadeTimer;
                    boolean isPersistent = messageFadeTimer <= 0;
                    logMessageMethod.invoke(consoleSink, messageLevel, message, isPersistent, fadeTimerDouble);
                } else if (useDoubleForFadeTimer) {
                    // Older double format: MessageLevel, Text, double
                    double fadeTimerDouble = messageFadeTimer;
                    logMessageMethod.invoke(consoleSink, messageLevel, Text.of(message), fadeTimerDouble);
                } else {
                    // Original format: MessageLevel, Text, int
                    logMessageMethod.invoke(consoleSink, messageLevel, Text.of(message), messageFadeTimer);
            }
        } catch (Exception e) {
            // If something goes wrong, disable Sodium console
            sodiumAvailable = false;
        }
    }

    private static Object getMessageLevel(int level) {
        if (level == 1) {
            return infoLevel;
        } else if (level == 2) {
            return warnLevel;
        } else {
            return severeLevel;
        }
    }

    // ==========================================
    // DEBUGGING METHODS (Not used in production)
    // ==========================================

    /**
     * Debug version of initialize with verbose logging.
     * Not used in production code.
     */
    private static void initializeWithDebugLogs() {
        if (initialized) return;
        initialized = true;

        for (String[] paths : KNOWN_SODIUM_PATHS) {
            if (tryInitializeWithDebugLogs(paths[0], paths[1], paths[2])) {
                return;
            }
        }

        System.out.println("Could not find any compatible Sodium console implementation");
    }

    /**
     * Debug version of tryInitialize with verbose logging.
     * Not used in production code.
     */
    private static boolean tryInitializeWithDebugLogs(String consolePath, String consoleSinkPath, String messageLevelPath) {
        try {
            System.out.println("Trying to initialize Sodium console with: " + consolePath);

            // Get Console instance
            Object consoleInstance = getConsoleInstanceWithDebugLogs(consolePath);
            if (consoleInstance == null) return false;

            // Get MessageLevel class and constants
            if (!initializeMessageLevelsWithDebugLogs(messageLevelPath)) return false;

            // Find suitable method signature
            if (!findCompatibleMethodWithDebugLogs(consoleSinkPath)) return false;

            sodiumAvailable = true;
            System.out.println("Successfully initialized Sodium console integration using: " + consolePath);
            return true;
        } catch (Exception e) {
            System.out.println("Error initializing Sodium console: " + e.getClass().getName() + ": " + e.getMessage());
        }
        System.out.println("Failed to initialize Sodium console integration using: " + consolePath);
        return false;
    }

    /**
     * Debug version of getConsoleInstance with verbose logging.
     * Not used in production code.
     */
    private static Object getConsoleInstanceWithDebugLogs(String consolePath) throws Exception {
        try {
            Class<?> consoleClass = Class.forName(consolePath);
            System.out.println("Found Console class: " + consolePath);

            Method instanceMethod = consoleClass.getMethod("instance");
            System.out.println("Found instance() method");

            // Get the ConsoleSink instance
            consoleSink = instanceMethod.invoke(null);
            System.out.println("Got ConsoleSink instance");
            return consoleSink;
        } catch (ClassNotFoundException e) {
            System.out.println("Class not found: " + e.getMessage());
            return null;
        }
    }

    /**
     * Debug version of initializeMessageLevels with verbose logging.
     * Not used in production code.
     */
    private static boolean initializeMessageLevelsWithDebugLogs(String messageLevelPath) {
        try {
            // Get the MessageLevel class and its constants
            messageLevelClass = Class.forName(messageLevelPath);
            System.out.println("Found MessageLevel class");

            infoLevel = messageLevelClass.getField("INFO").get(null);
            warnLevel = messageLevelClass.getField("WARN").get(null);
            severeLevel = messageLevelClass.getField("SEVERE").get(null);
            System.out.println("Got message level constants");
            return true;
        } catch (Exception e) {
            System.out.println("Failed to initialize message levels: " + e.getMessage());
            return false;
        }
    }

    /**
     * Debug version of findCompatibleMethod with verbose logging.
     * Not used in production code.
     */
    private static boolean findCompatibleMethodWithDebugLogs(String consoleSinkPath) {
        try {
            Class<?> consoleSinkClass = Class.forName(consoleSinkPath);

            // Try each known method signature
            if (tryMethodSignatureWithDebugLogs(consoleSinkClass, "logMessage", String.class, boolean.class, double.class)) {
                useDoubleForFadeTimer = true;
                newSignature = true;
                return true;
            }

            if (tryMethodSignatureWithDebugLogs(consoleSinkClass, "logMessage", Text.class, double.class)) {
                useDoubleForFadeTimer = true;
                newSignature = false;
                return true;
            }

            if (tryMethodSignatureWithDebugLogs(consoleSinkClass, "logMessage", Text.class, int.class)) {
                useDoubleForFadeTimer = false;
                newSignature = false;
                return true;
            }

            if (tryMethodSignatureWithDebugLogs(consoleSinkClass, "add", Text.class, int.class)) {
                useDoubleForFadeTimer = false;
                newSignature = false;
                return true;
            }

            // If we get here, list available methods for debugging
            System.out.println("Available methods in " + consoleSinkPath + ":");
            for (Method method : consoleSinkClass.getMethods()) {
                System.out.println("  " + method.getName() + ": " + method);
            }
            return false;
        } catch (ClassNotFoundException e) {
            System.out.println("ConsoleSink class not found: " + e.getMessage());
            return false;
        }
    }

    /**
     * Debug version of tryMethodSignature with verbose logging.
     * Not used in production code.
     */
    private static boolean tryMethodSignatureWithDebugLogs(Class<?> consoleSinkClass, String methodName, Class<?>... paramTypes) {
        try {
            // First parameter is always MessageLevel
            Class<?>[] fullParamTypes = new Class<?>[paramTypes.length + 1];
            fullParamTypes[0] = messageLevelClass;
            System.arraycopy(paramTypes, 0, fullParamTypes, 1, paramTypes.length);

            logMessageMethod = consoleSinkClass.getMethod(methodName, fullParamTypes);
            System.out.println("Found " + methodName + " method with signature: " + Arrays.toString(paramTypes));
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}