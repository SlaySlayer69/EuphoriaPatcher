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
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
            System.err.println("[EuphoriaPatcher] The Euphoria Patcher Mod should not be loaded on a server! Disabling...");
            return true;
        }
        return false;
    }

    @Override
    public String getCurrentDimension() {
        debugLog("Getting current dimension");
        MinecraftClient client = MinecraftClient.getInstance();

        if (client == null || client.world == null) {
            debugLog("Client or world is null, defaulting to 'overworld'");
            return "overworld"; // Default if world isn't loaded
        }

        // Get current dimension ID
        Identifier dimensionId = client.world.getRegistryKey().getValue();
        String currentDimensionId = dimensionId.toString();
        debugLog("Current dimension ID: " + currentDimensionId);

        return Dimensions.getCurrentDimension(currentDimensionId);
    }

    private void debugLog(String message) {
        EuphoriaLogger.debugLog("[FabricModLoaderSpecifics] " + message);
    }
}
