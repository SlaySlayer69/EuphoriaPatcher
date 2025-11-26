package mc.euphoria_patches.euphoria_patcher.services;

import mc.euphoria_patches.euphoria_patcher.logging.EuphoriaLogger;
import mc.euphoria_patches.euphoria_patcher.util.ArchiveOperations;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for validating shader files through various verification methods
 */
public class ShaderValidator {
    
    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ShaderValidator] " + message);
    }

    /**
     * Validates multiple shaders in parallel by extracting, re-archiving, and checking byte size
     * This is a heavy operation used to identify unpatched base shaders
     * 
     * @param paths List of paths to validate
     * @param progressCallback Callback for progress updates (filesScanned, totalFiles)
     * @return The first path that passes validation, or null if none pass
     */
    public Path validateByByteSizeParallel(List<Path> paths, ProgressCallback progressCallback) {
        if (paths == null || paths.isEmpty()) {
            return null;
        }

        int totalFiles = paths.size();
        AtomicInteger filesScanned = new AtomicInteger(0);
        
        // Use available processors, but cap at 4 to avoid excessive resource usage
        int threadCount = Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors()));
        debugLog("Using " + threadCount + " threads for parallel validation of " + totalFiles + " files");
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CompletionService<Path> completionService = new ExecutorCompletionService<>(executor);
        
        try {
            // Submit all validation tasks
            for (Path path : paths) {
                completionService.submit(() -> {
                    int currentCount = filesScanned.incrementAndGet();
                    
                    // Report progress every 5 files
                    if (currentCount % 5 == 0 && progressCallback != null) {
                        progressCallback.onProgress(currentCount, totalFiles);
                    }
                    
                    if (validateByByteSize(path, currentCount, totalFiles)) {
                        return path; // Return the valid path
                    }
                    return null;
                });
            }
            
            // Wait for results and return the first valid one
            for (int i = 0; i < totalFiles; i++) {
                try {
                    Future<Path> future = completionService.poll(30, TimeUnit.SECONDS);
                    if (future != null) {
                        Path result = future.get();
                        if (result != null) {
                            debugLog("Found valid shader: " + result.getFileName());
                            // Cancel remaining tasks since we found a valid shader
                            executor.shutdownNow();
                            return result;
                        }
                    }
                } catch (InterruptedException | ExecutionException e) {
                    debugLog("Error during parallel validation: " + e.getMessage());
                }
            }
            
            debugLog("No valid shader found after checking all " + totalFiles + " files");
            return null;
            
        } finally {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    debugLog("Executor did not terminate cleanly");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Validates a shader by extracting, re-archiving, and checking byte size
     * This is a heavy operation used to identify unpatched base shaders
     * 
     * @param path Path to the shader file or directory
     * @param filesScanned Current count of files scanned (for progress reporting)
     * @param totalFiles Total files to scan (for progress reporting)
     * @return true if the shader matches the expected byte size
     */
    public boolean validateByByteSize(Path path, int filesScanned, int totalFiles) {
        try {
            debugLog("Validating shader by byte size (" + filesScanned + "/" + totalFiles + "): " + path.getFileName());
            
            Path tempDir = ArchiveOperations.createTempDirectory();
            if (tempDir == null) {
                debugLog("Failed to create temp directory for byte size validation");
                return false;
            }
            debugLog("Created temp directory: " + tempDir);

            String baseName = path.getFileName().toString().replace(".zip", "");
            debugLog("Base name for extraction: " + baseName);

            // Extract if it's a zip file
            Path baseExtracted = tempDir.resolve(baseName);
            baseExtracted = ArchiveOperations.extract(path, baseExtracted, "extracting archive");
            if (baseExtracted == null) {
                debugLog("Failed to extract base for byte size validation");
                ArchiveOperations.deleteTempDirectory(tempDir);
                return false;
            }
            debugLog("Successfully extracted to: " + baseExtracted);

            // Archive for byte size comparison
            Path baseArchived = tempDir.resolve(baseName + ".tar");
            baseArchived = ArchiveOperations.archive(baseExtracted, baseArchived);
            if (baseArchived == null) {
                debugLog("Failed to archive base for byte size validation");
                ArchiveOperations.deleteTempDirectory(tempDir);
                return false;
            }
            debugLog("Successfully archived to: " + baseArchived);

            // Check byte size quietly
            boolean result = ArchiveOperations.verifyBaseArchiveQuiet(baseArchived);
            debugLog("Byte size verification result for " + path.getFileName() + ": " + result);

            // Clean up
            ArchiveOperations.deleteTempDirectory(tempDir);

            return result;
        } catch (Exception e) {
            debugLog("Exception during byte size validation: " + e.getMessage());
            return false;
        }
    }

    /**
     * Callback interface for progress updates during parallel validation
     */
    public interface ProgressCallback {
        void onProgress(int filesScanned, int totalFiles);
    }
}
