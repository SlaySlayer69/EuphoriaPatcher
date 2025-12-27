package com.euphoriapatches.euphoria_patcher.forge;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.Dimensions;
import com.euphoriapatches.euphoria_patcher.util.ModLoaderSpecifics;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;


public class ForgeModLoaderSpecifics extends ModLoaderSpecifics {

    private final Path shaderpacksPath;
    private final Path configDirectory;
    // 0 = unknown, 1 = obfuscated, 2 = modern, 3 = pre-1.16.5
    private static int mappingBranch = 0;

    public ForgeModLoaderSpecifics() {
        this.shaderpacksPath = FMLPaths.GAMEDIR.get().resolve("shaderpacks");
        this.configDirectory = FMLPaths.CONFIGDIR.get();
    }

    @Override
    public Path getShaderpacksPath() {
        return shaderpacksPath;
    }

    @Override
    public String getInstanceName() {
        return ModLoaderSpecifics.FORGE;
    }

    @Override
    public Path getConfigDirectory() {
        return configDirectory;
    }

    @Override
    public boolean serverCheck() {
        try {
            if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
                System.err.println("[EuphoriaPatcher] Server Detected! The Euphoria Patcher Mod disables itself gracefully on a server. Disabling...");
                return true;
            }
        } catch (Throwable t) {
            // Any error, assume not a server
        }
        return false;
    }

    @Override
    public String getCurrentDimension() {
        if (mappingBranch == 0) discoverMappingBranch();

        // Use cached result
        if (mappingBranch == 1) {
            return getCurrentDimensionObfuscated();
        } else if (mappingBranch == 2) {
            return getCurrentDimensionModern();
        } else if (mappingBranch == 3) {
            return getCurrentDimensionObfuscatedPre11605();
        }
        return "overworld";
    }

    @Override
    public boolean setClipboard(String str) {
        if (mappingBranch == 0) discoverMappingBranch();

        // Use cached result
        if (mappingBranch == 1) {
            return setClipboardObfuscated(str);
        } else if (mappingBranch == 2) {
            return setClipboardModern(str);
        } else if (mappingBranch == 3) {
            return setClipboardObfuscatedPre11605(str);
        }

        return false;
    }

    private boolean setClipboardObfuscated(String str) {
        try {
            Class<?> minecraftClass = Minecraft.class;
            Object minecraft = minecraftClass.getMethod("m_91087_").invoke(null);

            if (minecraft == null) {
                debugLog("Minecraft instance is null, cannot set clipboard");
                return false;
            }

            Object window = minecraftClass.getMethod("m_91268_").invoke(minecraft);
            debugLog("Accessed window via m_91268_ method");

            if (window == null) {
                debugLog("Window is null, cannot set clipboard");
                return false;
            }

            Class<?> windowClass = window.getClass();
            long windowHandle = (long) windowClass.getMethod("m_85439_").invoke(window);
                debugLog("Accessed window handle via m_85439_ method");

            org.lwjgl.glfw.GLFW.glfwSetClipboardString(windowHandle, str);
            return true;
        } catch (Exception e) {
            debugLog("Error setting clipboard (obfuscated): " + e.getMessage());
            return false;
        }
    }

    private boolean setClipboardModern(String str) {
        try {
            Class<?> minecraftClass = Minecraft.class;
            Object minecraft = minecraftClass.getMethod("getInstance").invoke(null);

            if (minecraft == null) {
                debugLog("Minecraft instance is null, cannot set clipboard");
                return false;
            }

            Object window = minecraftClass.getMethod("getWindow").invoke(minecraft);

            if (window == null) {
                debugLog("Window is null, cannot set clipboard");
                return false;
            }

            Class<?> windowClass = window.getClass();
            long windowHandle;
            try {
                windowHandle = (long) windowClass.getMethod("handle").invoke(window);
            } catch (NoSuchMethodException e1) {
                windowHandle = (long) windowClass.getMethod("getWindow").invoke(window);
            }

            org.lwjgl.glfw.GLFW.glfwSetClipboardString(windowHandle, str);
            return true;
        } catch (Exception e) {
            debugLog("Error setting clipboard (modern): " + e.getMessage());
            return false;
        }
    }

    private boolean setClipboardObfuscatedPre11605(String str) {
        try {
            Class<?> minecraftClass = Minecraft.class;
            Object minecraft = minecraftClass.getMethod("func_71410_x").invoke(null);

            if (minecraft == null) {
                debugLog("Minecraft instance is null, cannot set clipboard");
                return false;
            }

            Object window = minecraftClass.getMethod("func_228018_at_").invoke(minecraft);
            debugLog("Accessed window via func_228018_at_ method");

            if (window == null) {
                debugLog("Window is null, cannot set clipboard");
                return false;
            }

            Class<?> windowClass = window.getClass();
            long windowHandle = (long) windowClass.getMethod("func_198092_i").invoke(window);
            debugLog("Accessed window handle via func_198092_i method");

            org.lwjgl.glfw.GLFW.glfwSetClipboardString(windowHandle, str);
            return true;
        } catch (Exception e) {
            debugLog("Error setting clipboard (pre-1.16.5): " + e.getMessage());
            return false;
        }
    }

    /**
     * Discovers which mapping branch is being used and caches the result.
     * Tries obfuscated, then modern, then pre-1.16.5.
     */
    private void discoverMappingBranch() {
        if (mappingBranch != 0) {
            return; // Already discovered
        }

        // Try obfuscated first
        try {
            Class<?> minecraftClass = Minecraft.class;
            minecraftClass.getMethod("m_91087_").invoke(null);
            mappingBranch = 1;
            debugLog("Using obfuscated mappings");
            return;
        } catch (Throwable t) {
            debugLog("Obfuscated method failed, trying modern: " + t.getMessage());
        }

        // Try modern
        try {
            Class<?> minecraftClass = Minecraft.class;
            minecraftClass.getMethod("getInstance").invoke(null);
            mappingBranch = 2;
            debugLog("Using modern mappings");
            return;
        } catch (Throwable t) {
            debugLog("Modern method failed, trying pre-1.16.5: " + t.getMessage());
        }

        // Try pre-1.16.5
        try {
            Class<?> minecraftClass = Minecraft.class;
            minecraftClass.getMethod("func_71410_x").invoke(null);
            mappingBranch = 3;
            debugLog("Using obfuscated pre-1.16.5 mappings");
            return;
        } catch (Throwable t) {
            debugLog("Pre-1.16.5 method also failed: " + t.getMessage());
        }
    }

    private String getCurrentDimensionObfuscated() {
        debugLog("Getting current dimension (obfuscated mappings)");

        try {
            // Get Minecraft instance using obfuscated method
            Class<?> minecraftClass = Minecraft.class;
            Object minecraft = minecraftClass.getMethod("m_91087_").invoke(null);
            debugLog("Got Minecraft instance using m_91087_");

            if (minecraft == null) {
                return "overworld";
            }

            // Get level field using obfuscated name
            Object level = minecraft.getClass().getField("f_91073_").get(minecraft);
            debugLog("Got level field f_91073_");

            if (level == null) {
                return "overworld";
            }

            // Get dimension key using obfuscated method
            Object dimensionKey = level.getClass().getMethod("m_46472_").invoke(level);
            debugLog("Got dimension key using m_46472_");

            // Get location using obfuscated method
            Object location = dimensionKey.getClass().getMethod("m_135782_").invoke(dimensionKey);
            debugLog("Got location using m_135782_");

            String currentDimensionId = location.toString();
            debugLog("Dimension ID: " + currentDimensionId);

            return Dimensions.getCurrentDimension(currentDimensionId);
        } catch (Exception e) {
            debugLog("Error in obfuscated method: " + e.getClass().getName() + " - " + e.getMessage());
            return "overworld";
        }
    }

    private String getCurrentDimensionModern() {
        debugLog("Getting current dimension (modern mappings)");

        try {
            // Get Minecraft instance using modern method
            Class<?> minecraftClass = Minecraft.class;
            Object minecraft = minecraftClass.getMethod("getInstance").invoke(null);
            debugLog("Got Minecraft instance using getInstance");

            if (minecraft == null) {
                return "overworld";
            }

            // Get level field using modern name
            Object level = minecraft.getClass().getField("level").get(minecraft);
            debugLog("Got level field");

            if (level == null) {
                return "overworld";
            }

            // Get dimension key using modern method
            Object dimensionKey = level.getClass().getMethod("dimension").invoke(level);
            debugLog("Got dimension key using dimension");

            // Modern version doesn't have working location(), parse from toString
            String dimensionString = dimensionKey.toString();
            debugLog("Dimension key toString(): " + dimensionString);

            // Format: "ResourceKey[minecraft:dimension / minecraft:overworld]"
            String currentDimensionId;
            if (dimensionString.contains("/")) {
                currentDimensionId = dimensionString.substring(dimensionString.indexOf("/") + 1)
                        .replace("]", "").trim();
            } else {
                currentDimensionId = "minecraft:overworld";
            }

            debugLog("Current dimension ID (parsed): " + currentDimensionId);
            return Dimensions.getCurrentDimension(currentDimensionId);
        } catch (Exception e) {
            debugLog("Error in modern method: " + e.getClass().getName() + " - " + e.getMessage());
            return "overworld";
        }
    }

     private String getCurrentDimensionObfuscatedPre11605() {
        debugLog("Getting current dimension (obfuscated pre-1.16.5)");

        try {
            // Get Minecraft instance using obfuscated method
            Class<?> minecraftClass = Minecraft.class;
            Object minecraft = minecraftClass.getMethod("func_71410_x").invoke(null);
            debugLog("Got Minecraft instance using func_71410_x");

            if (minecraft == null) {
                return "overworld";
            }

            // Get world field using obfuscated name
            Object world = minecraft.getClass().getField("field_71441_e").get(minecraft);
            debugLog("Got world field field_71441_e " + world.toString());

            if (world == null) {
                return "overworld";
            }

            // Get dimension key using obfuscated method (1.16.5: func_234923_W_ = getDimensionKey)
            Object dimensionKey = world.getClass().getMethod("func_234923_W_").invoke(world);
            debugLog("Got dimension key using func_234923_W_ " + dimensionKey.toString());

            // Get location from dimension key (1.16.5: func_240901_a_ = getLocation)
            Object location = dimensionKey.getClass().getMethod("func_240901_a_").invoke(dimensionKey);
            debugLog("Got location using func_240901_a_ " + location.toString());

            String currentDimensionId = location.toString();
            debugLog("Dimension ID: " + currentDimensionId);

            return Dimensions.getCurrentDimension(currentDimensionId);
        } catch (Exception e) {
            debugLog("Error in obfuscated pre-1.16.5 method: " + e.getClass().getName() + " - " + e.getMessage());
            return "overworld";
        }
    }

    private void debugLog(String message) {
        EuphoriaLogger.debugLog("[ForgeModLoaderSpecifics] " + message);
    }
}
