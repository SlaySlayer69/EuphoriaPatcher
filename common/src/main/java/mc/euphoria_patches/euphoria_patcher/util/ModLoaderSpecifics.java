package mc.euphoria_patches.euphoria_patcher.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.nio.file.Path;

public class ModLoaderSpecifics {
    public static Path shaderpacks = FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
    public static Path configDirectory = FabricLoader.getInstance().getConfigDir();
    public static boolean isDevMode = FabricLoader.getInstance().isDevelopmentEnvironment();

    public static boolean serverCheck() {
        if(FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER){
            System.err.println("[EuphoriaPatcher] The Euphoria Patcher Mod should not be loaded on a server! Disabling...");
            return true;
        }
        return false;
    }

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ModLoaderSpecifics] " + message);
    }

    public static String getCurrentDimension() {
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
}
