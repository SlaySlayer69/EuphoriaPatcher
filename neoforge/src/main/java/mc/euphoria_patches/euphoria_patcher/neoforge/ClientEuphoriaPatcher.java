package mc.euphoria_patches.euphoria_patcher.neoforge;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import mc.euphoria_patches.euphoria_patcher.util.ModLoaderSpecifics;
import net.neoforged.fml.common.Mod;

@Mod("euphoria_patcher")
public class ClientEuphoriaPatcher {

    public ClientEuphoriaPatcher() {
        NeoforgeModLoaderSpecifics neoforgeSpecifics = new NeoforgeModLoaderSpecifics();
        ModLoaderSpecifics.setInstance(neoforgeSpecifics);

        if(ModLoaderSpecifics.serverCheckStatic()) return;
        new EuphoriaPatcher();
    }
}
