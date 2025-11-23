package mc.euphoria_patches.euphoria_patcher.forge_legacy;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import mc.euphoria_patches.euphoria_patcher.util.ModLoaderSpecifics;
import net.minecraftforge.fml.common.Mod;

@Mod(modid= ClientEuphoriaPatcher.MODID, name = ClientEuphoriaPatcher.NAME)
public class ClientEuphoriaPatcher {
    public static final String MODID = "euphoria_patcher";
    public static final String NAME = "Euphoria Patcher";

    public ClientEuphoriaPatcher() {
        ForgeLegacyModLoaderSpecifics forgeLegacySpecifics = new ForgeLegacyModLoaderSpecifics();
        ModLoaderSpecifics.setInstance(forgeLegacySpecifics);

        if (ModLoaderSpecifics.serverCheckStatic()) return;
        new EuphoriaPatcher();
    }
}
