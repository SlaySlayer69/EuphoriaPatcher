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
    private static Boolean useObfuscatedMappings = null; // null = not yet determined

    public ForgeModLoaderSpecifics() {
        this.shaderpacksPath = FMLPaths.GAMEDIR.get().resolve("shaderpacks");
        this.configDirectory = FMLPaths.CONFIGDIR.get();
    }

    @Override
    public Path getShaderpacksPath() {
        return shaderpacksPath;
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
        // Use cached result if available
        if (useObfuscatedMappings != null) {
            if (useObfuscatedMappings) {
                return getCurrentDimensionObfuscated();
            } else {
                return getCurrentDimensionModern();
            }
        }

        // First time - try obfuscated first
        try {
            String result = getCurrentDimensionObfuscated();
            useObfuscatedMappings = true;
            debugLog("Using obfuscated mappings");
            return result;
        } catch (Throwable t) {
            debugLog("Obfuscated method failed, trying modern: " + t.getMessage());
            try {
                String result = getCurrentDimensionModern();
                useObfuscatedMappings = false;
                debugLog("Using modern mappings");
                return result;
            } catch (Throwable t2) {
                debugLog("Modern method also failed: " + t2.getMessage());
                return "overworld";
            }
        }
    }

    private String getCurrentDimensionObfuscated() {
        debugLog("Getting current dimension (obfuscated mappings)");

        try {
            // Get Minecraft instance using obfuscated method
            Class<?> minecraftClass = Minecraft.class;
            Object minecraft = minecraftClass.getMethod("m_91087_").invoke(null);
            debugLog("Got Minecraft instance using m_91087_");

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
            throw new RuntimeException(e);
        }
    }

    private String getCurrentDimensionModern() {
        debugLog("Getting current dimension (modern mappings)");

        try {
            // Get Minecraft instance using modern method
            Class<?> minecraftClass = Minecraft.class;
            Object minecraft = minecraftClass.getMethod("getInstance").invoke(null);
            debugLog("Got Minecraft instance using getInstance");

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
            throw new RuntimeException(e);
        }
    }

    private void debugLog(String message) {
        EuphoriaLogger.debugLog("[ForgeModLoaderSpecifics] " + message);
    }
}
