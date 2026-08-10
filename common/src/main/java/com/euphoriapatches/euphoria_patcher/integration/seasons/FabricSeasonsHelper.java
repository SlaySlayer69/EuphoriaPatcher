package com.euphoriapatches.euphoria_patcher.integration.seasons;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.mod.ModChecker;
import com.euphoriapatches.euphoria_patcher.util.mod.ModLoaderSpecifics;

import java.lang.reflect.Method;

// Reflection bridge into Fabric Seasons (io.github.lucaargolo.seasons). Unlike Serene
// Seasons, it has no subseason concept and its 4 season lengths are independently
// configurable rather than always equal - getSeasonDuration() below approximates using the
// CURRENT season's own length, which is exact when all 4 are set equal (the common case)
// and only approximate otherwise, since the shared uniform contract assumes one uniform
// length across the whole cycle.
public final class FabricSeasonsHelper {

    private static boolean initialized = false;
    private static boolean available = false;

    private static Object config;
    private static Method GET_CURRENT_SEASON;
    private static Method GET_TIME_TO_NEXT_SEASON;
    private static Method GET_SPRING_LENGTH;
    private static Method GET_SUMMER_LENGTH;
    private static Method GET_FALL_LENGTH;
    private static Method GET_WINTER_LENGTH;

    private static final long STATE_CACHE_NANOS = 50_000_000L; // 50ms, comfortably more than one frame
    private static long cachedAtNanos = Long.MIN_VALUE;
    private static int cachedSeasonOrdinal;
    private static int cachedSeasonCycleTicks;
    private static int cachedSeasonDuration;

    private FabricSeasonsHelper() {}

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[FabricSeasonsHelper] " + message);
    }

    private static void init() {
        if (initialized) return;
        initialized = true;

        if (!ModChecker.isModPresent(ModChecker.ModNames.FABRIC_SEASONS)) {
            return;
        }

        try {
            Class<?> fabricSeasonsClass = Class.forName("io.github.lucaargolo.seasons.FabricSeasons");
            Class<?> worldClass = resolveWorldClass();
            GET_CURRENT_SEASON = fabricSeasonsClass.getMethod("getCurrentSeason", worldClass);
            GET_TIME_TO_NEXT_SEASON = fabricSeasonsClass.getMethod("getTimeToNextSeason", worldClass);

            config = fabricSeasonsClass.getField("CONFIG").get(null);
            Class<?> configClass = config.getClass();
            GET_SPRING_LENGTH = configClass.getMethod("getSpringLength");
            GET_SUMMER_LENGTH = configClass.getMethod("getSummerLength");
            GET_FALL_LENGTH = configClass.getMethod("getFallLength");
            GET_WINTER_LENGTH = configClass.getMethod("getWinterLength");

            available = true;
            debugLog("Bound to Fabric Seasons' API");
        } catch (Exception e) {
            available = false;
            debugLog("Failed to bind to Fabric Seasons: " + e.getMessage());
        }
    }

    // Depending on the environment, World may still be under its Yarn name or only under
    // its raw intermediary name (class_1937) - try Yarn first since that's the common case.
    private static Class<?> resolveWorldClass() throws ClassNotFoundException {
        try {
            return Class.forName("net.minecraft.world.World");
        } catch (ClassNotFoundException e) {
            return Class.forName("net.minecraft.class_1937");
        }
    }

    // Fabric Seasons only exposes "current season" + "ticks until next season" (plus each
    // season's configured length) rather than a single cycle-position value, so this
    // reconstructs the same spring-first cycle position Serene Seasons reports directly:
    // ticks elapsed since the most recent Spring boundary.
    private static void refresh() {
        long now = System.nanoTime();
        if (cachedAtNanos != Long.MIN_VALUE && (now - cachedAtNanos) < STATE_CACHE_NANOS) {
            return;
        }
        cachedAtNanos = now;

        init();
        cachedSeasonOrdinal = 0;
        cachedSeasonCycleTicks = 0;
        cachedSeasonDuration = 0;

        if (!available) return;

        Object level = ModLoaderSpecifics.getLevelStatic();
        if (level == null) return;

        try {
            // Spring-first order, matching Season's declared enum ordinals (SPRING, SUMMER, FALL, WINTER).
            long[] lengths = {
                    invokeAsLong(GET_SPRING_LENGTH, config),
                    invokeAsLong(GET_SUMMER_LENGTH, config),
                    invokeAsLong(GET_FALL_LENGTH, config),
                    invokeAsLong(GET_WINTER_LENGTH, config)
            };

            Object season = GET_CURRENT_SEASON.invoke(null, level);
            int ordinal = ((Enum<?>) season).ordinal();
            long currentLength = lengths[ordinal];

            long timeToNext = invokeAsLong(GET_TIME_TO_NEXT_SEASON, null, level);
            long elapsedInCurrent;
            if (timeToNext == Long.MAX_VALUE || currentLength <= 0) {
                // Season locked/disabled/invalid dimension - no real progression, park in the
                // middle of the current season's window so it renders as fully committed to it.
                elapsedInCurrent = currentLength > 0 ? currentLength / 2 : 0;
            } else {
                elapsedInCurrent = currentLength - timeToNext;
            }

            long ticksBeforeCurrentSeason = 0;
            for (int i = 0; i < ordinal; i++) {
                ticksBeforeCurrentSeason += lengths[i];
            }

            cachedSeasonOrdinal = ordinal;
            cachedSeasonCycleTicks = (int) (ticksBeforeCurrentSeason + elapsedInCurrent);
            cachedSeasonDuration = (int) currentLength;
        } catch (Exception e) {
            debugLog("Failed to read season state: " + e.getMessage());
        }
    }

    // Reflected getters here are documented as long but some Fabric Seasons versions may
    // return int (auto-widened at their own call sites) - read either without risking a
    // ClassCastException from a blind (long) cast on the boxed result.
    private static long invokeAsLong(Method method, Object receiver, Object... args) throws Exception {
        Object result = method.invoke(receiver, args);
        if (result instanceof Number) {
            return ((Number) result).longValue();
        }
        throw new ClassCastException("Unexpected return type from " + method.getName() + ": "
                + (result == null ? "null" : result.getClass().getName()));
    }

    public static int getSeasonOrdinal() {
        refresh();
        return cachedSeasonOrdinal;
    }

    public static int getSeasonCycleTicks() {
        refresh();
        return cachedSeasonCycleTicks;
    }

    public static int getSeasonDuration() {
        refresh();
        return cachedSeasonDuration;
    }
}
