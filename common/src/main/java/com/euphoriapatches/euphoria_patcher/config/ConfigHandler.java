package com.euphoriapatches.euphoria_patcher.config;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;

import java.util.Locale;

public class ConfigHandler {
    // Config Options
    public static boolean doPopUpLogging = true;
    public static String updateMode = UpdateMode.IMPORTANT; // important, all, none
    public static boolean doRenameOldShaderFiles = true;
    public static boolean doDeleteOldShaderFiles = false;
    public static boolean doDisplayShaderInGameMessage = true;
    public static boolean doDebugLogging = false;
    public static String alternativeShaderNames = "";

    public static void configStuff() {
        // Initialize config system (handles migration on first call)
        Config.initialize();

        // Set category order - categories will appear in this order in the TOML file
        // Any categories used but not listed here will appear at the end
        Config.setConfigCategoryOrder("display", "updates", "maintenance", "debug", "advanced");

        /*
        How to use: Cast to desired data type, then call readWriteConfig with category
        Parameters: category, key, defaultValue, description (supports multiline with "\n")
        Config will be organized into [category] sections in the TOML file

        Optional Parameters: allowedValues (String array) for string options with limited choices
        TypeMigration for changing option types while preserving user settings
        How Does TypeMigration work?
        If an option's type is changed (e.g., boolean to string), the TypeMigration parameter
        tells the config system how to convert existing user values to the new type.
        Example: Config.TypeMigration.map(false, "none") means: if the old boolean value was false,
        set the new string value to "none". Can be chained for multiple mappings.
        */

        // Type is auto-detected from the default value - no casting needed!
        doPopUpLogging = Config.readWriteConfig("display", "doPopUpLogging", true,
                "Option for the sodium message popup logging." +
                        "\nDefault = true");

        updateMode = Config.readWriteConfig("updates", "doUpdateChecking", UpdateMode.IMPORTANT,
                "Option which determines what updates the update checker considers." +
                        "\nUpdate checker mode: 'important' (critical or big updates only), 'all' (all updates), 'none' (disabled)." +
                        "\nUses the Modrinth API to fetch update information." +
                        "\nDefault = important",
                new String[]{UpdateMode.IMPORTANT, UpdateMode.ALL, UpdateMode.NONE},
                Config.TypeMigration.map(false, "none"),
                Config.TypeMigration.map(true, "important")).toLowerCase(Locale.ROOT);

        doRenameOldShaderFiles = Config.readWriteConfig("maintenance", "doRenameOldShaderFiles", true,
                "Option that automatically renames outdated Euphoria Patches folders and config files to a new name." +
                        "\nThis makes it easier for users to identify which ones are outdated." +
                        "\nDefault = true");
        doDeleteOldShaderFiles = Config.readWriteConfig("maintenance", "doDeleteOldShaderFiles", false,
                "Option that automatically deletes outdated Euphoria Patches folders and config files." +
                        "\nDefault = false");
        doDisplayShaderInGameMessage = Config.readWriteConfig("display", "doDisplayShaderInGameMessage", true,
                "Option that enables or disables the in-game shader messages, for example an update message made by the shader itself. Only works on Iris or Oculus" +
                        "\nDefault = true");

        alternativeShaderNames = Config.readWriteConfig("advanced", "alternativeShaderNames", "",
                "Here one can set alternative Shader Names which will also be generated alongside the normal one." +
                        "\nThis is useful if you want multiple different settings you can quickly switch between" +
                        "\nDefault = Empty String, which means no alternative names will be generated." +
                        "\nIn case of multiple names, separate them with a comma" +
                        "\nYou can also use {baseVersion} or {patchVersion} in names to insert the base shader or Euphoria Patches version." +
                        "\nExample: Euphoria Saturated, Comp_{baseVersion} + EP_{patchVersion} Dark Settings, EP High Performance, etc...");

        boolean configDebugLogging = Config.readWriteConfig("debug", "doDebugLogging", false,
                "Option that enables or disables debug logging. Alternatively, one can also set the JVM argument -DebugEP=true/false which takes priority over this setting." +
                        "\nDefault = false");
        handleJVMArgumentDebugLogging(configDebugLogging);

        // Regenerate config to apply proper ordering and ensure header is present
        Config.regenerateConfig();

        Config.startConfigWatcher();
    }
    @SuppressWarnings("SpellCheckingInspection")
    private static void handleJVMArgumentDebugLogging(boolean configDebugLogging) {
        // Check for JVM argument -DEPDebug=true/false which takes priority over config
        String jvmDebugArg = System.getProperty("ebugEP");
        if (jvmDebugArg != null) {
            String argLower = jvmDebugArg.trim().toLowerCase(Locale.ROOT);
            if ("true".equals(argLower) || "false".equals(argLower)) {
                doDebugLogging = Boolean.parseBoolean(argLower);
                debugLog("Debug logging set via JVM argument -DebugEP=" + jvmDebugArg + " (overriding config value)");
            } else {
                EuphoriaPatcher.log(2, 0, "Invalid value for -DebugEP: " + jvmDebugArg + ". Only 'true' or 'false' are accepted. Using config value.");
                doDebugLogging = configDebugLogging;
            }
        } else {
            doDebugLogging = configDebugLogging;
        }
    }

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ConfigHandler] " + message);
    }

    public static final class UpdateMode {
        public static final String IMPORTANT = "important";
        public static final String ALL = "all";
        public static final String NONE = "none";
        private UpdateMode() {}
    }

}
