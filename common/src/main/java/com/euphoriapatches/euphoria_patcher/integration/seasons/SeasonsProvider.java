package com.euphoriapatches.euphoria_patcher.integration.seasons;

import com.euphoriapatches.euphoria_patcher.util.mod.ModChecker;

public final class SeasonsProvider {

    private enum Backend { NONE, SERENE_SEASONS, FABRIC_SEASONS, ECLIPTIC_SEASONS }

    private static boolean initialized = false;
    private static Backend backend = Backend.NONE;

    private SeasonsProvider() {}

    private static void init() {
        if (initialized) return;
        initialized = true;

        if (ModChecker.isModPresent(ModChecker.ModNames.SERENE_SEASONS)) {
            backend = Backend.SERENE_SEASONS;
        } else if (ModChecker.isModPresent(ModChecker.ModNames.FABRIC_SEASONS)) {
            backend = Backend.FABRIC_SEASONS;
        } else if (ModChecker.isModPresent(ModChecker.ModNames.ECLIPTIC_SEASONS)) {
            backend = Backend.ECLIPTIC_SEASONS;
        }
    }

    public static int getSeasonOrdinal() {
        init();
        if (backend == Backend.SERENE_SEASONS) return SereneSeasonsHelper.getSeasonOrdinal();
        if (backend == Backend.FABRIC_SEASONS) return FabricSeasonsHelper.getSeasonOrdinal();
        if (backend == Backend.ECLIPTIC_SEASONS) return EclipticSeasonsHelper.getSeasonOrdinal();
        return 0;
    }

    // Fabric Seasons has no subseason concept.
    public static int getSubSeasonOrdinal() {
        init();
        if (backend == Backend.SERENE_SEASONS) return SereneSeasonsHelper.getSubSeasonOrdinal();
        if (backend == Backend.ECLIPTIC_SEASONS) return EclipticSeasonsHelper.getSubSeasonOrdinal();
        return 0;
    }

    public static int getSeasonCycleTicks() {
        init();
        if (backend == Backend.SERENE_SEASONS) return SereneSeasonsHelper.getSeasonCycleTicks();
        if (backend == Backend.FABRIC_SEASONS) return FabricSeasonsHelper.getSeasonCycleTicks();
        if (backend == Backend.ECLIPTIC_SEASONS) return EclipticSeasonsHelper.getSeasonCycleTicks();
        return 0;
    }

    public static int getSeasonDuration() {
        init();
        if (backend == Backend.SERENE_SEASONS) return SereneSeasonsHelper.getSeasonDuration();
        if (backend == Backend.FABRIC_SEASONS) return FabricSeasonsHelper.getSeasonDuration();
        if (backend == Backend.ECLIPTIC_SEASONS) return EclipticSeasonsHelper.getSeasonDuration();
        return 0;
    }

    public static int getTotalSeasonDuration() {
        init();
        if (backend == Backend.SERENE_SEASONS) return SereneSeasonsHelper.getTotalSeasonDuration();
        if (backend == Backend.FABRIC_SEASONS) return FabricSeasonsHelper.getTotalSeasonDuration();
        if (backend == Backend.ECLIPTIC_SEASONS) return EclipticSeasonsHelper.getTotalSeasonDuration();
        return 0;
    }
}
