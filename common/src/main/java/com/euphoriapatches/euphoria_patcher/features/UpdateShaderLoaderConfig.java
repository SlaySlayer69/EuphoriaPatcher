package com.euphoriapatches.euphoria_patcher.features;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.integration.iris.IrisReloadManager;
import com.euphoriapatches.euphoria_patcher.integration.ShaderLoader;

import java.io.*;
import java.nio.file.Path;

public class UpdateShaderLoaderConfig {

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[UpdateShaderLoaderConfig] " + message);
    }

    @SuppressWarnings("SpellCheckingInspection")
    public static void updateShaderLoaderConfig(boolean styleUnbound, boolean styleReimagined) {
        debugLog("Starting updateShaderLoaderConfig - Unbound: " + styleUnbound + ", Reimagined: " + styleReimagined);

        Path shaderLoaderConfig = ShaderLoader.getShaderLoaderConfigPath();
        if (shaderLoaderConfig == null) {
            debugLog("No shader loader config found");
            EuphoriaPatcher.log(0, "No shader loader config found");
            return;
        }
        debugLog("Found shader loader config at: " + shaderLoaderConfig);

        String shaderLoaderName = shaderLoaderConfig.toString().contains("iris") ? "iris.properties" :
                                 shaderLoaderConfig.toString().contains("oculus") ? "oculus.properties" :
                                 "OptiFine's optionsshaders.txt";
        debugLog("Identified shader loader: " + shaderLoaderName);

        File fileToBeModified = shaderLoaderConfig.toFile();
        StringBuilder oldContent = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new FileReader(fileToBeModified))) {
            debugLog("Reading shader loader config file");
            String line;
            while ((line = reader.readLine()) != null) {
                oldContent.append(line).append(System.lineSeparator());
            }
            debugLog("Successfully read shader loader config file");

            boolean hasPatchName = oldContent.toString().contains(EuphoriaPatcher.PATCH_NAME);
            boolean hasPatchVersion = oldContent.toString().contains(EuphoriaPatcher.PATCH_VERSION);
            debugLog("Config contains patch name: " + hasPatchName + ", contains patch version: " + hasPatchVersion);

            if (hasPatchName && !hasPatchVersion) {
                debugLog("Need to update shader config - found old version reference");
                String newContent = setNewShaderLoaderSelectedPackName(oldContent, styleUnbound, styleReimagined);
                debugLog("Generated new content for config file");

                try (FileWriter writer = new FileWriter(fileToBeModified)) {
                    debugLog("Writing updated content to config file");
                    writer.write(newContent);
                    debugLog("Successfully wrote updated content");
                } catch (IOException e) {
                    debugLog("Error writing to config file: " + e.getMessage());
                    EuphoriaPatcher.log(3,0, "Error writing to " + shaderLoaderName + " config file: " + e.getMessage());
                    return;
                }

                String oldPack = oldContent.toString().contains("shaderPack=") ?
                    oldContent.toString().split("shaderPack=")[1].split("\n")[0].trim() : "unknown";
                String newPack = newContent.contains("shaderPack=") ?
                    newContent.split("shaderPack=")[1].split("\n")[0].trim() : "unknown";
                debugLog("Updated shader pack reference from '" + oldPack + "' to '" + newPack + "'");

                EuphoriaPatcher.log(0, "Successfully applied new version in " + shaderLoaderName + " config file!");
                EuphoriaPatcher.log(0, oldPack + " -> " + newPack);
            } else {
                debugLog("No update needed for shader config");
            }
        } catch (IOException e) {
            debugLog("Error accessing shader loader config file: " + e.getMessage());
            EuphoriaPatcher.log(3,0, "Error reading or writing to " + shaderLoaderName + " config file: " + e.getMessage());
            return;
        }

        debugLog("Attempting to schedule shader reload");
        IrisReloadManager.findAndScheduleReload();
    }

    private static String setNewShaderLoaderSelectedPackName(StringBuilder oldContent, boolean styleUnbound, boolean styleReimagined) {
        String style = styleUnbound ? "Unbound" : "Reimagined";
        if (styleUnbound && styleReimagined) { // Both styles installed
            style = oldContent.toString().contains(EuphoriaPatcher.PATCH_NAME) && !oldContent.toString().contains(EuphoriaPatcher.PATCH_VERSION) && oldContent.toString().contains("Unbound") ? "Unbound" : "Reimagined";
        }
        String newName = EuphoriaPatcher.BRAND_NAME + style + EuphoriaPatcher.VERSION + " + " + EuphoriaPatcher.PATCH_NAME + EuphoriaPatcher.PATCH_VERSION;
        return oldContent.toString().replaceAll("shaderPack=.*", "shaderPack=" + newName);
    }
}
