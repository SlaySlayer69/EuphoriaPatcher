package com.euphoriapatches.euphoria_patcher.util;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class SpacEagle17 {
    public static boolean check(){
        try {
            boolean containsSpacEagle = EuphoriaPatcher.shaderpacks.toString().toLowerCase(Locale.ROOT).contains("spaceagle");
            debugLog("Contains SpacEagle in Path: " + containsSpacEagle);
            Path euphoriaFolder = EuphoriaPatcher.shaderpacks.resolve("Euphoria-Patches");
            boolean hasEuphoriaFolder = Files.exists(euphoriaFolder) && Files.isDirectory(euphoriaFolder);
            debugLog("Euphoria-Patches folder exists: " + hasEuphoriaFolder);
            return containsSpacEagle && hasEuphoriaFolder;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[SpacEagle17] " + message);
    }
}
