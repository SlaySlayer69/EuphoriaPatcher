package mc.euphoria_patches.euphoria_patcher.forge1_7_10;

import mc.euphoria_patches.euphoria_patcher.util.Dimensions;
import mc.euphoria_patches.euphoria_patcher.util.EuphoriaLogger;
import mc.euphoria_patches.euphoria_patcher.util.ModLoaderSpecifics;
import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.Loader;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;

public class Forge1710ModLoaderSpecifics extends ModLoaderSpecifics {
    
    private final Path shaderpacksPath;
    private final Path configDirectory;

    public Forge1710ModLoaderSpecifics() {
        File gameDirectory = Loader.instance().getConfigDir().getParentFile();
        this.shaderpacksPath = new File(gameDirectory, "shaderpacks").toPath();
        this.configDirectory = Loader.instance().getConfigDir().toPath();
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
        return false;
    }

    @Override
    public String getCurrentDimension() {
        return "overworld";
    }

    private void debugLog(String message) {
        EuphoriaLogger.debugLog("[Forge1710ModLoaderSpecifics] " + message);
    }
}
