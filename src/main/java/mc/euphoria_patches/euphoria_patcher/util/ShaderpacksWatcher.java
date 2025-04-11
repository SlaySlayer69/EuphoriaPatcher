package mc.euphoria_patches.euphoria_patcher.util;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ShaderpacksWatcher {
    private final Path shaderpacks;
    private final WatchService watchService;
    private final ScheduledExecutorService executor;
    private final EuphoriaPatcher patcher;
    private boolean isRunning = false;
    // Track processed files to avoid duplicates
    private final Set<String> processedFiles = new HashSet<>();
    // Track files that failed byte size verification so we can recheck them
    private final Set<String> invalidByteSizeFiles = new HashSet<>();
    // Track file metadata to detect content changes even with the same filename
    private final Map<String, FileMetadata> fileMetadata = new HashMap<>();
    // Cache for byte size verification results to avoid repeated processing
    private final Map<String, Boolean> byteSizeVerificationCache = new HashMap<>();
    // Time of last byte size verification to prevent excessive checking
    private long lastByteSizeVerificationTime = 0;
    // Minimum time between byte size verifications (5 seconds)
    private static final long BYTE_SIZE_VERIFICATION_COOLDOWN = 5000;

    public ShaderpacksWatcher(EuphoriaPatcher patcher) throws IOException {
        this.patcher = patcher;
        this.shaderpacks = EuphoriaPatcher.shaderpacks;
        this.watchService = FileSystems.getDefault().newWatchService();
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "EuphoriaPatches-FileWatcher");
            thread.setDaemon(true);
            return thread;
        });

        // Register the directory to watch for multiple event types
        shaderpacks.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.OVERFLOW
        );

        // Also do an initial scan of existing files just in case
        initialScan();
    }

    // Helper class to track file metadata for change detection
    private static class FileMetadata {
        final long size;
        final long lastModified;

        FileMetadata(long size, long lastModified) {
            this.size = size;
            this.lastModified = lastModified;
        }

        boolean hasChanged(FileMetadata other) {
            return this.size != other.size || this.lastModified != other.lastModified;
        }
    }

    private void initialScan() {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks)) {
            for (Path path : stream) {
                String fileName = path.getFileName().toString();
                if (isPotentialShaderPack(path)) {
                    processedFiles.add(fileName);
                    // Save metadata for this file
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                        fileMetadata.put(fileName, new FileMetadata(attrs.size(), attrs.lastModifiedTime().toMillis()));
                    } catch (IOException e) {
                        EuphoriaPatcher.log(2, "Error reading file attributes: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            EuphoriaPatcher.log(2, "Error during initial directory scan: " + e.getMessage());
        }
    }

    public void startWatching() {
        if (isRunning) return;
        isRunning = true;

        EuphoriaPatcher.log(0, "Watching shaderpacks folder for " + EuphoriaPatcher.BRAND_NAME + "Shaders" + EuphoriaPatcher.VERSION + "...");

        executor.scheduleWithFixedDelay(() -> {
            try {
                WatchKey key = watchService.poll();
                if (key != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();

                        // Handle OVERFLOW by doing a full rescan
                        if (kind == StandardWatchEventKinds.OVERFLOW) {
                            EuphoriaPatcher.log(0, "Detected filesystem overflow, rescanning directory");
                            scanDirectory();
                            continue;
                        }

                        // Handle DELETE events - remove from tracking
                        if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                            @SuppressWarnings("unchecked")
                            WatchEvent<Path> ev = (WatchEvent<Path>) event;
                            Path fileName = ev.context();
                            String fileNameStr = fileName.toString();

                            processedFiles.remove(fileNameStr);
                            invalidByteSizeFiles.remove(fileNameStr);
                            fileMetadata.remove(fileNameStr);
                            continue;
                        }

                        // Handle CREATE or MODIFY events
                        if (kind == StandardWatchEventKinds.ENTRY_CREATE ||
                                kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                            @SuppressWarnings("unchecked")
                            WatchEvent<Path> ev = (WatchEvent<Path>) event;
                            Path fileName = ev.context();
                            Path fullPath = shaderpacks.resolve(fileName);
                            String fileNameStr = fileName.toString();

                            try {
                                // Give the file system a moment to finish copying the file
                                Thread.sleep(1000);

                                // Check if we're still supposed to be running
                                if (!isRunning) {
                                    return;
                                }

                                if (!Files.exists(fullPath) || !isPotentialShaderPack(fullPath)) {
                                    continue;
                                }

                                // Check if we need to process this file
                                boolean shouldProcess = false;

                                try {
                                    BasicFileAttributes attrs = Files.readAttributes(fullPath, BasicFileAttributes.class);
                                    FileMetadata newMetadata = new FileMetadata(attrs.size(), attrs.lastModifiedTime().toMillis());
                                    FileMetadata oldMetadata = fileMetadata.get(fileNameStr);

                                    // Process if:
                                    // 1. It's a new file (not in processedFiles)
                                    // 2. It was previously invalid
                                    // 3. Its metadata has changed
                                    boolean isNewFile = !processedFiles.contains(fileNameStr);
                                    boolean isInvalidByteSize = invalidByteSizeFiles.contains(fileNameStr);
                                    boolean hasChanged = oldMetadata != null && oldMetadata.hasChanged(newMetadata);

                                    shouldProcess = isNewFile || isInvalidByteSize || hasChanged;

                                    // Always update the metadata
                                    fileMetadata.put(fileNameStr, newMetadata);

                                    if (shouldProcess) {
                                        if (isNewFile) {
                                            EuphoriaPatcher.log(0, "Detected new shader pack: " + fileNameStr);
                                        } else if (hasChanged) {
                                            EuphoriaPatcher.log(0, "Detected changed shader pack: " + fileNameStr);
                                        } else if (isInvalidByteSize) {
                                            EuphoriaPatcher.log(0, "Re-checking previously invalid shader pack: " + fileNameStr);
                                        }

                                        boolean wasSuccessful = patcher.processNewShaderpack(fullPath);

                                        // Update tracking sets
                                        if (wasSuccessful) {
                                            processedFiles.add(fileNameStr);
                                            invalidByteSizeFiles.remove(fileNameStr);
                                        } else {
                                            invalidByteSizeFiles.add(fileNameStr);
                                        }
                                    }
                                } catch (IOException e) {
                                    EuphoriaPatcher.log(2, "Error reading file attributes: " + e.getMessage());
                                }
                            } catch (InterruptedException ie) {
                                // Thread was interrupted, likely during shutdown - no need to log an error
                                Thread.currentThread().interrupt(); // Restore the interrupted status
                                return;
                            } catch (Exception e) {
                                EuphoriaPatcher.log(3, "Error in shader pack watcher: " + e.getMessage());
                            }
                        }
                    }
                    key.reset();
                }
            } catch (Exception e) {
                EuphoriaPatcher.log(3, "Error in shader pack watcher: " + e.getMessage());
            }
        }, 0, 2, TimeUnit.SECONDS);
    }

    private void scanDirectory() {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks)) {
            for (Path path : stream) {
                String fileName = path.getFileName().toString();

                if (!isPotentialShaderPack(path)) {
                    continue;
                }

                try {
                    BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                    FileMetadata newMetadata = new FileMetadata(attrs.size(), attrs.lastModifiedTime().toMillis());
                    FileMetadata oldMetadata = fileMetadata.get(fileName);

                    boolean isNewFile = !processedFiles.contains(fileName);
                    boolean isInvalidByteSize = invalidByteSizeFiles.contains(fileName);
                    boolean hasChanged = oldMetadata != null && oldMetadata.hasChanged(newMetadata);

                    boolean shouldProcess = isNewFile || isInvalidByteSize || hasChanged;

                    // Update metadata regardless
                    fileMetadata.put(fileName, newMetadata);

                    if (shouldProcess) {
                        if (isNewFile) {
                            EuphoriaPatcher.log(0, "Found new shader pack during scan: " + fileName);
                        } else if (hasChanged) {
                            EuphoriaPatcher.log(0, "Found changed shader pack during scan: " + fileName);
                        } else if (isInvalidByteSize) {
                            EuphoriaPatcher.log(0, "Re-checking previously invalid shader pack during scan: " + fileName);
                        }

                        boolean wasSuccessful = patcher.processNewShaderpack(path);

                        // Update tracking based on success
                        if (wasSuccessful) {
                            processedFiles.add(fileName);
                            invalidByteSizeFiles.remove(fileName);
                        } else {
                            invalidByteSizeFiles.add(fileName);
                        }
                    }
                } catch (IOException e) {
                    EuphoriaPatcher.log(2, "Error reading file attributes during scan: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            EuphoriaPatcher.log(2, "Error scanning directory: " + e.getMessage());
        }
    }

    public void stopWatching() {
        if (!isRunning) return;
        isRunning = false;

        EuphoriaPatcher.log(0, "Stopping shaderpacks folder watcher");
        executor.shutdownNow();
        try {
            watchService.close();
        } catch (IOException e) {
            EuphoriaPatcher.log(3, "Error closing watch service: " + e.getMessage());
        }
    }

    // Method to clear processed files and force a full rescan
    public void resetProcessedFiles() {
        processedFiles.clear();
        invalidByteSizeFiles.clear();
        fileMetadata.clear();
        EuphoriaPatcher.log(0, "Resetting file watcher to detect replacements");
    }

    // Method to handle byte size failure case
    public void resetAfterByeSizeFailure() {
        // Only clear processed files and metadata, keep invalid file tracking
        processedFiles.clear();
        fileMetadata.clear();
        if (!isRunning) {
            try {
                startWatching();
            } catch (Exception e) {
                EuphoriaPatcher.log(3, "Failed to restart watcher after byte size failure: " + e.getMessage());
            }
        }
    }

    // Track a file that failed byte size verification
    public void trackInvalidByteSizeFile(String fileName) {
        if (fileName != null && !fileName.isEmpty()) {
            invalidByteSizeFiles.add(fileName);
        }
    }

    // Getter for the running state
    public boolean isRunning() {
        return isRunning;
    }

    // Create a static factory method that handles exceptions internally
    public static ShaderpacksWatcher createAndStart(EuphoriaPatcher patcher) {
        try {
            ShaderpacksWatcher watcher = new ShaderpacksWatcher(patcher);
            watcher.startWatching();
            return watcher;
        } catch (IOException e) {
            EuphoriaPatcher.log(3, "Failed to create shaderpacks watcher: " + e.getMessage());
            return null;
        }
    }

    private boolean isPotentialShaderPack(Path path) {
        try {
            String fileName = path.getFileName().toString();

            // Check if the name matches what we're looking for
            boolean nameMatches = fileName.matches(EuphoriaPatcher.BRAND_NAME + ".*" + EuphoriaPatcher.VERSION + ".*") &&
                    !fileName.contains(EuphoriaPatcher.PATCH_NAME);

            // Fast path: if name matches, return immediately
            if (nameMatches) {
                // For zip files
                if (fileName.endsWith(".zip")) {
                    return Files.exists(path) && Files.size(path) > 0;
                }
                // For directories
                return Files.isDirectory(path);
            }

            // If name doesn't match, check if we've already verified this file
            if (byteSizeVerificationCache.containsKey(fileName)) {
                return byteSizeVerificationCache.get(fileName);
            }

            // Throttle byte size verification to avoid excessive CPU usage
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastByteSizeVerificationTime < BYTE_SIZE_VERIFICATION_COOLDOWN) {
                return false; // Skip verification during cooldown
            }
            
            // Update last verification time
            lastByteSizeVerificationTime = currentTime;

            // For files of reasonable size or directories, verify by byte size
            if ((fileName.endsWith(".zip") && Files.exists(path) && Files.size(path) > 100000) ||
                    Files.isDirectory(path)) {
                
                EuphoriaPatcher.log(0, "Checking if file matches by byte size (watcher): " + fileName);
                
                boolean isValidByByteSize = false;
                if (patcher != null) {
                    isValidByByteSize = patcher.isValidShaderByByteSize(path);
                    
                    // If valid by byte size, rename the file to the correct format
                    if (isValidByByteSize) {
                        EuphoriaPatcher.log(0, "Found valid shader by byte size in watcher: " + fileName);
                        
                        // Rename the file
                        Path renamedPath = patcher.renameToCorrectShaderName(path);
                        
                        // Update the cache with both old and new names
                        String newFileName = renamedPath.getFileName().toString();
                        byteSizeVerificationCache.put(fileName, true);
                        if (!fileName.equals(newFileName)) {
                            byteSizeVerificationCache.put(newFileName, true);
                            
                            // Update tracking in case the file was renamed
                            processedFiles.remove(fileName);
                            invalidByteSizeFiles.remove(fileName);
                            fileMetadata.remove(fileName);
                        }
                    }
                }
                
                // Cache the result
                byteSizeVerificationCache.put(fileName, isValidByByteSize);
                
                return isValidByByteSize;
            }

            return false;

        } catch (IOException e) {
            EuphoriaPatcher.log(2, "Error checking shader pack name: " + e.getMessage());
            return false;
        }
    }
}