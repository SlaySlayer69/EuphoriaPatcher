package com.euphoriapatches.euphoria_patcher.integration.iris;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.google.common.collect.ImmutableSet;

public class IrisColortexAdder {
    private static final int additionalRenderTargets = 16;

    public static void appendAdditionalRenderTargets(ImmutableSet.Builder<Integer> instance) {
        ImmutableSet<Integer> original = instance.build();

        int startIndex = original.stream()
            .mapToInt(Integer::intValue)
            .max()
            .orElse(-1) + 1;

        for (int i = startIndex; i < startIndex + additionalRenderTargets; i++) {
            instance.add(i);
        }

        debugLog("Extended colortex amount from " + original.size() + " to " + (original.size() + additionalRenderTargets));
    }

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[IrisColortexAdder] " + message);
    }
}
