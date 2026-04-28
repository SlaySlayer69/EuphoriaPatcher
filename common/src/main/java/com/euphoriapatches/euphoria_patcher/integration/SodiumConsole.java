package com.euphoriapatches.euphoria_patcher.integration;

import com.euphoriapatches.euphoria_patcher.config.ConfigHandler;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;

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
    private static final boolean debugLogging = ConfigHandler.doDebugLogging;

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

    private static void log(String message) {
        EuphoriaLogger.debugLog("[SodiumConsole] " + message);
    }

    private static void initialize() {
        if (initialized) return;
        initialized = true;

        log("Initializing...");

        // Initialize text class
        initializeTextClass();

        // Try all known Sodium paths
        for (String[] paths : KNOWN_SODIUM_PATHS) {
            log("Trying path: " + paths[0]);
            if (tryInitialize(paths[0], paths[1], paths[2])) {
                log("Successfully initialized with path: " + paths[0]);
                return;
            }
        }

        log("All regular initialization attempts failed");

        // Try specific fallback methods for problematic versions
        if (trySpecificVersionFallbacks()) {
            log("Fallback initialization succeeded");
            return;
        }

        log("Failed to initialize Sodium console");
    }

    private static boolean trySpecificVersionFallbacks() {
        try {
            // Try the specific 1.20.1 approach that's known to work
            Class<?> consoleClass = Class.forName("me.jellysquid.mods.sodium.client.gui.console.Console");
            log("Found Console class in fallback");

            Method instanceMethod = consoleClass.getMethod("instance");
            consoleSink = instanceMethod.invoke(null);
            log("Got Console instance in fallback");

            messageLevelClass = Class.forName("me.jellysquid.mods.sodium.client.gui.console.message.MessageLevel");
            infoLevel = messageLevelClass.getField("INFO").get(null);
            warnLevel = messageLevelClass.getField("WARN").get(null);
            severeLevel = messageLevelClass.getField("SEVERE").get(null);
            log("Got MessageLevel constants in fallback");

            // For 1.20.1 Fabric, check if we need to initialize the text class
            if (textClass == null) {
                try {
                    // Try obfuscated Fabric class
                    textClass = Class.forName("net.minecraft.class_2561");
                    log("Found obfuscated Text class during fallback");

                    // Try to find a suitable method
                    for (Method m : textClass.getMethods()) {
                        if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) &&
                                m.getReturnType().equals(textClass) &&
                                m.getParameterCount() == 1 &&
                                m.getParameterTypes()[0].equals(String.class)) {
                            textOfMethod = m;
                            log("Found potential text method in fallback: " + m.getName());
                            break;
                        }
                    }
                } catch (Exception e) {
                    log("Could not find obfuscated text class during fallback");
                }
            }

            Class<?> consoleSinkClass = consoleSink.getClass();
            log("ConsoleSink class: " + consoleSinkClass.getName());

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
                        log("Found method with direct reflection: " + method);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            log("Fallback attempt failed: " + e.getMessage());
        }
        return false;
    }

    private static void initializeTextClass() {
        log("Initializing text class");

        // Try Fabric path first
        if (tryLoadTextClass("net.minecraft.text.Text", "of", "Fabric Text")) return;

        // Try Forge/NeoForge path
        if (tryLoadTextClass("net.minecraft.network.chat.Component", "literal", "Forge Component")) return;

        // Try obfuscated Fabric class (Fabric 1.20.1)
        if (tryLoadObfuscatedTextClass()) return;

        // Try even older Forge path
        try {
            textClass = Class.forName("net.minecraft.util.text.StringTextComponent");
            textOfMethod = null;
            textConstructor = textClass.getConstructor(String.class);
            log("Found legacy StringTextComponent class");
        } catch (Exception e) {
            log("Failed to find legacy StringTextComponent class: " + e.getMessage());
            // Could not find Text class
            textClass = null;
            textOfMethod = null;
            textConstructor = null;
            log("Could not find any text class");
        }
    }

    private static boolean tryLoadTextClass(String className, String methodName, String description) {
        try {
            textClass = Class.forName(className);
            textOfMethod = textClass.getMethod(methodName, String.class);
            textConstructor = null;
            log("Found " + description + " class");
            return true;
        } catch (Exception e) {
            log("Failed to find " + description + " class: " + e.getMessage());
            return false;
        }
    }

    private static boolean tryLoadObfuscatedTextClass() {
        try {
            textClass = Class.forName("net.minecraft.class_2561");
            log("Found obfuscated Text class");

            // Try different method names in order
            String[] methodNames = {"of", "method_10851", "literal"};
            for (String methodName : methodNames) {
                try {
                    textOfMethod = textClass.getMethod(methodName, String.class);
                    log("Found obfuscated Text." + methodName + " method");
                    return true;
                } catch (NoSuchMethodException e) {
                    log("No '" + methodName + "' method");
                }
            }

            // Last resort: just find any static method that takes a String and returns Text
            log("Searching all methods in Text class");
            if (debugLogging) {
                for (Method m : textClass.getMethods()) {
                    log(" - " + m.getName() + ": " + m);
                }
            }

            for (Method m : textClass.getMethods()) {
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) &&
                        m.getReturnType().equals(textClass) &&
                        m.getParameterCount() == 1 &&
                        m.getParameterTypes()[0].equals(String.class)) {
                    textOfMethod = m;
                    log("Found potential text method: " + m.getName());
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            log("Failed to find obfuscated Text class: " + e.getMessage());
            return false;
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
            log("Error creating text component: " + e.getMessage());
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
            log("Error creating text component: " + e.getMessage());
            // Last attempt - try the special fix
            return getTextComponentViaReflection(message);
        }
    }

    private static boolean tryInitialize(String consolePath, String consoleSinkPath, String messageLevelPath) {
        try {
            // Get Console instance
            Class<?> consoleClass = Class.forName(consolePath);
            log("Found Console class: " + consolePath);

            Method instanceMethod = consoleClass.getMethod("instance");
            log("Found instance method");

            consoleSink = instanceMethod.invoke(null);
            log("Got Console instance");

            // Get MessageLevel class and constants
            messageLevelClass = Class.forName(messageLevelPath);
            log("Found MessageLevel class: " + messageLevelPath);

            infoLevel = messageLevelClass.getField("INFO").get(null);
            warnLevel = messageLevelClass.getField("WARN").get(null);
            severeLevel = messageLevelClass.getField("SEVERE").get(null);
            log("Got message level constants");

            // Find suitable method signature
            Class<?> consoleSinkClass = Class.forName(consoleSinkPath);
            log("Found ConsoleSink class: " + consoleSinkPath);

            // Dump all available methods to help debug
            if (debugLogging) {
                log("Available methods in " + consoleSinkPath + ":");
                for (Method method : consoleSinkClass.getMethods()) {
                    if (method.getName().equals("logMessage") || method.getName().equals("add")) {
                        log("  " + method.getName() + ": " + method);
                    }
                }
            }

            // Try method signatures in order of most likely to work
            if (textClass != null && tryMethodSignature(consoleSinkClass, "logMessage", textClass, int.class)) {
                useDoubleForFadeTimer = false;
                newSignature = false;
                log("Found int-based logMessage method");
                return true;
            } else if (textClass != null && tryMethodSignature(consoleSinkClass, "logMessage", textClass, double.class)) {
                useDoubleForFadeTimer = true;
                newSignature = false;
                log("Found double-based logMessage method");
                return true;
            } else if (tryMethodSignature(consoleSinkClass, "logMessage", String.class, boolean.class, double.class)) {
                useDoubleForFadeTimer = true;
                newSignature = true;
                log("Found newest logMessage method format");
                return true;
            } else if (textClass != null && tryMethodSignature(consoleSinkClass, "add", textClass, int.class)) {
                useDoubleForFadeTimer = false;
                newSignature = false;
                log("Found add method");
                return true;
            } else {
                log("No compatible methods found");
                return false;
            }
        } catch (Exception e) {
            log("Error in tryInitialize: " + e.getMessage());
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
            log("Found method: " + methodName + " with signature: " + Arrays.toString(paramTypes));
            return true;
        } catch (NoSuchMethodException e) {
            log("Method not found: " + methodName + " with signature: " + Arrays.toString(paramTypes));
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
                boolean isPersistent = messageFadeTimer <= 0;
                logMessageMethod.invoke(consoleSink, messageLevel, message, isPersistent, (double) messageFadeTimer);
            } else if (useDoubleForFadeTimer) {
                // Older double format: MessageLevel, Text, double
                logMessageMethod.invoke(consoleSink, messageLevel, createTextComponent(message), (double) messageFadeTimer);
            } else {
                // Original format: MessageLevel, Text, int
                logMessageMethod.invoke(consoleSink, messageLevel, createTextComponent(message), messageFadeTimer);
            }
        } catch (Exception e) {
            // If something goes wrong, disable Sodium console
            sodiumAvailable = false;
            log("Error logging message: " + e.getMessage());
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
}
