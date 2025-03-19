package mc.euphoria_patches.euphoria_patcher.util;

import java.lang.reflect.Constructor;
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

    // Text handling
    private static Class<?> textClass = null;
    private static Method textOfMethod = null;
    private static Constructor<?> textConstructor = null;

    // Debug flag - can be enabled to show detailed logs
    private static boolean debugLogging = false;

    // Known Sodium package paths - prioritize 1.20.1 paths first
    private static final List<String[]> KNOWN_SODIUM_PATHS = Arrays.asList(
            // 1.20.1 common path (prioritized)
            new String[] {
                    "me.jellysquid.mods.sodium.client.gui.console.Console",
                    "me.jellysquid.mods.sodium.client.gui.console.ConsoleSink",
                    "me.jellysquid.mods.sodium.client.gui.console.message.MessageLevel"
            },
            // 1.21+ relocated path
            new String[] {
                    "net.caffeinemc.mods.sodium.client.console.Console",
                    "net.caffeinemc.mods.sodium.client.console.ConsoleSink",
                    "net.caffeinemc.mods.sodium.client.console.message.MessageLevel"
            }
    );

    private static void initialize() {
        if (initialized) return;

        if (debugLogging) {
            initializeWithDebugLogs();
            return;
        }
        initialized = true;

        // Initialize text class
        initializeTextClass();

        // Try all known Sodium paths
        for (String[] paths : KNOWN_SODIUM_PATHS) {
            if (tryInitialize(paths[0], paths[1], paths[2])) {
                return;
            }
        }

        // Try specific fallback methods for problematic versions
        if (trySpecificVersionFallbacks()) {
            return;
        }
    }

    private static boolean trySpecificVersionFallbacks() {
        try {
            // Try the specific 1.20.1 approach that's known to work
            Class<?> consoleClass = Class.forName("me.jellysquid.mods.sodium.client.gui.console.Console");
            Method instanceMethod = consoleClass.getMethod("instance");
            consoleSink = instanceMethod.invoke(null);

            messageLevelClass = Class.forName("me.jellysquid.mods.sodium.client.gui.console.message.MessageLevel");
            infoLevel = messageLevelClass.getField("INFO").get(null);
            warnLevel = messageLevelClass.getField("WARN").get(null);
            severeLevel = messageLevelClass.getField("SEVERE").get(null);

            // For 1.20.1 Fabric, check if we need to initialize the text class
            if (textClass == null) {
                try {
                    // Try obfuscated Fabric class
                    textClass = Class.forName("net.minecraft.class_2561");

                    // Try to find a suitable method
                    for (Method m : textClass.getMethods()) {
                        if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) &&
                                m.getReturnType().equals(textClass) &&
                                m.getParameterCount() == 1 &&
                                m.getParameterTypes()[0].equals(String.class)) {
                            textOfMethod = m;
                            break;
                        }
                    }
                } catch (Exception e) {
                    // Silently fail
                }
            }

            Class<?> consoleSinkClass = consoleSink.getClass();

            try {
                // Try to find the exact method without using our helper - direct approach for 1.20.1
                for (Method method : consoleSinkClass.getMethods()) {
                    if (method.getName().equals("logMessage") && method.getParameterCount() == 3) {
                        Class<?>[] paramTypes = method.getParameterTypes();
                        if (messageLevelClass.isAssignableFrom(paramTypes[0]) &&
                                (paramTypes[2].equals(int.class) || paramTypes[2].equals(double.class))) {

                            logMessageMethod = method;
                            useDoubleForFadeTimer = paramTypes[2].equals(double.class);
                            newSignature = false;
                            sodiumAvailable = true;
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                // Silently fail
            }
        } catch (Exception e) {
            // Silently fail
        }
        return false;
    }

    private static void initializeTextClass() {
        try {
            // Try Fabric path first
            textClass = Class.forName("net.minecraft.text.Text");
            textOfMethod = textClass.getMethod("of", String.class);
            textConstructor = null;
        } catch (Exception e) {
            try {
                // Try Forge/NeoForge path
                textClass = Class.forName("net.minecraft.network.chat.Component");
                textOfMethod = textClass.getMethod("literal", String.class);
                textConstructor = null;
            } catch (Exception e2) {
                try {
                    // Try obfuscated Fabric class (Fabric 1.20.1)
                    textClass = Class.forName("net.minecraft.class_2561");
                    try {
                        textOfMethod = textClass.getMethod("of", String.class);
                    } catch (NoSuchMethodException nsme) {
                        // Try alternate method name - some versions use 'literal' instead of 'of'
                        try {
                            textOfMethod = textClass.getMethod("method_10851", String.class);
                        } catch (NoSuchMethodException nsme2) {
                            try {
                                textOfMethod = textClass.getMethod("literal", String.class);
                            } catch (NoSuchMethodException nsme3) {
                                // Last resort: just find any static method that takes a String and returns Text
                                for (Method m : textClass.getMethods()) {
                                    if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) &&
                                            m.getReturnType().equals(textClass) &&
                                            m.getParameterCount() == 1 &&
                                            m.getParameterTypes()[0].equals(String.class)) {
                                        textOfMethod = m;
                                        break;
                                    }
                                }
                                if (textOfMethod == null) {
                                    throw new NoSuchMethodException("Could not find any suitable text method");
                                }
                            }
                        }
                    }
                } catch (Exception e3) {
                    // Try even older Forge path
                    try {
                        textClass = Class.forName("net.minecraft.util.text.StringTextComponent");
                        textOfMethod = null;
                        textConstructor = textClass.getConstructor(String.class);
                    } catch (Exception e4) {
                        // Could not find Text class
                        textClass = null;
                        textOfMethod = null;
                        textConstructor = null;
                    }
                }
            }
        }
    }

    private static Object getTextComponentViaReflection(String message) {
        try {
            // Try to get the text component via different methods
            // First, try Fabric's StaticTextContent
            try {
                Class<?> staticTextContentClass = Class.forName("net.minecraft.class_2585");
                Constructor<?> constructor = staticTextContentClass.getConstructor(String.class);
                return constructor.newInstance(message);
            } catch (Exception e) {
                // Attempt to find Text.of/literal/method_10851 method via the obfuscated class
                try {
                    Class<?> obfTextClass = Class.forName("net.minecraft.class_2561");
                    for (Method method : obfTextClass.getMethods()) {
                        if (java.lang.reflect.Modifier.isStatic(method.getModifiers()) &&
                                method.getReturnType().equals(obfTextClass) &&
                                method.getParameterCount() == 1 &&
                                method.getParameterTypes()[0].equals(String.class)) {
                            return method.invoke(null, message);
                        }
                    }
                } catch (Exception e2) {
                    // Last resort
                    return message;
                }
            }
        } catch (Exception e) {
            if (debugLogging) {
                System.out.println("[SodiumConsole] Error creating text component: " + e.getMessage());
            }
        }
        return message;
    }

    private static Object createTextComponent(String message) {
        try {
            if (textClass == null) {
                // Try the special fix for 1.20.1 Fabric
                return getTextComponentViaReflection(message);
            }

            if (textOfMethod != null) {
                // Use static method (of/literal)
                return textOfMethod.invoke(null, message);
            } else if (textConstructor != null) {
                // Use constructor for older versions
                return textConstructor.newInstance(message);
            } else {
                // Try the special fix for 1.20.1 Fabric
                return getTextComponentViaReflection(message);
            }
        } catch (Exception e) {
            if (debugLogging) {
                System.out.println("[SodiumConsole] Error creating text component: " + e.getMessage());
            }
            // Last attempt - try the special fix
            return getTextComponentViaReflection(message);
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

            // Try method signatures in order of most likely to work
            if (textClass != null && tryMethodSignature(consoleSinkClass, "logMessage", textClass, int.class)) {
                useDoubleForFadeTimer = false;
                newSignature = false;
                return true;
            } else if (textClass != null && tryMethodSignature(consoleSinkClass, "logMessage", textClass, double.class)) {
                useDoubleForFadeTimer = true;
                newSignature = false;
                return true;
            } else if (tryMethodSignature(consoleSinkClass, "logMessage", String.class, boolean.class, double.class)) {
                useDoubleForFadeTimer = true;
                newSignature = true;
                return true;
            } else if (textClass != null && tryMethodSignature(consoleSinkClass, "add", textClass, int.class)) {
                useDoubleForFadeTimer = false;
                newSignature = false;
                return true;
            } else {
                return false;
            }
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
            sodiumAvailable = true;
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
                logMessageMethod.invoke(consoleSink, messageLevel, createTextComponent(message), fadeTimerDouble);
            } else {
                // Original format: MessageLevel, Text, int
                logMessageMethod.invoke(consoleSink, messageLevel, createTextComponent(message), messageFadeTimer);
            }
        } catch (Exception e) {
            // If something goes wrong, disable Sodium console
            sodiumAvailable = false;
            if (debugLogging) {
                System.out.println("[SodiumConsole] Error logging message: " + e.getMessage());
            }
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

    // ================================================
    // DEBUG METHODS - Only used for troubleshooting
    // ================================================

    /**
     * Enable debug logging for SodiumConsole
     */
    private static void initializeWithDebugLogs() {
        if (initialized) return;
        initialized = true;

        System.out.println("[SodiumConsole] Initializing...");

        // Initialize text class first
        initializeTextClassWithDebugLogs();
        if (textClass != null) {
            System.out.println("[SodiumConsole] Found text class: " + textClass.getName());
        } else {
            System.out.println("[SodiumConsole] Could not find text class");
        }

        // Try all known Sodium paths
        for (String[] paths : KNOWN_SODIUM_PATHS) {
            System.out.println("[SodiumConsole] Trying path: " + paths[0]);
            if (tryInitializeWithDebugLogs(paths[0], paths[1], paths[2])) {
                System.out.println("[SodiumConsole] Successfully initialized with path: " + paths[0]);
                return;
            }
        }

        System.out.println("[SodiumConsole] All regular initialization attempts failed");

        // Try specific fallback methods for problematic versions
        if (trySpecificVersionFallbacksWithDebugLogs()) {
            System.out.println("[SodiumConsole] Fallback initialization succeeded");
            return;
        }

        System.out.println("[SodiumConsole] Failed to initialize Sodium console");
    }

    private static boolean trySpecificVersionFallbacksWithDebugLogs() {
        try {
            // Try the specific 1.20.1 approach that's known to work
            Class<?> consoleClass = Class.forName("me.jellysquid.mods.sodium.client.gui.console.Console");
            System.out.println("[SodiumConsole] Found Console class in fallback");

            Method instanceMethod = consoleClass.getMethod("instance");
            consoleSink = instanceMethod.invoke(null);
            System.out.println("[SodiumConsole] Got Console instance in fallback");

            messageLevelClass = Class.forName("me.jellysquid.mods.sodium.client.gui.console.message.MessageLevel");
            infoLevel = messageLevelClass.getField("INFO").get(null);
            warnLevel = messageLevelClass.getField("WARN").get(null);
            severeLevel = messageLevelClass.getField("SEVERE").get(null);
            System.out.println("[SodiumConsole] Got MessageLevel constants in fallback");

            // For 1.20.1 Fabric, check if we need to initialize the text class
            if (textClass == null) {
                try {
                    // Try obfuscated Fabric class
                    textClass = Class.forName("net.minecraft.class_2561");
                    System.out.println("[SodiumConsole] Found obfuscated Text class during fallback");
                    // Try to find a suitable method
                    for (Method m : textClass.getMethods()) {
                        if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) &&
                                m.getReturnType().equals(textClass) &&
                                m.getParameterCount() == 1 &&
                                m.getParameterTypes()[0].equals(String.class)) {
                            textOfMethod = m;
                            System.out.println("[SodiumConsole] Found potential text method in fallback: " + m.getName());
                            break;
                        }
                    }
                } catch (Exception e) {
                    System.out.println("[SodiumConsole] Could not find obfuscated text class during fallback");
                }
            }

            Class<?> consoleSinkClass = consoleSink.getClass();
            System.out.println("[SodiumConsole] ConsoleSink class: " + consoleSinkClass.getName());

            try {
                // Try to find the exact method without using our helper - direct approach for 1.20.1
                for (Method method : consoleSinkClass.getMethods()) {
                    if (method.getName().equals("logMessage") && method.getParameterCount() == 3) {
                        Class<?>[] paramTypes = method.getParameterTypes();
                        if (messageLevelClass.isAssignableFrom(paramTypes[0]) &&
                                (paramTypes[2].equals(int.class) || paramTypes[2].equals(double.class))) {

                            logMessageMethod = method;
                            useDoubleForFadeTimer = paramTypes[2].equals(double.class);
                            newSignature = false;
                            sodiumAvailable = true;
                            System.out.println("[SodiumConsole] Found method with direct reflection: " + method);
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("[SodiumConsole] Error searching methods: " + e.getMessage());
            }
        } catch (Exception e) {
            System.out.println("[SodiumConsole] Fallback attempt failed: " + e.getMessage());
        }
        return false;
    }

    private static void initializeTextClassWithDebugLogs() {
        try {
            // Try Fabric path first
            textClass = Class.forName("net.minecraft.text.Text");
            textOfMethod = textClass.getMethod("of", String.class);
            textConstructor = null;
            System.out.println("[SodiumConsole] Found Fabric Text class");
        } catch (Exception e) {
            System.out.println("[SodiumConsole] Failed to find Fabric Text class: " + e.getMessage());
            try {
                // Try Forge/NeoForge path
                textClass = Class.forName("net.minecraft.network.chat.Component");
                textOfMethod = textClass.getMethod("literal", String.class);
                textConstructor = null;
                System.out.println("[SodiumConsole] Found Forge Component class");
            } catch (Exception e2) {
                System.out.println("[SodiumConsole] Failed to find Forge Component class: " + e2.getMessage());
                try {
                    // Try obfuscated Fabric class (Fabric 1.20.1)
                    textClass = Class.forName("net.minecraft.class_2561");
                    try {
                        textOfMethod = textClass.getMethod("of", String.class);
                        System.out.println("[SodiumConsole] Found obfuscated Text.of method");
                    } catch (NoSuchMethodException nsme) {
                        System.out.println("[SodiumConsole] No 'of' method, trying alternatives");
                        // Try alternate method name - some versions use 'literal' instead of 'of'
                        try {
                            textOfMethod = textClass.getMethod("method_10851", String.class);
                            System.out.println("[SodiumConsole] Found obfuscated Text.method_10851 method");
                        } catch (NoSuchMethodException nsme2) {
                            System.out.println("[SodiumConsole] No 'method_10851' method, trying more alternatives");
                            try {
                                textOfMethod = textClass.getMethod("literal", String.class);
                                System.out.println("[SodiumConsole] Found obfuscated Text.literal method");
                            } catch (NoSuchMethodException nsme3) {
                                System.out.println("[SodiumConsole] No 'literal' method, searching all methods");
                                // Last resort: just find any static method that takes a String and returns Text
                                System.out.println("[SodiumConsole] Available methods in Text class:");
                                for (Method m : textClass.getMethods()) {
                                    System.out.println(" - " + m.getName() + ": " + m);
                                    if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) &&
                                            m.getReturnType().equals(textClass) &&
                                            m.getParameterCount() == 1 &&
                                            m.getParameterTypes()[0].equals(String.class)) {
                                        textOfMethod = m;
                                        System.out.println("[SodiumConsole] Found potential text method: " + m.getName());
                                        break;
                                    }
                                }
                                if (textOfMethod == null) {
                                    throw new NoSuchMethodException("Could not find any suitable text method");
                                }
                            }
                        }
                    }
                    System.out.println("[SodiumConsole] Found obfuscated Text class");
                } catch (Exception e3) {
                    System.out.println("[SodiumConsole] Failed to find obfuscated Text class: " + e3.getMessage());
                    // Try even older Forge path
                    try {
                        textClass = Class.forName("net.minecraft.util.text.StringTextComponent");
                        textOfMethod = null;
                        textConstructor = textClass.getConstructor(String.class);
                        System.out.println("[SodiumConsole] Found legacy StringTextComponent class");
                    } catch (Exception e4) {
                        System.out.println("[SodiumConsole] Failed to find legacy StringTextComponent class: " + e4.getMessage());
                        // Could not find Text class
                        textClass = null;
                        textOfMethod = null;
                        textConstructor = null;
                        System.out.println("[SodiumConsole] Could not find any text class");
                    }
                }
            }
        }
    }

    private static boolean tryInitializeWithDebugLogs(String consolePath, String consoleSinkPath, String messageLevelPath) {
        try {
            // Get Console instance
            Class<?> consoleClass = Class.forName(consolePath);
            System.out.println("[SodiumConsole] Found Console class: " + consolePath);

            Method instanceMethod = consoleClass.getMethod("instance");
            System.out.println("[SodiumConsole] Found instance method");

            consoleSink = instanceMethod.invoke(null);
            System.out.println("[SodiumConsole] Got Console instance");

            // Get MessageLevel class and constants
            messageLevelClass = Class.forName(messageLevelPath);
            System.out.println("[SodiumConsole] Found MessageLevel class: " + messageLevelPath);

            infoLevel = messageLevelClass.getField("INFO").get(null);
            warnLevel = messageLevelClass.getField("WARN").get(null);
            severeLevel = messageLevelClass.getField("SEVERE").get(null);
            System.out.println("[SodiumConsole] Got message level constants");

            // Find suitable method signature
            Class<?> consoleSinkClass = Class.forName(consoleSinkPath);
            System.out.println("[SodiumConsole] Found ConsoleSink class: " + consoleSinkPath);

            // Dump all available methods to help debug
            System.out.println("[SodiumConsole] Available methods in " + consoleSinkPath + ":");
            for (Method method : consoleSinkClass.getMethods()) {
                if (method.getName().equals("logMessage") || method.getName().equals("add")) {
                    System.out.println("  " + method.getName() + ": " + method);
                }
            }

            // Try method signatures in order of most likely to work
            if (textClass != null && tryMethodSignatureWithDebugLogs(consoleSinkClass, "logMessage", textClass, int.class)) {
                useDoubleForFadeTimer = false;
                newSignature = false;
                System.out.println("[SodiumConsole] Found int-based logMessage method");
                return true;
            } else if (textClass != null && tryMethodSignatureWithDebugLogs(consoleSinkClass, "logMessage", textClass, double.class)) {
                useDoubleForFadeTimer = true;
                newSignature = false;
                System.out.println("[SodiumConsole] Found double-based logMessage method");
                return true;
            } else if (tryMethodSignatureWithDebugLogs(consoleSinkClass, "logMessage", String.class, boolean.class, double.class)) {
                useDoubleForFadeTimer = true;
                newSignature = true;
                System.out.println("[SodiumConsole] Found newest logMessage method format");
                return true;
            } else if (textClass != null && tryMethodSignatureWithDebugLogs(consoleSinkClass, "add", textClass, int.class)) {
                useDoubleForFadeTimer = false;
                newSignature = false;
                System.out.println("[SodiumConsole] Found add method");
                return true;
            } else {
                System.out.println("[SodiumConsole] No compatible methods found");
                return false;
            }
        } catch (Exception e) {
            System.out.println("[SodiumConsole] Error in tryInitialize: " + e.getMessage());
            return false;
        }
    }

    private static boolean tryMethodSignatureWithDebugLogs(Class<?> consoleSinkClass, String methodName, Class<?>... paramTypes) {
        try {
            // First parameter is always MessageLevel
            Class<?>[] fullParamTypes = new Class<?>[paramTypes.length + 1];
            fullParamTypes[0] = messageLevelClass;
            System.arraycopy(paramTypes, 0, fullParamTypes, 1, paramTypes.length);

            logMessageMethod = consoleSinkClass.getMethod(methodName, fullParamTypes);
            sodiumAvailable = true;
            System.out.println("[SodiumConsole] Found method: " + methodName + " with signature: " + Arrays.toString(paramTypes));
            return true;
        } catch (NoSuchMethodException e) {
            System.out.println("[SodiumConsole] Method not found: " + methodName + " with signature: " + Arrays.toString(paramTypes));
            return false;
        }
    }
}