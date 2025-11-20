package mc.euphoria_patches.euphoria_patcher.util;

import java.nio.file.Path;

/**
 * Abstract class for mod loader-specific functionality.
 * Each mod loader (Fabric, Forge, NeoForge, etc.) should provide its own implementation.
 */
public abstract class ModLoaderSpecifics {
    private static ModLoaderSpecifics instance;

    /**
     * Set the mod loader-specific instance.
     * This should be called early in the mod initialization by the specific loader implementation.
     */
    public static void setInstance(ModLoaderSpecifics impl) {
        instance = impl;
    }

    /**
     * Get the current mod loader-specific instance.
     */
    public static ModLoaderSpecifics getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ModLoaderSpecifics instance has not been set! " +
                    "Make sure your mod loader implementation calls setInstance() during initialization.");
        }
        return instance;
    }

    // Abstract methods that must be implemented by each mod loader

    /**
     * Get the path to the shaderpacks directory.
     */
    public abstract Path getShaderpacksPath();

    /**
     * Get the path to the config directory.
     */
    public abstract Path getConfigDirectory();

    /**
     * Check if the mod is running in a development environment.
     */
    public abstract boolean isDevMode();

    /**
     * Check if the mod is running on a server (and should be disabled).
     * @return true if running on server, false otherwise
     */
    public abstract boolean serverCheck();

    /**
     * Get the current dimension the player is in.
     * @return dimension string: "overworld", "nether", "end", or "other"
     */
    public abstract String getCurrentDimension();

    // Convenience static methods that delegate to the instance

    public static Path shaderpacks() {
        return getInstance().getShaderpacksPath();
    }

    public static Path configDirectory() {
        return getInstance().getConfigDirectory();
    }

    public static boolean isDevModeStatic() {
        return getInstance().isDevMode();
    }

    public static boolean serverCheckStatic() {
        return getInstance().serverCheck();
    }

    public static String getCurrentDimensionStatic() {
        return getInstance().getCurrentDimension();
    }
}
