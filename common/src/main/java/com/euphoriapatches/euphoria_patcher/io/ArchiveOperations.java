package com.euphoriapatches.euphoria_patcher.io;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.targets.ShaderTarget;
import com.euphoriapatches.euphoria_patcher.targets.ShaderTargets;
import com.euphoriapatches.euphoria_patcher.util.HashUtils;
import com.euphoriapatches.euphoria_patcher.util.shader.ShaderVersionComparator;
import com.euphoriapatches.euphoria_patcher.util.UserInstallErrorMessages;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ArchiveOperations {

    private static ShaderVersionComparator cachedVersionComparator = null;

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ArchiveOperations] " + message);
    }

    /**
     * Get version comparator instance from EuphoriaPatcher
     */
    private static ShaderVersionComparator getVersionComparator() {
        if (cachedVersionComparator == null) {
            EuphoriaPatcher instance = EuphoriaPatcher.getInstance();
            cachedVersionComparator = instance != null ? instance.getVersionComparator() : null;
        }
        return cachedVersionComparator;
    }

    public static Path extract(Path source, Path targetDir, String operationName) {
        try {
            debugLog("Extracting: " + source.getFileName() + " to " + targetDir);
            if (!Files.isDirectory(source)) {
                ArchiveUtils.extract(source, targetDir);
                debugLog("Extraction completed successfully");
            } else {
                debugLog("Source is already a directory, no extraction needed");
                return source; // Already a directory
            }
            return targetDir;
        } catch (IOException | ArchiveException e) {
            debugLog("Error " + operationName + ": " + e.getMessage());
            EuphoriaPatcher.log(2, "Error " + operationName + ": " + e.getMessage());
            return null;
        }
    }

    public static Path archive(Path source, Path targetArchive) {
        try {
            debugLog("Archiving: " + source.getFileName() + " to " + targetArchive);
            ArchiveUtils.archive(source, targetArchive);
            debugLog("Archiving completed successfully");
            return targetArchive;
        } catch (IOException e) {
            debugLog("Error creating archive: " + e.getMessage());
            EuphoriaPatcher.log(2, "Error creating archive: " + e.getMessage());
            return null;
        }
    }

    /**
     * Create a temporary directory for shader operations
     */
    public static Path createTempDirectory() {
        try {
            Path temp = Files.createTempDirectory("euphoria-patcher-");
            debugLog("Created temporary directory: " + temp);
            return temp;
        } catch (IOException e) {
            debugLog("Error creating temporary directory: " + e.getMessage());
            EuphoriaPatcher.log(3, "Error creating temporary directory: " + e.getMessage());
            return null;
        }
    }

    /**
     * Cleans up a temporary directory
     */
    public static void deleteTempDirectory(Path tempDir) {
        if (tempDir == null || !Files.exists(tempDir)) {
            debugLog("Temp directory is null or does not exist: " + tempDir);
            return;
        }
        try {
            debugLog("Cleaning up temp directory: " + tempDir);
            FileUtils.deleteDirectory(tempDir.toFile());
        } catch (IOException e) {
            debugLog("Failed to clean up temp directory: " + e.getMessage());
        }
    }

    public static void deleteRecursively(Path path) throws IOException { // needed to delete folders
        if (Files.isDirectory(path)) {
            try (Stream<Path> entries = Files.list(path)) {
                entries.forEach(entry -> {
                    try {
                        deleteRecursively(entry);
                    } catch (IOException e) {
                        EuphoriaPatcher.log(2, 0, "Error deleting entry: " + entry + " - " + e.getMessage());
                    }
                });
            }
        }
        Files.delete(path);
    }

    /**
     * Finds an entry by exact path first, then by the same path nested one directory deeper.
     * Example fallback: "folder/shaders/file.glsl" for requested "shaders/file.glsl".
     */
    private static String normalizeZipEntryPath(String path) {
        String normalized = path.replace("\\", "/");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static ZipEntry findEntryWithOptionalSingleTopLevelFolder(ZipFile zipFile, String filePathInZip) {
        String normalizedPath = normalizeZipEntryPath(filePathInZip);

        ZipEntry exactMatch = zipFile.getEntry(normalizedPath);
        if (exactMatch != null && !exactMatch.isDirectory()) {
            return exactMatch;
        }

        String nestedSuffix = "/" + normalizedPath;
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry candidate = entries.nextElement();
            if (candidate == null || candidate.isDirectory()) {
                continue;
            }

            String candidatePath = normalizeZipEntryPath(candidate.getName());
            if (!candidatePath.endsWith(nestedSuffix)) {
                continue;
            }

            String topLevelFolder = candidatePath.substring(0, candidatePath.length() - nestedSuffix.length());
            if (!topLevelFolder.isEmpty() && topLevelFolder.indexOf('/') == -1) {
                debugLog("Resolved nested zip entry: " + candidatePath + " for requested path: " + normalizedPath);
                return candidate;
            }
        }

        return null;
    }

    /**
     * Check if a file exists inside a zip archive
     * @param zipPath Path to the zip file
     * @param filePathInZip Path to the file inside the zip (e.g., "shaders/myFile.glsl")
     * @return true if the file exists in the zip
     */
    public static boolean fileExistsInZip(Path zipPath, String filePathInZip) {
        if (!Files.exists(zipPath) || !zipPath.toString().endsWith(".zip")) {
            return false;
        }

        int maxAttempts = 3;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
                ZipEntry entry = findEntryWithOptionalSingleTopLevelFolder(zipFile, filePathInZip);
                boolean exists = entry != null && !entry.isDirectory();

                debugLog("Checked for " + filePathInZip + " in " + zipPath.getFileName() + ": " + exists + " (attempt " + attempt + ")");
                return exists;

            } catch (IOException e) {
                debugLog("Error checking file in zip (attempt " + attempt + "): " + e.getMessage());
            }
        }

        debugLog("Failed after " + maxAttempts + " attempts, returning false");
        return false;
    }

    /**
     * Read a file's content from a zip archive
     * @param zipPath Path to the zip file
     * @param filePathInZip Path to the file inside the zip (e.g., "shaders/pack.json")
     * @return The file content as a String, or null if the file doesn't exist or an error occurs
     */
    public static String readFileFromZip(Path zipPath, String filePathInZip) {
        if (!Files.exists(zipPath) || !zipPath.toString().endsWith(".zip")) {
            return null;
        }

        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            ZipEntry entry = findEntryWithOptionalSingleTopLevelFolder(zipFile, filePathInZip);
            if (entry == null || entry.isDirectory()) {
                debugLog("File not found in zip: " + filePathInZip);
                return null;
            }

            // Read the file content
            StringBuilder content = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(zipFile.getInputStream(entry)))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }

            debugLog("Read " + content.length() + " bytes from " + filePathInZip + " in " + zipPath.getFileName());
            return content.toString();
        } catch (IOException e) {
            debugLog("Error reading file from zip: " + e.getMessage());
            return null;
        }
    }

    public static boolean verifyBaseArchive(Path baseArchived, String originalFileName) {
        return verifyBaseArchive(baseArchived, originalFileName, ShaderTargets.defaultTarget());
    }

    public static boolean verifyBaseArchive(Path baseArchived, String originalFileName, ShaderTarget target) {
        try {
            String fileName = baseArchived.getFileName().toString();
            debugLog("Verifying archive: " + fileName + " against target " + target.getId());

            long fileSize = Files.size(baseArchived);
            debugLog("Archive size: " + fileSize + " bytes, expected: " + target.getBaseTarSize() + " bytes");

            // First check: byte size (fast check)
            boolean isValidSize = fileSize == target.getBaseTarSize();

            if (!isValidSize) {
                debugLog("Invalid archive size: verification failed");
                ShaderVersionComparator versionComparator = getVersionComparator();
                UserInstallErrorMessages.handleSizeMismatch(fileName, originalFileName, versionComparator);
                return false;
            }

            // Check for test/dev version
            if (ShaderVersionComparator.isTestOrDevVersion(fileName)) {
                debugLog("Test/fix version detected: " + originalFileName);
                UserInstallErrorMessages.handleDevVersion(fileName, originalFileName);
                return false;
            }

            // Second check: content hash verification (if size matches)
            if (HashUtils.hasIncorrectHash(baseArchived, target.getBaseTarSha256())) {
                debugLog("Archive hash verification failed - file has been modified");
                UserInstallErrorMessages.handleHashMismatch(fileName, originalFileName);
                return false;
            }

            debugLog("Archive size and hash verification passed");
        } catch (IOException e) {
            debugLog("Error during archive verification: " + e.getMessage());
            EuphoriaPatcher.log(3, "Something went wrong during the file verification: " + e.getMessage());
            return false;
        }
        return true;
    }

    public static boolean verifyBaseArchiveQuiet(Path baseArchived) {
        return verifyBaseArchiveQuiet(baseArchived, ShaderTargets.defaultTarget());
    }

    public static boolean verifyBaseArchiveQuiet(Path baseArchived, ShaderTarget target) {
        try {
            String fileName = baseArchived.getFileName().toString();
            debugLog("Quietly verifying archive: " + fileName + " against target " + target.getId());

            long fileSize = Files.size(baseArchived);

            boolean isValidSize = fileSize == target.getBaseTarSize();
            debugLog("Archive size: " + fileSize + " bytes, expected: " + target.getBaseTarSize() + " bytes, valid: " + isValidSize);
            if (!isValidSize) {
                return false;
            }

            // Check for test/fix version markers in filename
            if (ShaderVersionComparator.isTestOrDevVersion(fileName)) {
                debugLog("Test/fix version detected in quiet check: " + fileName);
                return false;
            }
            /*Skip hash verification in quiet mode since the byte check will rename matching files
            These renamed files will undergo full verification through the regular process later
            Hash verification here would incorrectly fail for renamed Unbound files since renaming appears to alter the hash
            For quiet mode, we consider size-matched files potentially valid, pending full verification in the subsequent steps*/

            return true;
        } catch (IOException e) {
            debugLog("Error during quiet archive verification: " + e.getMessage());
            return false;
        }
    }
}
