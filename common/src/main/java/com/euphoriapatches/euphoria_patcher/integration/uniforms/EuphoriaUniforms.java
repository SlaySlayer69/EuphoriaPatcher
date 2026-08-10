package com.euphoriapatches.euphoria_patcher.integration.uniforms;

import com.euphoriapatches.euphoria_patcher.integration.seasons.SeasonsProvider;
import com.euphoriapatches.euphoria_patcher.util.mod.ModLoaderSpecifics;

import java.time.LocalDateTime;

// Central registry of Euphoria Patches uniforms, dispatched to Iris or OptiFine
// backends through the shared UniformDeclarer interface.
public final class EuphoriaUniforms {

    private EuphoriaUniforms() {}

    public static void declareAll(UniformDeclarer declarer) {
        declarer.uniform1b("euphoriaPatchesIsDayAdvancing", ModLoaderSpecifics::isTimeAdvancingStatic);

        declarer.uniform1i("euphoriaPatchesCurrentDayMillis", () -> (int) (System.currentTimeMillis() % 86400000));

        declarer.uniform1i("euphoriaPatchesCurrentDayMillisLocal", EuphoriaUniforms::msSinceMidnightLocal);

        declarer.uniform1i("euphoriaPatchesSeason", SeasonsProvider::getSeasonOrdinal);

        declarer.uniform1i("euphoriaPatchesSubSeason", SeasonsProvider::getSubSeasonOrdinal);

        declarer.uniform1i("euphoriaPatchesSeasonCycleTicks", SeasonsProvider::getSeasonCycleTicks);

        declarer.uniform1i("euphoriaPatchesSeasonDuration", SeasonsProvider::getSeasonDuration);
    }

    private static int msSinceMidnightLocal() {
        LocalDateTime now = LocalDateTime.now();
        return (now.getHour() * 3600000) + (now.getMinute() * 60000) + (now.getSecond() * 1000) + (now.getNano() / 1000000);
    }
}
