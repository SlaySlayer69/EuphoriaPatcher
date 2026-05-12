package com.euphoriapatches.euphoria_patcher.util.mod;

import java.nio.file.Path;

/**
 * Abstract class for mod loader-specific functionality.
 * Each mod loader (Fabric, Forge, NeoForge, etc.) should provide its own implementation.
 */
public abstract class ModLoaderSpecifics {
    // Instance name constants
    public static final String FABRIC = "Fabric";
    public static final String FORGE = "Forge";
    public static final String NEOFORGE = "NeoForge";
    public static final String FORGE_LEGACY = "ForgeLegacy";
    public static final String FORGE_1_7_10 = "Forge1.7.10";

    private static ModLoaderSpecifics instance;
    private static boolean instanceLogged = false;

    /**
     * Set the mod loader-specific instance.
     * This should be called early in the mod initialization by the specific loader implementation.
     */
    public static void setInstance(ModLoaderSpecifics impl) {
        instance = impl;
        if (!instanceLogged && impl != null) {
            instanceLogged = true;
        }
    }

    /**
     * Get the current mod loader-specific instance.
     */
    public static ModLoaderSpecifics getInstance() {
        if (instance == null) {
            throw new IllegalStateException("[EuphoriaPatcher] ModLoaderSpecifics instance not set! This indicates a serious initialization error. - ModLoaderSpecifics getInstance() called before setInstance()");
        }
        return instance;
    }

    // Abstract methods that must be implemented by each mod loader

    /**
     * Get the name of this mod loader instance.
     * Should return one of the constant values: FABRIC, FORGE, NEOFORGE, etc.
     */
    public abstract String getInstanceName();

    /**
     * Get the path to the shaderpacks directory.
     */
    public abstract Path getShaderpacksPath();

    /**
     * Get the path to the config directory.
     */
    public abstract Path getConfigDirectory();

    /**
     * Check if the mod is running on a server (and should be disabled).
     * @return true if running on server, false otherwise
     */
    public abstract boolean serverCheck();

    /**
     * Get the current dimension the player is in.
     * @return dimension string: "overworld", "nether", "end", or "or sanitized dimension ID"
     */
    public abstract String getCurrentDimension();

    /**
     * Check if the current dimension ID is present in the dimension mappings.
     * @return true if present, false otherwise
     */
    public abstract boolean isCurrentDimensionInMappings();

    /**
     * Set the system clipboard to the given string.
     * @param str String to set in clipboard
     * @return true if successful, false otherwise
     */
    public abstract boolean setClipboard(String str);

    // Convenience static methods that delegate to the instance

    /**
     * Get the name of the current mod loader instance.
     */
    public static String getInstanceNameStatic() {
        return getInstance().getInstanceName();
    }

    /**
     * Check if the current instance matches the given name.
     * @param name Instance name to check (use constants like FABRIC, FORGE, etc.)
     * @return true if the current instance matches the given name
     */
    public static boolean isInstance(String name) {
        return getInstance().getInstanceName().equals(name);
    }

    /**
     * Get the path to the shaderpacks directory.
     */
    public static Path shaderpacks() {
        return getInstance().getShaderpacksPath();
    }

    /**
     * Get the path to the config directory.
     */
    public static Path configDirectory() {
        return getInstance().getConfigDirectory();
    }

    /**
     * Check if the mod is running on a server (and should be disabled).
     * @return true if running on server, false otherwise
     */
    public static boolean serverCheckStatic() {
        return getInstance().serverCheck();
    }

    /**
     * Get the current dimension the player is in.
     * @return dimension string: "overworld", "nether", "end", or "or sanitized dimension ID"
     */
    public static String getCurrentDimensionStatic() {
        return getInstance().getCurrentDimension();
    }

    /**
     * Check if the current dimension ID is present in the dimension mappings.
     * @return true if present, false otherwise
     */
    public static boolean isCurrentDimensionInMappingsStatic() {
        return getInstance().isCurrentDimensionInMappings();
    }

    /**
     * Set the system clipboard to the given string.
     * @param str String to set in clipboard
     * @return true if successful, false otherwise
     */
    public static boolean setClipboardStatic(String str) {
        return getInstance().setClipboard(str);
    }
}
