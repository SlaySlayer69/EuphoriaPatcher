package mc.euphoria_patches.euphoria_patcher;

import mc.euphoria_patches.euphoria_patcher.config.Config;
import mc.euphoria_patches.euphoria_patcher.features.*;
import mc.euphoria_patches.euphoria_patcher.integration.ShaderLoader;
import mc.euphoria_patches.euphoria_patcher.logging.EuphoriaLogger;
import mc.euphoria_patches.euphoria_patcher.monitoring.PotatoFileMonitor;
import mc.euphoria_patches.euphoria_patcher.monitoring.ShaderpacksWatcher;
import mc.euphoria_patches.euphoria_patcher.services.*;
import mc.euphoria_patches.euphoria_patcher.services.ShaderDetector.ShaderInfo;
import mc.euphoria_patches.euphoria_patcher.util.*;

import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class EuphoriaPatcher {
    public static final String BRAND_NAME = "Complementary";
    public static final String PATCH_NAME = "EuphoriaPatches";
    public static final String VERSION = PatchInfo.VERSION;
    public static final String PATCH_VERSION = PatchInfo.PATCH_VERSION;

    public static final String DOWNLOAD_URL = "https://www.complementary.dev/";
    public static final String COMMON_LOCATION = "shaders/lib/common.glsl";
    public static final String LANG_LOCATION = "shaders/lang";
    public static final String SHADERS_PROPERTIES_LOCATION = "shaders/shaders.properties";
    public static final String SHADER_MYFILE_LOCATION = "shaders/lib/misc/myFile.glsl";

    // Get necessary paths
    public static Path shaderpacks = ModLoaderSpecifics.shaderpacks();
    public static Path configDirectory = ModLoaderSpecifics.configDirectory();
    public static Path modDirectory = shaderpacks.getParent().resolve("mods");

    // Config Options
    public static boolean doPopUpLogging = true;
    public static boolean doUpdateChecking = true;
    public static boolean doRenameOldShaderFiles = true;
    public static boolean doDeleteOldShaderFiles = false;
    public static boolean doDisplayShaderInGameMessage = true;
    public static boolean doDebugLogging = false;
    public static String alternativeShaderNames = "";

    // Global Variables and Objects
    private static boolean ALREADY_LAUNCHED = false;
    private static boolean IS_BASE_MESSAGE_SHOWN = false;
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
        GUIScreen.doSomethingRandomToPreventMinimization(3, 7);

        configStuff();

        modDirectory = determineModsDirectory();
        if (ModFolderVersionChecker.existsNewerModInFolder()) return;

        if (doPopUpLogging) loggerInstance.checkAndSetupSodiumLogging();

        UpdateChecker.checkForUpdates();

        ShaderLoader.getShaderLoader();

        log(0, JsonUtilReader.getRandomMessage("startupMessages"));

        UpdateShaderConfig.markEuphoriaPatchesSettingsFiles();

        // Initialize service classes
        initializeServices();

        // Detect installed Complementary Shaders versions
        ShaderInfo shaderInfo = shaderDetector.detectInstalledShaders(namingService);

        if (!shaderInfo.isAlreadyInstalled) {
            if (shaderInfo.baseFile == null) {
                installBaseMessage();
                return;
            }
        } else {
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

        if (doDeleteOldShaderFiles) ModifyOutdatedPatches.delete();
        if (doRenameOldShaderFiles) ModifyOutdatedPatches.rename();

        thankYouMessage(shaderInfo.baseFile, shaderInfo.styleUnbound, shaderInfo.styleReimagined, shaderInfo.installedDir, shaderInfo.isAlreadyInstalled);
        return true;
    }

    public void configStuff() {
        // How to use: Cast to desired data type, then call readWriteConfig, it returns a String.
        // First parameter is the config name, second is the value
        // Third one is the description, it can either be null or a String, supports multi line descriptions with "\n"
        doPopUpLogging = Boolean.parseBoolean(Config.readWriteConfig("doPopUpLogging", "true", "Option for the sodium message popup logging." +
                "\nDefault = true"));
        doUpdateChecking = Boolean.parseBoolean(Config.readWriteConfig("doUpdateChecking", "true", "Option that enables or disables the update checker, which verifies if a new version of the mod is available." +
                "\nUses the Modrinth API to fetch update information." +
                "\nDefault = true"));
        doRenameOldShaderFiles = Boolean.parseBoolean(Config.readWriteConfig("doRenameOldShaderFiles", "true", "Option that automatically renames outdated Euphoria Patches folders and config files to a new name." +
                "\nThis makes it easier for users to identify which ones are outdated." +
                "\nDefault = true"));
        doDeleteOldShaderFiles = Boolean.parseBoolean(Config.readWriteConfig("doDeleteOldShaderFiles", "false", "Option that automatically deleted outdated Euphoria Patches folders and config files." +
                "\nDefault = false"));
        doDisplayShaderInGameMessage = Boolean.parseBoolean(Config.readWriteConfig("doDisplayShaderInGameMessage", "true", "Option that enables or disables the in-game shader messages, for example an update message made by the shader itself. Only works on Iris" +
                "\nDefault = true"));
        doDebugLogging = Boolean.parseBoolean(Config.readWriteConfig("doDebugLogging", "false", "Option that enables or disables debug logging." +
                "\nDefault = false"));
        alternativeShaderNames = Config.readWriteConfig("alternativeShaderNames", "", "Here one can set alternative Shader Names which will also be generated alongside the normal one." +
                "\nThis is useful if you want multiple different settings you can quickly switch between" +
                "\nDefault = Empty String, which means no alternative names will be generated." +
                "\nIn case of multiple names, separate them with a comma" +
                "\nYou can also use {baseVersion} or {patchVersion} in names to insert the base shader or Euphoria Patches version." +
                "\nExample: Euphoria Saturated, Comp_{baseVersion} + EP_{patchVersion} Dark Settings, EP High Performance, etc...");

        Config.startConfigWatcher();
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
            } catch (Exception ignored) {
            }
        }));
    }

    private static Path determineModsDirectory() {
        // Default mods directory
        Path defaultModsDir = shaderpacks.getParent().resolve("mods");

        // Check if the installation info file exists
        Path installInfoPath = configDirectory.resolve("installedByCompInstaller.txt");
        if (!Files.exists(installInfoPath)) {
            debugLog("Installation info file not found, using default mods directory");
            return defaultModsDir;
        }
        
        try {
            // Read the first line of the installation info file
            String line = Files.readAllLines(installInfoPath).get(0);
            String prefix = "in the ";
            String suffix = " folder";
            
            if (line.contains(prefix) && line.contains(suffix)) {
                // Extract the path part between "in the " and " folder"
                int startIndex = line.indexOf(prefix) + prefix.length();
                int endIndex = line.indexOf(suffix);
                
                if (startIndex >= prefix.length() && endIndex > startIndex) {
                    String customPath = line.substring(startIndex, endIndex);
                    debugLog("Found custom path in installation info file: " + customPath);
                    
                    if (customPath.equals("mods")) {
                        debugLog("Custom path is the standard mods folder");
                        return defaultModsDir;
                    }
                    
                    // Construct the potential custom mods path
                    Path customModsDir = shaderpacks.getParent().resolve(customPath);
                    
                    // Verify this path exists
                    if (Files.exists(customModsDir) && Files.isDirectory(customModsDir)) {
                        debugLog("Using custom mods directory: " + customModsDir);
                        return customModsDir;
                    } else {
                        debugLog("Custom mods directory doesn't exist: " + customModsDir + ", falling back to default");
                    }
                }
            }
        } catch (IOException | IndexOutOfBoundsException e) {
            debugLog("Error reading installation info file: " + e.getMessage());
        }
        
        return defaultModsDir;
    }

    public static boolean isSpacEagle() {
        try {
            boolean containsSpacEagle = shaderpacks.toString().contains("SpacEagle");
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
        // Create a safe way to get the shader path
        Path shader = null;
        
        // First try using the already detected installed directory
        if (installedDir != null) {
            debugLog("Using already detected installed directory: " + installedDir);
            shader = installedDir;
        } 
        // Fall back to standard method if installedDir is null
        else if (baseFile != null) {
            shader = instance.namingService.getPatchedShaderPath(baseFile);
        } else {
            // If baseFile is null, try to find the patched shader directory directly
            try {
                DirectoryStream.Filter<Path> filter = path -> 
                    (Files.isDirectory(path) || 
                    (Files.isRegularFile(path) && path.toString().endsWith(".zip"))) && 
                    path.getFileName().toString().contains(BRAND_NAME) && 
                    path.getFileName().toString().contains(" + " + PATCH_NAME + PATCH_VERSION);
                    
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks, filter)) {
                    for (Path path : stream) {
                        shader = path;
                        break;  // Use the first matching directory
                    }
                }
            } catch (IOException e) {
                log(3, "Error finding patched shader directory: " + e.getMessage());
            }
        }

        // Only proceed with update checking if we found a valid shader path
        if (shader != null) {
            boolean isIris = ShaderLoader.getShaderLoader().equals(ShaderLoader.IRIS);
            boolean isOculus = ShaderLoader.getShaderLoader().equals(ShaderLoader.OCULUS);
            boolean isOptifine = ShaderLoader.getShaderLoader().equals(ShaderLoader.OPTIFINE);
            if (UpdateChecker.isUpdateAvailable() && UpdateChecker.isMajorUpdate()) {
                String newVersionText = "value.info19.0=§c" + PATCH_VERSION.replace("_", "") + " §r->§a " + UpdateChecker.getNewModVersion();
                if (isOculus || isOptifine && !ShaderLoader.isMinecraftVersionAtLeast("1.21.1")) {
                    newVersionText = "value.info19.0=§c" + PATCH_VERSION.replace("_", "") + " -> " + UpdateChecker.getNewModVersion();
                }
                try {
                    ModifyPatchedShaderpacks.modifyFiles(shader, styleUnbound, styleReimagined, SHADERS_PROPERTIES_LOCATION, null, "screen=<empty> <empty>", "screen=info19 info20");
                    ModifyPatchedShaderpacks.modifyFiles(shader, styleUnbound, styleReimagined, LANG_LOCATION, ".lang", "value\\.info19\\.0=.*", newVersionText);
                } catch (IOException e) {
                    log(3, 0, "Could not modify the shader to show the user that a new version is available" + e.getMessage());
                }
            }

            try {
                String shaderLoaderVersion = ShaderLoader.getShaderLoaderVersionString();
                ModifyPatchedShaderpacks.modifyFiles(shader, styleUnbound, styleReimagined, SHADER_MYFILE_LOCATION, "null", "\\/\\/ Shader Loader Version Placeholder|#define EUPHORIA_PATCHES_.*_VERSION \\d{1,5}", shaderLoaderVersion);
                ModifyPatchedShaderpacks.modifyFiles(shader, styleUnbound, styleReimagined, "shaders/block.properties", "null", "# Shader Loader Version Placeholder|\\/\\/ Shader Loader Version Placeholder|#define EUPHORIA_PATCHES_.*_VERSION \\d{1,5}", shaderLoaderVersion);
            } catch (IOException e) {
                log(3, 0, "Could not modify the shader to show the shader loader version" + e.getMessage());
            }

            boolean isMacOS = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac");
            if (isMacOS || !(isIris || isOculus)) {
                try {
                    // Change COLORED_LIGHTING from 192 to 0
                    ModifyPatchedShaderpacks.modifyFiles(shader, styleUnbound, styleReimagined, SHADERS_PROPERTIES_LOCATION, null, 
                        "(profile\\.POPULAR\\s+=\\s+.*?COLORED_LIGHTING=)192(\\s+.*)", "$10  $2");
                    
                    // Change END_CRYSTAL_VORTEX from 3 to 0
                    ModifyPatchedShaderpacks.modifyFiles(shader, styleUnbound, styleReimagined, SHADERS_PROPERTIES_LOCATION, null, 
                        "(profile\\.POPULAR\\s+=\\s+.*?END_CRYSTAL_VORTEX=)3(\\s+.*)", "$10$2");
                    
                    // Change DRAGON_DEATH_EFFECT to !DRAGON_DEATH_EFFECT
                    ModifyPatchedShaderpacks.modifyFiles(shader, styleUnbound, styleReimagined, SHADERS_PROPERTIES_LOCATION, null, 
                        "(profile\\.POPULAR\\s+=\\s+.*?)\\s+DRAGON_DEATH_EFFECT(\\s+.*)", "$1 !DRAGON_DEATH_EFFECT$2");
                    
                    // Change END_PORTAL_BEAM to !END_PORTAL_BEAM
                    ModifyPatchedShaderpacks.modifyFiles(shader, styleUnbound, styleReimagined, SHADERS_PROPERTIES_LOCATION, null, 
                        "(profile\\.POPULAR\\s+=\\s+.*?)\\s+END_PORTAL_BEAM(\\s+.*)", "$1 !END_PORTAL_BEAM$2");
                    
                    debugLog("Applied compatibility modifications for macOS/non-iris loader: disabled COLORED_LIGHTING, END_CRYSTAL_VORTEX, DRAGON_DEATH_EFFECT, and END_PORTAL_BEAM in POPULAR profile");
                } catch (IOException e) {
                    log(3, 0, "Could not apply compatibility shader modifications: " + e.getMessage());
                }
            }

            if (isSpacEagle()) {
                try {
                    ModifyPatchedShaderpacks.modifyFiles(shader, styleUnbound, styleReimagined, SHADER_MYFILE_LOCATION, null, "\\/\\/ Developed by SpacEagle17", "#define SPACEAGLE17");
                } catch (IOException e) {
                    log(3, 0, "Could not modify the shader for SpacEagle17" + e.getMessage());
                }
                // Create alternative shader names if specified in config
                namingService.createAlternativeShaderNames(shader, isAlreadyInstalled, alternativeShaderNames);
                log(1, "Have fun developing Euphoria Patches!\n");
            } else {
                // Create alternative shader names if specified in config
                namingService.createAlternativeShaderNames(shader, isAlreadyInstalled, alternativeShaderNames);
                log(-1, "Thank you for using Euphoria Patches - SpacEagle17");
            }
        } else {
            debugLog("No valid shader path found for thank you message");
            log(-1, "Thank you for using Euphoria Patches - SpacEagle17");
        }
    }

    private void installBaseMessage() {
        if (IS_BASE_MESSAGE_SHOWN) return;
        IS_BASE_MESSAGE_SHOWN = true;
        
        // Try to find the highest older version
        Path highestOlderVersion = versionComparator.findHighestOlderVersion();
        
        log(3, 8, "=== SHADER NOT FOUND ===");
        log(3, 8, "Required: " + BRAND_NAME + "Shaders " + VERSION.replace("_", ""));
        
        if (highestOlderVersion != null) {
            log(3, 8, "Found: " + highestOlderVersion.getFileName().toString());
            log(3, 8, "You have an older version installed.");
            log(3, 8, "");
            log(3, 8, "SOLUTION: Download and install " + BRAND_NAME + "Shaders " + VERSION.replace("_", ""));
        } else {
            log(3, 8, "");
            log(3, 8, "No " + BRAND_NAME + " shader found in your shaderpacks folder.");
            log(3, 8, "");
            log(3, 8, "SOLUTION: Download " + BRAND_NAME + "Shaders " + VERSION.replace("_", ""));
        }
        
        log(3, 8, "Download from: " + DOWNLOAD_URL);

        // Start watching for the shader to be added
        startShaderpacksWatcher();
    }

    private void startShaderpacksWatcher() {
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

            // Create temporary directory
            Path temp = ArchiveOperations.createTempDirectory();
            if (temp == null) return false;

            // Create shader info object
            ShaderInfo shaderInfo = new ShaderInfo();
            shaderInfo.baseFile = baseFile;
            String name = baseFile.getFileName().toString();
            
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
