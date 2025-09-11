package mc.euphoria_patches.euphoria_patcher;

import mc.euphoria_patches.euphoria_patcher.features.*;
import mc.euphoria_patches.euphoria_patcher.util.*;

import io.sigpipe.jbsdiff.InvalidHeaderException;
import io.sigpipe.jbsdiff.ui.FileUI;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class EuphoriaPatcher {

    private static final boolean IS_DEV = false; // Manual Boolean. REMEMBER TO SET TO FALSE BEFORE COMPILING
    private static final boolean isDevModLoader = ModLoaderSpecifics.isDevMode;

    public static final String BRAND_NAME = "Complementary";
    public static final String PATCH_NAME = "EuphoriaPatches";
    public static final String VERSION = "_r5.5.1";
    public static final String PATCH_VERSION = "_1.6.7";

    public static final String BASE_TAR_SHA256 = "c1c128eb0b15657670e5b8e3884fd8a495aaec1fd4226682b18e1ade702217cf";
    public static final int BASE_TAR_SIZE = 1340416;

    public static final String DOWNLOAD_URL = "https://www.complementary.dev/";
    public static final String COMMON_LOCATION = "shaders/lib/common.glsl";
    public static final String LANG_LOCATION = "shaders/lang";
    public static final String SHADERS_PROPERTIES_LOCATION = "shaders/shaders.properties";
    public static final String SHADER_MYFILE_LOCATION = "shaders/lib/misc/myFile.glsl";

    // Get necessary paths
    public static Path shaderpacks = ModLoaderSpecifics.shaderpacks;
    public static Path configDirectory = ModLoaderSpecifics.configDirectory;
    public static Path mainIntellijDir = shaderpacks.getParent().getParent();
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
    private static int filesScannedCounter = 0;
    private static int totalFilesToScan = 0;

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

        configStuff();

        modDirectory = determineModsDirectory();
        if (ModFolderVersionChecker.existsNewerModInFolder()) return;

        if (doPopUpLogging) loggerInstance.checkAndSetupSodiumLogging();

        if (doUpdateChecking) UpdateChecker.checkForUpdates();

        ShaderLoader.getShaderLoader();

        log(0, JsonUtilReader.getRandomMessage("startupMessages"));

        UpdateShaderConfig.markEuphoriaPatchesSettingsFiles();

        // Detect installed Complementary Shaders versions
        ShaderInfo shaderInfo = detectInstalledShaders();

        if (!shaderInfo.isAlreadyInstalled) {
            if (shaderInfo.baseFile == null) {
                installBaseMessage();
                if (!isDevFunc()) return;
            }
        } else {
            thankYouMessage(shaderInfo.baseFile, shaderInfo.styleUnbound, shaderInfo.styleReimagined, shaderInfo.installedDir);
            return;
        }

        // Create temporary directory
        Path temp = createTempDirectory();
        if (temp == null || shaderInfo.baseFile == null && !isDevFunc()) return;

        completeShaderPatching(shaderInfo, temp);
    }

    public static EuphoriaPatcher getInstance() {
        return instance;
    }

    private boolean completeShaderPatching(ShaderInfo shaderInfo, Path temp) {
        // Process and patch shaders
        if (!processAndPatchShaders(shaderInfo, temp)) return false;

        // Update .txt shader config file
        UpdateShaderConfig.updateShaderTxtConfigFile(shaderInfo.styleUnbound, shaderInfo.styleReimagined);

        // Update shader loader (iris) config
        UpdateShaderLoaderConfig.updateShaderLoaderConfig(shaderInfo.styleUnbound, shaderInfo.styleReimagined);

        if (doDeleteOldShaderFiles) ModifyOutdatedPatches.delete();
        if (doRenameOldShaderFiles) ModifyOutdatedPatches.rename();

        thankYouMessage(shaderInfo.baseFile, shaderInfo.styleUnbound, shaderInfo.styleReimagined, shaderInfo.installedDir);
        return true;
    }

    public void configStuff() {
        // How to use: Cast to desired data type, then call readWriteConfig, it returns a String.
        // First parameter is the config name, second is the value
        // Third one is the description, it can either be null or a String, supports multi line descriptions with "\n"
        doPopUpLogging = Boolean.parseBoolean(Config.readWriteConfig("doPopUpLogging", "true", "Option for the sodium message popup logging." +
                "\nDefault = true"));
        doUpdateChecking = Boolean.parseBoolean(Config.readWriteConfig("doUpdateChecking", "true", "Option that enables or disables the update checker, which verifies if a new version of the mod is available." +
                "\nMore info here: https://github.com/EuphoriaPatches/PatcherUpdateChecker" +
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
            } catch (Exception ignored) {
            }
        }));
    }

    public static boolean isDevFunc() {
        return IS_DEV && isDevModLoader;
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

    private ShaderInfo detectInstalledShaders() {
        ShaderInfo info = new ShaderInfo();
        try {
            // First check if patched shaders already exist, even if base shader is missing
            checkForExistingPatchedShaders(info);
            if (info.isAlreadyInstalled) {
                return info;
            }

            // Find all potential shader paths (both files and directories) by name
            List<Path> potentialShaderPaths = new ArrayList<>();

            // Add files ending with .zip
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks,
                    path -> isBrandNameShader(path, true))) {
                stream.forEach(potentialShaderPaths::add);
            }

            // Add directories
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks,
                    path -> isBrandNameShader(path, false))) {
                stream.forEach(potentialShaderPaths::add);
            }

            // Process all found paths by name
            for (Path path : potentialShaderPaths) {
                processShaderPath(path, info);
                if (info.styleReimagined && info.styleUnbound) break;
            }

            // If no valid shader found by name, try using byte size verification
            if (info.baseFile == null) {
                log(2, 0, "No shaders with expected name pattern found, checking via byte size...");
                log(2, 0, "If you have a lot of shaders installed, this may take a while. Please be patient.");
                log(2, 0, "Please wait... \n");
                Path shaderByByteSize = findShaderByByteSize();
                if (shaderByByteSize != null) {
                    log(0, "Found valid shader by byte size: " + shaderByByteSize.getFileName());
                    // Determine shader style from path or assume default
                    String name = shaderByByteSize.getFileName().toString();
                    info.styleReimagined = name.contains("Reimagined") || !name.contains("Unbound");
                    info.styleUnbound = name.contains("Unbound");
                    info.baseFile = shaderByByteSize;
                    checkIfAlreadyInstalled(shaderByByteSize, info);
                }
            }
        } catch (IOException e) {
            log(3, "Error reading shaderpacks directory: " + e.getMessage());
        }
        return info;
    }

    /**
     * Checks for existing patched shader directories that may exist even if the base shader is gone
     */
    private void checkForExistingPatchedShaders(ShaderInfo info) {
        try {
            // First check using the standard naming pattern
            DirectoryStream.Filter<Path> patchedFilter = path -> 
                path.getFileName().toString().contains(BRAND_NAME) && 
                path.getFileName().toString().contains(" + " + PATCH_NAME + PATCH_VERSION) &&
                (Files.isDirectory(path) || 
                (Files.isRegularFile(path) && path.toString().endsWith(".zip")));
            
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks, patchedFilter)) {
                for (Path path : stream) {
                    checkIfAlreadyInstalled(path, info);
                    if (info.isAlreadyInstalled) {
                        return;
                    }
                }
            }

            debugLog("No existing patched shaders found by standard naming pattern, checking for Euphoria Patches files...");
            
            // If not found by name, check all directories for the myFile.glsl with version signature
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks, Files::isDirectory)) {

                if (isDevFunc() || info.isAlreadyInstalled) {
                    return;
                }

                for (Path directory : stream) {
                    Path myFilePath = directory.resolve(SHADER_MYFILE_LOCATION);

                    debugLog("Checking directory: " + directory.getFileName() + " for myFile.glsl");
                    
                    // Skip if the myFile.glsl doesn't exist
                    if (!Files.exists(myFilePath)) {
                        continue;
                    }

                    debugLog("Found myFile.glsl in directory: " + directory.getFileName());
                    
                    // Read first line of the file
                    String firstLine;
                    try (BufferedReader reader = Files.newBufferedReader(myFilePath)) {
                        firstLine = reader.readLine();
                    }
                    
                    // Check if it's an Euphoria Patches file with the matching version
                    if (firstLine != null && firstLine.startsWith("// Euphoria Patches")) {
                        String fileVersion = firstLine.replace("// Euphoria Patches ", "").trim();
                        String expectedVersion = PATCH_VERSION.replace("_", "");
                        
                        debugLog("Found potential correct Euphoria Patches version in: " + directory.getFileName());
                        debugLog("File version: " + fileVersion + ", Expected: " + expectedVersion);
                        
                        if (fileVersion.equals(expectedVersion)) {
                            debugLog("Version match found - this is a correct Euphoria Patches installation");
                            
                            info.isAlreadyInstalled = true;
                            info.installedDir = directory;
                            
                            // Try to determine style from directory name or common.glsl
                            String dirName = directory.getFileName().toString();
                            if (dirName.contains("Reimagined")) {
                                info.styleReimagined = true;
                            } else if (dirName.contains("Unbound")) {
                                info.styleUnbound = true;
                            } else {
                                // If not clear from directory name, check common.glsl
                                String detectedStyle = detectStyleFromCommonFile(directory);
                                info.styleReimagined = "Reimagined".equals(detectedStyle);
                                info.styleUnbound = "Unbound".equals(detectedStyle);
                            }
                            
                            log(0, PATCH_NAME + PATCH_VERSION + " is already installed as the renamed folder: " + directory.getFileName());
                            return;
                        }
                    }
                }
            }
        } catch (IOException e) {
            log(3, "Error checking for existing patched shaders: " + e.getMessage());
        }
    }

    private boolean isBrandNameShader(Path path, boolean isFile) {
        String name = path.getFileName().toString();
        
        // Basic conditions
        boolean hasBrandName = name.startsWith(BRAND_NAME);
        boolean notPatched = !name.contains(PATCH_NAME);
        boolean hasExactVersion = name.contains(VERSION);
        boolean notModifiedByOthers = !name.contains(" + ");

        // Exclude development or pre-release versions
        boolean isNotDevVersion = !name.contains("_dev");
        boolean isNotPreVersion = !name.contains("_pre");

        boolean matchesPattern = hasBrandName && notPatched && hasExactVersion &&
                                notModifiedByOthers &&
                                isNotDevVersion && isNotPreVersion;

        if (isFile) {
            return matchesPattern && name.endsWith(".zip");
        } else {
            return matchesPattern && Files.isDirectory(path);
        }
    }

    private void resetFilesScannedCounter() {
        filesScannedCounter = 0;
        totalFilesToScan = 0;
        debugLog("Reset files scanned counter");
    }

    private Path findShaderByByteSize() {
        try {
            // Reset counter at the start of a new scan
            resetFilesScannedCounter();
            
            // Count total files first
            int zipFileCount = 0;
            int dirCount = 0;
            
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks, 
                    path -> path.toString().endsWith(".zip") && Files.isRegularFile(path))) {
                for (Path ignored : stream) {
                    zipFileCount++;
                }
            }
            
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks, Files::isDirectory)) {
                for (Path ignored : stream) {
                    dirCount++;
                }
            }
            
            totalFilesToScan = zipFileCount + dirCount;
            debugLog("Total files to scan: " + totalFilesToScan + " (" + zipFileCount + " ZIP files, " + dirCount + " directories)");
            
            // First check ZIP files
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks,
                    path -> path.toString().endsWith(".zip") && Files.isRegularFile(path))) {
                for (Path zipFile : stream) {
                    if (isValidShaderByByteSize(zipFile)) {
                        // Found a valid shader by byte size, rename it to the correct format
                        return renameToCorrectShaderName(zipFile);
                    }
                }
            }

            // Then check directories
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks,
                    Files::isDirectory)) {
                for (Path dir : stream) {
                    if (isValidShaderByByteSize(dir)) {
                        // Found a valid shader by byte size, rename it to the correct format
                        return renameToCorrectShaderName(dir);
                    }
                }
            }
        } catch (IOException e) {
            log(3, "Error searching for shaders by byte size: " + e.getMessage());
        }
        return null;
    }

    public boolean isValidShaderByByteSize(Path path) {
        try {
            // Increment counter and show progress message every 5 files
            filesScannedCounter++;
            if (filesScannedCounter % 5 == 0) {
                log(2, 0, "Please wait... Scanned " + filesScannedCounter + " of " + totalFilesToScan + " files so far");
            }
            
            debugLog("Checking if shader is valid by byte size (" + filesScannedCounter + "/" + totalFilesToScan + "): " + path.getFileName());
            
            Path tempDir = createTempDirectory();
            if (tempDir == null) {
                debugLog("Failed to create temp directory for byte size check");
                return false;
            }
            debugLog("Created temp directory: " + tempDir);

            String baseName = path.getFileName().toString().replace(".zip", "");
            debugLog("Base name for extraction: " + baseName);

            // Extract if it's a zip file
            Path baseExtracted = extractBase(path, tempDir, baseName);
            if (baseExtracted == null) {
                debugLog("Failed to extract base for byte size check");
                return false;
            }
            debugLog("Successfully extracted to: " + baseExtracted);

            // Archive for byte size comparison
            Path baseArchived = archiveBase(baseExtracted, tempDir, baseName);
            if (baseArchived == null) {
                debugLog("Failed to archive base for byte size check");
                return false;
            }
            debugLog("Successfully archived to: " + baseArchived);

            // Check byte size quietly
            boolean result = ArchiveOperations.verifyBaseArchiveQuiet(baseArchived);
            debugLog("Byte size verification result for " + path.getFileName() + ": " + result);

            // Clean up
            try {
                debugLog("Cleaning up temp directory: " + tempDir);
                FileUtils.deleteDirectory(tempDir.toFile());
            } catch (IOException e) {
                // Ignore cleanup errors
                debugLog("Failed to clean up temp directory: " + e.getMessage());
            }

            return result;
        } catch (Exception e) {
            debugLog("Exception during byte size check: " + e.getMessage());
            System.out.println("Exception during byte size check: " + e.getMessage());
            return false;
        }
    }

    public Path renameToCorrectShaderName(Path path) {
        try {
            String fileName = path.getFileName().toString();
            String style;
            
            // First try to determine style from filename
            if (fileName.contains("Unbound")) {
                style = "Unbound";
            } else if (fileName.contains("Reimagined")) {
                style = "Reimagined";
            } else {
                // If not in filename, check the common.glsl file
                style = detectStyleFromCommonFile(path);
                debugLog("Detected " + style + " style from common.glsl file");
            }
            
            // Create the correct name format
            String correctName = BRAND_NAME + style + VERSION;
            if (fileName.endsWith(".zip")) {
                correctName += ".zip";
            }
            
            // If the name is already correct, return the original path
            if (fileName.equals(correctName)) {
                return path;
            }
            
            // Create path for the renamed shader
            Path targetPath = path.resolveSibling(correctName);
            
            // Skip if a file with the target name already exists
            if (Files.exists(targetPath)) {
                debugLog("A file with the correct name already exists: " + targetPath.getFileName());
                return path;
            }
            
            // Rename the file/directory
            Path renamedPath = Files.move(path, targetPath);
            log(0, "Renamed shader from \"" + fileName + "\" to \"" + correctName + "\"");
            
            return renamedPath;
        } catch (IOException e) {
            log(2, "Failed to rename shader: " + e.getMessage());
            return path; // Return original path if renaming failed
        }
    }

        /**
     * Determines shader style by reading the common.glsl file
     * @param shaderPath Path to the shader file or directory
     * @return "Reimagined" or "Unbound" based on the SHADER_STYLE value
     */
    private String detectStyleFromCommonFile(Path shaderPath) {
        Path tempDir = null;
        try {
            // Create temp directory
            tempDir = createTempDirectory();
            if (tempDir == null) return "Reimagined"; // Default if we can't create temp dir
            
            String baseName = shaderPath.getFileName().toString().replace(".zip", "");
            
            // Extract if needed
            Path extractedPath;
            if (shaderPath.toString().endsWith(".zip")) {
                extractedPath = extractBase(shaderPath, tempDir, baseName);
                if (extractedPath == null) return "Reimagined";
            } else {
                extractedPath = shaderPath;
            }
            
            // Read the common.glsl file
            Path commonFile = extractedPath.resolve(COMMON_LOCATION);
            if (Files.exists(commonFile)) {
                String content = FileUtils.readFileToString(commonFile.toFile(), "UTF-8");
                
                // Look for SHADER_STYLE definition
                if (content.contains("SHADER_STYLE 4")) {
                    debugLog("Detected Unbound style from common.glsl");
                    return "Unbound";
                } else if (content.contains("SHADER_STYLE 1") || content.contains("SHADER_STYLE")) {
                    debugLog("Detected Reimagined style from common.glsl");
                    return "Reimagined";
                }
            }
        } catch (IOException e) {
            log(2, "Error reading common.glsl: " + e.getMessage());
        } finally {
            // Clean up temp directory
            if (tempDir != null) {
                try {
                    FileUtils.deleteDirectory(tempDir.toFile());
                } catch (IOException ignored) {}
            }
        }
        return "Reimagined"; // Default fallback
    }

    private void processShaderPath(Path path, ShaderInfo info) {
        String name = path.getFileName().toString();
        
        // Check shader style from filename first
        boolean styleFromName = false;
        
        if (name.contains("Reimagined")) {
            info.styleReimagined = true;
            styleFromName = true;
            if (info.baseFile == null) {
                info.baseFile = path;
            }
        } else if (name.contains("Unbound")) {
            info.styleUnbound = true;
            styleFromName = true;
            if (info.baseFile == null) {
                info.baseFile = path;
            }
        }
        
        // If style isn't clear from the filename, check common.glsl
        if (!styleFromName) {
            String detectedStyle = detectStyleFromCommonFile(path);
            if ("Reimagined".equals(detectedStyle)) {
                info.styleReimagined = true;
                if (info.baseFile == null) {
                    info.baseFile = path;
                }
            } else if ("Unbound".equals(detectedStyle)) {
                info.styleUnbound = true;
                if (info.baseFile == null) {
                    info.baseFile = path;
                }
            }
            log(0, "Shader style not in filename, detected " + detectedStyle + " from common.glsl");
        }
        
        checkIfAlreadyInstalled(path, info);
    }

    // Check if the patch is already installed
    private void checkIfAlreadyInstalled(Path path, ShaderInfo info) {
        Path potentialInstallPath;
        boolean isDirectPatchedDir = path.getFileName().toString().contains(" + " + PATCH_NAME + PATCH_VERSION);
        
        if (isDirectPatchedDir) {
            // This is already a patched shader directory
            potentialInstallPath = path;
            
            // Try to reconstruct the base file name
            String name = path.getFileName().toString();
            String baseName = name.substring(0, name.indexOf(" + " + PATCH_NAME + PATCH_VERSION));
            Path potentialBaseZip = shaderpacks.resolve(baseName + ".zip");
            
            // Set shader styles based on directory name
            info.styleReimagined = name.contains("Reimagined");
            info.styleUnbound = name.contains("Unbound");
            
            if (Files.exists(potentialBaseZip)) {
                info.baseFile = potentialBaseZip;
            }
        } else {
            // This is a base shader file
            potentialInstallPath = getPatchedShaderPath(path);
            if (info.baseFile == null) {
                info.baseFile = path;
            }
        }

        // Skip check in certain situations
        if (isDevFunc() || info.isAlreadyInstalled || potentialInstallPath == null) {
            return;
        }

        // If the patched directory exists, check if it contains EuphoriaPatches files
        if (Files.exists(potentialInstallPath)) {
            try {
                boolean containsEuphoriaFile;
                try (Stream<Path> pathStream = Files.walk(potentialInstallPath)) {
                    containsEuphoriaFile = pathStream
                            .filter(Files::isRegularFile)
                            .anyMatch(p -> p.getFileName().toString().contains("EuphoriaPatches"));
                }

                if (containsEuphoriaFile) {
                    info.isAlreadyInstalled = true;
                    info.installedDir = potentialInstallPath;
                    log(0, PATCH_NAME + PATCH_VERSION + " is already installed.");
                } else {
                    // No EuphoriaPatches file found, delete the directory
                    log(0, "Found incomplete installation. Cleaning up " + potentialInstallPath.getFileName());
                    UsefulFunctions.deleteRecursively(potentialInstallPath);
                    info.isAlreadyInstalled = false;
                }
            } catch (IOException e) {
                log(3, "Error checking installation status. Cleaning up: " + e.getMessage());
                try {
                    UsefulFunctions.deleteRecursively(potentialInstallPath);
                } catch (IOException ex) {
                    log(3, "Error deleting directory: " + ex.getMessage());
                }
                info.isAlreadyInstalled = false;
            }
        }
    }

    /**
     * Gets the path for a patched shader based on the base shader file
     *
     * @param baseFile Path to the base shader file or directory
     * @return Path to the patched shader, or null if baseFile is null
     */
    public static Path getPatchedShaderPath(Path baseFile) {
        if (baseFile == null) {
            log(3, "Cannot create patched shader path - base file is null");
            return null;
        }

        try {
            String fileName = baseFile.getFileName().toString();
            String baseName = fileName.endsWith(".zip") ? fileName.replace(".zip", "") : fileName;
            baseName = cleanBaseName(baseName);
            
            return baseFile.resolveSibling(baseName + " + " + PATCH_NAME + PATCH_VERSION);
        } catch (Exception e) {
            log(3, "Error creating patched shader path: " + e.getMessage());
            return null;
        }
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

    private void thankYouMessage(Path baseFile, boolean styleUnbound, boolean styleReimagined, Path installedDir) {
        // Create a safe way to get the shader path
        Path shader = null;
        
        // First try using the already detected installed directory
        if (installedDir != null) {
            debugLog("Using already detected installed directory: " + installedDir);
            shader = installedDir;
        } 
        // Fall back to standard method if installedDir is null
        else if (baseFile != null) {
            shader = getPatchedShaderPath(baseFile);
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
            if (UpdateChecker.NEW_VERSION_AVAILABLE && doUpdateChecking) {
                String newVersionText = "value.info19.0=§c" + PATCH_VERSION.replace("_", "") + " §r->§a " + UpdateChecker.NEW_MOD_VERSION;
                if (ShaderLoader.getShaderLoader().equals(ShaderLoader.OCULUS) || ShaderLoader.getShaderLoader().equals(ShaderLoader.OPTIFINE) && !ShaderLoader.isMinecraftVersionAtLeast("1.21.1")) {
                    newVersionText = "value.info19.0=§c" + PATCH_VERSION.replace("_", "") + " -> " + UpdateChecker.NEW_MOD_VERSION;
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
                ModifyPatchedShaderpacks.modifyFiles(shader, styleUnbound, styleReimagined, "shaders/block.properties", "null", "# Shader Loader Version Placeholder|#define EUPHORIA_PATCHES_.*_VERSION \\d{1,5}", shaderLoaderVersion);
            } catch (IOException e) {
                log(3, 0, "Could not modify the shader to show the shader loader version" + e.getMessage());
            }

            if (isSpacEagle()) {
                try {
                    ModifyPatchedShaderpacks.modifyFiles(shader, styleUnbound, styleReimagined, SHADER_MYFILE_LOCATION, null, "\\/\\/ Developed by SpacEagle17", "#define SPACEAGLE17");
                } catch (IOException e) {
                    log(3, 0, "Could not modify the shader for SpacEagle17" + e.getMessage());
                }
                // Create alternative shader names if specified in config
                createAlternativeShaderNames(shader);
                log(1, "Have fun developing Euphoria Patches!\n");
            } else {
                // Create alternative shader names if specified in config
                createAlternativeShaderNames(shader);
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
        Path highestOlderVersion = findHighestOlderVersion();
        
        log(3, 8, "You need to have " + BRAND_NAME + "Shaders" + VERSION + " installed!");
        
        if (highestOlderVersion != null) {
            log(3, 8, "Found older version: " + highestOlderVersion.getFileName().toString());
            log(3, 8, "Please update to specifically " + BRAND_NAME + "Shaders" + VERSION + " from " + DOWNLOAD_URL + " and place it into your shaderpacks folder.");
        } else {
            log(3, 8, "Please download it from " + DOWNLOAD_URL + " and place it into your shaderpacks folder.");
        }

        // Start watching for the shader to be added
        startShaderpacksWatcher();
    }

    /**
     * Finds the highest version of any older Complementary shader
     * @return Path to the highest version file/directory, or null if none found
     */
    private Path findHighestOlderVersion() {
        Path highestVersionPath = null;
        int[] highestVersion = {0, 0, 0}; // major, minor, patch
        
        try {
            // Check files
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks)) {
                for (Path path : stream) {
                    if (isOlderBrandNameShader(path, Files.isRegularFile(path) && path.toString().endsWith(".zip"))) {
                        int[] version = extractVersionNumbers(path.getFileName().toString());
                        if (compareVersions(version, highestVersion) > 0) {
                            highestVersion = version;
                            highestVersionPath = path;
                        }
                    }
                }
            }
        } catch (IOException e) {
            log(3, "Error checking for older shader versions: " + e.getMessage());
        }
        
        return highestVersionPath;
    }

    /**
     * Checks if a path is an older version of Complementary shader
     */
    private boolean isOlderBrandNameShader(Path path, boolean isFile) {
        String name = path.getFileName().toString();
        
        // First check if it's a Complementary shader without the patch
        boolean isComplementary = name.contains(BRAND_NAME) && 
                                 name.matches(".*_r\\d+\\.\\d+(?:\\.\\d+)?.*") && 
                                 !name.contains(PATCH_NAME);
        
        if (isComplementary) {
            // Extract version numbers and compare
            int[] fileVersion = extractVersionNumbers(name);
            int[] targetVersion = extractVersionNumbers(VERSION);
            
            // Only consider it "older" if the version is actually lower
            boolean isOlder = compareVersions(fileVersion, targetVersion) < 0;
            
            return isOlder && (isFile ? name.endsWith(".zip") : Files.isDirectory(path));
        }
        
        return false;
    }

    /**
     * Checks if the given filename represents a newer version of the shader than what's expected
     * @param fileName The filename to check
     * @return true if it's a newer version, false otherwise
     */
    public static boolean isNewerShaderVersion(String fileName) {
        // First check if it's a Complementary shader
        if (!fileName.contains(BRAND_NAME)) {
            return false;
        }
        
        // Extract version numbers using regex
        int[] fileVersion = extractVersionNumbers(fileName);
        int[] targetVersion = extractVersionNumbers(VERSION);
        
        // Compare versions - positive means fileVersion is newer than targetVersion
        return compareVersions(fileVersion, targetVersion) > 0;
    }

    public static String getVersionStringFromFileName(String fileName) {
        int[] versionNumbers = EuphoriaPatcher.extractVersionNumbers(fileName);
        StringBuilder sb = new StringBuilder("r").append(versionNumbers[0]).append(".").append(versionNumbers[1]);
        if (versionNumbers[2] > 0) {
            sb.append(".").append(versionNumbers[2]);
        }
        return sb.toString();
    }

    /**
     * Extract version numbers from a filename
     * @return int array with [major, minor, patch]
     */
    public static int[] extractVersionNumbers(String filename) {
        int[] version = {0, 0, 0};
        
        // Extract r-version number (e.g., _r5.1 or _r5.3.2)
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("_r(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
        java.util.regex.Matcher matcher = pattern.matcher(filename);
        
        if (matcher.find()) {
            version[0] = Integer.parseInt(matcher.group(1));  // Major
            version[1] = Integer.parseInt(matcher.group(2));  // Minor
            version[2] = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;  // Patch
        }
        
        return version;
    }

    /**
     * Compare two version arrays
     * @return positive if v1 > v2, 0 if equal, negative if v1 < v2
     */
    private static int compareVersions(int[] v1, int[] v2) {
        for (int i = 0; i < 3; i++) {
            if (v1[i] != v2[i]) {
                return v1[i] - v2[i];
            }
        }
        return 0;
    }

    // Create temporary directory
    private Path createTempDirectory() {
        try {
            return Files.createTempDirectory("euphoria-patcher-");
        } catch (IOException e) {
            log(3, "Error creating temporary directory: " + e.getMessage());
            return null;
        }
    }

    public static String cleanBaseName(String baseName) {
        if (baseName == null) return null;
        debugLog("Before Cleaning base name: " + baseName);
        String cleaned = baseName.replaceAll("(?i)(?:[\\s_-]+(?:\\(copy\\)|copy|\\(\\d+\\)|\\d+))+$", ""); // Remove copy suffixes like (1), (2), Copy, etc.
        cleaned = cleaned.replaceAll("\\s+", " ").trim(); // Remove any duplicate spaces that might result from the cleaning
        debugLog("Cleaned base name: " + cleaned);
        return cleaned;
    }

    // Process and patch shaders
    private boolean processAndPatchShaders(ShaderInfo info, Path temp) {
        if (info.baseFile == null && !isDevFunc()) {
            installBaseMessage();
            return false;
        }
        assert info.baseFile != null;
        
        // Get base name and remove .zip extension
        String baseName = info.baseFile.getFileName().toString().replace(".zip", "");
        baseName = cleanBaseName(baseName);
        
        String patchedName = baseName + " + " + PATCH_NAME + PATCH_VERSION;

        Path baseExtracted = extractBase(info.baseFile, temp, baseName);
        if (baseExtracted == null) return false;

        normalizeShaderStyleInCommon(baseExtracted);

        Path baseArchived = archiveBase(baseExtracted, temp, baseName);

        if (!ArchiveOperations.verifyBaseArchive(baseArchived, info.baseFile.getFileName().toString())) return false;

        boolean result = applyPatch(baseArchived, temp, patchedName, info.styleUnbound, info.styleReimagined);

        try {
            debugLog("Cleaning up the temporary directory...");
            FileUtils.deleteDirectory(temp.toFile());
        } catch (IOException e) {
            log(2, "Error cleaning up temporary directory: " + e.getMessage());
        }
        return result;
    }

    // Extract base shader
    private Path extractBase(Path baseFile, Path temp, String baseName) {
        Path baseExtracted = temp.resolve(baseName);
        return ArchiveOperations.extract(baseFile, baseExtracted, "extracting archive");
    }

    // Archive base shader
    private Path archiveBase(Path baseExtracted, Path temp, String baseName) {
        Path baseArchived = temp.resolve(baseName + ".tar");
        return ArchiveOperations.archive(baseExtracted, baseArchived);
    }

    // Update common file
    private void normalizeShaderStyleInCommon(Path baseExtracted) {
        try {
            Path commons = baseExtracted.resolve(COMMON_LOCATION);
            String config = FileUtils.readFileToString(commons.toFile(), "UTF-8").replaceFirst("SHADER_STYLE [14]", "SHADER_STYLE 1");
            FileUtils.writeStringToFile(commons.toFile(), config, "UTF-8");
        } catch (IOException e) {
            log(3, "Error normalizing shader style in common file: " + e.getMessage());
        }
    }

    // Apply patch
    private boolean applyPatch(Path baseArchived, Path temp, String patchedName, boolean styleUnbound, boolean styleReimagined) {
        Path patchedArchive = temp.resolve(patchedName + ".tar");
        Path patchedFile = shaderpacks.resolve(patchedName);

        return isDevFunc()
                ? applyDevPatch(baseArchived, patchedArchive, patchedFile)
                : applyProductionPatch(baseArchived, patchedArchive, temp.resolve(patchedName + ".patch"),
                patchedFile, styleUnbound, styleReimagined);
    }

    private boolean applyDevPatch(Path baseArchived, Path patchedArchive, Path patchedFile) {
        Path[] directories = {
                mainIntellijDir.resolve("src/main/resources"),
                mainIntellijDir.resolve("EuphoriaPatchFiles")
        };

        boolean success = true;
        for (Path dir : directories) {
            checkBuildPath(dir);
            Path patchFile = dir.resolve(PATCH_NAME + PATCH_VERSION + ".patch");
            success &= createDevPatch(baseArchived, patchedFile, patchedArchive, patchFile);
        }
        return success;
    }

    private void checkBuildPath(Path buildDir) {
        if (!Files.exists(buildDir)) {
            try {
                Files.createDirectories(buildDir);
                log(2, "Build directory created successfully: " + buildDir);
            } catch (IOException e) {
                log(3, "Failed to create directory: " + e.getMessage());
            }
        }
    }

    // Create dev patch
    private boolean createDevPatch(Path baseArchived, Path patchedFile, Path patchedArchive, Path patchFile) {
        try {
            ArchiveUtils.archive(patchedFile, patchedArchive);
            FileUI.diff(baseArchived.toFile(), patchedArchive.toFile(), patchFile.toFile());
            log(0, ".patch file successfully created in " + patchFile + "!");
            return true;
        } catch (CompressorException | IOException | InvalidHeaderException e) {
            log(3, "Error creating dev patch: " + e.getMessage());
            return false;
        }
    }

    // Apply production patch
    private boolean applyProductionPatch(Path baseArchived, Path patchedArchive, Path patchFile, Path patchedFile, boolean styleUnbound, boolean styleReimagined) {
        try (InputStream patchStream = getClass().getClassLoader().getResourceAsStream(PATCH_NAME + PATCH_VERSION + ".patch")) {
            if (patchStream != null) {
                FileUtils.copyInputStreamToFile(Objects.requireNonNull(patchStream), patchFile.toFile());
                FileUI.patch(baseArchived.toFile(), patchedArchive.toFile(), patchFile.toFile());
                try {
                    ArchiveUtils.extract(patchedArchive, patchedFile);
                } catch (IOException | ArchiveException e) {
                    log(2, "Error extracting archive: " + e.getMessage());
                }
                applyStyleSettings(patchedFile, styleUnbound, styleReimagined);
                log(1, PATCH_NAME + " was successfully installed. Enjoy! -SpacEagle17");
                return true;
            }
        } catch (IOException | CompressorException | InvalidHeaderException e) {
            log(3, "Error applying patch file: " + e.getMessage());
        }
        return false;
    }

    // Apply style settings
    private void applyStyleSettings(Path patchedFile, boolean styleUnbound, boolean styleReimagined) throws IOException {
        if (!styleUnbound && !styleReimagined) return;

        File commons = new File(patchedFile.toFile(), COMMON_LOCATION);
        String commonContent = FileUtils.readFileToString(commons, "UTF-8");

        // Create both style configs
        String reimaginedConfig = commonContent.replaceFirst("SHADER_STYLE [14]", "SHADER_STYLE 1");
        String unboundConfig = commonContent.replaceFirst("SHADER_STYLE [14]", "SHADER_STYLE 4");

        if (!styleReimagined) {
            // Only Unbound style
            FileUtils.writeStringToFile(commons, unboundConfig, "UTF-8");
            return;
        }

        if (!styleUnbound) {
            // Only Reimagined style
            FileUtils.writeStringToFile(commons, reimaginedConfig, "UTF-8");
            return;
        }

        // Handle both styles
        boolean isReimagined = patchedFile.getFileName().toString().contains("Reimagined");
        String otherStyle = isReimagined ? "Unbound" : "Reimagined";
        String currentStyle = isReimagined ? "Reimagined" : "Unbound";

        File otherStyleFile = new File(patchedFile.getParent().toFile(),
                patchedFile.getFileName().toString().replace(currentStyle, otherStyle));

        FileUtils.copyDirectory(patchedFile.toFile(), otherStyleFile);

        // Apply correct config to each file
        if (isReimagined) {
            // Current file is Reimagined, other file is Unbound
            FileUtils.writeStringToFile(commons, reimaginedConfig, "UTF-8");
            FileUtils.writeStringToFile(new File(otherStyleFile, COMMON_LOCATION), unboundConfig, "UTF-8");
        } else {
            // Current file is Unbound, other file is Reimagined
            FileUtils.writeStringToFile(commons, unboundConfig, "UTF-8");
            FileUtils.writeStringToFile(new File(otherStyleFile, COMMON_LOCATION), reimaginedConfig, "UTF-8");
        }
    }

    private void startShaderpacksWatcher() {
        if (shaderpacksWatcher != null && shaderpacksWatcher.isRunning()) return;

        shaderpacksWatcher = ShaderpacksWatcher.createAndStart(this);
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
            Path temp = createTempDirectory();
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
                String detectedStyle = detectStyleFromCommonFile(baseFile);
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

    private void createAlternativeShaderNames(Path patchedShaderPath) {
        if (alternativeShaderNames.isEmpty()) {
            debugLog("No alternative shader names configured, skipping creation.");
            return; // No alternative names to create
        }

        log(0, "Creating alternative shader names from: " + patchedShaderPath.getFileName());

        String baseVersion = VERSION.replace("_", "");
        String patchVersion = PATCH_VERSION.replace("_", "");

        // Define illegal characters for file/folder names on most OSes
        String illegalChars = "[\\\\/:*?\"<>|]";

        // Split the names by comma
        String[] alternativeNames = alternativeShaderNames.split(",");

        for (String name : alternativeNames) {
            String trimmedName = name.trim();

            if (trimmedName.isEmpty()) {
                continue; // Skip empty names
            }

            // Replace placeholders with actual version values
            String finalName = trimmedName
                    .replace("{baseVersion}", baseVersion)
                    .replace("{patchVersion}", patchVersion);

            // Check for illegal characters
            if (finalName.matches(".*" + illegalChars + ".*")) {
                log(2, "Skipping alternative shader name with illegal characters: \"" + finalName + "\"");
                continue;
            }

            // Create a copy with this alternative name
            createShaderCopy(patchedShaderPath, finalName);
        }
    }

    private void createShaderCopy(Path sourceShaderPath, String newName) {
        try {
            // Get the parent directory (shaderpacks folder)
            Path shaderpacks = sourceShaderPath.getParent();
            
            // Create the new path with the alternative name
            Path targetPath = shaderpacks.resolve(newName);
            
            // Check if it already exists
            if (Files.exists(targetPath)) {
                // Check if it's an outdated version by examining myFile.glsl
                Path myFilePath = targetPath.resolve(SHADER_MYFILE_LOCATION);
                
                if (Files.exists(myFilePath)) {
                    // Read first line of the file to extract version
                    String firstLine;
                    try (BufferedReader reader = Files.newBufferedReader(myFilePath)) {
                        firstLine = reader.readLine();
                    }
                    
                    // Check if it's an Euphoria Patches file with a different version
                    if (firstLine != null && firstLine.startsWith("// Euphoria Patches")) {
                        String fileVersion = firstLine.replace("// Euphoria Patches ", "").trim();
                        String expectedVersion = PATCH_VERSION.replace("_", "");
                        
                        if (!fileVersion.equals(expectedVersion)) {
                            debugLog("Found outdated alternative shader \"" + newName + "\" (version " + fileVersion + "), updating to " + expectedVersion);
                            // Delete outdated version
                            UsefulFunctions.deleteRecursively(targetPath);
                        } else {
                            // Version is current, skip creation
                            debugLog("Skipping creation of alternative shader name \"" + newName + "\" as it already exists with current version.");
                            return;
                        }
                    } else {
                        // Not an Euphoria Patches file or can't determine version
                        debugLog("Found existing shader with name \"" + newName + "\" but couldn't verify version, replacing it.");
                        UsefulFunctions.deleteRecursively(targetPath);
                    }
                } else {
                    // myFile.glsl doesn't exist, assume not an Euphoria shader or corrupted
                    debugLog("Found existing shader with name \"" + newName + "\" but it doesn't appear to be a valid Euphoria shader, replacing it.");
                    UsefulFunctions.deleteRecursively(targetPath);
                }
            }
            
            // Copy the directory
            debugLog("Creating alternative shader with name: \"" + newName + "\"");
            FileUtils.copyDirectory(sourceShaderPath.toFile(), targetPath.toFile());
            
            log(0, "Successfully created alternative shader: \"" + newName + "\"");
        } catch (IOException e) {
            log(2, "Error creating alternative shader \"" + newName + "\": " + e.getMessage());
        }
    }

    // Helper class to store shader information
    private static class ShaderInfo {
        Path baseFile = null;
        Path installedDir = null;
        boolean styleReimagined = false;
        boolean styleUnbound = false;
        boolean isAlreadyInstalled = false;
    }
}
