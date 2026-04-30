package com.euphoriapatches.euphoria_patcher;

import com.euphoriapatches.euphoria_patcher.config.Config;
import com.euphoriapatches.euphoria_patcher.config.ConfigHandler;
import com.euphoriapatches.euphoria_patcher.features.*;
import com.euphoriapatches.euphoria_patcher.features.properties.PropertiesWatcher;
import com.euphoriapatches.euphoria_patcher.integration.ShaderLoader;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.monitoring.PotatoFileMonitor;
import com.euphoriapatches.euphoria_patcher.monitoring.ShaderpacksWatcher;
import com.euphoriapatches.euphoria_patcher.services.*;
import com.euphoriapatches.euphoria_patcher.services.ShaderDetector.ShaderInfo;
import com.euphoriapatches.euphoria_patcher.util.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class EuphoriaPatcher {
    public static final String BRAND_NAME = "Complementary";
    public static final String PATCH_NAME = "EuphoriaPatches";
    public static final String VERSION = PatchInfo.VERSION;
    public static final String PATCH_VERSION = PatchInfo.PATCH_VERSION;

    public static final String COMP_DOWNLOAD_URL = "https://www.complementary.dev/";
    public static final String EP_DOWNLOAD_URL = "https://euphoriapatches.com/download";
    public static final String COMMON_LOCATION = "shaders/lib/common.glsl";
    public static final String LANG_LOCATION = "shaders/lang";
    public static final String SHADERS_PROPERTIES_LOCATION = "shaders/shaders.properties";
    public static final String SHADER_MYFILE_LOCATION = "shaders/lib/misc/myFile.glsl";

    // Get necessary paths
    public static Path shaderpacks = ModLoaderSpecifics.shaderpacks();
    public static Path configDirectory = ModLoaderSpecifics.configDirectory();

    // Global Variables and Objects
    private static boolean ALREADY_LAUNCHED = false;
    private static EuphoriaPatcher instance;
    private ShaderpacksWatcher shaderpacksWatcher;
    private static EuphoriaLogger loggerInstance;

    // Service classes
    private ShaderDetector shaderDetector;
    private ShaderPatchingService patchingService;
    private ShaderVersionComparator versionComparator;
    private ShaderNamingService namingService;

    public EuphoriaPatcher() {
        if (ALREADY_LAUNCHED) {
            return;
        }
        ALREADY_LAUNCHED = true;
        instance = this;
        System.out.println("\nEuphoria Patcher:");

        // Initialize the logger
        loggerInstance = new EuphoriaLogger();
        loggerInstance.checkErrorLogFileAndAddSeparator();
        JarLauncher.touch(); // Call to prevent minimizers from removing the GUI code.

        ConfigHandler.configStuff();

        ShaderData.validateShaderDataHash();
        if (ShaderData.isIncorrectVersion()) ShaderData.resetShaderStyles();

        if (ModFolderVersionChecker.existsNewerModInFolder()) return;

        if (ConfigHandler.doPopUpLogging) loggerInstance.checkAndSetupSodiumLogging();

        UpdateChecker.checkForUpdates();

        ShaderLoader.getShaderLoader();

        log(0, JsonUtilReader.getRandomMessage("startupMessages"));

        UpdateShaderConfig.markAllEPSettingsFiles();

        // Initialize service classes
        initializeServices();

        // Detect installed Complementary Shaders versions
        ShaderInfo shaderInfo = shaderDetector.detectInstalledShaders(namingService);

        if (!shaderInfo.isAlreadyInstalled) {
            if (shaderInfo.baseFile == null) {
                UserInstallErrorMessages.handleShaderNotFound(versionComparator);
                return;
            }
        } else {
            // Load shader style data from persistent storage if styles weren't detected
            if (!shaderInfo.styleReimagined && !shaderInfo.styleUnbound && ShaderData.dataFileExists()) {
                ShaderData.PersistentShaderData data = ShaderData.load();
                if (data.styleReimagined == null || data.styleUnbound == null) {
                    debugLog("Could not load data.json, cannot check for missing styles");
                } else {
                    shaderInfo.styleReimagined = data.styleReimagined;
                    shaderInfo.styleUnbound = data.styleUnbound;
                }
            }
            thankYouMessage(shaderInfo.baseFile, shaderInfo.styleUnbound, shaderInfo.styleReimagined, shaderInfo.installedDir, true);
            return;
        }

        // Create temporary directory
        Path temp = ArchiveOperations.createTempDirectory();
        if (temp == null || shaderInfo.baseFile == null) return;

        completeShaderPatching(shaderInfo, temp);
    }

    /**
     * Initialize all service classes
     */
    private void initializeServices() {
        // Initialize version comparator
        versionComparator = new ShaderVersionComparator(BRAND_NAME, PATCH_NAME, VERSION, shaderpacks);

        // Initialize detector (without naming service initially)
        shaderDetector = new ShaderDetector(BRAND_NAME, PATCH_NAME, VERSION, PATCH_VERSION,
                                           COMMON_LOCATION, SHADER_MYFILE_LOCATION, shaderpacks);

        // Initialize naming service (needs detector for some operations)
        namingService = new ShaderNamingService(BRAND_NAME, PATCH_NAME, VERSION, PATCH_VERSION,
                                               COMMON_LOCATION, SHADER_MYFILE_LOCATION, shaderpacks, shaderDetector);

        // Initialize patching service
        patchingService = new ShaderPatchingService(PATCH_NAME, PATCH_VERSION, COMMON_LOCATION,
                                                   shaderpacks, namingService);
    }

    public static EuphoriaPatcher getInstance() {
        return instance;
    }

    /**
     * Get the version comparator service
     */
    public ShaderVersionComparator getVersionComparator() {
        return versionComparator;
    }

    /**
     * Get the shader detector service
     */
    public ShaderDetector getShaderDetector() {
        return shaderDetector;
    }

    /**
     * Get the naming service
     */
    public ShaderNamingService getNamingService() {
        return namingService;
    }

    private boolean completeShaderPatching(ShaderInfo shaderInfo, Path temp) {
        // Process and patch shaders
        if (!patchingService.processAndPatchShaders(shaderInfo, temp)) return false;

        // Update .txt shader config file
        UpdateShaderConfig.updateShaderTxtConfigFile(shaderInfo.styleUnbound, shaderInfo.styleReimagined);

        // Update shader loader (iris) config
        UpdateShaderLoaderConfig.updateShaderLoaderConfig(shaderInfo.styleUnbound, shaderInfo.styleReimagined);

        if (ConfigHandler.doDeleteOldShaderFiles) ModifyOutdatedPatches.delete();
        if (ConfigHandler.doRenameOldShaderFiles) ModifyOutdatedPatches.rename();

        ShaderData.saveShaderStyles(shaderInfo.styleReimagined, shaderInfo.styleUnbound);

        thankYouMessage(shaderInfo.baseFile, shaderInfo.styleUnbound, shaderInfo.styleReimagined, shaderInfo.installedDir, shaderInfo.isAlreadyInstalled);
        return true;
    }

    public static void log(int messageLevel, int messageFadeTimer, String message) {
        if (loggerInstance == null) {
            System.out.println("EuphoriaPatcher (early log): " + message);
            return;
        }
        loggerInstance.log(messageLevel, messageFadeTimer, message);
    }

    public static void log(int messageLevel, String message) {
        if (loggerInstance == null) {
            System.out.println("EuphoriaPatcher (early log): " + message);
            return;
        }
        loggerInstance.log(messageLevel, message);
    }

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[EuphoriaPatcher] " + message);
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                UpdateShaderConfig.shutdownFileWriter();
                Config.stopConfigWatcher();
                instance.shaderpacksWatcher.stopWatching();
                PotatoFileMonitor.stopMonitoring();
                PropertiesWatcher.stopMonitoring();
            } catch (Exception ignored) {
            }
        }));
    }

    public static boolean isSpacEagle() {
        try {
            boolean containsSpacEagle = shaderpacks.toString().toLowerCase(Locale.ROOT).contains("spaceagle");
            debugLog("Contains SpacEagle in Path: " + containsSpacEagle);
            Path euphoriaFolder = shaderpacks.resolve("Euphoria-Patches");
            boolean hasEuphoriaFolder = Files.exists(euphoriaFolder) && Files.isDirectory(euphoriaFolder);
            debugLog("Euphoria-Patches folder exists: " + hasEuphoriaFolder);
            return containsSpacEagle && hasEuphoriaFolder;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void thankYouMessage(Path baseFile, boolean styleUnbound, boolean styleReimagined, Path installedDir, boolean isAlreadyInstalled) {
        // Get the installed shader path
        Path shader = findInstalledShaderPath(baseFile, installedDir);

        if (shader != null) {
            applyShaderModifications(shader, styleUnbound, styleReimagined, isAlreadyInstalled);
            displayFinalMessage();
        } else {
            debugLog("No valid shader path found for thank you message");
            log(-1, "Thank you for using Euphoria Patches - SpacEagle17");
        }
    }

    private Path findInstalledShaderPath(Path baseFile, Path installedDir) {
        if (installedDir != null) {
            debugLog("Using already detected installed directory: " + installedDir);
            return installedDir;
        }

        if (baseFile != null) {
            return namingService.getPatchedShaderPath(baseFile);
        }

        return shaderDetector.findPatchedShaderDirectory();
    }

    private void applyShaderModifications(Path shader, boolean styleUnbound, boolean styleReimagined, boolean isAlreadyInstalled) {
        applyUpdateNotification(shader, styleUnbound, styleReimagined);
        applyCompatibilityModifications(shader, styleUnbound, styleReimagined);
        applyDeveloperModifications(shader, styleUnbound, styleReimagined);
        namingService.createAlternativeShaderNames(shader, isAlreadyInstalled, ConfigHandler.alternativeShaderNames);

        // Initialize properties watcher if auto-merge is enabled
        if (ConfigHandler.autoMergeBlockProperties) {
            debugLog("Auto-merge block properties is enabled, starting properties watcher");
            PropertiesWatcher.startWatcher();
        } else {
            debugLog("Auto-merge block properties is disabled, not starting properties watcher");
        }
    }

    private void applyUpdateNotification(Path shader, boolean styleUnbound, boolean styleReimagined) {
        if (!UpdateChecker.shouldUserUpdate()) {
            return;
        }

        boolean isOculus = ShaderLoader.getShaderLoader().equals(ShaderLoader.OCULUS);
        boolean isOptifine = ShaderLoader.getShaderLoader().equals(ShaderLoader.OPTIFINE);

        String newVersionText = "value.info19.0=§c" + PATCH_VERSION.replace("_", "") + " §r->§a " + UpdateChecker.getNewModVersion();
        if (isOculus || isOptifine && !ShaderLoader.isMinecraftVersionAtLeast("1.21.1")) {
            newVersionText = "value.info19.0=§c" + PATCH_VERSION.replace("_", "") + " -> " + UpdateChecker.getNewModVersion();
        }

        try {
            // Shaders Properties Modifications
            ModifyPatchedShaderpacks.modifyFiles(shader, styleUnbound, styleReimagined, SHADERS_PROPERTIES_LOCATION, null, "screen=<empty> <empty>", "screen=info19 info20");
            ModifyPatchedShaderpacks.modifyFiles(shader, styleUnbound, styleReimagined, LANG_LOCATION, ".lang", "value\\.info19\\.0=.*", newVersionText);

            // Iris Shader Folder Mod Description
            // 4 \\\\ have to be used to represent 1 \ in the final JSON due to multiple layers of escaping
            String shaderDescriptionText = "\\\\u00A7cComplementary Shaders " + VERSION.replace("_", "") + "\\\\u00A7r + \\\\u00A7dEuphoria Patches " + PATCH_VERSION.replace("_", "") + "\\\\u00A7r Complementary add-on by SpacEagle17 extending it with many more unique optional features and settings.\\\\nDev versions: \\\\u00A7dwww.euphoriapatches.com/support\\\\u00A7r";

            ModifyPatchedShaderpacks.modifyFiles(shader, styleUnbound, styleReimagined, "shaders/pack.json", null,
            "\"shaderDescription\":.*", "\"shaderDescription\": \"" + "\\\\u00A7aUPDATE AVAILABLE!\\\\u00A7r\\\\nCurrent version: \\\\u00A7c" + PATCH_VERSION.replace("_", "") + "\\\\u00A7r -> " + "New version: \\\\u00A7a" + UpdateChecker.getNewModVersion() + "\\\\u00A7r\\\\n----------------\\\\n" + shaderDescriptionText + "\",");

        } catch (IOException e) {
            log(3, 0, "Could not modify the shader to show the user that a new version is available" + e.getMessage());
        }
    }

    private void applyCompatibilityModifications(Path shader, boolean styleUnbound, boolean styleReimagined) {
        boolean isOptifine = ShaderLoader.getShaderLoader().equals(ShaderLoader.OPTIFINE);
        boolean isAngelica = ShaderLoader.getShaderLoader().equals(ShaderLoader.ANGELICA);
        boolean isMacOS = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac");
        boolean isPhotonicsInstalled = ModChecker.isModPresent(ModChecker.ModClasses.PHOTONICS);

        // This changes the popular settings profile dynamically
        if (isOptifine || isMacOS) {
            removeColoredLighting(shader, styleUnbound, styleReimagined);
            removeEndCrystalVortex(shader, styleUnbound, styleReimagined);
            removeDragonDeathEffect(shader, styleUnbound, styleReimagined);
            removeEndPortalBeam(shader, styleUnbound, styleReimagined);
        } else if (isAngelica) {
            removeEndCrystalVortex(shader, styleUnbound, styleReimagined);
            removeDragonDeathEffect(shader, styleUnbound, styleReimagined);
            removeEndPortalBeam(shader, styleUnbound, styleReimagined);
        } else if (isPhotonicsInstalled) {
            removeColoredLighting(shader, styleUnbound, styleReimagined);
        }
    }

    private void removeColoredLighting(Path shader, boolean styleUnbound, boolean styleReimagined) {
        try {
            // Change COLORED_LIGHTING from 192 to 0
            ModifyPatchedShaderpacks.modifyFiles(shader, styleUnbound, styleReimagined, SHADERS_PROPERTIES_LOCATION, null,
                "(profile2\\.POPULAR\\s+=\\s+.*?COLORED_LIGHTING=)192(\\s+.*)", "$10  $2");
            debugLog("Removed COLORED_LIGHTING=192 from POPULAR profile");
        } catch (IOException e) {
            log(3, 0, "Could not remove COLORED_LIGHTING=192 from POPULAR profile: " + e.getMessage());
        }
    }

    private void removeEndCrystalVortex(Path shader, boolean styleUnbound, boolean styleReimagined) {
        try {
            // Change END_CRYSTAL_VORTEX from 3 to 0
            ModifyPatchedShaderpacks.modifyFiles(shader, styleUnbound, styleReimagined, SHADERS_PROPERTIES_LOCATION, null,
                "(profile2\\.POPULAR\\s+=\\s+.*?END_CRYSTAL_VORTEX=)3(\\s+.*)", "$10$2");
            debugLog("Removed END_CRYSTAL_VORTEX=3 from POPULAR profile");
        } catch (IOException e) {
            log(3, 0, "Could not remove END_CRYSTAL_VORTEX=3 from POPULAR profile: " + e.getMessage());
        }
    }

    private void removeEndPortalBeam(Path shader, boolean styleUnbound, boolean styleReimagined) {
        try {
            // Change END_PORTAL_BEAM to !END_PORTAL_BEAM
            ModifyPatchedShaderpacks.modifyFiles(shader, styleUnbound, styleReimagined, SHADERS_PROPERTIES_LOCATION, null,
                    "(profile2\\.POPULAR\\s+=\\s+.*?)\\s+END_PORTAL_BEAM(\\s+.*)", "$1 !END_PORTAL_BEAM$2");
            debugLog("Removed END_PORTAL_BEAM from POPULAR profile");
        } catch (IOException e) {
            log(3, 0, "Could not remove END_PORTAL_BEAM=1 from POPULAR profile: " + e.getMessage());
        }
    }

    private void removeDragonDeathEffect(Path shader, boolean styleUnbound, boolean styleReimagined) {
        try {
            // Change DRAGON_DEATH_EFFECT=1 to DRAGON_DEATH_EFFECT=0
            ModifyPatchedShaderpacks.modifyFiles(shader, styleUnbound, styleReimagined, SHADERS_PROPERTIES_LOCATION, null,
                    "(profile2\\.POPULAR\\s+=\\s+.*?)\\s+DRAGON_DEATH_EFFECT=1(\\s+.*)", "$1 DRAGON_DEATH_EFFECT=0$2");
            debugLog("Removed DRAGON_DEATH_EFFECT=1 from POPULAR profile");
        } catch (IOException e) {
            log(3, 0, "Could not remove DRAGON_DEATH_EFFECT=1 from POPULAR profile: " + e.getMessage());
        }
    }

    private void applyDeveloperModifications(Path shader, boolean styleUnbound, boolean styleReimagined) {
        if (isSpacEagle()) {
            try {
                ModifyPatchedShaderpacks.modifyFiles(shader, styleUnbound, styleReimagined, SHADER_MYFILE_LOCATION, null, "\\/\\/ Developed by SpacEagle17", "#define SPACEAGLE17");
            } catch (IOException e) {
                log(3, 0, "Could not modify the shader for SpacEagle17" + e.getMessage());
            }
        }
    }

    private void displayFinalMessage() {
        if (isSpacEagle()) {
            log(1, "Have fun developing Euphoria Patches!\n\n ");
        } else {
            log(-1, "Thank you for using Euphoria Patches - SpacEagle17");
        }
    }

    public void startShaderpacksWatcher() {
        if (shaderpacksWatcher != null && shaderpacksWatcher.isRunning()) return;

        shaderpacksWatcher = ShaderpacksWatcher.createAndStart(this, true);
        if (shaderpacksWatcher != null) {
            log(0, "Watching shaderpacks folder for changes...");
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
            log(0, "Processing newly detected shader pack: " + baseFile.getFileName());

            // Create shader info object
            ShaderInfo shaderInfo = new ShaderInfo();
            shaderInfo.baseFile = baseFile;
            String name = baseFile.getFileName().toString();

            // Check if this is a newer dev version first - if so, just accept it
            if (shaderDetector.isNewerDevVersion(baseFile, shaderInfo)) {
                debugLog("Accepted newer dev version: " + baseFile.getFileName());

                stopShaderpacksWatcher();
                thankYouMessage(baseFile, shaderInfo.styleUnbound, shaderInfo.styleReimagined, shaderInfo.installedDir, true);
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
                String detectedStyle = ShaderPropertyReader.detectStyleFromCommonFile(baseFile, COMMON_LOCATION);
                shaderInfo.styleReimagined = "Reimagined".equals(detectedStyle);
                shaderInfo.styleUnbound = "Unbound".equals(detectedStyle);
            }

            // Use the common method for patching
            boolean success = completeShaderPatching(shaderInfo, temp);

            // Stop watching only if successful
            if (success) {
                stopShaderpacksWatcher();
            } else if (shaderpacksWatcher != null) {
                // Track this file as having an invalid byte size
                shaderpacksWatcher.trackInvalidByteSizeFile(baseFile.getFileName().toString());
            }

            return success;
        } catch (Exception e) {
            log(3, "Error processing newly detected shader pack: " + e.getMessage());
            return false;
        }
    }
}
