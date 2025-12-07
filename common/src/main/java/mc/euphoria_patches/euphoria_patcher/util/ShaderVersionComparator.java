package mc.euphoria_patches.euphoria_patcher.util;

import mc.euphoria_patches.euphoria_patcher.logging.EuphoriaLogger;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for shader version comparison and extraction
 */
public class ShaderVersionComparator {
    private final String brandName;
    private final String patchName;
    private final String version;
    private final Path shaderpacks;
    private ShaderVersionComparator instance;
    public static final Pattern VERSION_PATTERN = Pattern.compile("1\\.(\\d+)\\.(\\d+)");

    public ShaderVersionComparator(String brandName, String patchName, String version, Path shaderpacks) {
        this.brandName = brandName;
        this.patchName = patchName;
        this.version = version;
        this.shaderpacks = shaderpacks;
    }

    public ShaderVersionComparator getInstance() {
        if (instance == null) {
            instance = new ShaderVersionComparator(brandName, patchName, version, shaderpacks);
        }
        return instance;
    }
    /**
     * Checks if the given filename represents a newer version of the shader than what's expected
     * @param fileName The filename to check
     * @return true if it's a newer version, false otherwise
     */
    public boolean isNewerShaderVersion(String fileName) {
        // First check if it's a Complementary shader
        if (!fileName.contains(brandName)) {
            return false;
        }

        // Extract version numbers using regex
        int[] fileVersion = extractVersionNumbers(fileName);
        int[] targetVersion = extractVersionNumbers(version);

        // Compare versions using generic comparator
        return VersionComparator.compareVersionArrays(fileVersion, targetVersion) > 0;
    }

    /**
     * Extract version string from a filename
     * @param fileName The filename to extract from
     * @return Version string in format "r5.1" or "r5.3.2"
     */
    public String getVersionStringFromFileName(String fileName) {
        int[] versionNumbers = extractVersionNumbers(fileName);
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
    public int[] extractVersionNumbers(String filename) {
        int[] version = {0, 0, 0};

        // Extract r-version number (e.g., _r5.1 or _r5.3.2)
        Pattern pattern = Pattern.compile("_r(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
        Matcher matcher = pattern.matcher(filename);

        if (matcher.find()) {
            version[0] = Integer.parseInt(matcher.group(1));  // Major
            version[1] = Integer.parseInt(matcher.group(2));  // Minor
            version[2] = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;  // Patch
        }

        return version;
    }

    /**
     * Converts a shader loader version string to integer representation
     * @param versionString Version in format "1.8.8"
     * @return Integer representation (10808)
     */
    public static int convertShaderVersionToInt(String versionString) {
        Matcher matcher = VERSION_PATTERN.matcher(versionString);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid version format: " + versionString);
        }

        // Extract parts from 1.XX.YY format
        int minor = Integer.parseInt(matcher.group(1));
        int release = Integer.parseInt(matcher.group(2));

        // Convert to format 1MMRR
        return 10000 + (minor * 100) + release;
    }

    /**
     * Finds the highest version of any older Complementary shader
     * @return Path to the highest version file/directory, or null if none found
     */
    public Path findHighestOlderVersion() {
        Path highestVersionPath = null;
        int[] highestVersion = {0, 0, 0}; // major, minor, patch

        try {
            // Check files
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks)) {
                for (Path path : stream) {
                    if (isOlderBrandNameShader(path, Files.isRegularFile(path) && path.toString().endsWith(".zip"))) {
                        int[] version = extractVersionNumbers(path.getFileName().toString());
                        if (VersionComparator.compareVersionArrays(version, highestVersion) > 0) {
                            highestVersion = version;
                            highestVersionPath = path;
                        }
                    }
                }
            }
        } catch (IOException e) {
            EuphoriaLogger.debugLog("[ShaderVersionComparator] Error checking for older shader versions: " + e.getMessage());
        }

        return highestVersionPath;
    }

    /**
     * Checks if a path is an older version of Complementary shader
     */
    public boolean isOlderBrandNameShader(Path path, boolean isFile) {
        String name = path.getFileName().toString();

        // First check if it's a Complementary shader without the patch
        boolean isComplementary = name.contains(brandName) &&
                                 name.matches(".*_r\\d+\\.\\d+(?:\\.\\d+)?.*") &&
                                 !name.contains(patchName);

        if (isComplementary) {
            // Extract version numbers and compare
            int[] fileVersion = extractVersionNumbers(name);
            int[] targetVersion = extractVersionNumbers(version);

            // Only consider it "older" if the version is actually lower
            boolean isOlder = VersionComparator.compareVersionArrays(fileVersion, targetVersion) < 0;

            return isOlder && (isFile ? name.endsWith(".zip") : Files.isDirectory(path));
        }

        return false;
    }

    /**
     * Validates if a shader file/directory name matches the expected version format
     * @return true if it matches the expected brand, version format, and is not a test/dev version
     */
    public static boolean isTestOrDevVersion(String fileName) {
        String fileNameLower = fileName.toLowerCase(Locale.ROOT);
        return fileNameLower.contains("test") || fileNameLower.contains("fix") ||
                fileNameLower.contains("dev") || fileNameLower.contains("pre");
    }
}
