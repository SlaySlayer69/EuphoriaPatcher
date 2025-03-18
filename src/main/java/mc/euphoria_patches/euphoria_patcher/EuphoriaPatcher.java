package mc.euphoria_patches.euphoria_patcher;

import mc.euphoria_patches.euphoria_patcher.features.*;
import mc.euphoria_patches.euphoria_patcher.util.*;

import io.sigpipe.jbsdiff.InvalidHeaderException;
import io.sigpipe.jbsdiff.ui.FileUI;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class EuphoriaPatcher {

    private static final boolean IS_DEV = false; // Manual Boolean. DON'T FORGET TO SET TO FALSE BEFORE COMPILING
    private static final boolean isDevModLoader = ModLoaderSpecifics.isDevMode;

    public static final String BRAND_NAME = "Complementary";
    public static final String PATCH_NAME = "EuphoriaPatches";
    public static final String VERSION = "_r5.4";
    public static final String PATCH_VERSION = "_1.5.2";

    private static final String BASE_TAR_HASH = "d2c7b2d30a992623e6b23cdecf7f997b";
    private static final int BASE_TAR_SIZE = 1328640;

    public static final String DOWNLOAD_URL = "https://www.complementary.dev/";
    public static final String COMMON_LOCATION = "shaders/lib/common.glsl";
    public static final String LANG_LOCATION = "shaders/lang";
    public static final String SHADERS_PROPERTIES_LOCATION = "shaders/shaders.properties";
    public static final String SHADER_MYFILE_LOCATION = "shaders/lib/myFile.glsl";

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

    // Global Variables and Objects
    public static Logger LOGGER = LogManager.getLogger("euphoriaPatches");
    public static boolean isSodiumInstalled = false;
    private static boolean ALREADY_LAUNCHED = false;
    private static boolean IS_BASE_MESSAGE_SHOWN = false;

    public EuphoriaPatcher() {
        if (ALREADY_LAUNCHED) {
            return;
        }
        ALREADY_LAUNCHED = true;
        System.out.println("\nEuphoria Patcher:");
        if (ModFolderVersionChecker.existsNewerModInFolder()) return;
        configStuff();

        if(doPopUpLogging) isSodiumInstalled();

        if(doUpdateChecking) UpdateChecker.checkForUpdates();

        log(0, JsonUtilReader.getRandomMessage("startupMessages"));

        // Detect installed Complementary Shaders versions
        ShaderInfo shaderInfo = detectInstalledShaders();

        if(!shaderInfo.isAlreadyInstalled) {
            if (shaderInfo.baseFile == null){
                installBaseMessage();
                if(!isDevFunc()) return;
            }
        } else {
            thankYouMessage(shaderInfo.baseFile, shaderInfo.styleUnbound, shaderInfo.styleReimagined);
            return;
        }

        // Create temporary directory
        Path temp = createTempDirectory();
        if (temp == null || shaderInfo.baseFile == null && !isDevFunc()) return;

        // Process and patch shaders
        if (!processAndPatchShaders(shaderInfo, temp)) return;

        // Update .txt shader config file
        UpdateShaderConfig.updateShaderTxtConfigFile(shaderInfo.styleUnbound, shaderInfo.styleReimagined);

        // Update shader loader (iris) config
        UpdateShaderLoaderConfig.updateShaderLoaderConfig(shaderInfo.styleUnbound, shaderInfo.styleReimagined);

        if(doDeleteOldShaderFiles) ModifyOutdatedPatches.delete();
        if(doRenameOldShaderFiles) ModifyOutdatedPatches.rename();

        thankYouMessage(shaderInfo.baseFile, shaderInfo.styleUnbound, shaderInfo.styleReimagined);
    }

    private void configStuff(){
        // How to use: Cast to desired data type, then call readWriteConfig, it returns a String.
        // First parameter is the config name, second is the value
        // Third one is the description, it can either be null or a String, supports multi line descriptions with "\n"
        doPopUpLogging = Boolean.parseBoolean(Config.readWriteConfig("doPopUpLogging", "true","Option for the sodium message popup logging." +
                "\nDefault = true"));
        doUpdateChecking = Boolean.parseBoolean(Config.readWriteConfig("doUpdateChecking", "true","Option that enables or disables the update checker, which verifies if a new version of the mod is available." +
                "\nMore info here: https://github.com/EuphoriaPatches/PatcherUpdateChecker" +
                "\nDefault = true"));
        doRenameOldShaderFiles = Boolean.parseBoolean(Config.readWriteConfig("doRenameOldShaderFiles", "true","Option that automatically renames outdated Euphoria Patches folders and config files to a new name." +
                "\nThis makes it easier for users to identify which ones are outdated." +
                "\nDefault = true"));
        doDeleteOldShaderFiles = Boolean.parseBoolean(Config.readWriteConfig("doDeleteOldShaderFiles", "false","Option that automatically deleted outdated Euphoria Patches folders and config files." +
                "\nDefault = false"));
    }

    private void isSodiumInstalled() {
        String sodiumVersion = "me.jellysquid.mods.sodium.client.gui.console.Console"; // "net.caffeinemc.mods.sodium.client.console.Console" // Newer Sodium versions // Crashes the game if used - import classes are different in SodiumConsole.java
        try {
            Class.forName(sodiumVersion);
            log(0, "Sodium found, using Sodium logging!");
            isSodiumInstalled = true;
        } catch (ClassNotFoundException ignored) {
        }
    }

    // Logging method
    public static void log(int messageLevel, int messageFadeTimer, String message) {
        String loggingMessage = "EuphoriaPatcher: " + message;
        if (messageLevel == -1) loggingMessage = "\n\n" + loggingMessage + "\n";
        if (isSodiumInstalled && messageFadeTimer > 0) {
            SodiumConsole.logMessage(messageLevel, messageFadeTimer, loggingMessage);
        }
        switch (messageLevel) {
            case -1:
            case 0:
            case 1:
                LOGGER.info(loggingMessage);
                break;
            case 2:
                LOGGER.warn(loggingMessage);
                break;
            case 3:
                LOGGER.error(loggingMessage);
                break;
            default:
                System.out.println(loggingMessage);
                break;
        }
    }
    public static void log(int messageLevel, String message) { // Method overloading for optional parameter
        int messageFadeTimer = 0;
        switch (messageLevel) {
            case 1:
                messageFadeTimer = 4;
                break;
            case 2:
                messageFadeTimer = 8;
                break;
            case 3:
                messageFadeTimer = 16;
                break;
        }
        log(messageLevel, messageFadeTimer, message);
    }

    public static boolean isDevFunc() {
        return IS_DEV && isDevModLoader;
    }

    // Detect installed Complementary Shaders versions
    private ShaderInfo detectInstalledShaders() {
        ShaderInfo info = new ShaderInfo();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks, this::isComplementaryShader)) {
            for (Path potentialFile : stream) {
                processShaderFile(potentialFile, info);
                if (info.styleReimagined && info.styleUnbound) break;
            }
            if (!info.styleReimagined && !info.styleUnbound) {
                detectInstalledDirectories(info);
            }
        } catch (IOException e) {
            log(3, "Error reading shaderpacks directory: " + e.getMessage());
        }
        return info;
    }

    // Helper method to check if a file is a Complementary Shader
    private boolean isComplementaryShader(Path path) {
        String name = path.getFileName().toString();
        return name.matches(BRAND_NAME + ".*" + VERSION + ".*") && name.endsWith(".zip") && !name.contains(PATCH_NAME);
    }

    // Process each shader file
    private void processShaderFile(Path file, ShaderInfo info) {
        String name = file.getFileName().toString();
        if (name.contains("Reimagined")) {
            info.styleReimagined = true;
            if (info.baseFile == null) {
                info.baseFile = file;
            }
        } else if (name.contains("Unbound")) {
            info.styleUnbound = true;
            if (info.baseFile == null) {
                info.baseFile = file;
            }
        }
        checkIfAlreadyInstalled(file, info);
    }

    // Check if the patch is already installed
    private void checkIfAlreadyInstalled(Path file, ShaderInfo info) {
        Path potentialInstallPath = getPatchedShaderPath(file);

        if (info.baseFile != null && Files.exists(potentialInstallPath) && !isDevFunc() && !info.isAlreadyInstalled) {
            // Check if any file containing "EuphoriaPatches" exists in the directory
            try {
                boolean containsEuphoriaFile = Files.walk(potentialInstallPath)
                        .filter(Files::isRegularFile)
                        .anyMatch(p -> p.getFileName().toString().contains("EuphoriaPatches"));

                if (!containsEuphoriaFile) {
                    // No EuphoriaPatches file found, delete the directory
                    log(0, "Found incomplete installation. Cleaning up " + potentialInstallPath.getFileName());
                    UsefulFunctions.deleteRecursively(potentialInstallPath);
                    info.isAlreadyInstalled = false;
                } else {
                    info.isAlreadyInstalled = true;
                    log(0, PATCH_NAME + PATCH_VERSION + " is already installed.");
                }
            } catch (IOException e) {
                log(3, "Error checking installation status. Cleaning up: " + e.getMessage());
                try {UsefulFunctions.deleteRecursively(potentialInstallPath);} catch (IOException ex) {log(3, "Error deleting directory: " + ex.getMessage());}
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
            return baseFile.resolveSibling(baseName + " + " + PATCH_NAME + PATCH_VERSION);
        } catch (Exception e) {
            log(3, "Error creating patched shader path: " + e.getMessage());
            return null;
        }
    }

    public static boolean isSpacEagle() {
        try {
            boolean containsSpacEagle = shaderpacks.toString().contains("SpacEagle");
            Path euphoriaFolder = shaderpacks.resolve("Euphoria-Patches");
            boolean hasEuphoriaFolder = Files.exists(euphoriaFolder) && Files.isDirectory(euphoriaFolder);
            return containsSpacEagle && hasEuphoriaFolder;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void thankYouMessage(Path baseFile, boolean styleUnbound, boolean styleReimagined) {
        Path shader = getPatchedShaderPath(baseFile);
        if (UpdateChecker.NEW_VERSION_AVAILABLE && doUpdateChecking && baseFile != null) {
            String newVersionText = "value.info19.0=§c" + PATCH_VERSION.replace("_", "") + " §r->§a " + UpdateChecker.NEW_MOD_VERSION;
            if(ShaderLoader.getShaderLoader().equals(ShaderLoader.OCULUS) || ShaderLoader.getShaderLoader().equals(ShaderLoader.OPTIFINE) && !ShaderLoader.isMinecraftVersionAtLeast("1.21.1")){
                newVersionText = "value.info19.0=§c" + PATCH_VERSION.replace("_", "") + " -> " + UpdateChecker.NEW_MOD_VERSION;
            }
            try {
                ModifyPatchedShaderpacks.modifyFiles(shader, styleUnbound, styleReimagined, SHADERS_PROPERTIES_LOCATION, null, "screen=<empty> <empty>", "screen=info19 info20");
                ModifyPatchedShaderpacks.modifyFiles(shader, styleUnbound, styleReimagined, LANG_LOCATION, ".lang", "value\\.info19\\.0=.*", newVersionText);
            } catch (IOException e) {
                log(3, 0, "Could not modify the shader to show the user that a new version is available" + e.getMessage());
            }
        }
        if (isSpacEagle()) {
            try {
                ModifyPatchedShaderpacks.modifyFiles(shader, styleUnbound, styleReimagined, SHADER_MYFILE_LOCATION, null, "^$", "#define SPACEAGLE17");
            } catch (IOException e) {
                log(3, 0, "Could not modify the shader for SpacEagle17" + e.getMessage());
            }
            log(1, "Have fun developing Euphoria Patches!\n");
        } else {
            log(-1, "Thank you for using Euphoria Patches - SpacEagle17");
        }
    }

    private void installBaseMessage(){
        if (IS_BASE_MESSAGE_SHOWN) return;
        IS_BASE_MESSAGE_SHOWN = true;
        log(3, 8, "You need to have " + BRAND_NAME + "Shaders" + VERSION + " installed!");
        log(3, 8, "Please download it from " + DOWNLOAD_URL + ", place it into your shaderpacks folder and restart Minecraft!");
    }

    // Detect installed directories
    private void detectInstalledDirectories(ShaderInfo info) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks, this::isComplementaryShaderDirectory)) {
            for (Path potentialFile : stream) {
                processShaderDirectory(potentialFile, info);
                if (info.styleReimagined && info.styleUnbound) break;
            }
        }
    }

    // Helper method to check if a directory is a Complementary Shader
    private boolean isComplementaryShaderDirectory(Path path) {
        return path.getFileName().toString().matches(BRAND_NAME + ".*" + VERSION + ".*") && Files.isDirectory(path);
    }

    // Process each shader directory
    private void processShaderDirectory(Path directory, ShaderInfo info) {
        String name = directory.getFileName().toString();
        if (name.contains(PATCH_NAME)) {
            if(name.contains(PATCH_NAME + PATCH_VERSION) && !info.isAlreadyInstalled) {
                info.isAlreadyInstalled = true;
                log(0, PATCH_NAME +  PATCH_VERSION + " is already installed.");
            }
            return;
        }
        if (name.contains("Reimagined")) {
            info.styleReimagined = true;
            if (info.baseFile == null) {
                info.baseFile = directory;
            }
        } else if (name.contains("Unbound")) {
            info.styleUnbound = true;
            if (info.baseFile == null) {
                info.baseFile = directory;
            }
        }
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

    // Process and patch shaders
    private boolean processAndPatchShaders(ShaderInfo info, Path temp) {
        if (info.baseFile == null && !isDevFunc()) {
            installBaseMessage();
            return false;
        }
        String baseName = info.baseFile.getFileName().toString().replace(".zip", "");
        String patchedName = baseName + " + " + PATCH_NAME + PATCH_VERSION;

        Path baseExtracted = extractBase(info.baseFile, temp, baseName);
        if (baseExtracted == null) return false;

        if (!updateCommonFile(baseExtracted)) return false;

        Path baseArchived = archiveBase(baseExtracted, temp, baseName);

        if (!verifyBaseArchive(baseArchived)) return false;

        boolean result = applyPatch(baseArchived, temp, patchedName, info.styleUnbound, info.styleReimagined);

        try {
            log(0, "Cleaning up the temporary directory...");
            FileUtils.deleteDirectory(temp.toFile());
        } catch (IOException e) {
            log(2, "Error cleaning up temporary directory: " + e.getMessage());
        }
        return result;
    }

    // Extract base shader
    private Path extractBase(Path baseFile, Path temp, String baseName) {
        Path baseExtracted = temp.resolve(baseName);
        if (!Files.isDirectory(baseFile)) {
            try {
                ArchiveUtils.extract(baseFile, baseExtracted);
            } catch (IOException | ArchiveException e) {
                log(2, "Error extracting archive: " + e.getMessage());
            }
        } else {
            baseExtracted = baseFile;
        }
        return baseExtracted;
    }

    // Update common file
    private boolean updateCommonFile(Path baseExtracted) {
        try {
            Path commons = baseExtracted.resolve(COMMON_LOCATION);
            String config = FileUtils.readFileToString(commons.toFile(), "UTF-8").replaceFirst("SHADER_STYLE [14]", "SHADER_STYLE 1");
            FileUtils.writeStringToFile(commons.toFile(), config, "UTF-8");
            return true;
        } catch (IOException e) {
            log(3, "Error extracting style information: " + e.getMessage());
            return false;
        }
    }

    // Archive base shader
    private Path archiveBase(Path baseExtracted, Path temp, String baseName) {
        Path baseArchived = temp.resolve(baseName + ".tar");
        try {
            ArchiveUtils.archive(baseExtracted, baseArchived);
        } catch (IOException e) {
            log(2, "Error extracting archive: " + e.getMessage());
            // Handle the error appropriately
        }
        return baseArchived;
    }

    // Verify base archive
    private boolean verifyBaseArchive(Path baseArchived) {
        try {
            if (isDevFunc()) {
                String hash = DigestUtils.md5Hex(Files.newInputStream(baseArchived));
                log(0, "Hash of base: " + hash);
                log(0, FileUtils.sizeOf(baseArchived.toFile()) + " bytes");
            } else {
                String hash = DigestUtils.md5Hex(Arrays.copyOf(Files.readAllBytes(baseArchived), BASE_TAR_SIZE));
                if (!hash.equals(BASE_TAR_HASH)) {
                    log(3, 8, "The shader " + BRAND_NAME + "Shaders" + " that was found in your shaderpacks folder can't be used as a base for " + PATCH_NAME);
                    log(3, 8, "Please download " + BRAND_NAME + "Shaders" + VERSION + " from " + DOWNLOAD_URL + ", place it into your shaderpacks folder and restart Minecraft.");
                    if (baseArchived.getFileName().toString().matches(BRAND_NAME + ".*" + VERSION + ".*")) {
                        log(3, 8, "Correct Shader Version Found. BUT it might have been modified. The expected hash does not match.");
                    } else {
                        log(3, 8, "Incorrect Shader Version found or unexpected error. The expected hash does not match.");
                    }
                    return false;
                }
            }
        } catch (IOException e) {
            log(3, "Something went wrong during the hash verification" + e.getMessage());
            return false;
        }
        return true;
    }

    // Apply patch
    private boolean applyPatch(Path baseArchived, Path temp, String patchedName, boolean styleUnbound, boolean styleReimagined) {
        Path patchedArchive = temp.resolve(patchedName + ".tar");
        Path patchedFile = shaderpacks.resolve(patchedName);
        Path patchFile;
        if (isDevFunc()){
            // All this code to generate the .patch file in the resources directory and a new directory for the patch files
            Path resourcesDir = mainIntellijDir.resolve("src/main/resources");
            Path patchDir = mainIntellijDir.resolve("EuphoriaPatchFiles");
            return devPatchFilePrep(resourcesDir, baseArchived, patchedFile, patchedArchive) &&
                    devPatchFilePrep(patchDir, baseArchived, patchedFile, patchedArchive);
        } else {
            patchFile = temp.resolve(patchedName + ".patch");
            return applyProductionPatch(baseArchived, patchedArchive, patchFile, patchedFile, styleUnbound, styleReimagined);
        }
    }

    private boolean devPatchFilePrep(Path buildDir, Path baseArchived, Path patchedFile, Path patchedArchive){
        checkBuildPath(buildDir);
        Path patchFile = buildDir.resolve(PATCH_NAME + PATCH_VERSION + ".patch");
        return createDevPatch(baseArchived, patchedFile, patchedArchive, patchFile);
    }

    private void checkBuildPath(Path buildDir){
        if (!Files.exists(buildDir)){
            try {
                Files.createDirectories(buildDir);
                log(2,"Build directory created successfully: " + buildDir);
            } catch (IOException e) {
                log(3,"Failed to create directory: " + e.getMessage());
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
        if (styleUnbound) {
            File commons = new File(patchedFile.toFile(), COMMON_LOCATION);
            String unboundConfig = FileUtils.readFileToString(commons, "UTF-8").replaceFirst("SHADER_STYLE 1", "SHADER_STYLE 4");
            if (!styleReimagined) {
                FileUtils.writeStringToFile(commons, unboundConfig, "UTF-8");
            } else if (patchedFile.getFileName().toString().contains("Reimagined")) {
                File unbound = new File(patchedFile.getParent().toFile(), patchedFile.getFileName().toString().replace("Reimagined", "Unbound"));
                FileUtils.copyDirectory(patchedFile.toFile(), unbound);
                FileUtils.writeStringToFile(new File(unbound, COMMON_LOCATION), unboundConfig, "UTF-8");
            } else {
                File reimagined = new File(patchedFile.getParent().toFile(), patchedFile.getFileName().toString().replace("Unbound", "Reimagined"));
                FileUtils.copyDirectory(patchedFile.toFile(), reimagined);
                FileUtils.writeStringToFile(commons, unboundConfig, "UTF-8");
            }
        }
    }

    // Helper class to store shader information
    private static class ShaderInfo {
        Path baseFile = null;
        boolean styleReimagined = false;
        boolean styleUnbound = false;
        boolean isAlreadyInstalled = false;
    }
}
