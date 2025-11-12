package mc.euphoria_patches.euphoria_patcher.util;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import org.apache.commons.compress.archivers.ArchiveException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public class ArchiveOperations {

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ArchiveOperations] " + message);
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

    public static boolean verifyBaseArchive(Path baseArchived, String originalFileName) {
        try {
            String fileName = baseArchived.getFileName().toString();
            debugLog("Verifying archive: " + fileName);
            
            if (EuphoriaPatcher.isDevFunc()) {
                long fileSize = Files.size(baseArchived);
                EuphoriaPatcher.log(0, "Archive Name: " + fileName + " Archive size: " + fileSize + " bytes");
                String hash = calculateSHA256(baseArchived);
                EuphoriaPatcher.log(0, "Archive SHA-256: " + hash);
            } else {
                // Get the file size
                long fileSize = Files.size(baseArchived);
                debugLog("Archive size: " + fileSize + " bytes, expected: " + EuphoriaPatcher.BASE_TAR_SIZE + " bytes");
                
                // First check: byte size (fast check)
                boolean isValidSize = fileSize == EuphoriaPatcher.BASE_TAR_SIZE;
                
                if (!isValidSize) {
                    debugLog("Invalid archive size: verification failed");

                    // First identify if it's a newer version
                    if (EuphoriaPatcher.isNewerShaderVersion(fileName)) {
                        // Get version string from filename
                        String detectedVersion = EuphoriaPatcher.getVersionStringFromFileName(fileName);
                        
                        // Newer version detected - recommend mod update if available
                        EuphoriaPatcher.log(3, 8, "=== VERSION MISMATCH ===");
                        EuphoriaPatcher.log(3, 8, "Found shader: " + originalFileName + " (version " + detectedVersion + ")");
                        EuphoriaPatcher.log(3, 8, "Required shader: " + EuphoriaPatcher.BRAND_NAME + "Shaders " + EuphoriaPatcher.VERSION);
                        EuphoriaPatcher.log(3, 8, "You have a NEWER shader version than what this mod version supports.");
                        
                        if (UpdateChecker.isUpdateAvailable()) {
                            // Update available - recommend updating the mod
                            EuphoriaPatcher.log(3, 8, "");
                            EuphoriaPatcher.log(3, 8, "SOLUTION: Update " + EuphoriaPatcher.PATCH_NAME + " to the latest version: " + UpdateChecker.getNewModVersion());
                            EuphoriaPatcher.log(3, 8, "Download from: https://euphoriapatches.com/download");
                        } else {
                            // No update available - need the specific version
                            EuphoriaPatcher.log(3, 8, "");
                            EuphoriaPatcher.log(3, 8, "SOLUTION 1: Wait for a " + EuphoriaPatcher.PATCH_NAME + " update that supports version " + detectedVersion);
                            EuphoriaPatcher.log(3, 8, "SOLUTION 2: Download the compatible shader version " + EuphoriaPatcher.VERSION);
                            EuphoriaPatcher.log(3, 8, "Download from: " + EuphoriaPatcher.DOWNLOAD_URL);
                        }
                    }
                    // Check if it's the correct version with incorrect size
                    else if (fileName.matches(EuphoriaPatcher.BRAND_NAME + ".*" + EuphoriaPatcher.VERSION + ".*")) {
                        EuphoriaPatcher.log(3, 8, "=== FILE VERIFICATION FAILED ===");
                        EuphoriaPatcher.log(3, 8, "Shader file: " + originalFileName);
                        EuphoriaPatcher.log(3, 8, "This file appears to be incomplete or has been modified.");
                        EuphoriaPatcher.log(3, 8, "This can happen if the shader was manually edited or if it's from an unofficial source.");
                        EuphoriaPatcher.log(3, 8, "");
                        EuphoriaPatcher.log(3, 8, "SOLUTION: Re-download " + EuphoriaPatcher.BRAND_NAME + "Shaders " + EuphoriaPatcher.VERSION);
                        EuphoriaPatcher.log(3, 8, "Download from: " + EuphoriaPatcher.DOWNLOAD_URL);
                    }
                    // Wrong version completely
                    else {
                        EuphoriaPatcher.log(3, 8, "=== WRONG SHADER VERSION ===");
                        EuphoriaPatcher.log(3, 8, "Found: " + originalFileName);
                        EuphoriaPatcher.log(3, 8, "Required: " + EuphoriaPatcher.BRAND_NAME + "Shaders " + EuphoriaPatcher.VERSION);
                        EuphoriaPatcher.log(3, 8, "");
                        EuphoriaPatcher.log(3, 8, "SOLUTION: Download the correct shader version " + EuphoriaPatcher.VERSION);
                        EuphoriaPatcher.log(3, 8, "Download from: " + EuphoriaPatcher.DOWNLOAD_URL);
                    }

                    EuphoriaPatcher.log(0, "Watching for the correct shader to be added...");

                    // Start the watcher
                    EuphoriaPatcher instance = EuphoriaPatcher.getInstance();
                    if (instance != null) {
                        instance.startWatcherAfterByteSizeFailure();
                        // If we have a watcher, track this file as invalid
                        ShaderpacksWatcher watcher = instance.getShaderpacksWatcher();
                        if (watcher != null) {
                            watcher.trackInvalidByteSizeFile(fileName);
                        }
                    }

                    return false;
                }

                // If size matches, check if it's a test/fix version by filename
                if (isTestOrDevVersion(fileName)) {
                    debugLog("Test/fix version detected: " + originalFileName);
                    EuphoriaPatcher.log(3, 8, "=== DEV VERSION DETECTED ===");
                    EuphoriaPatcher.log(3, 8, "Found: " + originalFileName);
                    EuphoriaPatcher.log(3, 8, "This appears to be a test, dev, or pre-release version.");
                    EuphoriaPatcher.log(3, 8, "");
                    EuphoriaPatcher.log(3, 8, "SOLUTION: Download the official " + EuphoriaPatcher.BRAND_NAME + " release version: " + EuphoriaPatcher.VERSION);
                    EuphoriaPatcher.log(3, 8, "Download from: " + EuphoriaPatcher.DOWNLOAD_URL);

                    // Start the watcher
                    EuphoriaPatcher instance = EuphoriaPatcher.getInstance();
                    if (instance != null) {
                        instance.startWatcherAfterByteSizeFailure();
                        // If we have a watcher, track this file as invalid
                        ShaderpacksWatcher watcher = instance.getShaderpacksWatcher();
                        if (watcher != null) {
                            watcher.trackInvalidByteSizeFile(fileName);
                        }
                    }
                    
                    return false;
                }

                // Second check: content hash verification (if size matches)
                if (detectIncorrectHash(baseArchived)) {
                    debugLog("Archive hash verification failed - file has been modified");
                    EuphoriaPatcher.log(3, 8, "=== FILE VERIFICATION FAILED ===");
                    EuphoriaPatcher.log(3, 8, "Shader file: " + originalFileName);
                    EuphoriaPatcher.log(3, 8, "This file appears to have been modified.");
                    EuphoriaPatcher.log(3, 8, "This can happen if the shader was manually edited or if it's from an unofficial source.");
                    EuphoriaPatcher.log(3, 8, "File size matches but content hash does not.");
                    EuphoriaPatcher.log(3, 8, "");
                    EuphoriaPatcher.log(3, 8, "SOLUTION: Download the original unmodified " + EuphoriaPatcher.BRAND_NAME + " shader");
                    EuphoriaPatcher.log(3, 8, "Download from: " + EuphoriaPatcher.DOWNLOAD_URL);

                    // Start the watcher
                    EuphoriaPatcher instance = EuphoriaPatcher.getInstance();
                    if (instance != null) {
                        instance.startWatcherAfterByteSizeFailure();
                        // If we have a watcher, track this file as invalid
                        ShaderpacksWatcher watcher = instance.getShaderpacksWatcher();
                        if (watcher != null) {
                            watcher.trackInvalidByteSizeFile(fileName);
                        }
                    }
                    
                    return false;
                }

                debugLog("Archive size and hash verification passed");
            }
        } catch (IOException e) {
            debugLog("Error during archive verification: " + e.getMessage());
            EuphoriaPatcher.log(3, "Something went wrong during the file verification: " + e.getMessage());
            return false;
        }
        return true;
    }

    private static String calculateSHA256(Path filePath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(filePath);
            byte[] hashBytes = digest.digest(fileBytes);
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            debugLog("Error calculating SHA-256: " + e.getMessage());
            return null;
        }
    }

    private static boolean detectIncorrectHash(Path archivePath) {
        String expectedHash = EuphoriaPatcher.BASE_TAR_SHA256;
        
        String actualHash = calculateSHA256(archivePath);
        if (actualHash == null) {
            return true;
        }
        
        boolean hashMatches = expectedHash.equals(actualHash);
        debugLog("Hash verification - Expected: " + expectedHash);
        debugLog("Hash verification - Actual:   " + actualHash);
        debugLog("Hash verification - Match: " + hashMatches);
        
        return !hashMatches;
    }

    private static boolean isTestOrDevVersion(String fileName) {
        String fileNameLower = fileName.toLowerCase(Locale.ROOT);
        return fileNameLower.contains("test") || fileNameLower.contains("fix") || 
            fileNameLower.contains("dev") || fileNameLower.contains("pre");
    }

    public static boolean verifyBaseArchiveQuiet(Path baseArchived) {
        try {
            String fileName = baseArchived.getFileName().toString();
            debugLog("Quietly verifying archive: " + fileName);
            
            if (EuphoriaPatcher.isDevFunc()) {
                debugLog("Dev mode: bypassing verification (returning true)");
            } else {
                long fileSize = Files.size(baseArchived);
                
                boolean isValidSize = fileSize == EuphoriaPatcher.BASE_TAR_SIZE;
                debugLog("Archive size: " + fileSize + " bytes, expected: " + EuphoriaPatcher.BASE_TAR_SIZE + " bytes, valid: " + isValidSize);
                if (!isValidSize) {
                    return false;
                }

                // Check for test/fix version markers in filename
                if (isTestOrDevVersion(fileName)) {
                    debugLog("Test/fix version detected in quiet check: " + fileName);
                    return false;
                }
                /*Skip hash verification in quiet mode since the byte check will rename matching files
                These renamed files will undergo full verification through the regular process later
                Hash verification here would incorrectly fail for renamed Unbound files since renaming appears to alter the hash
                For quiet mode, we consider size-matched files potentially valid, pending full verification in the subsequent steps*/
            }
            return true; // In dev mode, accept any file
        } catch (IOException e) {
            debugLog("Error during quiet archive verification: " + e.getMessage());
            return false;
        }
    }
}