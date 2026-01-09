package com.euphoriapatches.euphoria_patcher.integration.iris;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.features.UpdateShaderConfig;
import com.euphoriapatches.euphoria_patcher.integration.ShaderLoader;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.monitoring.PotatoFileMonitor;
import com.euphoriapatches.euphoria_patcher.util.ModLoaderSpecifics;
import com.euphoriapatches.euphoria_patcher.util.UpdateChecker;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

public class IrisDefineHelper {
    private static int injectCount = 0;
    private static boolean injectedOnce = false;
    public static boolean isIrisRunning = false;

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[IrisDefineHelper] " + message);
    }

    public static void addEuphoriaDefines(List<?> standardDefines, boolean isLegacy,
                                         BiConsumer<List<?>, String> defineKey,
                                         BiConsumer<List<?>, String[]> defineKeyValue) {
        try {
            injectCount++;
            debugLog("Adding Euphoria Patches defines to Iris" + (isLegacy ? " (Legacy)" : ""));
            isIrisRunning = true;

            if (EuphoriaPatcher.isSpacEagle()) {
                defineKey.accept(standardDefines, "SPACEAGLE17");
                debugLog("Adding SPACEAGLE17 define");
            }

            String currentVersion = formatVersion(EuphoriaPatcher.PATCH_VERSION);
            defineKeyValue.accept(standardDefines, new String[]{"CURRENT_EUPHORIA_PATCHES_VERSION", currentVersion});
            debugLog("Adding CURRENT_EUPHORIA_PATCHES_VERSION = " + currentVersion + " define");

            defineKey.accept(standardDefines, "EUPHORIA_PATCHES_MOD_INSTALLED");
            debugLog("Adding EUPHORIA_PATCHES_MOD_INSTALLED define");

            if (ModLoaderSpecifics.isCurrentDimensionInMappingsStatic()) {
                defineKey.accept(standardDefines, "EUPHORIA_PATCHES_DIMENSION_IN_PROPERTIES");
                debugLog("Adding EUPHORIA_PATCHES_DIMENSION_IN_PROPERTIES define");
            } else {
                debugLog("Not adding EUPHORIA_PATCHES_DIMENSION_IN_PROPERTIES define - dimension not in dimensions.properties");
            }

            String currentDimension = "CURRENT_EUPHORIA_PATCHES_DIMENSION_" + ModLoaderSpecifics.getCurrentDimensionStatic().toUpperCase(Locale.ROOT);
            defineKey.accept(standardDefines, currentDimension);
            debugLog("Adding " + currentDimension + " define");

            String shaderLoader = ShaderLoader.getShaderLoader().toUpperCase(Locale.ROOT);
            defineKey.accept(standardDefines, "EUPHORIA_PATCHES_" + shaderLoader);
            debugLog("Adding EUPHORIA_PATCHES_" + shaderLoader + " define");

            String[] shaderLoaderVersionDefine = ShaderLoader.getShaderLoaderVersionDefine();
            if (shaderLoaderVersionDefine != null) {
                defineKeyValue.accept(standardDefines, shaderLoaderVersionDefine);
                debugLog("Adding " + shaderLoaderVersionDefine[0] + " = " + shaderLoaderVersionDefine[1] + " define");
            }

            // Check for potato.png file and add the define if it doesn't exist
            Path currentShaderpack = ShaderLoader.getCurrentShaderpackPath();
            if (currentShaderpack != null) {
                if (PotatoFileMonitor.shouldAddPotatoRemovedDefine(currentShaderpack)) {
                    defineKey.accept(standardDefines, "EUPHORIA_PATCHES_POTATO_REMOVED");
                    debugLog("Adding EUPHORIA_PATCHES_POTATO_REMOVED define - potato.png not found");
                } else {
                    debugLog("Not adding EUPHORIA_PATCHES_POTATO_REMOVED define - potato.png found");
                }
            } else {
                debugLog("Cannot check for potato.png - currentShaderpack is null");
            }

            if (UpdateChecker.shouldUserUpdate() && EuphoriaPatcher.doDisplayShaderInGameMessage && !injectedOnce) {
                defineKey.accept(standardDefines, "NEW_EUPHORIA_PATCHES_UPDATE");
                debugLog("Adding NEW_EUPHORIA_PATCHES_UPDATE define");

                if (UpdateChecker.getNewModVersion() != null) {
                    String nextVersionFormatted = formatVersion(UpdateChecker.getNewModVersion());
                    defineKeyValue.accept(standardDefines, new String[]{"NEXT_EUPHORIA_PATCHES_VERSION", nextVersionFormatted});
                    debugLog("Adding NEXT_EUPHORIA_PATCHES_VERSION: " + nextVersionFormatted + " define");
                }
            }

            UpdateShaderConfig.markEuphoriaPatchesSettingsFiles();

            if (injectCount == 1) {
                EuphoriaPatcher.log(0, "Added Euphoria Patches defines to Iris" + (isLegacy ? " (Legacy)" : ""));
            }

            injectedOnce = true;
        } catch (Exception e) {
            debugLog("Exception while adding defines: " + e.getMessage());
        }
    }

    public static String formatVersion(String version) {
        String[] versionParts = version.replace("_", "").split("\\.");
        StringBuilder versionBuilder = new StringBuilder();
        for (int i = 0; i < versionParts.length; i++) {
            versionBuilder.append("_").append(versionParts[i]);
            if (i < versionParts.length - 1) {
                versionBuilder.append(", _dot, ");
            }
        }
        return versionBuilder.toString();
    }
}
