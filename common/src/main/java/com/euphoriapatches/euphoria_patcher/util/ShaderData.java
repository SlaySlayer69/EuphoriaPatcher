package com.euphoriapatches.euphoria_patcher.util;

import com.google.gson.*;
import com.euphoriapatches.euphoria_patcher.config.Config;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Handles persistent storage of shader data in a JSON file
 */
public class ShaderData {
    private static final Path DATA_FILE = Config.CONFIG_DIR.resolve("data.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ShaderData] " + message);
    }

    /**
     * Data class to hold shader style information
     */
    public static class ShaderStyleData {
        public boolean styleReimagined;
        public boolean styleUnbound;

        public ShaderStyleData() {
            this.styleReimagined = false;
            this.styleUnbound = false;
        }

        public ShaderStyleData(boolean styleReimagined, boolean styleUnbound) {
            this.styleReimagined = styleReimagined;
            this.styleUnbound = styleUnbound;
        }
    }

    /**
     * Save shader style data to the data.json file
     * @param styleReimagined Whether Reimagined style is used
     * @param styleUnbound Whether Unbound style is used
     * @return true if save was successful, false otherwise
     */
    public static boolean saveShaderStyle(boolean styleReimagined, boolean styleUnbound) {
        debugLog("Saving shader style data: Reimagined=" + styleReimagined + ", Unbound=" + styleUnbound);

        try {
            // Ensure config directory exists
            if (!Files.exists(Config.CONFIG_DIR)) {
                Files.createDirectories(Config.CONFIG_DIR);
                debugLog("Created config directory: " + Config.CONFIG_DIR);
            }

            // Create data object
            ShaderStyleData data = new ShaderStyleData(styleReimagined, styleUnbound);

            // Write to file
            try (Writer writer = new OutputStreamWriter(
                    Files.newOutputStream(DATA_FILE), StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
                debugLog("Successfully saved shader style data to " + DATA_FILE);
                return true;
            }
        } catch (IOException e) {
            debugLog("Error saving shader style data: " + e.getMessage());
            return false;
        }
    }

    /**
     * Load shader style data from the data.json file
     * @return ShaderStyleData object with loaded data, or default values if file doesn't exist
     */
    public static ShaderStyleData loadShaderStyle() {
        debugLog("Loading shader style data from " + DATA_FILE);

        if (!Files.exists(DATA_FILE)) {
            debugLog("Data file does not exist, returning default values");
            return new ShaderStyleData();
        }

        try (Reader reader = new InputStreamReader(
                Files.newInputStream(DATA_FILE), StandardCharsets.UTF_8)) {
            ShaderStyleData data = GSON.fromJson(reader, ShaderStyleData.class);

            if (data == null) {
                debugLog("Parsed data is null, returning default values");
                return new ShaderStyleData();
            }

            debugLog("Successfully loaded shader style data: Reimagined=" +
                    data.styleReimagined + ", Unbound=" + data.styleUnbound);
            return data;
        } catch (IOException e) {
            debugLog("Error loading shader style data: " + e.getMessage());
            return new ShaderStyleData();
        } catch (JsonSyntaxException e) {
            debugLog("Invalid JSON format in data file: " + e.getMessage());
            return new ShaderStyleData();
        }
    }

    /**
     * Check if the data file exists
     * @return true if the data file exists
     */
    public static boolean dataFileExists() {
        return Files.exists(DATA_FILE);
    }

    /**
     * Delete the data file
     * @return true if deletion was successful or file didn't exist
     */
    public static boolean deleteDataFile() {
        debugLog("Deleting data file: " + DATA_FILE);
        try {
            if (Files.exists(DATA_FILE)) {
                Files.delete(DATA_FILE);
                debugLog("Successfully deleted data file");
                return true;
            }
            debugLog("Data file does not exist, nothing to delete");
            return true;
        } catch (IOException e) {
            debugLog("Error deleting data file: " + e.getMessage());
            return false;
        }
    }
}
