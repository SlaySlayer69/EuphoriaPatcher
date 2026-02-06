package com.euphoriapatches.euphoria_patcher.util;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
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

    // Cache for loaded data to avoid repeated disk I/O
    private static PersistentShaderData cachedData = null;

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ShaderData] " + message);
    }

    /**
     * Validates that the stored shaderpacks directory hash matches the current one.
     * If the hash differs, deletes the data file to prevent cross-user data usage.
     * If no hash is stored, saves the current one.
     */
    public static void validateShaderDataHash() {
        debugLog("Validating shaderpacks directory hash");

        String currentShadersDir = EuphoriaPatcher.shaderpacks.toString();
        debugLog("Current shaderpacks directory: " + currentShadersDir);

        String currentShaderHash = HashUtils.calculateSHA256(currentShadersDir);
        if (currentShaderHash == null) {
            debugLog("Failed to calculate hash for current shaderpacks directory");
            return;
        }
        debugLog("Current shaderpacks directory hash: " + currentShaderHash);

        if (!dataFileExists()) {
            debugLog("Data file does not exist, will create with current shaderpacks hash");
            save(SaveData.of(DataField.SHADER_HASH, currentShaderHash));
            return;
        }

        try (Reader reader = new InputStreamReader(
                Files.newInputStream(DATA_FILE), StandardCharsets.UTF_8)) {
            PersistentShaderData data = GSON.fromJson(reader, PersistentShaderData.class);

            if (data == null || data.shaderHash == null) {
                debugLog("No shaderpacks hash stored in data file, will save current one");
                save(SaveData.of(DataField.SHADER_HASH, currentShaderHash));
                return;
            }

            if (!data.shaderHash.equals(currentShaderHash)) {
                debugLog("Shaderpacks hash mismatch! Stored: " + data.shaderHash + ", Current: " + currentShaderHash);
                debugLog("Deleting data file to ensure user-specific data");
                deleteDataFile();
                save(SaveData.of(DataField.SHADER_HASH, currentShaderHash));
            } else {
                debugLog("Current shader data directory hash: " + data.shaderHash);
                debugLog("Shaderpacks hash matches, data file is valid for this user");
            }
        } catch (IOException e) {
            debugLog("Error reading data file for validation: " + e.getMessage());
        } catch (JsonSyntaxException e) {
            debugLog("Invalid JSON format in data file during validation: " + e.getMessage());
        }
    }

    /**
     * Enum representing fields that can be saved/loaded
     */
    public enum DataField {
        STYLE_REIMAGINED("styleReimagined"),
        STYLE_UNBOUND("styleUnbound"),
        SHADER_HASH("shaderHash"),
        SUPPORT_EP_BUTTON("supportEPButtonVisible");

        private final String jsonKey;

        DataField(String jsonKey) {
            this.jsonKey = jsonKey;
        }

        public String getJsonKey() {
            return jsonKey;
        }

        /**
         * Get all allowed JSON keys as a set
         * @return Set of allowed JSON key names
         */
        public static java.util.Set<String> getAllowedKeys() {
            java.util.Set<String> keys = new java.util.HashSet<>();
            for (DataField field : values()) {
                keys.add(field.getJsonKey());
            }
            return keys;
        }
    }

    /**
     * Helper class to pair a field with its value for saving
     */
    public static class SaveData {
        public final DataField field;
        public final Object value;

        private SaveData(DataField field, Object value) {
            this.field = field;
            this.value = value;
        }

        public static SaveData of(DataField field, Object value) {
            return new SaveData(field, value);
        }
    }

    /**
     * Save specific fields to the data.json file. Only updates the specified fields,
     * preserving other existing data.
     *
     * @param updates One or more field updates to save
     */
    public static void save(SaveData... updates) {
        if (updates == null || updates.length == 0) {
            debugLog("No updates provided to save");
            return;
        }

        debugLog("Saving " + updates.length + " field(s) to data file");

        try {
            // Ensure config directory exists
            if (!Files.exists(Config.CONFIG_DIR)) {
                Files.createDirectories(Config.CONFIG_DIR);
                debugLog("Created config directory: " + Config.CONFIG_DIR);
            }

            // Load existing JSON or create new object
            JsonObject jsonObject;
            if (dataFileExists()) {
                try (Reader reader = new InputStreamReader(
                        Files.newInputStream(DATA_FILE), StandardCharsets.UTF_8)) {
                    JsonElement element = GSON.fromJson(reader, JsonElement.class);
                    jsonObject = (element != null && element.isJsonObject()) ? element.getAsJsonObject() : new JsonObject();
                }
            } else {
                jsonObject = new JsonObject();
            }

            // Remove any old/unknown fields that are no longer allowed
            java.util.Set<String> allowedKeys = DataField.getAllowedKeys();
            java.util.Set<String> keysToRemove = new java.util.HashSet<>();
            for (String key : jsonObject.keySet()) {
                if (!allowedKeys.contains(key)) {
                    keysToRemove.add(key);
                }
            }
            if (!keysToRemove.isEmpty()) {
                debugLog("Removing " + keysToRemove.size() + " old/unknown field(s): " + keysToRemove);
                for (String key : keysToRemove) {
                    jsonObject.remove(key);
                }
            }

            // Update specified fields only
            for (SaveData update : updates) {
                switch (update.field) {
                    case STYLE_REIMAGINED:
                    case STYLE_UNBOUND:
                    case SUPPORT_EP_BUTTON:
                        jsonObject.addProperty(update.field.getJsonKey(), (Boolean) update.value);
                        debugLog("Updating " + update.field + " to " + update.value);
                        break;
                    case SHADER_HASH:
                        jsonObject.addProperty(update.field.getJsonKey(), (String) update.value);
                        debugLog("Updating " + update.field + " to " + update.value);
                        break;
                }
            }

            // Write to file
            try (Writer writer = new OutputStreamWriter(
                    Files.newOutputStream(DATA_FILE), StandardCharsets.UTF_8)) {
                GSON.toJson(jsonObject, writer);
                debugLog("Successfully saved data to " + DATA_FILE);
                cachedData = null; // Invalidate cache after save
            }
        } catch (IOException e) {
            debugLog("Error saving data: " + e.getMessage());
        } catch (ClassCastException e) {
            debugLog("Invalid type for field update: " + e.getMessage());
        }
    }

    /**
     * Convenience method to save both shader styles at once
     *
     * @param styleReimagined Whether Reimagined style is used
     * @param styleUnbound    Whether Unbound style is used
     */
    public static void saveShaderStyles(boolean styleReimagined, boolean styleUnbound) {
        save(
            SaveData.of(DataField.STYLE_REIMAGINED, styleReimagined),
            SaveData.of(DataField.STYLE_UNBOUND, styleUnbound)
        );
    }

    /**
     * Load shader data from the data.json file
     * @return PersistentShaderData object with loaded data, or default values if file doesn't exist
     */
    public static PersistentShaderData load() {
        // Return cached data if available
        if (cachedData != null) {
            debugLog("Returning cached shader data");
            return cachedData;
        }

        debugLog("Loading shader data from " + DATA_FILE);

        if (!Files.exists(DATA_FILE)) {
            debugLog("Data file does not exist, returning default values");
            cachedData = new PersistentShaderData();
            return cachedData;
        }

        try (Reader reader = new InputStreamReader(
                Files.newInputStream(DATA_FILE), StandardCharsets.UTF_8)) {
            PersistentShaderData data = GSON.fromJson(reader, PersistentShaderData.class);

            if (data == null) {
                debugLog("Parsed data is null, returning default values");
                cachedData = new PersistentShaderData();
                return cachedData;
            }

            cachedData = data;
            return cachedData;
        } catch (IOException e) {
            debugLog("Error loading shader data: " + e.getMessage());
            cachedData = new PersistentShaderData();
            return cachedData;
        } catch (JsonSyntaxException e) {
            debugLog("Invalid JSON format in data file: " + e.getMessage());
            cachedData = new PersistentShaderData();
            return cachedData;
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
     */
    public static void deleteDataFile() {
        debugLog("Deleting data file: " + DATA_FILE);
        try {
            if (Files.exists(DATA_FILE)) {
                Files.delete(DATA_FILE);
                debugLog("Successfully deleted data file");
                cachedData = null; // Invalidate cache after delete
                return;
            }
            debugLog("Data file does not exist, nothing to delete");
        } catch (IOException e) {
            debugLog("Error deleting data file: " + e.getMessage());
        }
    }

    /**
     * Data class to hold persistent shader information
     */
    public static class PersistentShaderData {
        public Boolean styleReimagined;
        public Boolean styleUnbound;
        public String shaderHash;
        public Boolean supportEPButtonVisible;

        public PersistentShaderData() {
            this.styleReimagined = null;
            this.styleUnbound = null;
            this.shaderHash = null;
            this.supportEPButtonVisible = null;
        }
    }
}
