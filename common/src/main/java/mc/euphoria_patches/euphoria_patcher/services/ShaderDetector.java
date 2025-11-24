package mc.euphoria_patches.euphoria_patcher.services;

import mc.euphoria_patches.euphoria_patcher.util.ArchiveOperations;
import mc.euphoria_patches.euphoria_patcher.util.EuphoriaLogger;
import org.apache.commons.io.FileUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
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

    private int filesScannedCounter = 0;
    private int totalFilesToScan = 0;

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
            // First check using the standard naming pattern
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
                                String detectedStyle = detectStyleFromCommonFile(directory);
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

            // First check ZIP files (using our filtered list)
            for (Path zipFile : zipFiles) {
                if (isValidShaderByByteSize(zipFile)) {
                    // Found a valid shader by byte size, rename it to the correct format
                    return namingService.renameToCorrectShaderName(zipFile);
                }
            }

            // Then check directories (using our filtered list)
            for (Path dir : dirs) {
                if (isValidShaderByByteSize(dir)) {
                    // Found a valid shader by byte size, rename it to the correct format
                    return namingService.renameToCorrectShaderName(dir);
                }
            }
        } catch (IOException e) {
            log(3, "Error searching for shaders by byte size: " + e.getMessage());
        }
        return null;
    }

    /**
     * Check if shader is valid by byte size
     */
    public boolean isValidShaderByByteSize(Path path) {
        try {
            // Increment counter and show progress message every 5 files
            filesScannedCounter++;
            if (filesScannedCounter % 5 == 0) {
                log(2, 0, "Please wait... Scanned " + filesScannedCounter + " of " + totalFilesToScan + " files so far");
            }
            
            debugLog("Checking if shader is valid by byte size (" + filesScannedCounter + "/" + totalFilesToScan + "): " + path.getFileName());
            
            Path tempDir = ArchiveOperations.createTempDirectory();
            if (tempDir == null) {
                debugLog("Failed to create temp directory for byte size check");
                return false;
            }
            debugLog("Created temp directory: " + tempDir);

            String baseName = path.getFileName().toString().replace(".zip", "");
            debugLog("Base name for extraction: " + baseName);

            // Extract if it's a zip file
            Path baseExtracted = tempDir.resolve(baseName);
            baseExtracted = ArchiveOperations.extract(path, baseExtracted, "extracting archive");
            if (baseExtracted == null) {
                debugLog("Failed to extract base for byte size check");
                return false;
            }
            debugLog("Successfully extracted to: " + baseExtracted);

            // Archive for byte size comparison
            Path baseArchived = tempDir.resolve(baseName + ".tar");
            baseArchived = ArchiveOperations.archive(baseExtracted, baseArchived);
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
            return false;
        }
    }

    /**
     * Determines shader style by reading the common.glsl file
     * @param shaderPath Path to the shader file or directory
     * @return "Reimagined" or "Unbound" based on the SHADER_STYLE value
     */
    public String detectStyleFromCommonFile(Path shaderPath) {
        Path tempDir = null;
        try {
            // Create temp directory
            tempDir = ArchiveOperations.createTempDirectory();
            if (tempDir == null) return "Reimagined"; // Default if we can't create temp dir
            
            String baseName = shaderPath.getFileName().toString().replace(".zip", "");
            
            // Extract if needed
            Path extractedPath;
            if (shaderPath.toString().endsWith(".zip")) {
                extractedPath = ArchiveOperations.extract(shaderPath, tempDir.resolve(baseName), "extracting archive");
                if (extractedPath == null) return "Reimagined";
            } else {
                extractedPath = shaderPath;
            }
            
            // Read the common.glsl file
            Path commonFile = extractedPath.resolve(commonLocation);
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
        mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher.log(level, message);
    }

    @SuppressWarnings("SameParameterValue")
    private void log(int level, int fadeTimer, String message) {
        mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher.log(level, fadeTimer, message);
    }

    private void debugLog(String message) {
        EuphoriaLogger.debugLog("[ShaderDetector] " + message);
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
