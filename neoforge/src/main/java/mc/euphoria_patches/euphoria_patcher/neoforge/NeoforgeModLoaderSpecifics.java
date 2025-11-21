package mc.euphoria_patches.euphoria_patcher.neoforge;

import mc.euphoria_patches.euphoria_patcher.util.Dimensions;
import mc.euphoria_patches.euphoria_patcher.util.EuphoriaLogger;
import mc.euphoria_patches.euphoria_patcher.util.ModLoaderSpecifics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;

/**
 * Neoforge-specific implementation of ModLoaderSpecifics.
 */
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
                System.err.println("The Euphoria Patcher Mod should not be loaded on a server! Disabling...");
                return true;
            }
        } catch (NoSuchMethodException e) {
            // Fallback for older NeoForge versions
            if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
                System.err.println("The Euphoria Patcher Mod should not be loaded on a server! Disabling...");
                return true;
            }
        } catch (Throwable t) {
            // Any other error, assume not a server
        }
        return false;
    }

    @Override
    public String getCurrentDimension() {
        debugLog("Getting current dimension");
        Minecraft minecraft = Minecraft.getInstance();
        
        if (minecraft == null || minecraft.level == null) {
            debugLog("Minecraft or level is null, defaulting to 'overworld'");
            return "overworld"; // Default if world isn't loaded
        }
        
        // Get current dimension ID
        ResourceLocation dimensionId = minecraft.level.dimension().location();
        String currentDimensionId = dimensionId.toString();
        debugLog("Current dimension ID: " + currentDimensionId);
        
        return Dimensions.getCurrentDimension(currentDimensionId);
    }

    private void debugLog(String message) {
        EuphoriaLogger.debugLog("[FabricModLoaderSpecifics] " + message);
    }
}
