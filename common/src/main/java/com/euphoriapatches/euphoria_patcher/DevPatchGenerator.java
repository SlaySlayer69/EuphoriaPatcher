package com.euphoriapatches.euphoria_patcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.sigpipe.jbsdiff.ui.FileUI;
import com.euphoriapatches.euphoria_patcher.services.ShaderDetector;
import com.euphoriapatches.euphoria_patcher.targets.ShaderTarget;
import com.euphoriapatches.euphoria_patcher.targets.ShaderTargets;
import com.euphoriapatches.euphoria_patcher.io.ArchiveUtils;
import com.euphoriapatches.euphoria_patcher.util.mod.ModLoaderSpecifics;
import com.euphoriapatches.euphoria_patcher.util.HashUtils;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Standalone patch generator for development purposes.
 * This can be run directly from IntelliJ to generate .patch files without needing to launch Minecraft.
 * Usage:
 * 1. Ensure you have the base shader and patched shader in your shaderpacks folder
 * 2. Run this class directly (it has a main method)
 * 3. The .patch files will be generated in common/src/main/resources and EuphoriaPatchFiles
 */
public class DevPatchGenerator {
    // ANSI escape codes for colored output
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String BLUE = "\u001B[34m";
    private static final String YELLOW = "\u001B[33m";

    // Constants - use PatchInfo directly to avoid EuphoriaPatcher initialization
    private static final String PATCH_NAME = EuphoriaPatcher.PATCH_NAME;

    // Resolved from the selected target in main(); a target bundles brand name, base version and
    // everything else that used to be hardcoded to Complementary Shaders.
    private static ShaderTarget target = ShaderTargets.defaultTarget();
    private static String BRAND_NAME = target.getBrandName();
    private static String VERSION = target.getBaseVersion();
    private static String PATCH_VERSION = target.getPatchVersion();
    private static String MODRINTH_PROJECT_ID = target.getModrinthProjectId();

    // Optional explicit inputs, see printUsage()
    private static Path explicitBase = null;
    private static Path explicitPatched = null;

    // Modrinth API constants
    private static final String MODRINTH_API_BASE = "https://api.modrinth.com/v2";
    private static final String USER_AGENT = "EuphoriaPatcher-DevPatchGenerator/1.0";

    // Get the IntelliJ project base directory
    private static final Path INTELLIJ_BASE_DIR = Paths.get("").toAbsolutePath();

    // Derive paths from the base directory
    private static final Path RESOURCES_DIR = INTELLIJ_BASE_DIR.resolve("common/src/main/resources");
    private static final Path PATCH_FILES_DIR = INTELLIJ_BASE_DIR.resolve("EuphoriaPatchFiles");
    private static final Path SHADERPACKS_DIR = INTELLIJ_BASE_DIR.resolve("shaderpacks");

    public static void main(String[] args) {
        System.out.println(BLUE + "=== Euphoria Patches - Dev Patch Generator ===" + RESET);
        System.out.println("IntelliJ Base Directory: " + BLUE + INTELLIJ_BASE_DIR + RESET);

        if (!parseArgs(args)) {
            printUsage();
            return;
        }

        System.out.println("Target: " + BLUE + target.getId() + RESET
                + " (" + target.getBrandName() + target.getBaseVersion() + ")");
        System.out.println();

        // Initialize a dummy ModLoaderSpecifics to avoid initialization errors
        initializeDummyModLoaderSpecifics();

        // Ensure shaderpacks folder exists
        try {
            ensureDirectoryExists(SHADERPACKS_DIR);
        } catch (IOException e) {
            System.err.println(RED + "ERROR: Could not create shaderpacks folder!" + RESET);
        }

        System.out.println("Shaderpacks folder: " + BLUE + SHADERPACKS_DIR + RESET);
        System.out.println();

        // Clean up old shader zip files
        try {
            cleanupOldShaderZips();
        } catch (IOException e) {
            System.err.println(YELLOW + "Warning: Could not clean up old shader zips: " + e.getMessage() + RESET);
        }

        try {
            // Find base and patched shaders
            ShaderPair shaderPair = findShaders();

            // If base shader is missing, try to download it
            if (shaderPair == null || shaderPair.baseShader == null) {
                System.out.println(YELLOW + "Base shader not found locally. Attempting to download from Modrinth..." + RESET);
                Path downloadedShader = downloadBaseShaderFromModrinth();
                if (downloadedShader != null) {
                    shaderPair = findShaders(); // Re-scan after download
                }
            }

            // Final check after potential download
            if (shaderPair == null || shaderPair.baseShader == null || shaderPair.patchedShader == null) {
                System.err.println(RED + "ERROR: Could not find both base and patched shaders!" + RESET);
                throw new RuntimeException("Missing required shader files");
            }

            System.out.println(GREEN + "Found base shader: " + BLUE + shaderPair.baseShader.getFileName() + RESET);
            System.out.println(GREEN + "Found patched shader: " + BLUE + shaderPair.patchedShader.getFileName() + RESET);
            System.out.println();

            // Generate patch files
            generatePatchFiles(shaderPair);

            System.out.println();
            System.out.println(GREEN + "=== Patch generation completed successfully! ===" + RESET);

        } catch (Exception e) {
            System.err.println(RED + "ERROR: " + e.getMessage() + RESET);
            e.printStackTrace();
        }
    }

    /**
     * Parses the optional command line arguments.
     *
     * @return false when the arguments are invalid and usage should be printed
     */
    private static boolean parseArgs(String[] args) {
        if (args == null) return true;

        for (String arg : args) {
            if (arg.startsWith("--target=")) {
                String id = arg.substring("--target=".length());
                ShaderTarget resolved = ShaderTargets.byId(id);
                if (resolved == null) {
                    System.err.println(RED + "Unknown target: " + id + RESET);
                    return false;
                }
                target = resolved;
                BRAND_NAME = target.getBrandName();
                VERSION = target.getBaseVersion();
                PATCH_VERSION = target.getPatchVersion();
                MODRINTH_PROJECT_ID = target.getModrinthProjectId();
            } else if (arg.startsWith("--base=")) {
                explicitBase = Paths.get(arg.substring("--base=".length())).toAbsolutePath();
            } else if (arg.startsWith("--patched=")) {
                explicitPatched = Paths.get(arg.substring("--patched=".length())).toAbsolutePath();
            } else {
                System.err.println(RED + "Unknown argument: " + arg + RESET);
                return false;
            }
        }
        return true;
    }

    private static void printUsage() {
        System.out.println();
        System.out.println("Usage: DevPatchGenerator [--target=<id>] [--base=<zip|dir>] [--patched=<zip|dir>]");
        System.out.println();
        System.out.println("  --target=<id>       which base shader to build a patch for");
        StringBuilder ids = new StringBuilder();
        for (ShaderTarget t : ShaderTargets.all()) {
            if (ids.length() > 0) ids.append(", ");
            ids.append(t.getId());
        }
        System.out.println("                      known targets: " + ids);
        System.out.println("  --base=<path>       unpatched base shaderpack; downloaded from Modrinth when omitted");
        System.out.println("  --patched=<path>    patched shaderpack source; looked up in ./shaderpacks when omitted");
    }

    /**
     * Initialize a dummy ModLoaderSpecifics implementation to avoid initialization errors
     */
    private static void initializeDummyModLoaderSpecifics() {
        ModLoaderSpecifics.setInstance(new ModLoaderSpecifics() {
            @Override
            public String getInstanceName() {
                return "PatchGenerator";
            }

            @Override
            public Path getShaderpacksPath() {
                return SHADERPACKS_DIR;
            }

            @Override
            public Path getConfigDirectory() {
                return INTELLIJ_BASE_DIR.resolve("dummy");
            }

            @Override
            public boolean serverCheck() {
                return false;
            }

            @Override
            public String getCurrentDimension() {
                return "overworld";
            }

            @Override
            public boolean isCurrentDimensionInMappings() {
                return true;
            }

            @Override
            public boolean setClipboard(String str) {
                return false;
            }

            @Override
            public boolean isTimeAdvancing() {
                return true;
            }

            @Override
            public Object getLevel() {
                return null;
            }
        });
    }

    /**
     * Cleans up old shader zip files that don't match the current VERSION
     */
    private static void cleanupOldShaderZips() throws IOException {
        ensureDirectoryExists(SHADERPACKS_DIR);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(SHADERPACKS_DIR)) {
            for (Path path : stream) {
                String name = path.getFileName().toString();

                // Only consider zip files
                if (!name.endsWith(".zip")) {
                    continue;
                }

                if (name.startsWith(BRAND_NAME)) {
                    // If it doesn't contain the current VERSION, delete it
                    if (!name.contains(VERSION)) {
                        System.out.println(YELLOW + "Deleting outdated shader zip: " + BLUE + name + RESET);
                        try {
                            Files.delete(path);
                            System.out.println(GREEN + "✓ Deleted: " + name + RESET);
                        } catch (IOException e) {
                            System.err.println(RED + "Failed to delete " + name + ": " + e.getMessage() + RESET);
                        }
                    }
                }
            }
        }
    }

    /**
     * Downloads the base shader from Modrinth if not found locally
     */
    private static Path downloadBaseShaderFromModrinth() {
        try {
            System.out.println(YELLOW + "Fetching latest version from Modrinth API..." + RESET);

            // Fetch project versions from Modrinth API
            String apiUrl = MODRINTH_API_BASE + "/project/" + MODRINTH_PROJECT_ID + "/version";
            HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() != 200) {
                System.err.println(RED + "ERROR: Modrinth API returned status " + conn.getResponseCode() + RESET);
                return null;
            }

            // Parse JSON response
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            JsonArray versions = JsonParser.parseString(response.toString()).getAsJsonArray();

            // Get the latest version (first in the array)
            if (versions.isEmpty()) {
                System.err.println(RED + "ERROR: No versions found on Modrinth!" + RESET);
                return null;
            }

            // Prefer the version the target is pinned to, so patch builds stay reproducible when
            // upstream publishes a newer release
            JsonObject selectedVersion = null;
            String pinned = VERSION.startsWith("_") ? VERSION.substring(1) : VERSION;
            for (int i = 0; i < versions.size(); i++) {
                JsonObject candidate = versions.get(i).getAsJsonObject();
                if (candidate.get("version_number").getAsString().equals(pinned)) {
                    selectedVersion = candidate;
                    break;
                }
            }

            if (selectedVersion == null) {
                System.out.println(YELLOW + "Pinned version " + pinned
                        + " not found on Modrinth, falling back to the latest release" + RESET);
                selectedVersion = versions.get(0).getAsJsonObject();
            }

            String versionNumber = selectedVersion.get("version_number").getAsString();
            System.out.println(GREEN + "Selected version: " + versionNumber + RESET);

            // Get the first file (there's only one)
            JsonArray files = selectedVersion.getAsJsonArray("files");
            JsonObject file = files.get(0).getAsJsonObject();

            String downloadUrl = file.get("url").getAsString();
            String fileName = file.get("filename").getAsString();

            System.out.println(YELLOW + "Downloading: " + BLUE + fileName + RESET);
            System.out.println("From: " + downloadUrl);

            // Download the file
            Path outputPath = SHADERPACKS_DIR.resolve(fileName);
            HttpURLConnection downloadConn = (HttpURLConnection) new URL(downloadUrl).openConnection();
            downloadConn.setRequestProperty("User-Agent", USER_AGENT);

            long fileSize = downloadConn.getContentLengthLong();
            System.out.println("File size: " + BLUE + (fileSize / 1024 / 1024) + " MB" + RESET);

            try (InputStream in = downloadConn.getInputStream();
                 FileOutputStream out = new FileOutputStream(outputPath.toFile())) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytesRead = 0;
                int lastPercentage = 0;

                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;

                    int percentage = (int) ((totalBytesRead * 100) / fileSize);
                    if (percentage > lastPercentage && percentage % 10 == 0) {
                        System.out.println("  Progress: " + percentage + "%");
                        lastPercentage = percentage;
                    }
                }
            }

            System.out.println(GREEN + "✓ Successfully downloaded: " + fileName + RESET);
            return outputPath;

        } catch (Exception e) {
            System.err.println(RED + "ERROR: Failed to download from Modrinth: " + e.getMessage() + RESET);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Finds the base and patched shader files
     */
    private static ShaderPair findShaders() {
        // Explicitly provided paths win over anything found in the shaderpacks folder
        if (explicitBase != null || explicitPatched != null) {
            Path base = explicitBase;
            Path patched = explicitPatched;

            if (base != null && !Files.exists(base)) {
                System.err.println(RED + "ERROR: --base path does not exist: " + base + RESET);
                base = null;
            }
            if (patched != null && !Files.exists(patched)) {
                System.err.println(RED + "ERROR: --patched path does not exist: " + patched + RESET);
                patched = null;
            }
            if (base != null && patched != null) {
                return new ShaderPair(base, patched);
            }
            // Fall through so the missing half can still be discovered or downloaded
            ShaderPair discovered = findShadersInShaderpacksDir();
            return new ShaderPair(
                    base != null ? base : (discovered == null ? null : discovered.baseShader),
                    patched != null ? patched : (discovered == null ? null : discovered.patchedShader));
        }

        return findShadersInShaderpacksDir();
    }

    private static ShaderPair findShadersInShaderpacksDir() {
        // Check if directory exists and is not empty
        if (!Files.exists(SHADERPACKS_DIR) || !Files.isDirectory(SHADERPACKS_DIR)) {
            System.err.println(RED + "ERROR: shaderpacks directory not found at: " + SHADERPACKS_DIR + RESET);
            return null;
        }

        Path baseShader = null;
        Path patchedShader = null;

        // Look for base shader (e.g., ComplementaryReimagined_r5.3.zip)
        // and patched shader (e.g., ComplementaryReimagined_r5.3 + EuphoriaPatches_1.7.8 OR Euphoria-Patches directory)
        try {
            for (Path path : Files.newDirectoryStream(SHADERPACKS_DIR)) {
                String name = path.getFileName().toString();

                // Check if it's a base shader
                if (name.startsWith(BRAND_NAME) &&
                    name.contains(VERSION) &&
                    !name.contains(PATCH_NAME)) {
                    baseShader = path;
                }

                // Check if it's the standard patched shader format
                if (name.contains(BRAND_NAME) &&
                    name.contains(" + " + PATCH_NAME + PATCH_VERSION)) {
                    patchedShader = path;
                }

                // Check if it's the Euphoria-Patches directory (default target only - other targets
                // pass their patched source explicitly via --patched)
                if (target == ShaderTargets.defaultTarget() && name.equals("Euphoria-Patches") && Files.isDirectory(path)) {
                    Path gitDir = path.resolve(".git");
                    if (Files.exists(gitDir) && Files.isDirectory(gitDir)) {
                        // Verify version from pack.json using ShaderDetector
                        ShaderDetector detector = new ShaderDetector(BRAND_NAME, PATCH_NAME, VERSION, PATCH_VERSION,
                            null, null, SHADERPACKS_DIR);
                        String detectedVersion = detector.readVersionFromPackJson(path);
                        if (detectedVersion != null && detectedVersion.equals(PATCH_VERSION.replace("_", ""))) {
                            System.out.println(GREEN + "Found Euphoria-Patches directory with version " + detectedVersion + RESET);
                            patchedShader = path;
                        } else {
                            System.err.println(YELLOW + "Warning: Euphoria-Patches found but version mismatch. Expected: " +
                                PATCH_VERSION.replace("_", "") + ", Found: " + detectedVersion + RESET);
                        }
                    } else {
                        System.err.println(YELLOW + "Warning: Euphoria-Patches directory found but missing .git folder" + RESET);
                    }
                }

                if (baseShader != null && patchedShader != null) {
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println(RED + "ERROR: Failed to read shaderpacks directory: " + e.getMessage() + RESET);
            return null;
        }

        // Return the ShaderPair even if some are null - let the caller handle it
        if (baseShader == null && patchedShader == null) {
            // Both missing - likely first time running, no need to log
            return new ShaderPair(baseShader, patchedShader);
        }

        if (patchedShader == null) {
            System.err.println(YELLOW + "Patched shader not found in " + SHADERPACKS_DIR + RESET);
            System.err.println("Need either:");
            System.err.println("  - " + BRAND_NAME + "Reimagined" + VERSION + " + " + PATCH_NAME + PATCH_VERSION);
            System.err.println("  - Euphoria-Patches directory with .git folder and correct version in pack.json");
        }

        return new ShaderPair(baseShader, patchedShader);
    }

    /**
     * Generates the .patch files
     */
    private static void generatePatchFiles(ShaderPair shaderPair) throws Exception {
        System.out.println(YELLOW + "Creating temporary directory..." + RESET);
        Path tempDir = Files.createTempDirectory("euphoria-patch-gen-");

        try {
            // Extract base shader (if it's a zip) or copy directory
            System.out.println(YELLOW + "Extracting base shader..." + RESET);
            Path baseExtracted = tempDir.resolve("base-extracted");
            extractOrCopyShader(shaderPair.baseShader, baseExtracted);

            // Extract patched shader (if it's a zip) or copy directory
            System.out.println(YELLOW + "Extracting patched shader..." + RESET);
            Path patchedExtracted = tempDir.resolve("patched-extracted");
            extractOrCopyShader(shaderPair.patchedShader, patchedExtracted);

            // Verify version in patched shader
            System.out.println(YELLOW + "Verifying patched shader version..." + RESET);
            ShaderDetector detector = new ShaderDetector(BRAND_NAME, PATCH_NAME, VERSION, PATCH_VERSION,
                null, null, SHADERPACKS_DIR);
            String patchedVersion = detector.readVersionFromPackJson(patchedExtracted);
            String expectedVersion = PATCH_VERSION.replace("_", "");

            if (patchedVersion == null) {
                System.err.println(RED + "ERROR: Could not read version from patched shader's pack.json!" + RESET);
                throw new RuntimeException("Missing version in patched shader's pack.json at " + patchedExtracted.resolve("shaders/pack.json"));
            }

            if (!patchedVersion.equals(expectedVersion)) {
                System.err.println(RED + "ERROR: Version mismatch in patched shader!" + RESET);
                System.err.println("Expected version: " + expectedVersion);
                System.err.println("Found version: " + patchedVersion);
                throw new RuntimeException("Patched shader version mismatch. Expected: " + expectedVersion + ", Found: " + patchedVersion);
            }

            System.out.println(GREEN + "✓ Version verified: " + patchedVersion + RESET);

            // Archive base shader to TAR
            System.out.println(YELLOW + "Creating TAR archive of base shader..." + RESET);
            Path baseArchived = tempDir.resolve(shaderPair.baseShader.getFileName().toString().replace(".zip", ".tar"));
            ArchiveUtils.archive(baseExtracted, baseArchived);

            // Log archive info
            String baseFileName = baseArchived.getFileName().toString();
            long fileSize = Files.size(baseArchived);
            System.out.println("Archive Name: " + baseFileName + " Archive size: " + BLUE + fileSize + " bytes" + RESET);
            String hash = HashUtils.calculateSHA256(baseArchived);
            System.out.println("Archive SHA-256: " + BLUE + hash + RESET);

            // Archive patched shader to TAR
            System.out.println(YELLOW + "Creating TAR archive of patched shader..." + RESET);
            Path patchedArchived = tempDir.resolve("patched.tar");
            ArchiveUtils.archive(patchedExtracted, patchedArchived);

            // Ensure output directories exist
            ensureDirectoryExists(RESOURCES_DIR);
            ensureDirectoryExists(PATCH_FILES_DIR);

            // Generate patch files
            String patchFileName = target.getPatchResourceName(PATCH_NAME);

            System.out.println(YELLOW + "Generating patch file: " + patchFileName + RESET);
            System.out.println(YELLOW + "This may take a moment..." + RESET);

            // Delete outdated patch files in resources dir
            try {
                for (Path path : Files.newDirectoryStream(RESOURCES_DIR)) {
                    String fileName = path.getFileName().toString();
                    if (fileName.endsWith(".patch")
                            && fileName.contains(PATCH_NAME)
                            && !fileName.contains(PATCH_VERSION)) {
                        System.out.println(YELLOW + "Deleting outdated patch file: " + BLUE + path + RESET);
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            System.err.println(RED + "Failed to delete: " + path + " (" + e.getMessage() + ")" + RESET);
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println(RED + "Error checking for outdated patch files: " + e.getMessage() + RESET);
            }

            // Generate to resources directory
            Path resourcesPatchFile = RESOURCES_DIR.resolve(patchFileName);
            FileUI.diff(baseArchived.toFile(), patchedArchived.toFile(), resourcesPatchFile.toFile());
            System.out.println(GREEN + "✓ Created: " + BLUE + resourcesPatchFile + RESET);

            // Generate to EuphoriaPatchFiles directory
            Path patchFilesDirPatch = PATCH_FILES_DIR.resolve(patchFileName);
            FileUI.diff(baseArchived.toFile(), patchedArchived.toFile(), patchFilesDirPatch.toFile());
            System.out.println(GREEN + "✓ Created: " + BLUE + patchFilesDirPatch + RESET);

        } finally {
            // Clean up temp directory
            System.out.println(YELLOW + "Cleaning up temporary files..." + RESET);
            try {
                FileUtils.deleteDirectory(tempDir.toFile());
            } catch (IOException e) {
                System.err.println(YELLOW + "Warning: Could not delete temp directory: " + e.getMessage() + RESET);
            }
        }
    }

    /**
     * Extracts a shader pack (if it's a zip) or copies it (if it's a directory)
     */
    private static void extractOrCopyShader(Path shaderPath, Path outputDir) throws Exception {
        if (Files.isDirectory(shaderPath)) {
            String[] packagedRoots = target.getPackagedRoots();
            if (packagedRoots != null) {
                // The target declares exactly what a released pack contains, so copy only those
                // entries. This keeps a source checkout (docs, scripts, CI config) from leaking
                // into the patch.
                System.out.println("  " + BLUE + shaderPath.getFileName() + RESET
                        + " is a source checkout, copying packaged roots only...");
                copyPackagedRoots(shaderPath, outputDir, packagedRoots);
                return;
            }

            // Check if it's the Euphoria-Patches directory
            boolean isEuphoriaPatchesDir = shaderPath.getFileName().toString().equals("Euphoria-Patches");

            if (isEuphoriaPatchesDir) {
                System.out.println("  " + BLUE + shaderPath.getFileName() + RESET + " is the Euphoria-Patches directory, copying with exclusions...");
                copyEuphoriaPatchesDirectory(shaderPath, outputDir);
            } else {
                // It's already a directory, just copy it
                System.out.println("  " + BLUE + shaderPath.getFileName() + RESET + " is a directory, copying...");
                FileUtils.copyDirectory(shaderPath.toFile(), outputDir.toFile());
            }
        } else {
            // It's a file (probably a zip), extract it
            System.out.println("  " + BLUE + shaderPath.getFileName() + RESET + " is an archive, extracting...");
            ArchiveUtils.extract(shaderPath, outputDir);
        }
    }

    /**
     * Copies only the top level entries a released pack of this target consists of, so that a
     * source checkout produces byte identical content to the published zip.
     */
    private static void copyPackagedRoots(Path source, Path destination, String[] packagedRoots) throws IOException {
        Files.createDirectories(destination);

        for (String root : packagedRoots) {
            Path entry = source.resolve(root);
            if (!Files.exists(entry)) {
                System.err.println(YELLOW + "Warning: packaged root missing in source: " + root + RESET);
                continue;
            }

            Path targetPath = destination.resolve(root);
            if (Files.isDirectory(entry)) {
                FileUtils.copyDirectory(entry.toFile(), targetPath.toFile());
            } else {
                Files.createDirectories(targetPath.getParent());
                Files.copy(entry, targetPath);
            }
            System.out.println("    " + GREEN + "Included: " + root + RESET);
        }
    }

    /**
     * Copies the Euphoria-Patches directory while excluding unwanted files/folders
     */
    private static void copyEuphoriaPatchesDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(source)) {
            for (Path entry : stream) {
                String fileName = entry.getFileName().toString();

                // Skip excluded files and directories from root of Euphoria-Patches
                if (fileName.equals(".git") || fileName.equals(".github") || fileName.equals(".vscode") || fileName.endsWith(".zip")) {
                    System.out.println("    " + YELLOW + "Skipping: " + fileName + RESET);
                    continue;
                }

                Path targetPath = target.resolve(fileName);

                if (Files.isDirectory(entry)) {
                    // Recursively copy directories with exclusions
                    copyEuphoriaPatchesDirectoryRecursive(entry, targetPath);
                } else {
                    // Copy files
                    Files.copy(entry, targetPath);
                }
            }
        }
    }

    /**
     * Recursively copies a directory while excluding specific files at any depth
     */
    private static void copyEuphoriaPatchesDirectoryRecursive(Path source, Path target) throws IOException {
        Files.createDirectories(target);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(source)) {
            for (Path entry : stream) {
                String fileName = entry.getFileName().toString();

                // Skip excluded files at any depth (just check filename, not full path)
                if (fileName.equals("propertiesFragmenter.py") || fileName.equals("settingsRanges.py") || fileName.equals("TextToGLSLText.py") || fileName.endsWith(".eptext") || fileName.endsWith(".zip")) {
                    System.out.println("    " + YELLOW + "Skipping: " + source.relativize(entry) + RESET);
                    continue;
                }

                Path targetPath = target.resolve(fileName);

                if (Files.isDirectory(entry)) {
                    // Recursively copy subdirectories
                    copyEuphoriaPatchesDirectoryRecursive(entry, targetPath);
                } else {
                    // Copy files
                    Files.copy(entry, targetPath);
                }
            }
        }
    }

    /**
     * Ensures a directory exists, creating it if necessary
     */
    private static void ensureDirectoryExists(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            System.out.println(YELLOW + "Creating directory: " + BLUE + dir + RESET);
            Files.createDirectories(dir);
        }
    }

    /**
     * Helper class to hold base and patched shader paths
     */
    private static class ShaderPair {
        final Path baseShader;
        final Path patchedShader;

        ShaderPair(Path baseShader, Path patchedShader) {
            this.baseShader = baseShader;
            this.patchedShader = patchedShader;
        }
    }
}
