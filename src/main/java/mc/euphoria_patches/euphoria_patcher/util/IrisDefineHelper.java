package mc.euphoria_patches.euphoria_patcher.mixin;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import mc.euphoria_patches.euphoria_patcher.features.UpdateShaderConfig;
import mc.euphoria_patches.euphoria_patcher.util.ModLoaderSpecifics;
import mc.euphoria_patches.euphoria_patcher.util.UpdateChecker;

import java.util.List;
import java.util.function.BiConsumer;

public class IrisDefineHelper {
    private static int injectCount = 0;
    private static boolean injectedOnce = false;

    public static void addEuphoriaDefines(List<?> standardDefines, boolean isLegacy,
                                         BiConsumer<List<?>, String> defineKey,
                                         BiConsumer<List<?>, String[]> defineKeyValue) {
        try {
            injectCount++;

            if (EuphoriaPatcher.isSpacEagle()) 
                defineKey.accept(standardDefines, "SPACEAGLE17");

            String currentVersion = formatVersion(EuphoriaPatcher.PATCH_VERSION);
            defineKeyValue.accept(standardDefines, new String[]{"CURRENT_EUPHORIA_PATCHES_VERSION", currentVersion});

            defineKey.accept(standardDefines, "EUPHORIA_PATCHES_MOD_INSTALLED");

            defineKey.accept(standardDefines, "CURRENT_EUPHORIA_PATCHES_DIMENSION_" + ModLoaderSpecifics.getCurrentDimension().toUpperCase());

            if (UpdateChecker.NEW_VERSION_AVAILABLE && EuphoriaPatcher.doUpdateChecking
                && EuphoriaPatcher.doDisplayShaderInGameMessage && !injectedOnce) {
                    
                defineKey.accept(standardDefines, "NEW_EUPHORIA_PATCHES_UPDATE");

                if (UpdateChecker.NEW_MOD_VERSION != null) {
                    String nextVersionFormatted = formatVersion(UpdateChecker.NEW_MOD_VERSION);
                    defineKeyValue.accept(standardDefines, new String[]{"NEXT_EUPHORIA_PATCHES_VERSION", nextVersionFormatted});
                }
            }

            UpdateShaderConfig.markEuphoriaPatchesSettingsFiles();

            if (injectCount == 1) {
                EuphoriaPatcher.log(0, "Added Euphoria Patches defines to Iris" + (isLegacy ? " (Legacy)" : ""));
            }

            injectedOnce = true;
        } catch (Exception ignored) {
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