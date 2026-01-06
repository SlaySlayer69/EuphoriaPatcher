package com.euphoriapatches.euphoria_patcher.forge1_7_10;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.ModLoaderSpecifics;
import cpw.mods.fml.common.Loader;

import java.io.File;
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
    public String getInstanceName() {
        return ModLoaderSpecifics.FORGE_1_7_10;
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

    @Override
    public boolean isCurrentDimensionInMappings() {
        return true;
    }

    @Override
    public boolean setClipboard(String str) {
        return false;
    }

    private void debugLog(String message) {
        EuphoriaLogger.debugLog("[Forge1710ModLoaderSpecifics] " + message);
    }
}
