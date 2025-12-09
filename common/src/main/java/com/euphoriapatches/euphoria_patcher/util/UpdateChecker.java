package com.euphoriapatches.euphoria_patcher.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {
    private static final String PROJECT_ID = "4H6sumDB";
    private static final String UPDATE_URL = "https://api.modrinth.com/v2/project/" + PROJECT_ID + "/version";
    private static final String MOD_VERSION = EuphoriaPatcher.PATCH_VERSION.replace("_","");
    private static String NEW_MOD_VERSION = null;
    private static String LATEST_CHANGELOG = null;
    private static boolean NEW_VERSION_AVAILABLE = false;
    private static boolean UPDATE_CHECK_PERFORMED = false;

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[UpdateChecker] " + message);
    }

    /**
    * Checks if a new version of the mod is available.
    * @return true if a new version is available, false otherwise
    */
    public static boolean isUpdateAvailable() {
        try {
            checkForUpdates();
            return NEW_VERSION_AVAILABLE;
        } catch (Exception e) {
            return false;
        }
    }

    /**
    * Returns the new mod version if an update check has been performed.
    * If no update check has been performed yet, it triggers one.
    * @return The latest mod version as a String, formatted: "major.minor.patch"
    */
    public static String getNewModVersion() {
        checkForUpdates();
        return NEW_MOD_VERSION;
    }

    /**
    * Returns the latest changelog if an update check has been performed.
    * If no update check has been performed yet, it triggers one.
    * @return The latest changelog as a String
    */
    public static String getLatestChangelog() {
        checkForUpdates();
        return LATEST_CHANGELOG;
    }

    /**
     * Determines if the latest update is a major update based on the changelog content.
     * @return true if the update is major, false if minor or undetermined
     */
    public static boolean isMajorUpdate() {
        checkForUpdates();
        debugLog("Checking if update is major...");

        if (LATEST_CHANGELOG == null || LATEST_CHANGELOG.isEmpty()) {
            debugLog("No changelog available, assuming minor update");
            return false;
        }

        // Split changelog into lines
        String[] lines = LATEST_CHANGELOG.split("\n");

        for (String line : lines) {
            debugLog("Checking line: " + line.trim());

            // Check if line contains the pattern and "This is a minor update"
            if (line.toLowerCase().contains("updated to complementary shaders") &&
                line.toLowerCase().contains("euphoria patches") &&
                line.toLowerCase().contains("this is a minor update")) {

                debugLog("Found minor update indicator in changelog - this is NOT a major update");
                return false;
            }
        }

        debugLog("No minor update indicator found in changelog - this is a major update");
        return true;
    }

    /**
     * Check for updates by querying the Modrinth API
     */
    public static void checkForUpdates() {
        if (UPDATE_CHECK_PERFORMED || !EuphoriaPatcher.doUpdateChecking) {
            debugLog("Update check skipped. Already performed: " + UPDATE_CHECK_PERFORMED + ", Update checking enabled: " + EuphoriaPatcher.doUpdateChecking);
            return;
        }
        UPDATE_CHECK_PERFORMED = true;
        debugLog("Starting update check...");
        debugLog("Current version: " + MOD_VERSION);

        try {
            NEW_MOD_VERSION = fetchLatestVersion();
            if (NEW_MOD_VERSION == null) {
                EuphoriaPatcher.log(2, 0, "[UPDATE CHECKER] Failed to fetch the latest version.");
                debugLog("Failed to fetch latest version from Modrinth API");
                return;
            }

            debugLog("Latest version from Modrinth: " + NEW_MOD_VERSION);

            if (VersionComparator.isNewerVersion(NEW_MOD_VERSION, MOD_VERSION)) {
                NEW_VERSION_AVAILABLE = true;
                debugLog("New version available!");

                EuphoriaPatcher.log(2, 0, "[UPDATE CHECKER] A new version of the EuphoriaPatcher Mod is available: " + NEW_MOD_VERSION);
                EuphoriaPatcher.log(2, 0, "[UPDATE CHECKER] Download it from Modrinth: https://euphoriapatches.com/download");
                EuphoriaPatcher.log(2, 0, "[UPDATE CHECKER] Current Version: " + MOD_VERSION);
            } else {
                debugLog("Mod is up to date");
                EuphoriaPatcher.log(0, "[UPDATE CHECKER] The EuphoriaPatcher Mod is up to date");
            }
        } catch (Exception e) {
            String errorType = e.getClass().getSimpleName();
            String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown error";
            EuphoriaPatcher.log(2, 0, "[UPDATE CHECKER] Update check failed: " + errorType + " - " + errorMessage);
            debugLog("Update check failed with " + errorType + ": " + errorMessage);
            debugLog("Full exception: " + e);
        }
    }

    // Extract main version before the first dash (e.g., "1.7.4-r5.6.1-fabric" -> "1.7.4")
    private static String extractMainVersion(String versionString) {
        return versionString.split("-")[0];
    }

    // Fetch the latest version from the Modrinth API
    private static String fetchLatestVersion() throws Exception {
        debugLog("Fetching latest version from Modrinth API: " + UPDATE_URL);

        try {
            URL url = new URL(UPDATE_URL);
            debugLog("URL created successfully");

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            debugLog("Connection configured, attempting to connect...");

            int responseCode = connection.getResponseCode();
            debugLog("Modrinth API response code: " + responseCode);

            if (responseCode != 200) {
                EuphoriaPatcher.log(2, 0, "[UPDATE CHECKER] Failed to connect to Modrinth API. Response code: " + responseCode);
                debugLog("Connection failed with response code: " + responseCode);
                return null;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                debugLog("Reading response from API...");
                JsonElement jsonElement = new JsonParser().parse(reader); // Old method used to make it work with Java 8 for old MC versions
                JsonArray versions = jsonElement.getAsJsonArray();

                debugLog("Fetched " + versions.size() + " versions from Modrinth");

                if (versions.isEmpty()) {
                    EuphoriaPatcher.log(2, 0, "[UPDATE CHECKER] No versions found on Modrinth.");
                    debugLog("No versions found in API response");
                    return MOD_VERSION;
                }

                // Get the first version (latest)
                JsonObject latestVersion = versions.get(0).getAsJsonObject();
                String fullVersionNumber = latestVersion.get("version_number").getAsString();
                debugLog("Full version number from Modrinth: " + fullVersionNumber);

                // Extract and store changelog
                if (latestVersion.has("changelog")) {
                    LATEST_CHANGELOG = latestVersion.get("changelog").getAsString();
                    debugLog("Changelog fetched successfully!");
                    debugLog("Fetched Changelog is:\n" + LATEST_CHANGELOG);
                } else {
                    debugLog("No changelog available for latest version");
                }

                String mainVersion = extractMainVersion(fullVersionNumber);
                debugLog("Extracted main version: " + mainVersion);
                return mainVersion;
            } finally {
                connection.disconnect();
                debugLog("Connection closed");
            }
        } catch (java.net.UnknownHostException e) {
            debugLog("Network error: Unable to resolve Modrinth API host (no internet connection or DNS issue)");
            throw new Exception("Unable to reach Modrinth API - No internet connection or DNS issue: " + e.getMessage(), e);
        } catch (java.net.ConnectException e) {
            debugLog("Network error: Connection refused or timeout");
            throw new Exception("Unable to connect to Modrinth API - Connection refused or timeout: " + e.getMessage(), e);
        } catch (java.net.SocketTimeoutException e) {
            debugLog("Network error: Request timed out");
            throw new Exception("Modrinth API request timed out - Please check your internet connection: " + e.getMessage(), e);
        } catch (java.io.IOException e) {
            debugLog("IO error while fetching version: " + e.getMessage());
            throw new Exception("IO error while accessing Modrinth API: " + e.getMessage(), e);
        } catch (Exception e) {
            debugLog("Unexpected error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            throw new Exception("Unexpected error while fetching version: " + e.getMessage(), e);
        }
    }
}
