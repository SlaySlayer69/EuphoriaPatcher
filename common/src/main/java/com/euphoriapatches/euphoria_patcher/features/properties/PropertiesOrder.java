package com.euphoriapatches.euphoria_patcher.features.properties;

import java.util.*;

/**
 * Defines the merge order for block properties files.
 * This class provides a maintainable configuration for how fragmented
 * properties files should be merged into a single block.properties file.
 */
public class PropertiesOrder {

    /**
     * Represents a folder or file entry in the merge order
     */
    public static class OrderEntry {
        private final String name;
        private final List<OrderEntry> children;

        public OrderEntry(String name) {
            this.name = name;
            this.children = new ArrayList<>();
        }

        public OrderEntry(String name, OrderEntry... children) {
            this.name = name;
            this.children = new ArrayList<>(Arrays.asList(children));
        }

        public String getName() {
            return name;
        }

        public List<OrderEntry> getChildren() {
            return children;
        }

        public boolean hasChildren() {
            return !children.isEmpty();
        }
    }

    /**
     * Define the merge order for properties files.
     *
     * Order matters: files will be merged in the sequence defined here.
     * - First level: folder names inside shaders/properties/
     * - Nested levels: subfolders or specific file names within parent folders
     *
     * To add a new folder/file to the merge order:
     * 1. Add a new OrderEntry with the folder/file name
     * 2. If it has subfolders or multiple files, nest them as children
     * 3. Use regex patterns for flexible matching (patterns are auto-detected)
     *
     * Example structure:
     * properties/
     *   ├── instructions/          (merged first)
     *   │   └── readme.properties
     *   ├── 1.13+/                 (merged second)
     *   │   ├── block.5000-10081.properties  (merged first within 1.13+)
     *   │   ├── block.10082-15000.properties (merged alphabetically after)
     *   │   └── block.15001-20000.properties (merged alphabetically after)
     *   ├── 1.8+/                  (merged third)
     *   ├── 1.7.10/                (merged fourth)
     *   └── renderlayers/          (merged last)
     *
     * Code example - exact match:
     * new OrderEntry("1.13+",
     *     new OrderEntry("block.5000-10081.properties")
     * )
     *
     * Code example - regex pattern (auto-detected by presence of regex metacharacters):
     * new OrderEntry("1.13+",
     *     new OrderEntry("block\\.5000-.*\\.properties")
     * )
     * This will match any file like:
     * - block.5000-10081.properties
     * - block.5000-12345.properties
     * - block.5000-anything.properties
     *
     * Pattern matching rules:
     * - Use standard Java regex syntax
     * - Patterns are auto-detected (if name contains .*, [, ], etc.)
     * - Multiple matches are processed alphabetically
     * - Escape dots in filenames: "block\\.properties" not "block.properties"
     * - Common patterns:
     *   * .* = match anything
     *   * [0-9]+ = match one or more digits
     *   * (foo|bar) = match "foo" or "bar"
     *
     * This means:
     * - Process the 1.13+ folder
     * - Within it, process files matching the pattern FIRST
     * - Then process all remaining .properties files alphabetically
     * - Any subdirectories will also be processed alphabetically after defined files
     */
    private static final List<OrderEntry> MERGE_ORDER = Arrays.asList(
        new OrderEntry("instructions"),
        new OrderEntry("tags"),
        new OrderEntry("1.13+",
            new OrderEntry("block\\.5000-.*\\.properties")),
        new OrderEntry("1.8+"),
        new OrderEntry("1.7.10"),
        new OrderEntry("renderlayers")
    );

    /**
     * Get the merge order configuration
     * @return Unmodifiable list of order entries
     */
    public static List<OrderEntry> getMergeOrder() {
        return Collections.unmodifiableList(MERGE_ORDER);
    }

    /**
     * Check if a folder name is defined in the merge order
     * @param folderName The folder name to check
     * @return true if the folder is in the merge order, false otherwise
     */
    public static boolean isInMergeOrder(String folderName) {
        return findEntry(folderName, MERGE_ORDER) != null;
    }

    /**
     * Get the order index of a folder (lower index = merged earlier)
     * @param folderName The folder name
     * @return The index in the merge order, or -1 if not found
     */
    public static int getOrderIndex(String folderName) {
        for (int i = 0; i < MERGE_ORDER.size(); i++) {
            if (MERGE_ORDER.get(i).getName().equals(folderName)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Recursively find an entry by name
     */
    private static OrderEntry findEntry(String name, List<OrderEntry> entries) {
        for (OrderEntry entry : entries) {
            if (entry.getName().equals(name)) {
                return entry;
            }
            if (entry.hasChildren()) {
                OrderEntry found = findEntry(name, entry.getChildren());
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
