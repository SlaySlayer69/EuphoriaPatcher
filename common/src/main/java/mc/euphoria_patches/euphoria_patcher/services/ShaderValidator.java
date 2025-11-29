package mc.euphoria_patches.euphoria_patcher.services;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
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
     * Checks if OSHI library is available at runtime
     */
    private static boolean isOshiAvailable() {
        try {
            Class.forName("oshi.SystemInfo");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * Determines optimal thread count based on CPU usage using OSHI via reflection
     * 
     * @return Thread count between 1-16 based on CPU load, or -1 on failure
     */
    private static int determineOptimalThreadCountWithOshi() {
        try {
            // Use reflection to avoid hard dependency on OSHI classes
            Class<?> systemInfoClass = Class.forName("oshi.SystemInfo");
            Object systemInfo = systemInfoClass.newInstance();
            
            Object hardware = systemInfoClass.getMethod("getHardware").invoke(systemInfo);
            Object processor = hardware.getClass().getMethod("getProcessor").invoke(hardware);
            
            // Get CPU load over a 1 second interval
            long[] prevTicks = (long[]) processor.getClass().getMethod("getSystemCpuLoadTicks").invoke(processor);
            Thread.sleep(1000);
            
            double cpuLoad = (Double) processor.getClass()
                .getMethod("getSystemCpuLoadBetweenTicks", long[].class)
                .invoke(processor, (Object) prevTicks);
            
            int availableProcessors = getAvailableProcessors();
            debugLog("CPU usage: " + String.format("%.1f%%", cpuLoad * 100) + 
                    ", Available processors: " + availableProcessors);
            
            // Scale thread count based on CPU availability
            // Low usage (< 50%) -> use more threads (up to available processors)
            // Medium usage (50-80%) -> use half of available processors
            // High usage (> 80%) -> use quarter of available processors (minimum 1)
            int threadCount;
            if (cpuLoad < 0.5) {
                threadCount = Math.max(4, availableProcessors);
            } else if (cpuLoad < 0.8) {
                threadCount = Math.max(2, availableProcessors / 2);
            } else {
                threadCount = Math.max(1, availableProcessors / 4);
            }

            // Never exceed available processors
            threadCount = Math.min(threadCount, availableProcessors);

            debugLog("OSHI determined optimal thread count: " + threadCount);
            return threadCount;
            
        } catch (Exception e) {
            debugLog("Error using OSHI: " + e.getMessage());
            return -1; // Signal failure
        }
    }

    private static int getAvailableProcessors() {
        return Math.max(1, Runtime.getRuntime().availableProcessors());
    }
    
    /**
     * Determines optimal thread count based on CPU usage
     * Falls back to processor count if OSHI is not available
     * 
     * @return Thread count between 1-maxThreadCount, with fallback to 1-4
     */
    private static int determineOptimalThreadCount() {
        // Try OSHI if available
        if (isOshiAvailable()) {
            int threadCount = determineOptimalThreadCountWithOshi();
            if (threadCount > 0) {
                return threadCount;
            }
        }

        // Fallback: simple processor-based calculation
        debugLog("OSHI not available, using fallback thread count calculation");
        int availableProcessors = getAvailableProcessors();
        debugLog("Available processors: " + availableProcessors);
        return Math.min(4, Math.max(1, availableProcessors));
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
        
        // Determine optimal thread count based on CPU usage
        int threadCount = determineOptimalThreadCount();
        EuphoriaPatcher.log(0, "Using " + threadCount + " threads for parallel validation of " + totalFiles + " files");
        
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, String.format("ShaderByteSizeFinder-%03d", threadNumber.getAndIncrement()));
                t.setDaemon(true);
                return t;
            }
        };
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount, threadFactory);
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
