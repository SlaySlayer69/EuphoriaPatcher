package com.euphoriapatches.euphoria_patcher.util;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.PatchInfo;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

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

    public static boolean verifyBaseArchive(Path baseArchived, String originalFileName) {
        try {
            String fileName = baseArchived.getFileName().toString();
            debugLog("Verifying archive: " + fileName);

            long fileSize = Files.size(baseArchived);
            debugLog("Archive size: " + fileSize + " bytes, expected: " + PatchInfo.BASE_TAR_SIZE + " bytes");

            // First check: byte size (fast check)
            boolean isValidSize = fileSize == PatchInfo.BASE_TAR_SIZE;

            if (!isValidSize) {
                debugLog("Invalid archive size: verification failed");
                ShaderVersionComparator versionComparator = getVersionComparator();
                ShaderValidationErrorHandler.handleSizeMismatch(fileName, originalFileName, versionComparator);
                return false;
            }

            // Check for test/dev version
            if (ShaderVersionComparator.isTestOrDevVersion(fileName)) {
                debugLog("Test/fix version detected: " + originalFileName);
                ShaderValidationErrorHandler.handleDevVersion(fileName, originalFileName);
                return false;
            }

            // Second check: content hash verification (if size matches)
            if (HashUtils.hasIncorrectHash(baseArchived, PatchInfo.BASE_TAR_SHA256)) {
                debugLog("Archive hash verification failed - file has been modified");
                ShaderValidationErrorHandler.handleHashMismatch(fileName, originalFileName);
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
        try {
            String fileName = baseArchived.getFileName().toString();
            debugLog("Quietly verifying archive: " + fileName);

            long fileSize = Files.size(baseArchived);

            boolean isValidSize = fileSize == PatchInfo.BASE_TAR_SIZE;
            debugLog("Archive size: " + fileSize + " bytes, expected: " + PatchInfo.BASE_TAR_SIZE + " bytes, valid: " + isValidSize);
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
