package com.euphoriapatches.euphoria_patcher.services;

import com.euphoriapatches.euphoria_patcher.util.ArchiveOperations;
import com.euphoriapatches.euphoria_patcher.util.ShaderPropertyReader;
import com.euphoriapatches.euphoria_patcher.util.VersionComparator;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import org.apache.commons.io.FileUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Handles detection of installed shaders and their properties
 */
public class ShaderDetector {
    private final String brandName;
    private final String patchName;
    private final String version;
    private final String patchVersion;
    private final String commonLocation;
    private final String shaderMyFileLocation;
    private final Path shaderpacks;
    private final ShaderNamingService namingService;
    private final ShaderValidator shaderValidator;

    private int filesScannedCounter = 0;
    private int totalFilesToScan = 0;

    private String buildDateStr = null;
    private Integer currentBuildDate = null;

    public ShaderDetector(String brandName, String patchName, String version, String patchVersion,
                         String commonLocation, String shaderMyFileLocation, Path shaderpacks) {
        this.brandName = brandName;
        this.patchName = patchName;
        this.version = version;
        this.patchVersion = patchVersion;
        this.commonLocation = commonLocation;
        this.shaderMyFileLocation = shaderMyFileLocation;
        this.shaderpacks = shaderpacks;
        // Circular dependency will be resolved by setter
        this.namingService = null;
        this.shaderValidator = new ShaderValidator();
    }

    /**
     * Detect installed shaders
     */
    public ShaderInfo detectInstalledShaders(ShaderNamingService namingService) {
        ShaderInfo info = new ShaderInfo();
        try {
            // Check if patched shaders already exist
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
                processShaderPath(path, info, namingService);
                if (info.styleReimagined && info.styleUnbound) break;
            }

            // If no valid shader found by name, try using byte size verification
            if (info.baseFile == null) {
                log(2, 0, "No shaders with expected name pattern found, checking via byte size...");
                log(2, 0, "If you have a lot of shaders installed, this may take a while. Please be patient.");
                log(2, 0, "Please wait... \n");
                Path shaderByByteSize = findShaderByByteSize(namingService);
                if (shaderByByteSize != null) {
                    log(0, "Found valid shader by byte size: " + shaderByByteSize.getFileName());
                    // Determine shader style from path or assume default
                    String name = shaderByByteSize.getFileName().toString();
                    info.styleReimagined = name.contains("Reimagined") || !name.contains("Unbound");
                    info.styleUnbound = name.contains("Unbound");
                    info.baseFile = shaderByByteSize;
                    checkIfAlreadyInstalled(shaderByByteSize, info, namingService);
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
    public void checkForExistingPatchedShaders(ShaderInfo info) {
        try {
            // First check for newer dev versions
            if (checkForNewerDevVersion(info)) {
                return;
            }

            // Then check using the standard naming pattern
            DirectoryStream.Filter<Path> patchedFilter = path ->
                path.getFileName().toString().contains(brandName) &&
                path.getFileName().toString().contains(" + " + patchName + patchVersion) &&
                (Files.isDirectory(path) ||
                (Files.isRegularFile(path) && path.toString().endsWith(".zip")));

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks, patchedFilter)) {
                for (Path path : stream) {
                    checkIfAlreadyInstalled(path, info, null);
                    if (info.isAlreadyInstalled) {
                        return;
                    }
                }
            }

            debugLog("No existing patched shaders found by standard naming pattern, checking for Euphoria Patches files...");

            // If not found by name, check all directories for the myFile.glsl with version signature
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks, Files::isDirectory)) {

                if (info.isAlreadyInstalled) {
                    return;
                }

                for (Path directory : stream) {
                    Path myFilePath = directory.resolve(shaderMyFileLocation);

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

                    // Check if it's a Euphoria Patches file with the matching version
                    if (firstLine != null && firstLine.startsWith("// Euphoria Patches")) {
                        String fileVersion = firstLine.replace("// Euphoria Patches ", "").trim();
                        String expectedVersion = patchVersion.replace("_", "");

                        debugLog("Found potential correct Euphoria Patches version in: " + directory.getFileName());
                        debugLog("File version: " + fileVersion + ", Expected: " + expectedVersion);

                        if (fileVersion.equals(expectedVersion)) {
                            String dirName = directory.getFileName().toString();
                            if (dirName.equals(("Euphoria-Patches")) || dirName.matches("dev\\d+") || dirName.contains("earlyDev")) {
                                debugLog("Skipping dev Euphoria-Patches versions");
                                continue;
                            }
                            debugLog("Version match found - this is a correct Euphoria Patches installation");

                            info.isAlreadyInstalled = true;
                            info.installedDir = directory;

                            // Try to determine style from directory name or common.glsl
                            if (dirName.contains("Reimagined")) {
                                info.styleReimagined = true;
                            } else if (dirName.contains("Unbound")) {
                                info.styleUnbound = true;
                            } else {
                                // If not clear from directory name, check common.glsl
                                String detectedStyle = ShaderPropertyReader.detectStyleFromCommonFile(directory, commonLocation);
                                info.styleReimagined = "Reimagined".equals(detectedStyle);
                                info.styleUnbound = "Unbound".equals(detectedStyle);
                            }

                            log(0, patchName + patchVersion + " is already installed as the renamed folder: " + directory.getFileName());
                            return;
                        }
                    }
                }
            }
        } catch (IOException e) {
            log(3, "Error checking for existing patched shaders: " + e.getMessage());
        }
    }

    /**
     * Check if a path matches the brand name shader pattern
     */
    public boolean isBrandNameShader(Path path, boolean isFile) {
        String name = path.getFileName().toString();

        // Basic conditions
        boolean hasBrandName = name.startsWith(brandName);
        boolean notPatched = !name.contains(patchName);
        boolean hasExactVersion = name.contains(version);
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

    /**
     * Find shader by byte size verification
     */
    public Path findShaderByByteSize(ShaderNamingService namingService) {
        try {
            // Reset counter at the start of a new scan
            resetFilesScannedCounter();

            // Collect files and directories, filtering out well-known popular shader names to save time
            List<Path> zipFiles = new ArrayList<>();
            List<Path> dirs = new ArrayList<>();
            int skippedCount = 0;

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks)) {
                for (Path p : stream) {
                    boolean isZip = Files.isRegularFile(p) && p.toString().endsWith(".zip");
                    boolean isDir = Files.isDirectory(p);

                    if (!isZip && !isDir) continue;

                    if (isPopularShaderName(p)) {
                        skippedCount++;
                        continue;
                    }

                    if (isZip) {
                        zipFiles.add(p);
                    } else {
                        dirs.add(p);
                    }
                }
            }

            int zipFileCount = zipFiles.size();
            int dirCount = dirs.size();
            totalFilesToScan = zipFileCount + dirCount;
            debugLog("Total files to scan: " + totalFilesToScan + " (" + zipFileCount + " ZIP files, " + dirCount + " directories) - skipped " + skippedCount + " popular shaders");

            // Combine all paths for parallel processing
            List<Path> allPaths = new ArrayList<>(zipFiles);
            allPaths.addAll(dirs);

            // Use parallel validation with progress callback
            Path validShader = shaderValidator.validateByByteSizeParallel(allPaths,
                (scanned, total) -> {
                    log(2, 0, "Please wait... Scanned " + scanned + " of " + total + " files so far");
                }
            );

            if (validShader != null) {
                // Found a valid shader by byte size, rename it to the correct format
                return namingService.renameToCorrectShaderName(validShader);
            }
        } catch (IOException e) {
            log(3, "Error searching for shaders by byte size: " + e.getMessage());
        }
        return null;
    }

    /**
     * Process shader path to determine style and check installation
     */
    public void processShaderPath(Path path, ShaderInfo info, ShaderNamingService namingService) {
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
            String detectedStyle = ShaderPropertyReader.detectStyleFromCommonFile(path, commonLocation);
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

        checkIfAlreadyInstalled(path, info, namingService);
    }

    /**
     * Check if the patch is already installed
     */
    public void checkIfAlreadyInstalled(Path path, ShaderInfo info, ShaderNamingService namingService) {
        Path potentialInstallPath;
        boolean isDirectPatchedDir = path.getFileName().toString().contains(" + " + patchName + patchVersion);

        if (isDirectPatchedDir) {
            // This is already a patched shader directory
            potentialInstallPath = path;

            // Try to reconstruct the base file name
            String name = path.getFileName().toString();
            String baseName = name.substring(0, name.indexOf(" + " + patchName + patchVersion));
            Path potentialBaseZip = shaderpacks.resolve(baseName + ".zip");

            // Set shader styles based on directory name
            info.styleReimagined = name.contains("Reimagined");
            info.styleUnbound = name.contains("Unbound");

            if (Files.exists(potentialBaseZip)) {
                info.baseFile = potentialBaseZip;
            }
        } else {
            // This is a base shader file
            if (namingService != null) {
                potentialInstallPath = namingService.getPatchedShaderPath(path);
            } else {
                // Fallback if namingService is not available
                potentialInstallPath = null;
            }
            if (info.baseFile == null) {
                info.baseFile = path;
            }
        }

        // Skip check in certain situations
        if (info.isAlreadyInstalled || potentialInstallPath == null) {
            return;
        }

        // If the patched directory exists, check if it contains EuphoriaPatches files
        if (Files.exists(potentialInstallPath)) {
            verifyEuphoriaInstallation(potentialInstallPath, info);
        }
    }

    /**
     * Check if directory has Euphoria file
     */
    public boolean hasEuphoriaFile(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .anyMatch(p -> p.getFileName().toString().contains("EuphoriaPatches"));
        }
    }

    /**
     * Verify Euphoria installation
     */
    public void verifyEuphoriaInstallation(Path potentialInstallPath, ShaderInfo info) {
        if (info.isAlreadyInstalled || potentialInstallPath == null) {
            return;
        }

        try {
            boolean containsEuphoria = hasEuphoriaFile(potentialInstallPath);

            if (containsEuphoria) {
                info.isAlreadyInstalled = true;
                info.installedDir = potentialInstallPath;
                log(0, patchName + patchVersion + " is already installed.");
            } else {
                log(0, "Found incomplete installation. Cleaning up " + potentialInstallPath.getFileName());
                ArchiveOperations.deleteRecursively(potentialInstallPath);
                info.isAlreadyInstalled = false;
            }

        } catch (IOException e) {
            log(3, "Error checking installation status. Cleaning up: " + e.getMessage());
            try {
                ArchiveOperations.deleteRecursively(potentialInstallPath);
            } catch (IOException ex) {
                log(3, "Error deleting directory: " + ex.getMessage());
            }
            info.isAlreadyInstalled = false;
        }
    }

    /**
     * Check if a given path represents a newer dev version
     * Supports two formats:
     * 1. Comp.*EuphoriaPatches_(version)-dev(number).zip
     * 2. EuphoriaPatches_earlyDev_(yyyy-mm-dd).zip
     * @param path The path to check
     * @param info Optional ShaderInfo to update if a newer dev version is found (can be null)
     * @return true if this is a newer dev version
     */
    public boolean isNewerDevVersion(Path path, ShaderInfo info) {
        if (!Files.isRegularFile(path)) {
            return false;
        }

        String fileName = path.getFileName().toString();
        Pattern numberedDevPattern = Pattern.compile("Comp.*EuphoriaPatches_(\\d+\\.\\d+\\.\\d+)-dev\\d+\\.zip");
        Pattern earlyDevPattern = Pattern.compile("EuphoriaPatches_earlyDev_(\\d{4}-\\d{2}-\\d{2})\\.zip");

        String currentVersion = patchVersion.replace("_", "");

        // Initialize build date information only once
        if (buildDateStr == null) {
            buildDateStr = com.euphoriapatches.euphoria_patcher.util.JsonUtilReader.getString("buildDate");
            currentBuildDate = parseBuildDate(buildDateStr);
        }

        // Check numbered dev version
        if (checkNumberedDevVersion(fileName, numberedDevPattern, currentVersion)) {
            if (info != null) {
                info.isAlreadyInstalled = true;
                info.installedDir = path;
                String devVersion = fileName.replaceAll(".*EuphoriaPatches_(\\d+\\.\\d+\\.\\d+)-dev\\d+\\.zip", "$1");
                log(0, "Your dev version: " + devVersion + " vs current public release version: " + currentVersion);
                log(0, "Your dev version is more recent than the last public release. Enjoy!");
                log(0, "Thanks for the support! <3");
            }
            return true;
        }

        // Check early dev version
        if (checkEarlyDevVersion(fileName, earlyDevPattern, currentBuildDate)) {
            if (info != null) {
                info.isAlreadyInstalled = true;
                info.installedDir = path;
                String devDateStr = fileName.replaceAll(".*EuphoriaPatches_earlyDev_(\\d{4}-\\d{2}-\\d{2})\\.zip", "$1");
                log(0, "Your earlyDev version date: " + devDateStr + " vs current build date: " + buildDateStr);
                log(0, "Your earlyDev version is more recent than the last public release. Enjoy!");
                log(0, "Thanks for the support! <3");
            }
            return true;
        }

        return false;
    }

    /**
     * Convenience method to check if a path is a newer dev version without updating ShaderInfo
     * @param path The path to check
     * @return true if this is a newer dev version
     */
    public boolean isNewerDevVersion(Path path) {
        return isNewerDevVersion(path, null);
    }

    /**
     * Check for newer dev versions of pre-patched shaders
     * Supports two formats:
     * 1. Comp.*EuphoriaPatches_(version)-dev(number).zip
     * 2. EuphoriaPatches_earlyDev_(yyyy-mm-dd).zip
     */
    private boolean checkForNewerDevVersion(ShaderInfo info) {
        try {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks)) {
                for (Path path : stream) {
                    if (!Files.isRegularFile(path)) {
                        continue;
                    }

                    // Use the consolidated isNewerDevVersion method
                    if (isNewerDevVersion(path, info)) {
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            debugLog("Error checking for dev versions: " + e.getMessage());
        }
        return false;
    }

    public Integer parseBuildDate(String buildDateStr) {
        if (buildDateStr != null && !buildDateStr.equals("year-month-day")) {
            try {
                Integer date = Integer.parseInt(buildDateStr.replace("-", ""));
                debugLog("Current build date: " + buildDateStr + " (" + date + ")");
                return date;
            } catch (NumberFormatException e) {
                debugLog("Could not parse build date: " + buildDateStr);
            }
        }
        return null;
    }

    /**
     * Check if filename matches numbered dev version pattern and is newer
     * @return true if this is a newer numbered dev version
     */
    private boolean checkNumberedDevVersion(String fileName, Pattern pattern, String currentVersion) {
        Matcher matcher = pattern.matcher(fileName);
        if (matcher.matches()) {
            String devVersion = matcher.group(1);
            debugLog("Found numbered dev version file: " + fileName + " with version: " + devVersion);

            int comparison = VersionComparator.compareVersionStrings(devVersion, currentVersion);

            if (comparison > 0) {
                debugLog("Dev version " + devVersion + " is newer than current patch version " + currentVersion);
                return true;
            } else {
                debugLog("Dev version " + devVersion + " is older or equal to current patch version " + currentVersion);
            }
        }
        return false;
    }

    /**
     * Check if filename matches early dev version pattern and is newer
     * @return true if this is a newer early dev version
     */
    private boolean checkEarlyDevVersion(String fileName, Pattern pattern, Integer currentBuildDate) {
        Matcher matcher = pattern.matcher(fileName);
        if (matcher.matches() && currentBuildDate != null) {
            String devDateStr = matcher.group(1);
            debugLog("Found earlyDev file: " + fileName + " with date: " + devDateStr);

            try {
                int devDate = Integer.parseInt(devDateStr.replace("-", ""));
                debugLog("Comparing dates: earlyDev=" + devDate + " vs current=" + currentBuildDate);

                if (devDate > currentBuildDate) {
                    debugLog("EarlyDev date " + devDateStr + " is newer than current build date");
                    return true;
                } else {
                    debugLog("EarlyDev date " + devDateStr + " is older or equal to current build date");
                }
            } catch (NumberFormatException e) {
                debugLog("Could not parse earlyDev date: " + devDateStr);
            }
        }
        return false;
    }

    // Helper methods
    private void resetFilesScannedCounter() {
        filesScannedCounter = 0;
        totalFilesToScan = 0;
        debugLog("Reset files scanned counter");
    }

    private boolean isPopularShaderName(Path path) {
        try {
            String nameLower = path.getFileName().toString().toLowerCase(Locale.ROOT);

            List<String> popularPatterns = Arrays.asList(
                    ".*bsl_v\\d\\..*",
                    ".*sildur's.*",
                    ".*spooklementary.*",
                    ".*pixelcraftshaders_.*",
                    "ep_earlyDev_\\d+.*",
                    "outdated complementary.*_r\\d.*ep.*",
                    "comp\\d.*ep_\\d+.*",
                    ".*photon_v\\d.*",
                    ".*hysteria-shaders.*",
                    "rethinking-voxels_r\\d.*",
                    "solas shader v\\d.*",
                    "superdupervanilla.*",
                    "insanity-shader.*",
                    ".*(bliss_v\\d|bliss-shader).*",
                    ".*\\b(continuum)\\b.*",
                    ".*(chocapic|chocapic13).*",
                    ".*astra.*lex.*"
            );

            for (String regex : popularPatterns) {
                if (nameLower.matches(regex)) {
                    debugLog("Skipping popular shader name during byte-size scan: " + path.getFileName() + " (matches " + regex + ")");
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void log(int level, String message) {
        com.euphoriapatches.euphoria_patcher.EuphoriaPatcher.log(level, message);
    }

    @SuppressWarnings("SameParameterValue")
    private void log(int level, int fadeTimer, String message) {
        com.euphoriapatches.euphoria_patcher.EuphoriaPatcher.log(level, fadeTimer, message);
    }

    private void debugLog(String message) {
        EuphoriaLogger.debugLog("[ShaderDetector] " + message);
    }

    /**
     * Find the patched shader directory directly
     * Used when baseFile is null but we need to find the installed shader
     */
    public Path findPatchedShaderDirectory() {
        try {
            DirectoryStream.Filter<Path> filter = path ->
                (Files.isDirectory(path) ||
                (Files.isRegularFile(path) && path.toString().endsWith(".zip"))) &&
                path.getFileName().toString().contains(brandName) &&
                path.getFileName().toString().contains(" + " + patchName + patchVersion);

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks, filter)) {
                for (Path path : stream) {
                    debugLog("Found patched shader directory: " + path.getFileName());
                    return path;
                }
            }
        } catch (IOException e) {
            debugLog("Error finding patched shader directory: " + e.getMessage());
        }
        return null;
    }

    /**
     * Helper class to store shader information
     */
    public static class ShaderInfo {
        public Path baseFile = null;
        public Path installedDir = null;
        public boolean styleReimagined = false;
        public boolean styleUnbound = false;
        public boolean isAlreadyInstalled = false;
    }
}
