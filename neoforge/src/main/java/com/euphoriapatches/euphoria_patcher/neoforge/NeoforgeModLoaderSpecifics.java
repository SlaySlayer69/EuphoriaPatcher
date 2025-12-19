package com.euphoriapatches.euphoria_patcher.neoforge;

import com.euphoriapatches.euphoria_patcher.util.Dimensions;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.ModLoaderSpecifics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;

public class NeoforgeModLoaderSpecifics extends ModLoaderSpecifics {

    private final Path shaderpacksPath;
    private final Path configDirectory;

    public NeoforgeModLoaderSpecifics() {
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
            // Try to use getDist() if available (NeoForge 1.21.10+)
            java.lang.reflect.Method getDistMethod = FMLEnvironment.class.getMethod("getDist");
            Object dist = getDistMethod.invoke(null);
            if (dist == Dist.DEDICATED_SERVER) {
                System.err.println("[EuphoriaPatcher] Server Detected! The Euphoria Patcher Mod disables itself gracefully on a server. Disabling...");
                return true;
            }
        } catch (NoSuchMethodException e) {
            // Fallback for older NeoForge versions
            if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
                System.err.println("[EuphoriaPatcher] Server Detected! The Euphoria Patcher Mod disables itself gracefully on a server. Disabling...");
                return true;
            }
        } catch (Throwable t) {
            // Any other error, assume not a server
        }
        return false;
    }

    @Override
    public String getCurrentDimension() {
        try {
            debugLog("Getting current dimension");
            Minecraft minecraft = Minecraft.getInstance();

            if (minecraft.level == null) {
                debugLog("Minecraft or level is null, defaulting to 'overworld'");
                return "overworld";
            }

            String currentDimensionId;
            String dimensionString = minecraft.level.dimension().toString();
            debugLog("Dimension toString(): " + dimensionString);
            // Format: "ResourceKey[minecraft:dimension / minecraft:overworld]"
            if (dimensionString.contains("/")) {
                currentDimensionId = dimensionString.substring(dimensionString.indexOf("/") + 1)
                        .replace("]", "").trim();
            } else {
                currentDimensionId = "minecraft:overworld";
            }

            debugLog("Current dimension ID: " + currentDimensionId);
            return Dimensions.getCurrentDimension(currentDimensionId);
        } catch (Throwable t) {
            debugLog("Unexpected error getting current dimension: " + t.getMessage());
            return "overworld";
        }
    }

    private void debugLog(String message) {
        EuphoriaLogger.debugLog("[NeoforgeModLoaderSpecifics] " + message);
    }
}
