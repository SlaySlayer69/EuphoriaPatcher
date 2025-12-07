package mc.euphoria_patches.euphoria_patcher.integration;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import mc.euphoria_patches.euphoria_patcher.logging.EuphoriaLogger;
import mc.euphoria_patches.euphoria_patcher.util.ShaderVersionComparator;
import mc.euphoria_patches.euphoria_patcher.util.VersionComparator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.stream.Stream;

/**
 * Utility class for detecting and interacting with shader loader mods (Iris, Oculus, OptiFine, Angelica)
 * Provides methods to:
 * - Detect which shader loader is installed
 * - Extract version information from shader loader filenames
 * - Locate and read shader loader configuration files
 * - Get information about the currently selected shaderpack
 */
public class ShaderLoader {
    // Constants for shader types
    public static final String IRIS = "iris";
    public static final String OCULUS = "oculus";
    public static final String OPTIFINE = "optifine";
    public static final String ANGELICA = "angelica";
    public static final String UNKNOWN = "unknown";

    // Pattern to validate Minecraft version format (1.X.Y or 1.XX.YY)

    // Cache variables
    private static File cachedShaderFile = null;
    private static boolean shaderFileSearched = false;
    private static String cachedShaderLoader = null;
    private static String cachedMCVersion = null;
    private static Integer cachedShaderLoaderVersion = null;

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ShaderLoader] " + message);
    }

    private static File findShaderLoaderFile() {
        // Return cached result if we've already searched
        if (shaderFileSearched) {
            debugLog("Using cached shader file result: " + (cachedShaderFile != null ? cachedShaderFile.getName() : "null"));
            return cachedShaderFile;
        }

        debugLog("Searching for shader loader file in mods directory");
        try {
            File modsFolder = new File(String.valueOf(EuphoriaPatcher.modDirectory));
            File[] modFiles = modsFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar"));

            if (modFiles != null) {
                debugLog("Found " + modFiles.length + " JAR files in mods directory");
                for (File modFile : modFiles) {
                    String fileName = modFile.getName().toLowerCase(Locale.ROOT);
                    debugLog("Checking file: " + fileName);

                    // Skip compatibility and addon mods
                    if (fileName.contains("compat") ||
                        fileName.contains("addon") ||
                        fileName.contains("compatibility") ||
                        fileName.contains("flywheel")) {
                        debugLog("Skipping compatibility/addon mod: " + fileName);
                        continue;
                    }

                    if ((fileName.startsWith("iris") && ((fileName.contains("fabric") || fileName.contains("neoforge") || fileName.contains("+mc")))) ||
                        fileName.startsWith("oculus-mc") ||
                        fileName.startsWith("mekalus-mc") ||
                        fileName.startsWith("optifine_") ||
                        fileName.startsWith("angelica")) {
                        cachedShaderFile = modFile;
                        shaderFileSearched = true;
                        debugLog("Found shader loader file: " + modFile.getName());
                        return cachedShaderFile;
                    }
                }
            }
            debugLog("No shader loader file found");
            shaderFileSearched = true;
            return null;
        } catch (Exception e) {
            EuphoriaPatcher.log(2, 0, "Error finding shader loader: " + e.getMessage());
            debugLog("Exception while searching for shader loader: " + e.getMessage());
            shaderFileSearched = true;
            return null;
        }
    }

    /**
     * Gets the shader loader mod name from the shader loader filename.
     * Examples: "iris", "oculus", "optifine", "angelica"
     *
     * @return The shader loader mod name, or "unknown" if not detected
     */
    public static String getShaderLoader() {
        // Return cached result if available
        if (cachedShaderLoader != null) {
            debugLog("Using cached shader loader type: " + cachedShaderLoader);
            return cachedShaderLoader;
        }

        debugLog("Determining shader loader type");
        File shaderFile = findShaderLoaderFile();
        if (shaderFile == null) {
            debugLog("No shader file found, returning UNKNOWN");
            EuphoriaPatcher.log(2, 0, "No shader loader mod was found");
            cachedShaderLoader = UNKNOWN;
            return cachedShaderLoader;
        }

        String fileName = shaderFile.getName().toLowerCase(Locale.ROOT);
        debugLog("Analyzing file name: " + fileName);

        if (fileName.startsWith("iris")) {
            debugLog("Detected IRIS shader loader");
            cachedShaderLoader = IRIS;
        } else if (fileName.startsWith("oculus") || fileName.startsWith("mekalus")) { // Treat Mekalus as Oculus
            debugLog("Detected OCULUS shader loader" + (fileName.startsWith("mekalus") ? " (Mekalus fork)" : ""));
            cachedShaderLoader = OCULUS;
        } else if (fileName.startsWith("optifine")) {
            debugLog("Detected OPTIFINE shader loader");
            cachedShaderLoader = OPTIFINE;
        } else if (fileName.startsWith("angelica")) {
            debugLog("Detected ANGELICA shader loader");
            cachedShaderLoader = ANGELICA;
        } else {
            debugLog("Could not determine shader loader type, returning UNKNOWN");
            cachedShaderLoader = UNKNOWN;
        }

        return cachedShaderLoader;
    }

    /**
     * Gets the Minecraft version string from the shader loader filename.
     * Examples: "1.7.10", "1.8.9", "1.12.2", "1.16.2", "1.21.1"
     *
     * @return The Minecraft version as a string, or "unknown" if not detected
     */
    public static String getShaderLoaderMCVersion() {
        // Return cached result if available
        if (cachedMCVersion != null) {
            debugLog("Using cached MC version: " + cachedMCVersion);
            return cachedMCVersion;
        }

        debugLog("Extracting Minecraft version from shader loader filename");
        try {
            File shaderFile = findShaderLoaderFile();
            if (shaderFile == null) {
                debugLog("No shader file found, returning UNKNOWN version");
                cachedMCVersion = UNKNOWN;
                return cachedMCVersion;
            }

            String fileName = shaderFile.getName();
            String lowerFileName = fileName.toLowerCase(Locale.ROOT);
            String extractedVersion = null;
            debugLog("Parsing version from filename: " + fileName);

            // Handle Angelica format: angelica-1.0.0-betaXX.jar
            if (lowerFileName.startsWith("angelica")) {
                debugLog("Detected Angelica format - always for Minecraft 1.7.10");
                // Angelica is specifically for 1.7.10
                extractedVersion = "1.7.10";
                debugLog("Set version to 1.7.10 for Angelica");
            }
            // Handle OptiFine format: OptiFine_1.18.1_HD_U_H6.jar
            else if (lowerFileName.startsWith("optifine_")) {
                debugLog("Detected OptiFine format");
                String[] parts = fileName.split("_");
                if (parts.length >= 2) {
                    extractedVersion = parts[1]; // Return the version part (1.18.1)
                    debugLog("Extracted version: " + extractedVersion);
                }
            }
            // Handle Iris format: iris-fabric-1.8.8+mc1.21.4.jar
            else if (lowerFileName.startsWith("iris")) {
                debugLog("Detected Iris format");
                int mcIndex = lowerFileName.indexOf("+mc");
                if (mcIndex != -1) {
                    // Extract version after "+mc" until the next non-version character
                    String versionPart = lowerFileName.substring(mcIndex + 3);
                    // Find end of version (next dot that's not part of version number)
                    int endIndex = versionPart.indexOf(".jar");
                    if (endIndex != -1) {
                        extractedVersion = versionPart.substring(0, endIndex);
                        debugLog("Extracted version: " + extractedVersion);
                    }
                }
            }
            // Handle Oculus/Mekalus format: oculus-mc1.20.1-1.8.0.jar or mekalus-mc1.20.1-1.7.0.3.jar
            else if (lowerFileName.startsWith("oculus") || lowerFileName.startsWith("mekalus")) {
                debugLog("Detected " + (lowerFileName.startsWith("mekalus") ? "Mekalus" : "Oculus") + " format");
                int mcIndex = lowerFileName.indexOf("-mc");
                if (mcIndex != -1) {
                    // Extract version after "-mc" until the next dash
                    String afterMc = lowerFileName.substring(mcIndex + 3);
                    int dashIndex = afterMc.indexOf("-");
                    if (dashIndex != -1) {
                        extractedVersion = afterMc.substring(0, dashIndex);
                        debugLog("Extracted version: " + extractedVersion);
                    }
                }
            }

            // Validate the extracted version format
            if (extractedVersion != null) {
                Matcher matcher = ShaderVersionComparator.VERSION_PATTERN.matcher(extractedVersion);
                if (matcher.matches()) {
                    debugLog("Valid version format: " + extractedVersion);
                    cachedMCVersion = extractedVersion;
                    return cachedMCVersion;
                } else {
                    debugLog("Invalid version format: " + extractedVersion);
                    EuphoriaPatcher.log(1, 0, "Invalid version format detected: " + extractedVersion);
                }
            } else {
                debugLog("Could not extract version from filename");
            }

            debugLog("Setting version to UNKNOWN");
            cachedMCVersion = UNKNOWN;
            return cachedMCVersion;
        } catch (Exception e) {
            EuphoriaPatcher.log(2, 0, "Error extracting Minecraft version: " + e.getMessage());
            debugLog("Exception extracting Minecraft version: " + e.getMessage());
            cachedMCVersion = UNKNOWN;
            return cachedMCVersion;
        }
    }

    /**
     * Compares two Minecraft versions to determine if the first version is greater than or equal to the second.
     * For example, isVersionGreaterOrEqual("1.21.2", "1.21.1") returns true.
     *
     * @param version The version to check (e.g., "1.21.2")
     * @param minVersion The minimum required version (e.g., "1.21.1")
     * @return true if version >= minVersion, false otherwise or if any version is invalid
     */
    public static boolean isVersionGreaterOrEqual(String version, String minVersion) {
        if (version == null || minVersion == null || UNKNOWN.equals(version) || UNKNOWN.equals(minVersion)) {
            return false;
        }

        try {
            return VersionComparator.compareVersionStrings(version, minVersion) >= 0;
        } catch (Exception e) {
            EuphoriaPatcher.log(1, 0, "Error comparing versions: " + e.getMessage());
            return false;
        }
    }

    /**
     * Utility method to check if the detected shader loader's Minecraft version is
     * greater than or equal to a specific version.
     *
     * @param minVersion The minimum required version (e.g., "1.21.1")
     * @return true if the detected version >= minVersion, false otherwise
     */
    public static boolean isMinecraftVersionAtLeast(String minVersion) {
        String currentVersion = getShaderLoaderMCVersion();
        return isVersionGreaterOrEqual(currentVersion, minVersion);
    }

    /**
     * Gets the shader loader version from the shader loader filename.
     * Examples:
     * - iris-fabric-1.8.8+mc1.21.4.jar -> 10808
     * - oculus-mc1.20.1-1.8.0.jar -> 10800
     * - mekalus-mc1.20.1-1.7.0.3.jar -> 10700 (simplifying to 1.7.0)
     * - angelica-1.0.0-beta15.jar -> 10000
     *
     * @return The shader loader version as integer, or 0 if not detected or OptiFine
     */
    public static int getShaderLoaderVersion() {
        // Return cached result if available
        if (cachedShaderLoaderVersion != null) {
            debugLog("Using cached shader loader version: " + cachedShaderLoaderVersion);
            return cachedShaderLoaderVersion;
        }

        debugLog("Extracting shader loader version from filename");
        try {
            File shaderFile = findShaderLoaderFile();
            if (shaderFile == null) {
                debugLog("No shader file found, returning 0");
                cachedShaderLoaderVersion = 0;
                return cachedShaderLoaderVersion;
            }

            String fileName = shaderFile.getName();
            String lowerFileName = fileName.toLowerCase(Locale.ROOT);
            String extractedVersion = null;
            debugLog("Parsing shader loader version from filename: " + fileName);

            // Handle Iris format: iris-fabric-1.8.8+mc1.21.4.jar
            if (lowerFileName.startsWith("iris")) {
                debugLog("Detected Iris format");
                // Find version between "iris-fabric-" and "+mc"
                int fabricIndex = lowerFileName.indexOf("-fabric-");
                int mcIndex = lowerFileName.indexOf("+mc");
                if (fabricIndex != -1 && mcIndex != -1 && mcIndex > fabricIndex) {
                    extractedVersion = fileName.substring(fabricIndex + 8, mcIndex);
                    debugLog("Extracted Iris version: " + extractedVersion);
                }
            }
            // Handle Oculus/Mekalus format: oculus-mc1.20.1-1.8.0.jar or mekalus-mc1.20.1-1.7.0.3.jar
            else if (lowerFileName.startsWith("oculus") || lowerFileName.startsWith("mekalus")) {
                debugLog("Detected " + (lowerFileName.startsWith("mekalus") ? "Mekalus" : "Oculus") + " format");
                // Find the last dash before .jar to get the version
                int lastDashIndex = fileName.lastIndexOf('-');
                int jarIndex = lowerFileName.indexOf(".jar");
                if (lastDashIndex != -1 && jarIndex != -1 && jarIndex > lastDashIndex) {
                    String fullVersion = fileName.substring(lastDashIndex + 1, jarIndex);

                    // Handle Mekalus versioning (e.g., 1.7.0.3 -> 1.7.0)
                    if (lowerFileName.startsWith("mekalus") && fullVersion.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                        String[] parts = fullVersion.split("\\.");
                        if (parts.length >= 3) {
                            extractedVersion = parts[0] + "." + parts[1] + "." + parts[2];
                            debugLog("Simplified Mekalus version from " + fullVersion + " to " + extractedVersion);
                        }
                    } else {
                        extractedVersion = fullVersion;
                    }

                    debugLog("Extracted version: " + extractedVersion);
                }
            }
            // Handle Angelica format: angelica-1.0.0-betaXX.jar
            else if (lowerFileName.startsWith("angelica")) {
                debugLog("Detected Angelica format");
                // Find version between "angelica-" and "-beta"
                int angelicaIndex = lowerFileName.indexOf("angelica-");
                int betaIndex = lowerFileName.indexOf("-beta");
                if (angelicaIndex != -1 && betaIndex != -1 && betaIndex > angelicaIndex) {
                    extractedVersion = fileName.substring(angelicaIndex + 9, betaIndex);
                    debugLog("Extracted Angelica version: " + extractedVersion);
                }
            }
            // Handle OptiFine - return 0 as requested
            else if (lowerFileName.startsWith("optifine")) {
                debugLog("Detected OptiFine - returning 0 as requested");
                cachedShaderLoaderVersion = 0;
                return cachedShaderLoaderVersion;
            }

            // Convert the extracted version to integer
            if (extractedVersion != null) {
                try {
                    int versionInt = ShaderVersionComparator.convertShaderVersionToInt(extractedVersion);
                    debugLog("Converted shader loader version to integer: " + versionInt);
                    cachedShaderLoaderVersion = versionInt;
                    return cachedShaderLoaderVersion;
                } catch (Exception e) {
                    debugLog("Error converting shader loader version to int: " + e.getMessage());
                }
            } else {
                debugLog("Could not extract shader loader version from filename");
            }

            debugLog("Setting shader loader version to 0");
            cachedShaderLoaderVersion = 0;
            return cachedShaderLoaderVersion;
        } catch (Exception e) {
            EuphoriaPatcher.log(2, 0, "Error extracting shader loader version: " + e.getMessage());
            debugLog("Exception extracting shader loader version: " + e.getMessage());
            cachedShaderLoaderVersion = 0;
            return cachedShaderLoaderVersion;
        }
    }

    /**
     * Gets the shader loader version as a key-value pair for define injection
     * @return String array [key, value] or null if no shader loader detected
     */
    public static String[] getShaderLoaderVersionDefine() {
        int version = getShaderLoaderVersion();
        String shaderLoader = getShaderLoader();

        switch (shaderLoader) {
            case IRIS:
                return new String[]{"EUPHORIA_PATCHES_IRIS_VERSION", String.valueOf(version)};
            case OCULUS:
                return new String[]{"EUPHORIA_PATCHES_OCULUS_VERSION", String.valueOf(version)};
            case ANGELICA:
                return new String[]{"EUPHORIA_PATCHES_ANGELICA_VERSION", String.valueOf(version)};
            default:
                return null;
        }
    }

    /**
     * Locates the shader loader configuration file
     * Checks for Iris, Oculus, and OptiFine config files in that order
     * @return Path to the shader loader config file, or null if none found
     */
    public static Path getShaderLoaderConfigPath() {
        Path shaderLoaderConfig = EuphoriaPatcher.configDirectory.resolve("iris.properties");
        if (!Files.exists(shaderLoaderConfig)) shaderLoaderConfig = EuphoriaPatcher.configDirectory.resolve("oculus.properties");
        if (!Files.exists(shaderLoaderConfig)) shaderLoaderConfig = EuphoriaPatcher.shaderpacks.getParent().resolve("optionsshaders.txt");
        if (!Files.exists(shaderLoaderConfig)) shaderLoaderConfig = null;
        return shaderLoaderConfig;
    }

    /**
     * Gets the path to the currently selected shaderpack by reading the shader loader config
     * @return Path to the current shaderpack directory or zip file, or null if none is selected or an error occurs
     */
    public static Path getCurrentShaderpackPath() {
        Path shaderLoaderConfig = getShaderLoaderConfigPath();
        if (shaderLoaderConfig == null) {
            return null;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(shaderLoaderConfig.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("shaderPack=")) {
                    String shaderpackName = line.substring("shaderPack=".length()).trim();

                    // Check if no shaderpack is selected (empty or "OFF")
                    if (shaderpackName.isEmpty() || shaderpackName.equalsIgnoreCase("OFF")) {
                        return null;
                    }

                    // Find the actual shader file by name without relying on direct path resolution
                    try {
                        return findShaderpackByName(shaderpackName);
                    } catch (Exception e) {
                        debugLog("Error finding shaderpack: " + e.getMessage());
                        EuphoriaPatcher.log(2, 0, "Could not find shaderpack: " + shaderpackName + " - " + e.getMessage());
                        return null;
                    }
                }
            }
        } catch (IOException e) {
            EuphoriaPatcher.log(3, 0, "Error reading shader loader config: " + e.getMessage());
        }

        return null;
    }

    /**
     * Finds a shaderpack by name in the shaderpacks directory
     * Handles special characters that may cause issues with direct path resolution
     * @param shaderpackName The name of the shaderpack to find
     * @return Path to the shaderpack, or null if not found
     * @throws IOException if there's an error reading the directory
     */
    private static Path findShaderpackByName(String shaderpackName) throws IOException {
        // First try direct resolution (will work for normal filenames)
        try {
            Path directPath = EuphoriaPatcher.shaderpacks.resolve(shaderpackName);
            if (Files.exists(directPath)) {
                debugLog("Found shader directly: " + directPath);
                return directPath;
            }
        } catch (InvalidPathException e) {
            debugLog("Invalid path characters in shader name: " + e.getMessage());
        }

        debugLog("Direct path resolution failed for: " + shaderpackName + ", trying directory scan");

        // If direct resolution fails (likely due to special characters), list files and find match
        String normalizedName = normalizeShaderName(shaderpackName);
        debugLog("Normalized shader name: " + normalizedName);

        // Also try the special case for our error shader
        final boolean isErrorShader = shaderpackName.contains("EuphoriaPatches") &&
                                     shaderpackName.contains("Error") &&
                                     shaderpackName.contains("Shader");

        try (Stream<Path> fileStream = Files.list(EuphoriaPatcher.shaderpacks)) {
            // Try to find a file that matches when normalized
            Path result = fileStream
                    .filter(Files::exists)
                    .filter(path -> {
                        String fileName = path.getFileName().toString();

                        // Check exact match first
                        if (fileName.equals(shaderpackName)) {
                            debugLog("Found exact shader match: " + fileName);
                            return true;
                        }

                        // Special handling for error shader
                        if (isErrorShader && fileName.contains("EuphoriaPatches") &&
                            fileName.contains("Error") && fileName.contains("Shader")) {
                            debugLog("Found error shader: " + fileName);
                            return true;
                        }

                        // Try normalized match
                        String normalizedFileName = normalizeShaderName(fileName);
                        boolean matches = normalizedFileName.equals(normalizedName);
                        if (matches) {
                            debugLog("Found matching shader via normalization: " + fileName);
                        }
                        return matches;
                    })
                    .findFirst().orElse(null);

            if (result == null) {
                debugLog("No matching shader found in directory scan");
            }
            return result;
        }
    }

    /**
     * Properly normalizes a shader name by removing special characters
     * @param name The shader name to normalize
     * @return The normalized shader name
     */
    private static String normalizeShaderName(String name) {
        // First handle the § character specifically
        String withoutSection = name.replace("§", "").replace("\\u00A7", "");

        // Then handle other special characters
        String safeChars = withoutSection.replaceAll("[^a-zA-Z0-9_ \\-.+()\\[\\]{}]", "");

        if (!name.equals(safeChars)) {
            debugLog("Normalized '" + name + "' to '" + safeChars + "'");
        }
        return safeChars.trim();
    }
}
