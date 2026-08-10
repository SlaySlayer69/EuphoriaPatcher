package com.euphoriapatches.euphoria_patcher.integration.seasons;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.mod.ModChecker;
import com.euphoriapatches.euphoria_patcher.util.mod.ModLoaderSpecifics;

import java.lang.reflect.Method;

// Reflection bridge into Serene Seasons' SeasonHelper/ISeasonState API
public final class SereneSeasonsHelper {

    private static boolean initialized = false;
    private static boolean available = false;

    private static Method GET_SEASON_STATE;
    private static Method GET_SEASON;
    private static Method GET_SUB_SEASON;
    private static Method GET_SEASON_CYCLE_TICKS;
    private static Method GET_SEASON_DURATION;

    // The 4 uniform getters below are each polled independently, once per uniform, every
    // frame - without this they'd each trigger their own reflective getSeasonState() call.
    // A short-lived cache coalesces those into one real call per frame.
    private static final long STATE_CACHE_NANOS = 50_000_000L; // 50ms, comfortably more than one frame
    private static Object cachedState;
    private static long cachedAtNanos = Long.MIN_VALUE;

    private SereneSeasonsHelper() {}

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[SereneSeasonsHelper] " + message);
    }

    private static void init() {
        if (initialized) return;
        initialized = true;

        if (!ModChecker.isModPresent(ModChecker.ModNames.SERENE_SEASONS)) {
            return;
        }

        try {
            Class<?> seasonHelperClass = Class.forName("sereneseasons.api.season.SeasonHelper");
            Class<?> levelClass = Class.forName("net.minecraft.world.level.Level");
            GET_SEASON_STATE = seasonHelperClass.getMethod("getSeasonState", levelClass);

            Class<?> seasonStateClass = GET_SEASON_STATE.getReturnType();
            GET_SEASON = seasonStateClass.getMethod("getSeason");
            GET_SUB_SEASON = seasonStateClass.getMethod("getSubSeason");
            GET_SEASON_CYCLE_TICKS = seasonStateClass.getMethod("getSeasonCycleTicks");
            GET_SEASON_DURATION = seasonStateClass.getMethod("getSeasonDuration");

            available = true;
            debugLog("Bound to Serene Seasons' SeasonHelper API");
        } catch (Exception e) {
            available = false;
            debugLog("Failed to bind to Serene Seasons: " + e.getMessage());
        }
    }

    private static Object getSeasonState() {
        long now = System.nanoTime();
        if (cachedAtNanos != Long.MIN_VALUE && (now - cachedAtNanos) < STATE_CACHE_NANOS) {
            return cachedState;
        }

        init();
        Object state = null;
        if (available) {
            Object level = ModLoaderSpecifics.getLevelStatic();
            if (level != null) {
                try {
                    state = GET_SEASON_STATE.invoke(null, level);
                } catch (Exception e) {
                    debugLog("Failed to get season state: " + e.getMessage());
                }
            }
        }

        cachedState = state;
        cachedAtNanos = now;
        return state;
    }

    private static int ordinalOf(Method method, Object state) {
        try {
            return ((Enum<?>) method.invoke(state)).ordinal();
        } catch (Exception e) {
            debugLog("Failed to invoke " + method.getName() + ": " + e.getMessage());
            return 0;
        }
    }

    private static int intOf(Method method, Object state) {
        try {
            return (int) method.invoke(state);
        } catch (Exception e) {
            debugLog("Failed to invoke " + method.getName() + ": " + e.getMessage());
            return 0;
        }
    }

    public static int getSeasonOrdinal() {
        Object state = getSeasonState();
        return state == null ? 0 : ordinalOf(GET_SEASON, state);
    }

    public static int getSubSeasonOrdinal() {
        Object state = getSeasonState();
        return state == null ? 0 : ordinalOf(GET_SUB_SEASON, state);
    }

    public static int getSeasonCycleTicks() {
        Object state = getSeasonState();
        return state == null ? 0 : intOf(GET_SEASON_CYCLE_TICKS, state);
    }

    public static int getSeasonDuration() {
        Object state = getSeasonState();
        return state == null ? 0 : intOf(GET_SEASON_DURATION, state);
    }

    // Serene Seasons always uses 4 equal-length seasons (3 equal subseasons each), so the
    // full cycle is just the current season's duration times 4.
    public static int getTotalSeasonDuration() {
        return getSeasonDuration() * 4;
    }
}
