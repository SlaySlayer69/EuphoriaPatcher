package mc.euphoria_patches.euphoria_patcher.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Path;

public class ModLoaderSpecifics {
    public static Path shaderpacks = FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
    public static Path configDirectory = FabricLoader.getInstance().getConfigDir();

    public static boolean serverCheck() {
        if(FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER){
            System.err.println("[EuphoriaPatcher] The Euphoria Patcher Mod should not be loaded on a server! Disabling...");
            return true;
        }
        return false;
    }
}
