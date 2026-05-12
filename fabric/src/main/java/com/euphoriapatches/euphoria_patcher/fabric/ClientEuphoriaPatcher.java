package com.euphoriapatches.euphoria_patcher.fabric;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.util.mod.ModLoaderSpecifics;
import net.fabricmc.api.ModInitializer;

public class ClientEuphoriaPatcher implements ModInitializer {
    @Override
    public void onInitialize() {
        // Initialize Fabric-specific ModLoaderSpecifics
        FabricModLoaderSpecifics fabricSpecifics = new FabricModLoaderSpecifics();
        ModLoaderSpecifics.setInstance(fabricSpecifics);

        // Check if we're on server and abort if so
        if (ModLoaderSpecifics.serverCheckStatic()) return;

        // Initialize the common EuphoriaPatcher
        new EuphoriaPatcher();
    }
}
