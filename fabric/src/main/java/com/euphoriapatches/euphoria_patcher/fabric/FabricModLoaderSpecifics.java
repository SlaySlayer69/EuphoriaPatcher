package com.euphoriapatches.euphoria_patcher.fabric;

import com.euphoriapatches.euphoria_patcher.util.Dimensions;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.ModLoaderSpecifics;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.nio.file.Path;

public class FabricModLoaderSpecifics extends ModLoaderSpecifics {

    private final Path shaderpacksPath;
    private final Path configDirectory;
    private static Boolean useYarnMappings = null; // null = not yet determined

    public FabricModLoaderSpecifics() {
        this.shaderpacksPath = FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
        this.configDirectory = FabricLoader.getInstance().getConfigDir();
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
            if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
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
        if (useYarnMappings != null) {
            if (useYarnMappings) {
                return getCurrentDimensionYarn();
            } else {
                return getCurrentDimensionReflection();
            }
        }

        // First time - try Yarn first
        try {
            String result = getCurrentDimensionYarn();
            useYarnMappings = true;
            debugLog("Using Yarn mappings");
            return result;
        } catch (Throwable t) {
            debugLog("Yarn method failed, trying reflection: " + t.getMessage());
            try {
                String result = getCurrentDimensionReflection();
                useYarnMappings = false;
                debugLog("Using reflection");
                return result;
            } catch (Throwable t2) {
                debugLog("Reflection method also failed: " + t2.getMessage());
                return "overworld";
            }
        }
    }

    private String getCurrentDimensionYarn() {
        debugLog("Getting current dimension (Yarn)");
        try {
            MinecraftClient client = MinecraftClient.getInstance();

            if (client == null || client.world == null) {
                debugLog("Client or world is null, defaulting to 'overworld'");
                return "overworld";
            }

            Identifier dimensionId = client.world.getRegistryKey().getValue();
            String currentDimensionId = dimensionId.toString();
            debugLog("Current dimension ID: " + currentDimensionId);

            return Dimensions.getCurrentDimension(currentDimensionId);
        } catch (Exception e) {
            debugLog("Error in Yarn method: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private String getCurrentDimensionReflection() {
        debugLog("Getting current dimension (Reflection for net.minecraft.client.Minecraft)");

        try {
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
            Object mcInstance = mcClass.getMethod("getInstance").invoke(null);

            if (mcInstance == null) {
                debugLog("Minecraft instance is null, defaulting to 'overworld'");
                return "overworld";
            }

            Object level = mcClass.getField("level").get(mcInstance);
            if (level == null) {
                debugLog("Level is null, defaulting to 'overworld'");
                return "overworld";
            }

            Class<?> levelClass = level.getClass();
            Object dimension = levelClass.getMethod("dimension").invoke(level);
            String dimensionString = dimension.toString();
            debugLog("Dimension toString(): " + dimensionString);

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
            debugLog("Error in reflection method: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void debugLog(String message) {
        EuphoriaLogger.debugLog("[FabricModLoaderSpecifics] " + message);
    }
}
