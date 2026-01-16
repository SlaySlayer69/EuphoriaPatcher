package com.euphoriapatches.euphoria_patcher.util;

import com.euphoriapatches.euphoria_patcher.config.ConfigHandler;
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
import java.util.Locale;

public class UpdateChecker {
    private static final String PROJECT_ID = "4H6sumDB";
    private static final String UPDATE_URL = "https://api.modrinth.com/v2/project/" + PROJECT_ID + "/version";
    private static final String MOD_VERSION = EuphoriaPatcher.PATCH_VERSION.replace("_","");
    private static String NEW_MOD_VERSION = null;
    private static boolean NEW_VERSION_AVAILABLE = false;
    private static boolean UPDATE_CHECK_PERFORMED = false;
    private static String RECOMMENDED_PATCH_VERSION = null;
    private static Boolean CACHED_IS_IMPORTANT_UPDATE = null;
    private static Boolean CACHED_IS_PATCH_UPDATE_RECOMMENDED = null;

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
     * Determines if the user should update based on the type of update available.
     * @return true if an important update is available or a recommended patch update exists, false otherwise
     */
    public static boolean shouldUserUpdate() {
        return ConfigHandler.updateMode.equals(ConfigHandler.UpdateMode.ALL) ? isUpdateAvailable() : isUpdateAvailable() && (isImportantUpdate() || isPatchUpdateRecommended());
    }

    /**
     * Determines if the latest update is an important update by comparing major.minor version numbers.
     * An important update occurs when the major (X) or minor (Y) version number in X.Y.Z increases.
     * @return true if the update is important (X or Y increased), false if only patch version changed
     */
    public static boolean isImportantUpdate() {
        // Return cached result if already computed
        if (CACHED_IS_IMPORTANT_UPDATE != null) {
            debugLog("Using cached important update result: " + CACHED_IS_IMPORTANT_UPDATE);
            return CACHED_IS_IMPORTANT_UPDATE;
        }

        debugLog("Checking if update is important...");

        if (!isUpdateAvailable()) {
            debugLog("No new version available, not an important update");
            CACHED_IS_IMPORTANT_UPDATE = false;
            return false;
        }

        try {
            // Parse current version (X.Y.Z)
            String[] currentParts = MOD_VERSION.split("\\.");
            if (currentParts.length < 2) {
                debugLog("Invalid current version format: " + MOD_VERSION);
                CACHED_IS_IMPORTANT_UPDATE = false;
                return false;
            }
            int currentMajor = Integer.parseInt(currentParts[0]); // X
            int currentMinor = Integer.parseInt(currentParts[1]); // Y

            // Parse new version (X.Y.Z)
            String[] newParts = NEW_MOD_VERSION.split("\\.");
            if (newParts.length < 2) {
                debugLog("Invalid new version format: " + NEW_MOD_VERSION);
                CACHED_IS_IMPORTANT_UPDATE = false;
                return false;
            }
            int newMajor = Integer.parseInt(newParts[0]); // X
            int newMinor = Integer.parseInt(newParts[1]); // Y

            debugLog("Current version: " + currentMajor + "." + currentMinor + ", New version: " + newMajor + "." + newMinor);

            // Check if major or minor version increased
            boolean isImportant = (newMajor > currentMajor) || (newMajor == currentMajor && newMinor > currentMinor);

            if (isImportant) {
                debugLog("Important update detected: " + MOD_VERSION + " -> " + NEW_MOD_VERSION);
            } else {
                debugLog("Minor (patch) update detected: " + MOD_VERSION + " -> " + NEW_MOD_VERSION);
            }

            CACHED_IS_IMPORTANT_UPDATE = isImportant;
            return isImportant;
        } catch (NumberFormatException e) {
            debugLog("Error parsing version numbers: " + e.getMessage());
            CACHED_IS_IMPORTANT_UPDATE = false;
            return false;
        }
    }

    /**
     * Determines if a recommended patch update is available in the current patch cycle.
     * Returns true if there's a recommended patch version (X.Y.Z) within the same X.Y cycle
     * that is newer than the current version.
     * @return true if a recommended patch update is available in the same major.minor version
     */
    public static boolean isPatchUpdateRecommended() {
        // Return cached result if already computed
        if (CACHED_IS_PATCH_UPDATE_RECOMMENDED != null) {
            debugLog("Using cached patch update recommendation result: " + CACHED_IS_PATCH_UPDATE_RECOMMENDED);
            return CACHED_IS_PATCH_UPDATE_RECOMMENDED;
        }

        debugLog("Checking if patch update is recommended...");

        if (!isUpdateAvailable()) {
            debugLog("No new version available, patch update not recommended");
            CACHED_IS_PATCH_UPDATE_RECOMMENDED = false;
            return false;
        }

        try {
            // Parse current version (X.Y.Z)
            String[] currentParts = MOD_VERSION.split("\\.");
            if (currentParts.length < 3) {
                debugLog("Invalid current version format: " + MOD_VERSION);
                CACHED_IS_PATCH_UPDATE_RECOMMENDED = false;
                return false;
            }
            int currentMajor = Integer.parseInt(currentParts[0]);
            int currentMinor = Integer.parseInt(currentParts[1]);
            int currentPatch = Integer.parseInt(currentParts[2]);

            // Parse new version (X.Y.Z)
            String[] newParts = NEW_MOD_VERSION.split("\\.");
            if (newParts.length < 3) {
                debugLog("Invalid new version format: " + NEW_MOD_VERSION);
                CACHED_IS_PATCH_UPDATE_RECOMMENDED = false;
                return false;
            }
            int newMajor = Integer.parseInt(newParts[0]);
            int newMinor = Integer.parseInt(newParts[1]);

            // Check if we're in the same major.minor version cycle
            if (currentMajor != newMajor || currentMinor != newMinor) {
                debugLog("Not in the same patch cycle: current " + currentMajor + "." + currentMinor +
                         ", new " + newMajor + "." + newMinor);
                CACHED_IS_PATCH_UPDATE_RECOMMENDED = false;
                return false;
            }

            debugLog("In the same patch cycle: " + currentMajor + "." + currentMinor);

            // Fetch recommended patch version if not already done
            if (RECOMMENDED_PATCH_VERSION == null) {
                RECOMMENDED_PATCH_VERSION = fetchRecommendedPatchVersion(currentMajor, currentMinor);
                if (RECOMMENDED_PATCH_VERSION == null) {
                    RECOMMENDED_PATCH_VERSION = ""; // Use empty string to indicate "fetched but nothing found"
                }
            }

            if (RECOMMENDED_PATCH_VERSION.isEmpty()) {
                debugLog("No recommended patch version found in current cycle");
                CACHED_IS_PATCH_UPDATE_RECOMMENDED = false;
                return false;
            }

            // Check if current version is older than the recommended patch version
            String[] recParts = RECOMMENDED_PATCH_VERSION.split("\\.");
            if (recParts.length >= 3) {
                int recPatch = Integer.parseInt(recParts[2]);
                if (currentPatch < recPatch) {
                    debugLog("Recommended patch update found: " + RECOMMENDED_PATCH_VERSION + " (current: " + MOD_VERSION + ")");
                    CACHED_IS_PATCH_UPDATE_RECOMMENDED = true;
                    return true;
                }
            }

            debugLog("Current version is up to date with recommended patches");
            CACHED_IS_PATCH_UPDATE_RECOMMENDED = false;
            return false;

        } catch (NumberFormatException e) {
            debugLog("Error parsing version numbers: " + e.getMessage());
            CACHED_IS_PATCH_UPDATE_RECOMMENDED = false;
            return false;
        }
    }

    /**
     * Fetches the latest recommended patch version in the current patch cycle.
     * Since versions are ordered newest to oldest, returns the first version with the recommended flag.
     * @param major The major version number (X in X.Y.Z)
     * @param minor The minor version number (Y in X.Y.Z)
     * @return The latest recommended patch version in format "X.Y.Z", or null if none found
     */
    @SuppressWarnings("deprecation")
    private static String fetchRecommendedPatchVersion(int major, int minor) {
        debugLog("Fetching recommended patch version for " + major + "." + minor + ".x");

        try {
            URL url = new URL(UPDATE_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                debugLog("Failed to fetch versions for recommended patch check. Response code: " + responseCode);
                return null;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                JsonElement jsonElement = new JsonParser().parse(reader);
                JsonArray versions = jsonElement.getAsJsonArray();

                debugLog("Analyzing " + versions.size() + " versions for recommended patches");

                String lastCheckedVersion = null;

                // Iterate through all versions (newest to oldest)
                for (int i = 0; i < versions.size(); i++) {
                    JsonObject version = versions.get(i).getAsJsonObject();
                    String fullVersionNumber = version.get("version_number").getAsString();
                    String mainVersion = extractMainVersion(fullVersionNumber);

                    // Skip if we already checked this version (different mod loaders)
                    if (mainVersion.equals(lastCheckedVersion)) {
                        continue;
                    }
                    lastCheckedVersion = mainVersion;

                    // Parse version
                    String[] versionParts = mainVersion.split("\\.");
                    if (versionParts.length < 3) {
                        continue;
                    }

                    try {
                        int vMajor = Integer.parseInt(versionParts[0]);
                        int vMinor = Integer.parseInt(versionParts[1]);

                        // Check if this version is in the current patch cycle
                        if (vMajor != major || vMinor != minor) {
                            // Stop when we go below the current patch cycle
                            if (vMajor < major || (vMajor == major && vMinor < minor)) {
                                debugLog("Reached versions below current patch cycle, stopping");
                                break;
                            }
                            continue;
                        }

                        debugLog("Checking version " + mainVersion + " in current patch cycle");

                        // Check if this version has the recommended flag
                        if (version.has("changelog")) {
                            String changelog = version.get("changelog").getAsString();
                            if (isRecommendedUpdate(changelog)) {
                                debugLog("Found recommended patch version: " + mainVersion);
                                return mainVersion; // Early return with the first (latest) recommended version
                            }
                        }
                    } catch (NumberFormatException e) {
                        debugLog("Error parsing version " + mainVersion + ": " + e.getMessage());
                    }
                }

            } finally {
                connection.disconnect();
            }
        } catch (Exception e) {
            debugLog("Error fetching recommended patch version: " + e.getMessage());
        }

        return null;
    }

    /**
     * Checks if a changelog contains the recommended update marker.
     * @param changelog The changelog text to check
     * @return true if the changelog indicates a recommended update
     */
    private static boolean isRecommendedUpdate(String changelog) {
        if (changelog == null || changelog.isEmpty()) {
            return false;
        }

        String[] lines = changelog.split("\n");
        for (String line : lines) {
            String lowerLine = line.toLowerCase(Locale.ROOT);
            if (lowerLine.contains("updated to complementary shaders") &&
                lowerLine.contains("euphoria patches") &&
                lowerLine.contains("update recommended")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check for updates by querying the Modrinth API
     */
    public static void checkForUpdates() {
        if (UPDATE_CHECK_PERFORMED || ConfigHandler.updateMode.equals(ConfigHandler.UpdateMode.NONE)) {
            debugLog("Update check skipped. Already performed: " + UPDATE_CHECK_PERFORMED + ", Update checking mode set to: " + ConfigHandler.updateMode);
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
                EuphoriaPatcher.log(2, 0, "[UPDATE CHECKER] Download it from Modrinth: " + EuphoriaPatcher.EP_DOWNLOAD_URL);
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
    @SuppressWarnings("deprecation")
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
