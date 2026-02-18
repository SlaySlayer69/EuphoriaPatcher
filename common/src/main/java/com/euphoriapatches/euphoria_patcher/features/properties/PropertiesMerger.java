package com.euphoriapatches.euphoria_patcher.features.properties;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Handles merging of fragmented properties files into a single block.properties file.
 * This class is designed to be portable and independent of the watcher implementation.
 */
public class PropertiesMerger {

    /**
     * Merge all properties files from a source directory into a target file.
     * Files are merged according to the order defined in PropertiesOrder.
     *
     * @param propertiesDir The root properties directory (e.g., shaderpack/shaders/blockProperties)
     * @param targetFile The target file to merge into (e.g., shaderpack/shaders/block.properties)
     * @return true if merge was successful, false otherwise
     */
    public static boolean mergeProperties(Path propertiesDir, Path targetFile) {
        if (!Files.exists(propertiesDir) || !Files.isDirectory(propertiesDir)) {
            debugLog("Properties directory does not exist or is not a directory: " + propertiesDir);
            return false;
        }

        try {
            debugLog("Starting properties merge from: " + propertiesDir);

            // Collect all properties files according to the defined order
            List<Path> orderedFiles = collectOrderedPropertiesFiles(propertiesDir);

            if (orderedFiles.isEmpty()) {
                debugLog("No properties files found to merge");
                return false;
            }

            debugLog("Found " + orderedFiles.size() + " properties files to merge");

            // Merge all files into the target
            mergeFilesIntoTarget(orderedFiles, targetFile, propertiesDir);

            debugLog("Successfully merged properties into: " + targetFile);
            return true;

        } catch (IOException e) {
            debugLog("Error during properties merge: " + e.getMessage());
            debugLog(EuphoriaLogger.getStackTrace(e));
            return false;
        }
    }

    /**
     * Collect all .properties files from the properties directory in the correct order
     */
    private static List<Path> collectOrderedPropertiesFiles(Path propertiesDir) throws IOException {
        List<Path> orderedFiles = new ArrayList<>();
        List<PropertiesOrder.OrderEntry> mergeOrder = PropertiesOrder.getMergeOrder();

        for (PropertiesOrder.OrderEntry entry : mergeOrder) {
            Path entryPath = propertiesDir.resolve(entry.getName());

            if (!Files.exists(entryPath)) {
                debugLog("Order entry not found, skipping: " + entry.getName());
                continue;
            }

            // Collect files from this entry
            collectFilesFromEntry(entryPath, entry, orderedFiles);
        }

        return orderedFiles;
    }

    /**
     * Recursively collect properties files from an entry and its children
     */
    private static void collectFilesFromEntry(Path entryPath, PropertiesOrder.OrderEntry entry,
                                             List<Path> result) throws IOException {
        if (Files.isRegularFile(entryPath) && entryPath.toString().endsWith(".properties")) {
            result.add(entryPath);
            debugLog("Added file to merge order: " + entryPath.getFileName());
            return;
        }

        if (!Files.isDirectory(entryPath)) {
            return;
        }

        // If entry has children defined, process them first, then remaining files
        if (entry.hasChildren()) {
            // Track which files/folders were explicitly processed
            Set<String> processedNames = new HashSet<>();

            // First, process all explicitly defined children in order
            for (PropertiesOrder.OrderEntry child : entry.getChildren()) {
                String childName = child.getName();

                // Check if it's a pattern FIRST (before trying to resolve as a path)
                if (isPattern(childName)) {
                    List<Path> matchedPaths = findMatchingPaths(entryPath, childName);
                    if (!matchedPaths.isEmpty()) {
                        debugLog("Pattern '" + childName + "' matched " + matchedPaths.size() + " items");
                        for (Path matchedPath : matchedPaths) {
                            collectFilesFromEntry(matchedPath, child, result);
                            processedNames.add(matchedPath.getFileName().toString());
                        }
                    } else {
                        debugLog("Pattern '" + childName + "' matched no items");
                    }
                }
                // Try exact match for non-pattern names
                else {
                    Path childPath = entryPath.resolve(childName);
                    if (Files.exists(childPath)) {
                        collectFilesFromEntry(childPath, child, result);
                        processedNames.add(childName);
                    }
                }
            }

            // Then, collect remaining files/folders alphabetically
            collectRemainingPropertiesFiles(entryPath, processedNames, result);
        } else {
            // No specific order defined for children, collect all .properties files
            // Sort them alphabetically for consistent ordering
            collectAllPropertiesFiles(entryPath, result);
        }
    }

    /**
     * Check if a name string is a regex pattern (contains regex metacharacters)
     */
    private static boolean isPattern(String name) {
        // Check for common regex metacharacters
        return name.contains(".*") || name.contains(".+") || name.contains("[") ||
               name.contains("]") || name.contains("^") || name.contains("$") ||
               name.contains("|") || name.contains("?") || name.contains("{") ||
               name.matches(".*\\\\[dDwWsS].*"); // Escaped regex patterns like \d, \w, etc.
    }

    /**
     * Find all files/folders in a directory that match a regex pattern
     * @param directory The directory to search in
     * @param patternString The regex pattern to match against filenames
     * @return List of matching paths, sorted alphabetically
     */
    private static List<Path> findMatchingPaths(Path directory, String patternString) throws IOException {
        List<Path> matches = new ArrayList<>();

        try {
            Pattern pattern = Pattern.compile(patternString);

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
                for (Path path : stream) {
                    String fileName = path.getFileName().toString();
                    if (pattern.matcher(fileName).matches()) {
                        matches.add(path);
                        debugLog("Pattern '" + patternString + "' matched: " + fileName);
                    }
                }
            }

            // Sort matches alphabetically for consistent ordering
            matches.sort(Comparator.comparing(Path::getFileName));

        } catch (Exception e) {
            debugLog("Error compiling pattern '" + patternString + "': " + e.getMessage());
        }

        return matches;
    }

    /**
     * Collect properties files that weren't explicitly defined in the order, sorted alphabetically
     */
    private static void collectRemainingPropertiesFiles(Path directory, Set<String> excludeNames,
                                                       List<Path> result) throws IOException {
        List<Path> filesInDir = new ArrayList<>();
        List<Path> subDirs = new ArrayList<>();

        // Collect files and subdirectories not in the exclude list
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                String name = path.getFileName().toString();
                if (excludeNames.contains(name)) {
                    continue; // Skip already processed
                }

                if (Files.isRegularFile(path) && name.endsWith(".properties")) {
                    filesInDir.add(path);
                } else if (Files.isDirectory(path)) {
                    subDirs.add(path);
                }
            }
        }

        // Sort alphabetically
        filesInDir.sort(Comparator.comparing(Path::getFileName));
        subDirs.sort(Comparator.comparing(Path::getFileName));

        // Add remaining files
        for (Path file : filesInDir) {
            result.add(file);
            debugLog("Added remaining file to merge order: " + file.getFileName());
        }

        // Recurse into remaining subdirectories
        for (Path subDir : subDirs) {
            collectAllPropertiesFiles(subDir, result);
        }
    }

    /**
     * Collect all .properties files from a directory and its subdirectories
     * Files are sorted alphabetically for consistent ordering
     */
    private static void collectAllPropertiesFiles(Path directory, List<Path> result) throws IOException {
        List<Path> filesInDir = new ArrayList<>();
        List<Path> subDirs = new ArrayList<>();

        // First pass: collect immediate .properties files and subdirectories
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                if (Files.isRegularFile(path) && path.toString().endsWith(".properties")) {
                    filesInDir.add(path);
                } else if (Files.isDirectory(path)) {
                    subDirs.add(path);
                }
            }
        }

        // Sort files alphabetically
        filesInDir.sort(Comparator.comparing(Path::getFileName));

        // Add files from this directory first
        for (Path file : filesInDir) {
            result.add(file);
            debugLog("Added file to merge order: " + file.getFileName());
        }

        // Then recursively process subdirectories (sorted alphabetically)
        subDirs.sort(Comparator.comparing(Path::getFileName));
        for (Path subDir : subDirs) {
            collectAllPropertiesFiles(subDir, result);
        }
    }

    /**
     * Merge all collected files into the target file
     */
    private static void mergeFilesIntoTarget(List<Path> sourceFiles, Path targetFile,
                                            Path propertiesDir) throws IOException {
        // Ensure parent directory exists
        Files.createDirectories(targetFile.getParent());

        // Build the merged content
        StringBuilder mergedContent = new StringBuilder();

        // Add header
        mergedContent.append("###############################################################################\n");
        mergedContent.append("# Auto-merged block.properties file\n");
        mergedContent.append("# Generated by Euphoria Patcher - Properties Merger\n");
        mergedContent.append("# \n");
        mergedContent.append("# This file is automatically generated from fragmented properties files.\n");
        mergedContent.append("# Do not edit this file directly - your changes will be overwritten!\n");
        mergedContent.append("# Edit the individual files in shaders/blockProperties/ instead.\n");
        mergedContent.append("# Enable the \"autoMergeBlockProperties\" option in the Euphoria Patches Mod's config to automatically merge on changes.\n");
        mergedContent.append("###############################################################################\n\n");

        // Merge each source file
        for (Path sourceFile : sourceFiles) {
            // Calculate relative path from properties directory
            Path relativePath = propertiesDir.relativize(sourceFile);

            // Add section header
            mergedContent.append("\n");
            mergedContent.append("# ============================================================================\n");
            mergedContent.append("# Source: ").append(relativePath.toString().replace('\\', '/')).append("\n");
            mergedContent.append("# ============================================================================\n");

            // Read and append file content
            String content = readFileContent(sourceFile);
            mergedContent.append(content);

            // Ensure content ends with newline
            if (!content.endsWith("\n")) {
                mergedContent.append("\n");
            }
        }

        // Write merged content to target file (force overwrite)
        Files.write(targetFile, mergedContent.toString().getBytes(StandardCharsets.UTF_8),
                   StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        debugLog("Merged " + sourceFiles.size() + " files into " + targetFile.getFileName());
    }

    /**
     * Read the full content of a file
     */
    private static String readFileContent(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[PropertiesMerger] " + message);
    }
}
