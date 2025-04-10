package mc.euphoria_patches.euphoria_patcher.util;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class ArchiveOperations {
    public static Path extract(Path source, Path targetDir, String operationName) {
        try {
            if (!Files.isDirectory(source)) {
                ArchiveUtils.extract(source, targetDir);
            } else {
                return source; // Already a directory
            }
            return targetDir;
        } catch (IOException | ArchiveException e) {
            EuphoriaPatcher.log(2, "Error " + operationName + ": " + e.getMessage());
            return null;
        }
    }

    public static Path archive(Path source, Path targetArchive) {
        try {
            ArchiveUtils.archive(source, targetArchive);
            return targetArchive;
        } catch (IOException e) {
            EuphoriaPatcher.log(2, "Error creating archive: " + e.getMessage());
            return null;
        }
    }

    public static boolean verifyBaseArchive(Path baseArchived) {
        try {
            if (EuphoriaPatcher.isDevFunc()) {
                String hash = DigestUtils.md5Hex(Files.newInputStream(baseArchived));
                EuphoriaPatcher.log(0, "Hash of base: " + hash);
                EuphoriaPatcher.log(0, FileUtils.sizeOf(baseArchived.toFile()) + " bytes");
            } else {
                String hash = DigestUtils.md5Hex(Arrays.copyOf(Files.readAllBytes(baseArchived), EuphoriaPatcher.BASE_TAR_SIZE));
                if (!hash.equals(EuphoriaPatcher.BASE_TAR_HASH)) {
                    EuphoriaPatcher.log(3, 8, "The shader " + EuphoriaPatcher.BRAND_NAME + "Shaders" + " that was found in your shaderpacks folder can't be used as a base for " + EuphoriaPatcher.PATCH_NAME);
                    EuphoriaPatcher.log(3, 8, "Please download " + EuphoriaPatcher.BRAND_NAME + "Shaders" + EuphoriaPatcher.VERSION + " from " + EuphoriaPatcher.DOWNLOAD_URL + " and place it into your shaderpacks folder.");
                    // Track the file with invalid hash
                    String fileName = baseArchived.getFileName().toString();

                    if (fileName.matches(EuphoriaPatcher.BRAND_NAME + ".*" + EuphoriaPatcher.VERSION + ".*")) {
                        EuphoriaPatcher.log(3, 8, "Correct Shader Version Found. BUT it might have been modified. The expected hash does not match - make sure to download from official sources.");
                    } else {
                        EuphoriaPatcher.log(3, 8, "Incorrect Shader Version found or unexpected error. The expected hash does not match.");
                    }

                    EuphoriaPatcher.log(0, "Watching for the correct shader to be added...");

                    // Start the watcher
                    EuphoriaPatcher instance = EuphoriaPatcher.getInstance();
                    if (instance != null) {
                        instance.startWatcherAfterHashFailure();
                        // If we have a watcher, track this file as invalid
                        ShaderpacksWatcher watcher = instance.getShaderpacksWatcher();
                        if (watcher != null) {
                            watcher.trackInvalidHashFile(fileName);
                        }
                    }

                    return false;
                }
            }
        } catch (IOException e) {
            EuphoriaPatcher.log(3, "Something went wrong during the hash verification" + e.getMessage());
            return false;
        }
        return true;
    }

    public static boolean verifyBaseArchiveQuiet(Path baseArchived) {
        try {
            if (EuphoriaPatcher.isDevFunc()) {
                return true; // In dev mode, accept any file
            } else {
                String hash = DigestUtils.md5Hex(Arrays.copyOf(Files.readAllBytes(baseArchived), EuphoriaPatcher.BASE_TAR_SIZE));
                return hash.equals(EuphoriaPatcher.BASE_TAR_HASH);
            }
        } catch (IOException e) {
            return false;
        }
    }
}