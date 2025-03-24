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
    // Track files that failed hash verification so we can recheck them
    private final Set<String> invalidHashFiles = new HashSet<>();
    // Track file metadata to detect content changes even with the same filename
    private final Map<String, FileMetadata> fileMetadata = new HashMap<>();

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
                            invalidHashFiles.remove(fileNameStr);
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
                                    boolean isInvalidHash = invalidHashFiles.contains(fileNameStr);
                                    boolean hasChanged = oldMetadata != null && oldMetadata.hasChanged(newMetadata);

                                    shouldProcess = isNewFile || isInvalidHash || hasChanged;

                                    // Always update the metadata
                                    fileMetadata.put(fileNameStr, newMetadata);

                                    if (shouldProcess) {
                                        if (isNewFile) {
                                            EuphoriaPatcher.log(0, "Detected new shader pack: " + fileNameStr);
                                        } else if (hasChanged) {
                                            EuphoriaPatcher.log(0, "Detected changed shader pack: " + fileNameStr);
                                        } else if (isInvalidHash) {
                                            EuphoriaPatcher.log(0, "Re-checking previously invalid shader pack: " + fileNameStr);
                                        }

                                        boolean wasSuccessful = patcher.processNewShaderpack(fullPath);

                                        // Update tracking sets
                                        if (wasSuccessful) {
                                            processedFiles.add(fileNameStr);
                                            invalidHashFiles.remove(fileNameStr);
                                        } else {
                                            invalidHashFiles.add(fileNameStr);
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
                    boolean isInvalidHash = invalidHashFiles.contains(fileName);
                    boolean hasChanged = oldMetadata != null && oldMetadata.hasChanged(newMetadata);

                    boolean shouldProcess = isNewFile || isInvalidHash || hasChanged;

                    // Update metadata regardless
                    fileMetadata.put(fileName, newMetadata);

                    if (shouldProcess) {
                        if (isNewFile) {
                            EuphoriaPatcher.log(0, "Found new shader pack during scan: " + fileName);
                        } else if (hasChanged) {
                            EuphoriaPatcher.log(0, "Found changed shader pack during scan: " + fileName);
                        } else if (isInvalidHash) {
                            EuphoriaPatcher.log(0, "Re-checking previously invalid shader pack during scan: " + fileName);
                        }

                        boolean wasSuccessful = patcher.processNewShaderpack(path);

                        // Update tracking based on success
                        if (wasSuccessful) {
                            processedFiles.add(fileName);
                            invalidHashFiles.remove(fileName);
                        } else {
                            invalidHashFiles.add(fileName);
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
        invalidHashFiles.clear();
        fileMetadata.clear();
        EuphoriaPatcher.log(0, "Resetting file watcher to detect replacements");
    }

    // Method to handle hash failure case
    public void resetAfterHashFailure() {
        // Only clear processed files and metadata, keep invalid file tracking
        processedFiles.clear();
        fileMetadata.clear();
        if (!isRunning) {
            try {
                startWatching();
            } catch (Exception e) {
                EuphoriaPatcher.log(3, "Failed to restart watcher after hash failure: " + e.getMessage());
            }
        }
    }

    // Track a file that failed hash verification
    public void trackInvalidHashFile(String fileName) {
        if (fileName != null && !fileName.isEmpty()) {
            invalidHashFiles.add(fileName);
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

            if (!nameMatches) {
                return false;
            }

            // For zip files
            if (fileName.endsWith(".zip")) {
                return Files.exists(path) && Files.size(path) > 0;
            }

            // For directories
            return Files.isDirectory(path);

        } catch (IOException e) {
            EuphoriaPatcher.log(2, "Error checking shader pack name: " + e.getMessage());
            return false;
        }
    }
}