package mc.euphoria_patches.euphoria_patcher.forge;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import mc.euphoria_patches.euphoria_patcher.util.ModLoaderSpecifics;
import net.minecraftforge.fml.common.Mod;

@Mod("euphoria_patcher")
public class ClientEuphoriaPatcher {

    public ClientEuphoriaPatcher() {
        ForgeModLoaderSpecifics forgeSpecifics = new ForgeModLoaderSpecifics();
        ModLoaderSpecifics.setInstance(forgeSpecifics);

        if (ModLoaderSpecifics.serverCheckStatic()) return;
        new EuphoriaPatcher();
    }
}
