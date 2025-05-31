package mc.euphoria_patches.euphoria_patcher.util;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import org.apache.commons.compress.archivers.ArchiveException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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
                        EuphoriaPatcher.log(3, 8, "Newer shader version in shaderpacks folder detected: " + originalFileName);
                        EuphoriaPatcher.log(3, 8, EuphoriaPatcher.PATCH_NAME + EuphoriaPatcher.PATCH_VERSION + " only works with " + 
                            EuphoriaPatcher.BRAND_NAME + "Shaders" + EuphoriaPatcher.VERSION);
                        
                        if (UpdateChecker.isUpdateAvailable() && EuphoriaPatcher.doUpdateChecking) {
                            // Update available - recommend updating the mod
                            EuphoriaPatcher.log(3, 8, "Please update " + EuphoriaPatcher.PATCH_NAME + " to the latest version to support this shader version: " + detectedVersion);
                            EuphoriaPatcher.log(3, 8, "Download it from Modrinth: https://euphoriapatches.com/download");
                        } else {
                            // No update available - need the specific version
                            EuphoriaPatcher.log(3, 8, "This version of " + EuphoriaPatcher.PATCH_NAME + " requires " + 
                                EuphoriaPatcher.BRAND_NAME + "Shaders" + EuphoriaPatcher.VERSION);
                            EuphoriaPatcher.log(3, 8, "Please download it from " + EuphoriaPatcher.DOWNLOAD_URL + " or check for an " + EuphoriaPatcher.PATCH_NAME + " update manually at https://euphoriapatches.com/download");
                        }
                    }
                    // Check if it's the correct version with incorrect size
                    else if (fileName.matches(EuphoriaPatcher.BRAND_NAME + ".*" + EuphoriaPatcher.VERSION + ".*")) {
                        EuphoriaPatcher.log(3, 8, "The shader " + EuphoriaPatcher.BRAND_NAME + "Shaders" + " that was found in your shaderpacks folder can't be used as a base for " + EuphoriaPatcher.PATCH_NAME);
                        EuphoriaPatcher.log(3, 8, "The shader file appears to be incomplete or has been modified.");
                        EuphoriaPatcher.log(3, 8, "Please re-download " + EuphoriaPatcher.BRAND_NAME + "Shaders" + EuphoriaPatcher.VERSION + 
                            " from " + EuphoriaPatcher.DOWNLOAD_URL + " and place it in the shaderpacks folder.");
                    }
                    // Wrong version completely
                    else {
                        EuphoriaPatcher.log(3, 8, "The shader " + EuphoriaPatcher.BRAND_NAME + "Shaders" + " that was found in your shaderpacks folder can't be used as a base for " + EuphoriaPatcher.PATCH_NAME);
                        EuphoriaPatcher.log(3, 8, "Please download " + EuphoriaPatcher.BRAND_NAME + "Shaders" + EuphoriaPatcher.VERSION + 
                            " from " + EuphoriaPatcher.DOWNLOAD_URL + " and place it in the shaderpacks folder.");
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
                    EuphoriaPatcher.log(3, 8, "Test or development version detected: " + originalFileName);
                    EuphoriaPatcher.log(3, 8, EuphoriaPatcher.PATCH_NAME + " requires the official release of " + 
                        EuphoriaPatcher.BRAND_NAME + "Shaders" + EuphoriaPatcher.VERSION);
                    EuphoriaPatcher.log(3, 8, "Please download it from " + EuphoriaPatcher.DOWNLOAD_URL);

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
                    EuphoriaPatcher.log(3, 8, "The shader file that was found in your shaderpacks folder appears to have been modified.");
                    EuphoriaPatcher.log(3, 8, "This can happen if the shader was manually edited or if it's from an unofficial source.");
                    EuphoriaPatcher.log(3, 8, "Name of the potentially modified shader: " + originalFileName);
                    EuphoriaPatcher.log(3, 8, "Please download the original " + EuphoriaPatcher.BRAND_NAME + "Shaders" + EuphoriaPatcher.VERSION + 
                        " from " + EuphoriaPatcher.DOWNLOAD_URL + " and place it in the shaderpacks folder.");

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
        String fileNameLower = fileName.toLowerCase();
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