package com.euphoriapatches.euphoria_patcher.forge1_7_10;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.PatchInfo;
import com.euphoriapatches.euphoria_patcher.util.ModLoaderSpecifics;
import cpw.mods.fml.common.Mod;

@Mod(modid= ClientEuphoriaPatcher.MODID, name = ClientEuphoriaPatcher.NAME, version = PatchInfo.PATCH_VERSION)
public class ClientEuphoriaPatcher {
    public static final String MODID = "euphoria_patcher";
    public static final String NAME = "Euphoria Patcher";

    public ClientEuphoriaPatcher() {
        Forge1710ModLoaderSpecifics forge1710Specifics = new Forge1710ModLoaderSpecifics();
        ModLoaderSpecifics.setInstance(forge1710Specifics);

        if (ModLoaderSpecifics.serverCheckStatic()) return;
        new EuphoriaPatcher();
    }
}
