package com.euphoriapatches.euphoria_patcher.forge_legacy;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.ModLoaderSpecifics;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.nio.file.Path;

public class ForgeLegacyModLoaderSpecifics extends ModLoaderSpecifics {

    private final Path shaderpacksPath;
    private final Path configDirectory;

    public ForgeLegacyModLoaderSpecifics() {
        this.shaderpacksPath = Minecraft.getMinecraft().gameDir.toPath().resolve("shaderpacks");
        this.configDirectory = Minecraft.getMinecraft().gameDir.toPath().resolve("config");
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
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server != null && server.isDedicatedServer()) {
            System.err.println("The Euphoria Patcher Mod should not be loaded on a server! Disabling...");
            return true;
        }
        return false;
    }

    @Override
    public String getCurrentDimension() {
        // debugLog("Getting current dimension");
        // Minecraft minecraft = Minecraft.getInstance();

        // if (minecraft == null || minecraft.level == null) {
        //     debugLog("Minecraft or level is null, defaulting to 'overworld'");
        //     return "overworld"; // Default if world isn't loaded
        // }

        // // Get current dimension ID
        // ResourceLocation dimensionId = minecraft.level.dimension().location();
        // String currentDimensionId = dimensionId.toString();
        // debugLog("Current dimension ID: " + currentDimensionId);

        // return Dimensions.getCurrentDimension(currentDimensionId);
        return "overworld";
    }

    private void debugLog(String message) {
        EuphoriaLogger.debugLog("[ForgeLegacyModLoaderSpecifics] " + message);
    }
}
