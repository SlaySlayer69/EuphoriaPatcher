package com.euphoriapatches.euphoria_patcher.features;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.integration.ShaderLoader;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.services.ShaderDetector;
import com.euphoriapatches.euphoria_patcher.util.VersionComparator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateShaderConfig {
    private static final String EUPHORIA_IDENTIFIER = "AAA_THIS_IS_A_EUPHORIA_PATCHES_SETTINGS_FILE=true";
    private static final String VERSION_IDENTIFIER_PREFIX = "AAB_FOR_EUPHORIA_PATCHES_VERSION_";
    private static final String VERSION_IDENTIFIER_SUFFIX = "=true";
    private static final Pattern EUPHORIA_FILE_PATTERN = Pattern.compile(
            "Comp\\d+(?:\\.\\d+)*[rdp]?\\d*EP_|.*(?:EuphoriaPatches|Euphoria-Patches)",
        Pattern.CASE_INSENSITIVE
    );

    private static final java.util.concurrent.ScheduledExecutorService scheduler =
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "EuphoriaPatcher-FileWriter");
            t.setDaemon(true); // Make sure it doesn't prevent game exit
            return t;
        });

    public static void shutdownFileWriter() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[UpdateShaderConfig] " + message);
    }

    private static String getVersionIdentifier() {
        // Convert PATCH_VERSION (like "_1.5.2") to format "1_5_2" for the identifier
        String version = EuphoriaPatcher.PATCH_VERSION;
        version = version.substring(1); // Remove leading underscore
        return createVersionIdentifier(version);
    }

    // Create version identifier from a version string
    private static String createVersionIdentifier(String version) {
        // Convert version (like "1.5.2") to format "1_5_2" for the identifier
        String formattedVersion = version.replace(".", "_"); // Replace dots with underscores
        return VERSION_IDENTIFIER_PREFIX + formattedVersion + VERSION_IDENTIFIER_SUFFIX;
    }

    // Extract version from identifier line
    private static String extractVersionFromIdentifier(String line) {
        if (line.startsWith(VERSION_IDENTIFIER_PREFIX) && line.endsWith(VERSION_IDENTIFIER_SUFFIX)) {
            String versionPart = line.substring(VERSION_IDENTIFIER_PREFIX.length(),
                                              line.length() - VERSION_IDENTIFIER_SUFFIX.length());
            return "_" + versionPart.replace("_", "."); // Convert back to PATCH_VERSION format
        }
        return null;
    }

    public static void updateShaderTxtConfigFile(boolean styleUnbound, boolean styleReimagined) {
        try (DirectoryStream<Path> oldConfigTextStream = Files.newDirectoryStream(EuphoriaPatcher.shaderpacks,
                path -> isConfigFile(path, true))) {
            Path oldShaderConfigFilePath = findShaderConfigFile(oldConfigTextStream, true);
            if (oldShaderConfigFilePath != null) {
                doConfigFileCopy(oldShaderConfigFilePath, true, styleUnbound, styleReimagined);
            } else { // No Euphoria settings .txt file
                try (DirectoryStream<Path> baseShaderConfigTextStream = Files.newDirectoryStream(EuphoriaPatcher.shaderpacks,
                        path -> isConfigFile(path, false))) {
                    Path baseShaderConfigFilePath = findShaderConfigFile(baseShaderConfigTextStream, true);
                    if (baseShaderConfigFilePath != null) {
                        doConfigFileCopy(baseShaderConfigFilePath, false, styleUnbound, styleReimagined);
                    }
                } catch (IOException e) {
                    EuphoriaPatcher.log(3,0, "Error reading shaderpacks directory: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            EuphoriaPatcher.log(3,0, "Error reading shaderpacks directory: " + e.getMessage());
        }

        // Add identifier to all Euphoria Patches settings files
        markAllEPSettingsFiles();
    }

    /**
     * Marks all Euphoria Patches settings files in the shaderpacks directory
     */
    public static void markAllEPSettingsFiles() {
        try {
            // Get all the files that need to be updated
            List<Path> filesToUpdate = new ArrayList<>();
            List<String> versionIdentifiers = new ArrayList<>();

            // Get ShaderDetector instance
            EuphoriaPatcher instance = EuphoriaPatcher.getInstance();
            ShaderDetector detector = instance != null ? instance.getShaderDetector() : null;

            try (DirectoryStream<Path> configStream = Files.newDirectoryStream(EuphoriaPatcher.shaderpacks,
                    path -> Files.isRegularFile(path) && path.toString().endsWith(".txt"))) {

                for (Path configFile : configStream) {
                    String fileName = configFile.getFileName().toString();
                    // Check if matches any Euphoria Patches pattern
                    if (EUPHORIA_FILE_PATTERN.matcher(fileName).find()) {
                        String versionIdentifier = getVersionIdentifierForShader(fileName, detector);
                        filesToUpdate.add(configFile);
                        versionIdentifiers.add(versionIdentifier);
                        debugLog("Identified settings file: " + fileName + " (version identifier: " +
                                (versionIdentifier != null ? versionIdentifier : "none") + ")");
                    }
                }
            }

            // Schedule the updates to happen after a delay
            if (!filesToUpdate.isEmpty()) {
                debugLog("Scheduling identifier updates for " + filesToUpdate.size() + " files after shader reload");

                // Schedule the task to run 1 second after shader reload completes
                scheduler.schedule(() -> {
                    for (int i = 0; i < filesToUpdate.size(); i++) {
                        Path configFile = filesToUpdate.get(i);
                        String versionIdentifier = versionIdentifiers.get(i);

                        debugLog("Delayed processing of file: " + configFile.getFileName());
                        addIdentifierToSettingsFile(configFile, versionIdentifier);
                    }
                }, 1000, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
        } catch (IOException e) {
            EuphoriaPatcher.log(2, 0, "Error preparing to mark settings files: " + e.getMessage());
        }
    }

    /**
     * Marks only the current shaderpack's settings file if it's an EP shader
     * Uses ShaderLoader to get the current shaderpack and ShaderDetector to verify it's an EP shader
     */
    public static void markCurrentEPSettingsFile() {
        try {
            // Get current shaderpack path
            Path currentShaderpack = ShaderLoader.getCurrentShaderpackPath();
            if (currentShaderpack == null) {
                debugLog("No current shaderpack selected");
                return;
            }

            // Check if it's an EP shader using ShaderDetector
            EuphoriaPatcher instance = EuphoriaPatcher.getInstance();
            if (instance == null) {
                debugLog("EuphoriaPatcher instance not available");
                return;
            }

            ShaderDetector detector = instance.getShaderDetector();
            if (detector == null || !detector.isEuphoriaPatchesShader(currentShaderpack)) {
                debugLog("Current shaderpack is not an EP shader: " + currentShaderpack.getFileName());
                return;
            }

            // Construct the .txt config filename
            String shaderName = currentShaderpack.getFileName().toString();
            String configFileName = shaderName + ".txt";
            Path configFile = EuphoriaPatcher.shaderpacks.resolve(configFileName);

            // Check if the config file exists
            if (!Files.exists(configFile) || !Files.isRegularFile(configFile)) {
                debugLog("Config file does not exist: " + configFileName);
                return;
            }

            debugLog("Marking current EP settings file: " + configFileName);

            // Determine version identifier to use based on the actual shader version
            String versionIdentifier = getVersionIdentifierForShader(shaderName, detector);

            // Schedule the update
            scheduler.schedule(() -> {
                debugLog("Delayed processing of current settings file: " + configFile.getFileName());
                addIdentifierToSettingsFile(configFile, versionIdentifier);
            }, 1000, java.util.concurrent.TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            EuphoriaPatcher.log(2, 0, "Error marking current settings file: " + e.getMessage());
        }
    }

    /**
     * Determines the version identifier to use for a shader based on its actual version from pack.json
     * Only returns a version identifier if the shader version is >= current PATCH_VERSION
     * @param shaderName The name of the shader (without .txt extension for config files)
     * @param detector ShaderDetector instance, can be null
     * @return Version identifier string to add, or null if no version identifier should be added
     */
    private static String getVersionIdentifierForShader(String shaderName, ShaderDetector detector) {
        // Fallback to filename check if detector not available
        if (detector == null) {
            debugLog("ShaderDetector not available, falling back to filename check");
            if (shaderName.contains(EuphoriaPatcher.PATCH_VERSION)) {
                return getVersionIdentifier();
            }
            return null;
        }

        // Remove .txt extension if present
        String baseShaderName = shaderName.endsWith(".txt")
            ? shaderName.substring(0, shaderName.length() - 4)
            : shaderName;

        // Try to find the corresponding shader pack (directory or .zip)
        Path shaderPath = EuphoriaPatcher.shaderpacks.resolve(baseShaderName);
        if (!Files.exists(shaderPath)) {
            debugLog("Could not find shader pack for: " + baseShaderName + ", falling back to filename check");
            if (shaderName.contains(EuphoriaPatcher.PATCH_VERSION)) {
                return getVersionIdentifier();
            }
            return null;
        }

        // Get the version from pack.json
        String versionFromShader = detector.getEuphoriaPatchesVersionFromShader(shaderPath);
        if (versionFromShader == null) {
            debugLog("Could not read version from shader pack: " + baseShaderName + ", falling back to filename check");
            if (shaderName.contains(EuphoriaPatcher.PATCH_VERSION)) {
                return getVersionIdentifier();
            }
            return null;
        }

        // Compare with current PATCH_VERSION (remove leading underscore from PATCH_VERSION)
        String currentVersion = EuphoriaPatcher.PATCH_VERSION.substring(1); // e.g., "_1.8.3" -> "1.8.3"

        // Use VersionComparator to check if shader version >= current version
        int comparison = VersionComparator.compareVersionStrings(versionFromShader, currentVersion);

        debugLog("Version check for " + baseShaderName + ": pack.json=" + versionFromShader +
                ", current=" + currentVersion + ", comparison=" + comparison);

        // Only add version identifier if shader version >= current version
        if (comparison >= 0) {
            // Use the shader's actual version for the identifier
            String identifier = createVersionIdentifier(versionFromShader);
            debugLog("Will add version identifier: " + identifier);
            return identifier;
        } else {
            debugLog("Shader version is older than current version, not adding version identifier");
            return null;
        }
    }

    private static void addIdentifierToSettingsFile(Path configFile, String versionIdentifierToAdd) {
        // Try with increasing delay between attempts
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                if (attempt > 0) {
                    // Wait before retrying
                    Thread.sleep(200 * attempt);
                    EuphoriaPatcher.log(0, "Retry attempt " + attempt + " for file: " + configFile.getFileName());
                }

                debugLog("Starting to process file: " + configFile.getFileName());

                if (!Files.isWritable(configFile)) {
                    debugLog("File is not writable: " + configFile.getFileName());
                    return;
                }

                List<String> lines = Files.readAllLines(configFile, StandardCharsets.UTF_8);

                // Apply settings conversions
                List<String> convertedLines = ShaderSettingsConverter.convertLines(lines);
                // Check if any settings were actually changed
                boolean settingsChanged = !lines.equals(convertedLines);
                lines = convertedLines;

                debugLog("Applied settings conversions to file: " + configFile.getFileName() +
                         (settingsChanged ? " (changes applied)" : " (no changes needed)"));

                // Check for identifiers
                boolean mainIdentifierExists = false;
                String existingVersionIdentifier = null;
                int versionIdentifierCount = 0;

                // First scan the file to check what identifiers exist
                for (String line : lines) {
                    String trimmed = line.trim();

                    if (trimmed.equals(EUPHORIA_IDENTIFIER)) { // Use trim() for safety
                        mainIdentifierExists = true;
                    } else if (trimmed.startsWith(VERSION_IDENTIFIER_PREFIX) && trimmed.endsWith(VERSION_IDENTIFIER_SUFFIX)) {
                        versionIdentifierCount++;

                        // Keep highest embedded version if multiple markers are present.
                        if (existingVersionIdentifier == null) {
                            existingVersionIdentifier = trimmed;
                        } else {
                            String existingVersion = extractVersionFromIdentifier(existingVersionIdentifier);
                            String candidateVersion = extractVersionFromIdentifier(trimmed);
                            if (existingVersion != null && candidateVersion != null &&
                                compareVersionParts(candidateVersion.substring(1), existingVersion.substring(1)) > 0) {
                                existingVersionIdentifier = trimmed;
                            }
                        }
                    }
                }

                // Only return early if both conditions are true:
                // 1. No settings were changed
                // 2. Identifiers are already correct AND are at the top of the file after comments
                boolean identifiersAtTop = areIdentifiersAtTop(lines);
                boolean hasDuplicateVersionIdentifiers = versionIdentifierCount > 1;
                if (!settingsChanged && mainIdentifierExists &&
                    (versionIdentifierToAdd == null ||
                    (existingVersionIdentifier != null && existingVersionIdentifier.equals(versionIdentifierToAdd))) &&
                    identifiersAtTop && !hasDuplicateVersionIdentifiers) {

                    debugLog("No settings changes needed and identifiers are correctly positioned for: " + configFile.getFileName());
                    return; // Nothing to do
                } else if (hasDuplicateVersionIdentifiers) {
                    debugLog("Found duplicate version identifiers - will collapse to one marker for: " + configFile.getFileName());
                } else if (!identifiersAtTop) {
                    debugLog("Identifiers exist but are not at the top - will reposition them for: " + configFile.getFileName());
                }

                // We need to modify the file - create new content with reorganized structure
                List<String> newLines = new ArrayList<>();

                // Phase 1: Add all comment lines that start with #
                // Usually the first line is a timestamp comment
                for (String line : lines) {
                    if (line.trim().startsWith("#")) {
                        newLines.add(line);
                    }
                }

                // Phase 2: Add identifiers right after comments
                newLines.add(EUPHORIA_IDENTIFIER);

                if (versionIdentifierToAdd != null) {
                    newLines.add(versionIdentifierToAdd);
                } else if (existingVersionIdentifier != null) {
                    newLines.add(existingVersionIdentifier);
                    debugLog("Preserved existing version identifier: " + existingVersionIdentifier);
                }

                // Phase 3: Add all remaining lines except comments and identifiers
                for (String line : lines) {
                    String trimmed = line.trim();
                    // Skip comments (already added), and identifiers (also already added)
                    if (!trimmed.startsWith("#") &&
                        !trimmed.equals(EUPHORIA_IDENTIFIER) &&
                        !(trimmed.startsWith(VERSION_IDENTIFIER_PREFIX) && trimmed.endsWith(VERSION_IDENTIFIER_SUFFIX))) {
                        newLines.add(line);
                    }
                }

                debugLog("About to write to file: " + configFile.getFileName());

                // Use try-with-resources to ensure streams are properly closed
                try (java.io.BufferedWriter writer = Files.newBufferedWriter(
                        configFile, StandardCharsets.UTF_8)) {

                    for (String line : newLines) {
                        writer.write(line);
                        writer.newLine();
                    }

                    // Make sure to flush
                    writer.flush();
                }

                debugLog("Updated identifiers in settings file: " + configFile.getFileName());

                // If we got here without errors, break the retry loop
                break;
            } catch (IOException e) {
                if (attempt == 2) { // The Last attempt failed
                    EuphoriaPatcher.log(2, 0, "Error adding identifier to settings file " +
                                        configFile.getFileName() + ": " + e.getMessage());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    private static boolean areIdentifiersAtTop(List<String> lines) {
        boolean foundMainIdentifier = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                continue;
            }

            if (trimmed.startsWith("#")) {
                continue;
            }

            // First non-comment line should be our main identifier
            if (!foundMainIdentifier) {
                if (trimmed.equals(EUPHORIA_IDENTIFIER)) {
                    foundMainIdentifier = true;
                    continue;
                } else {
                    // Found a non-comment, non-identifier line first
                    return false;
                }
            }

            return true;
        }


        return foundMainIdentifier; // True only if we found main identifier in the correct position
    }

    private static void doConfigFileCopy(Path configFilePath, boolean containsPatchName, boolean styleUnbound, boolean styleReimagined){
        String style = styleUnbound ? "Unbound" : "Reimagined";
        String newName = EuphoriaPatcher.BRAND_NAME + style + EuphoriaPatcher.VERSION + " + " + EuphoriaPatcher.PATCH_NAME + EuphoriaPatcher.PATCH_VERSION + ".txt";
        try {
            Path newPath = configFilePath.resolveSibling(newName);

            // Check if target file already exists
            if (Files.exists(newPath)) {
                debugLog("Target config file already exists, skipping copy: " + newName);
                EuphoriaPatcher.log(0, "Shader config file already up to date!");
            } else {
                Files.copy(configFilePath, newPath); // Copy old config and rename it to current PATCH_VERSION

                // Add our identifiers to the new config file - include version since the name has current version
                addIdentifierToSettingsFile(newPath, getVersionIdentifier());

                EuphoriaPatcher.log(0, "Successfully updated shader config file to the latest version!");
            }
        } catch (IOException e) {
            EuphoriaPatcher.log(3,0, "Could not rename the config file: " + e.getMessage());
        }

        if (styleUnbound && styleReimagined) { // Yeah, this makes things unnecessarily complex lol
            EuphoriaPatcher.log(0, "Both shader styles detected!");
            try (DirectoryStream<Path> latestConfigTextStream = Files.newDirectoryStream(EuphoriaPatcher.shaderpacks,
                    path -> isConfigFile(path, containsPatchName))) { // Create a new DirectoryStream - The iterator of Files.newDirectoryStream can only be used once
                Path latestShaderConfigFilePath = findShaderConfigFile(latestConfigTextStream, false);
                if (latestShaderConfigFilePath != null) {
                    style = latestShaderConfigFilePath.toString().contains("Unbound") ? "Reimagined" : "Unbound"; // Detect what the previously renamed (oldShaderConfigFilePath) .txt contains
                    newName = EuphoriaPatcher.BRAND_NAME + style + EuphoriaPatcher.VERSION + " + " + EuphoriaPatcher.PATCH_NAME + EuphoriaPatcher.PATCH_VERSION + ".txt";
                    try { // Now copy and past the renamed .txt file with a new name - 2 identical.txt files with different style names are now in the shaderpacks folder
                        Path newPath = latestShaderConfigFilePath.resolveSibling(newName);

                        // Check if target file already exists
                        if (Files.exists(newPath)) {
                            debugLog("Target config file already exists, skipping copy: " + newName);
                        } else {
                            Files.copy(latestShaderConfigFilePath, newPath);

                            // Add our identifiers to this copy too
                            addIdentifierToSettingsFile(newPath, getVersionIdentifier());

                            EuphoriaPatcher.log(0, "Successfully copied shader config file and renamed it!");
                        }
                    } catch (IOException e) {
                        EuphoriaPatcher.log(3,0, "Could not copy and rename the config file: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                EuphoriaPatcher.log(3,0, "Error reading shaderpacks directory: " + e.getMessage());
            }
        }
    }

    // Helper method to check if a file is a config file
    private static boolean isConfigFile(Path path, boolean containsPatchName) {
        String nameText = path.getFileName().toString();

        // If we're specifically looking for patched files
        if (containsPatchName) {
            // Check for our standard naming first
            boolean hasStandardName = nameText.matches("(?:Comp\\d\\.\\d|" + EuphoriaPatcher.BRAND_NAME + ").*") &&
                                     nameText.endsWith(".txt") &&
                                     (nameText.contains(EuphoriaPatcher.PATCH_NAME) || nameText.contains(" + EP_"));

            // If it doesn't have our standard name, check ANY .txt file since it might have our identifier
            if (!hasStandardName) {
                return nameText.endsWith(".txt");
            }
            return true;
        } else {
            // Original logic for base files
            return nameText.matches(".*" + EuphoriaPatcher.BRAND_NAME + ".*(Reimagined|Unbound).*") && nameText.endsWith(".txt");
        }
    }

    private static Path findShaderConfigFile(DirectoryStream<Path> textStream, boolean searchOldEuphoriaConfigs) {
        List<Path> euphoriaFiles = new ArrayList<>();
        List<Path> baseFiles = new ArrayList<>();
        List<Path> flaggedFiles = new ArrayList<>();

        // Categorize all files
        for (Path potentialTextFile : textStream) {
            String name = potentialTextFile.getFileName().toString();
            if (name.endsWith(".txt")) {
                if (name.contains("EuphoriaPatches") || name.contains("EP_")) {
                    euphoriaFiles.add(potentialTextFile);
                    debugLog("Found Euphoria named file: " + name);
                } else {
                    // Check if this file contains our hidden flag
                    String versionFromFile = getEuphoriaPatchesVersionFromFile(potentialTextFile);
                    if (versionFromFile != null) {
                        flaggedFiles.add(potentialTextFile);
                        debugLog("Found flagged file: " + name + " with version " + versionFromFile);
                    } else {
                        // Only add to baseFiles if it matches the base file pattern
                        if (name.matches(".*" + EuphoriaPatcher.BRAND_NAME + ".*(Reimagined|Unbound).*")) {
                            baseFiles.add(potentialTextFile);
                            debugLog("Found base file: " + name);
                        } else {
                            debugLog("Skipping non-matching file: " + name);
                        }
                    }
                }
            }
        }

        debugLog("Found " + euphoriaFiles.size() + " Euphoria files, " +
                flaggedFiles.size() + " flagged files, and " + baseFiles.size() + " base files");

        // STEP 1: Try to find Euphoria files by name

        if (!euphoriaFiles.isEmpty()) {
            debugLog("Sorting Euphoria files by version...");
            euphoriaFiles.sort((p1, p2) -> compareConfigFileVersions(getConfigFileVersion(p1), getConfigFileVersion(p2)));
            Path latestEuphoriaConfig = euphoriaFiles.get(euphoriaFiles.size() - 1);
            String latestName = latestEuphoriaConfig.getFileName().toString();

            if (searchOldEuphoriaConfigs) {
                if (!latestName.contains(EuphoriaPatcher.PATCH_VERSION) || latestName.contains("dev")) {
                    debugLog("Selected old Euphoria config: " + latestName);
                    return latestEuphoriaConfig;
                }
                // Continue to check flagged files if no suitable Euphoria file found
            } else {
                debugLog("Selected latest Euphoria config: " + latestName);
                return latestEuphoriaConfig;
            }
        }

        // STEP 2: If no suitable Euphoria file by name, try flagged files
        if (!flaggedFiles.isEmpty()) {
            debugLog("Sorting flagged files by embedded version...");
            flaggedFiles.sort((p1, p2) -> compareConfigFileVersions(getConfigFileVersion(p1), getConfigFileVersion(p2)));
            Path latestFlaggedFile = flaggedFiles.get(flaggedFiles.size() - 1);
            String latestName = latestFlaggedFile.getFileName().toString();
            debugLog("Selected flagged file: " + latestName +
                       " with embedded version: " + getEuphoriaPatchesVersionFromFile(latestFlaggedFile));
            return latestFlaggedFile;
        }

        // STEP 3: Only as last resort, fall back to base versions
        if (!baseFiles.isEmpty()) {
            debugLog("No Euphoria files found, falling back to base files");
            baseFiles.sort((p1, p2) -> compareConfigFileVersions(getConfigFileVersion(p1), getConfigFileVersion(p2)));
            Path latestBaseFile = baseFiles.get(baseFiles.size() - 1);
            debugLog("Selected base file: " + latestBaseFile.getFileName());
            return latestBaseFile;
        }

        debugLog("No suitable config file found");
        return null;
    }

    private static String getEuphoriaPatchesVersionFromFile(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            boolean foundMainIdentifier = false;
            String highestVersion = null;

            for (String line : lines) {
                String trimmed = line.trim();

                if (trimmed.equals(EUPHORIA_IDENTIFIER)) {
                    foundMainIdentifier = true;
                    debugLog("Found main identifier in file: " + file.getFileName());
                } else if (foundMainIdentifier &&
                        trimmed.startsWith(VERSION_IDENTIFIER_PREFIX) &&
                        trimmed.endsWith(VERSION_IDENTIFIER_SUFFIX)) {
                    String version = extractVersionFromIdentifier(trimmed);
                    if (version != null) {
                        debugLog("Found version identifier in file: " + file.getFileName() + " - " + version);
                        if (highestVersion == null ||
                            compareVersionParts(version.substring(1), highestVersion.substring(1)) > 0) {
                            highestVersion = version;
                        }
                    }
                }
            }

            if (highestVersion != null) {
                debugLog("Using highest version identifier in file: " + file.getFileName() + " - " + highestVersion);
                return highestVersion;
            }

            if (foundMainIdentifier) {
                debugLog("Found main identifier but no version in file: " + file.getFileName() + " - using default 1.0.0");
                return "_1.0.0";
            } else {
                debugLog("Main identifier not found in file: " + file.getFileName());
            }
        } catch (IOException e) {
            EuphoriaPatcher.log(0, "Error reading file " + file.getFileName() + ": " + e.getMessage());
        }

        return null;
    }

    private static String getConfigFileVersion(Path path) {
        // First try to get version from file content
        String versionFromFile = getEuphoriaPatchesVersionFromFile(path);
        if (versionFromFile != null) {
            // Use any version found in file for patched configs
            String result = versionFromFile.substring(1) + "|0"; // Remove leading underscore
            debugLog("Using embedded version for " + path.getFileName() + ": " + result);
            return result;
        }

        // Fall back to parsing from filename
        String name = path.getFileName().toString();

        // First try to match Euphoria pattern
        Pattern euphoriaPattern = Pattern.compile("(?:[a-zA-Z_]+)?[rdp]?(\\d+(?:\\.\\d+)*)(?:[rdp]\\d+)?(?: \\+ )?(?:EuphoriaPatches_|EP_)(\\d+(?:\\.\\d+)*(?:-dev\\d+)?)");
        Matcher euphoriaMatcher = euphoriaPattern.matcher(name);
        if (euphoriaMatcher.find()) {
            String mainVersion = euphoriaMatcher.group(1);
            String patchVersion = euphoriaMatcher.group(2);
            if (patchVersion != null) {
                String result = patchVersion + "|" + mainVersion;
                debugLog("Extracted Euphoria version from filename " + name + ": " + result);
                return result; // Euphoria Patches version first
            }
            debugLog("Found main version only in " + name + ": 0|" + mainVersion);
            return "0|" + mainVersion; // If no Euphoria Patches version, use 0
        }

        // For base files (Complementary shaders without Euphoria)
        Pattern basePattern = Pattern.compile(".*" + EuphoriaPatcher.BRAND_NAME + ".*[_r]([\\d.]+).*");
        Matcher baseMatcher = basePattern.matcher(name);
        if (baseMatcher.find()) {
            String baseVersion = baseMatcher.group(1);
            String result = "0|" + baseVersion; // No Euphoria version, only base version
            debugLog("Extracted base version from filename " + name + ": " + result);
            return result;
        }

        debugLog("Could not extract any version from " + name + ", using default 0|0");
        return "0|0"; // Default version if no pattern matches
    }

    private static int compareConfigFileVersions(String v1, String v2) {
        String[] fullVersion1 = v1.split("\\|");
        String[] fullVersion2 = v2.split("\\|");

        // Compare Euphoria Patches versions first
        int epCompare = compareVersionParts(fullVersion1[0], fullVersion2[0]);
        if (epCompare != 0) {
            debugLog("Comparing versions: " + v1 + " vs " + v2 + " - patch versions differ: " + epCompare);
            return epCompare;
        }

        // If Euphoria Patches versions are the same, compare main versions
        int result = compareVersionParts(fullVersion1[1], fullVersion2[1]);
        debugLog("Comparing versions: " + v1 + " vs " + v2 + " - main versions: " + result);
        return result;
    }

    private static int compareVersionParts(String p1, String p2) {
        String[] parts1 = p1.split("[.\\-]");
        String[] parts2 = p2.split("[.\\-]");
        int length = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < length; i++) {
            String part1 = i < parts1.length ? parts1[i] : "0";
            String part2 = i < parts2.length ? parts2[i] : "0";

            boolean isP1Dev = part1.contains("dev");
            boolean isP2Dev = part2.contains("dev");

            if (isP1Dev && isP2Dev) {
                // Both are dev versions, compare them
                String[] devParts1 = part1.split("dev");
                String[] devParts2 = part2.split("dev");
                debugLog(devParts1[1] + "  " + devParts2[1]);
                int mainCompare = Integer.compare(Integer.parseInt(devParts1[1]), Integer.parseInt(devParts2[1]));
                if (mainCompare != 0) {
                    return mainCompare;
                }
                return Integer.parseInt(devParts1[1]);
            } else if (isP1Dev) {
                // p1 is a dev version, p2 is not. p2 is considered newer.
                return -1;
            } else if (isP2Dev) {
                // p2 is a dev version, p1 is not. p1 is considered newer.
                return 1;
            } else {
                // Neither is a dev version, compare as integers
                int compare = Integer.compare(Integer.parseInt(part1), Integer.parseInt(part2));
                if (compare != 0) {
                    return compare;
                }
            }
        }
        return 0;
    }
}
