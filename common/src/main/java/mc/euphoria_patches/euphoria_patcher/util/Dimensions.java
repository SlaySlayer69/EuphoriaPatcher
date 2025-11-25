package mc.euphoria_patches.euphoria_patcher.util;

import mc.euphoria_patches.euphoria_patcher.features.UpdateShaderLoaderConfig;
import mc.euphoria_patches.euphoria_patcher.logging.EuphoriaLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class Dimensions {
    public static final String SHADER_DIMENSION_PROPERTIES_LOCATION = "shaders/dimension.properties";

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[Dimensions] " + message);
    }

    /**
     * Gets the dimension category based on dimension ID and shader properties
     *
     * @param currentDimensionId The dimension identifier string
     * @return String representing the dimension: "overworld", "nether", "end", or "other"
     */
    public static String getCurrentDimension(String currentDimensionId) {
        if (currentDimensionId == null) {
            debugLog("Dimension ID is null, defaulting to 'overworld'");
            return "overworld";
        }

        // Get current shader pack path
        Path shaderpackPath = UpdateShaderLoaderConfig.getCurrentShaderpackPath();
        if (shaderpackPath == null) {
            debugLog("No active shaderpack found");
        } else {
            debugLog("Active shaderpack: " + shaderpackPath.getFileName());
        }
        
        if (shaderpackPath != null) {
            // Parse dimension mappings
            Map<String, String> dimensionMappings = parseDimensionProperties(shaderpackPath);
            debugLog("Parsed " + dimensionMappings.size() + " dimension mappings from properties file");

            // Check if the dimension is directly mapped
            if (dimensionMappings.containsKey(currentDimensionId)) {
                String mappedCategory = dimensionMappings.get(currentDimensionId);
                debugLog("Found mapping for current dimension: " + mappedCategory);
                return mappedCategory;
            } else {
                debugLog("No specific mapping found for dimension: " + currentDimensionId);
            }
        }

        // Check if it's a vanilla dimension
        debugLog("Checking vanilla dimension IDs");
        switch (currentDimensionId) {
            case "minecraft:overworld":
                debugLog("Identified as vanilla overworld");
                return "overworld";
            case "minecraft:the_nether":
                debugLog("Identified as vanilla nether");
                return "nether";
            case "minecraft:the_end":
                debugLog("Identified as vanilla end");
                return "end";
        }

        // If no specific mapping is found, return "other"
        debugLog("No mapping found, returning 'other'");
        return "other";
    }

    /**
     * Parses the dimension.properties file to create mappings from dimension IDs to
     * dimension categories (overworld, nether, end).
     *
     * @param shaderPackPath The path to the active shader pack
     * @return Map of dimension IDs to dimension categories
     */
    private static Map<String, String> parseDimensionProperties(Path shaderPackPath) {
        Map<String, String> dimensionMap = new HashMap<>();

        if (shaderPackPath == null) {
            debugLog("Shader pack path is null");
            return dimensionMap;
        }

        // Check if shader pack is a ZIP file
        if (shaderPackPath.toString().endsWith(".zip")) {
            debugLog("Reading from ZIP file: " + shaderPackPath.getFileName());
            try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(shaderPackPath.toFile())) {
                // Get the dimension.properties entry from the ZIP
                java.util.zip.ZipEntry entry = zipFile.getEntry(SHADER_DIMENSION_PROPERTIES_LOCATION);

                if (entry == null) {
                    debugLog("dimension.properties not found in ZIP");
                    return dimensionMap;
                }

                // Read the file from the ZIP
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(zipFile.getInputStream(entry), StandardCharsets.UTF_8))) {
                    return parseDimensionPropertiesFromReader(reader);
                }
            } catch (IOException e) {
                debugLog("Error reading dimension.properties from ZIP: " + e.getMessage());
            }
        } else {
            // Regular directory
            Path propertiesFile = shaderPackPath.resolve(SHADER_DIMENSION_PROPERTIES_LOCATION);

            if (Files.exists(propertiesFile)) {
                try (BufferedReader reader = Files.newBufferedReader(propertiesFile, StandardCharsets.UTF_8)) {
                    return parseDimensionPropertiesFromReader(reader);
                } catch (IOException e) {
                    debugLog("Error reading dimension.properties from directory: " + e.getMessage());
                }
            } else {
                debugLog("dimension.properties file not found at path: " + propertiesFile);
            }
        }

        return dimensionMap;
    }

    /**
     * Parse dimension properties from a reader
     * @param reader The reader containing dimension.properties content
     * @return Map of dimension IDs to dimension categories
     * @throws IOException If there's an error reading from the reader
     */
    private static Map<String, String> parseDimensionPropertiesFromReader(BufferedReader reader) throws IOException {
        Map<String, String> dimensionMap = new HashMap<>();
        StringBuilder currentLine = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            // Skip comments and empty lines
            if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                continue;
            }

            // Handle line continuation
            if (line.endsWith("\\")) {
                currentLine.append(line, 0, line.length() - 1).append(" ");
                continue;
            } else if (currentLine.length() > 0) {
                currentLine.append(line);
                line = currentLine.toString();
                currentLine.setLength(0);
            }

            // Process dimension mapping lines
            if (line.startsWith("dimension.world0=")) {
                // Overworld mappings
                addDimensionsToMap(line.substring("dimension.world0=".length()), "overworld", dimensionMap);
            } else if (line.startsWith("dimension.world-1=")) {
                // Nether mappings
                addDimensionsToMap(line.substring("dimension.world-1=".length()), "nether", dimensionMap);
            } else if (line.startsWith("dimension.world1=")) {
                // End mappings
                addDimensionsToMap(line.substring("dimension.world1=".length()), "end", dimensionMap);
            }
        }

        return dimensionMap;
    }

    /**
     * Helper method to parse dimension IDs from a line and add them to the mapping
     *
     * @param dimensionsLine Line containing dimension IDs
     * @param targetCategory Category to map these dimensions to (overworld, nether, end)
     * @param dimensionMap Map to populate
     */
    private static void addDimensionsToMap(String dimensionsLine, String targetCategory, Map<String, String> dimensionMap) {
        String[] parts = dimensionsLine.split("\\s+");
        for (String part : parts) {
            if (!part.trim().isEmpty() && !part.equals("\\")) {
                if (part.equals("*")) {
                    // Wildcard for any unmapped dimension - handled separately if needed
                    continue;
                }
                dimensionMap.put(part, targetCategory);
            }
        }
    }
}
