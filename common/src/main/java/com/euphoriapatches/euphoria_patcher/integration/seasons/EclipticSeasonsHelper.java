package com.euphoriapatches.euphoria_patcher.integration.seasons;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.ReflectionUtils;
import com.euphoriapatches.euphoria_patcher.util.mod.ModChecker;
import com.euphoriapatches.euphoria_patcher.util.mod.ModLoaderSpecifics;

import java.lang.reflect.Method;

// Helper for Ecliptic Seasons mod integration, using reflection to avoid hard dependencies on the mod's API.
public final class EclipticSeasonsHelper {

    private static final int TICKS_PER_DAY = 24000;

    private static boolean initialized = false;
    private static boolean modPresent = false;
    private static boolean methodsBound = false;
    private static boolean available = false;

    private static Class<?> apiClass;
    private static Object api;
    private static Method IS_SEASON_ENABLED;
    private static Method GET_SEASON;
    private static Method GET_SUB_SEASON;
    private static Method GET_SOLAR_TERM;
    private static Method GET_DAY_IN_TERM;
    private static Method GET_LASTING_DAYS_OF_EACH_TERM;

    // Optional smoothing - null if unavailable.
    private static Method GET_DAY_LENGTH;
    private static Method GET_CLOCK_TIME;

    private static final long STATE_CACHE_NANOS = 50_000_000L; // 50ms, comfortably more than one frame
    private static long cachedAtNanos = Long.MIN_VALUE;
    private static int cachedSeasonOrdinal;
    private static int cachedSubSeasonOrdinal;
    private static int cachedSeasonCycleTicks;
    private static int cachedSeasonDuration;
    private static int cachedTotalSeasonDuration;

    private EclipticSeasonsHelper() {}

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[EclipticSeasonsHelper] " + message);
    }

    private static void init() {
        if (initialized) return;
        initialized = true;

        if (!ModChecker.isModPresent(ModChecker.ModNames.ECLIPTIC_SEASONS)) {
            return;
        }

        try {
            apiClass = Class.forName("com.teamtea.eclipticseasons.api.EclipticSeasonsApi");
            api = apiClass.getMethod("getInstance").invoke(null);
            modPresent = true;
        } catch (Exception e) {
            debugLog("Failed to bind to Ecliptic Seasons' API: " + e.getMessage());
        }
    }

    // All the API methods below take a Level parameter that can't be resolved until we have a
    // live level instance to match its type against
    private static boolean bindMethods(Object level) {
        if (methodsBound) return available;
        methodsBound = true;

        try {
            IS_SEASON_ENABLED = ReflectionUtils.findMethodForInstance(apiClass, "isSeasonEnabled", level);
            GET_SEASON = ReflectionUtils.findMethodForInstance(apiClass, "getSeason", level);
            GET_SUB_SEASON = ReflectionUtils.findMethodForInstance(apiClass, "getSubSeason", level);
            GET_SOLAR_TERM = ReflectionUtils.findMethodForInstance(apiClass, "getSolarTerm", level);
            GET_DAY_IN_TERM = ReflectionUtils.findMethodForInstance(apiClass, "getDayInTerm", level);
            GET_LASTING_DAYS_OF_EACH_TERM = ReflectionUtils.findMethodForInstance(apiClass, "getLastingDaysOfEachTerm", level);

            available = true;
            debugLog("Bound to Ecliptic Seasons' API");

            bindSmoothTimeSource(level);
        } catch (Exception e) {
            available = false;
            debugLog("Failed to bind to Ecliptic Seasons: " + e.getMessage());
        }
        return available;
    }

    private static void bindSmoothTimeSource(Object level) {
        try {
            Class<?> eclipticUtilClass = Class.forName("com.teamtea.eclipticseasons.api.util.EclipticUtil");
            GET_DAY_LENGTH = ReflectionUtils.findMethodForInstance(eclipticUtilClass, "getDayLengthInMinecraft", level);
        } catch (Exception e) {
            GET_DAY_LENGTH = null;
            debugLog("No variable day-length source, using fixed " + TICKS_PER_DAY + " ticks/day: " + e.getMessage());
        }

        try {
            GET_CLOCK_TIME = ReflectionUtils.tryMethods(level.getClass(),
                    "getDefaultClockTime", "getDayTime", "m_46468_", "method_8532");
        } catch (Exception e) {
            GET_CLOCK_TIME = null;
            debugLog("No clock-time source found, falling back to once-per-day updates: " + e.getMessage());
        }
    }

    private static void refresh() {
        long now = System.nanoTime();
        if (cachedAtNanos != Long.MIN_VALUE && (now - cachedAtNanos) < STATE_CACHE_NANOS) {
            return;
        }
        cachedAtNanos = now;

        init();
        cachedSeasonOrdinal = 0;
        cachedSubSeasonOrdinal = 0;
        cachedSeasonCycleTicks = 0;
        cachedSeasonDuration = 0;
        cachedTotalSeasonDuration = 0;

        if (!modPresent) return;

        Object level = ModLoaderSpecifics.getLevelStatic();
        if (level == null) return;

        if (!bindMethods(level)) return;

        try {
            boolean enabled = (boolean) IS_SEASON_ENABLED.invoke(api, level);
            if (!enabled) return;

            int lastingDays = (int) GET_LASTING_DAYS_OF_EACH_TERM.invoke(api, level);
            if (lastingDays <= 0) return;

            Object solarTerm = GET_SOLAR_TERM.invoke(api, level);
            int termOrdinal = ((Enum<?>) solarTerm).ordinal(); // 0-23 valid, 24 = NONE (disabled)
            if (termOrdinal >= 24) return;

            int dayInTerm = (int) GET_DAY_IN_TERM.invoke(api, level); // 0..lastingDays-1
            int dayOfYear = termOrdinal * lastingDays + dayInTerm; // spring-first, 0-based

            Object season = GET_SEASON.invoke(api, level);
            Object subSeason = GET_SUB_SEASON.invoke(api, level);

            int ticksPerDay = resolveTicksPerDay(level);
            int ticksIntoToday = resolveTicksIntoToday(level, ticksPerDay);

            cachedSeasonOrdinal = ((Enum<?>) season).ordinal();
            cachedSubSeasonOrdinal = ((Enum<?>) subSeason).ordinal();
            cachedSeasonCycleTicks = dayOfYear * ticksPerDay + ticksIntoToday;
            cachedSeasonDuration = lastingDays * 6 * ticksPerDay; // 1 season = 6 solar terms, always equal
            cachedTotalSeasonDuration = lastingDays * 24 * ticksPerDay; // 1 year = 24 solar terms
        } catch (Exception e) {
            debugLog("Failed to read season state: " + e.getMessage());
        }
    }

    private static int resolveTicksPerDay(Object level) {
        if (GET_DAY_LENGTH == null) return TICKS_PER_DAY;
        try {
            long dayLength = numberOf(GET_DAY_LENGTH.invoke(null, level));
            return dayLength > 0 ? (int) dayLength : TICKS_PER_DAY;
        } catch (Exception e) {
            return TICKS_PER_DAY;
        }
    }

    private static int resolveTicksIntoToday(Object level, int ticksPerDay) {
        if (GET_CLOCK_TIME == null) return 0;
        try {
            long clockTime = numberOf(GET_CLOCK_TIME.invoke(level));
            return (int) (((clockTime % ticksPerDay) + ticksPerDay) % ticksPerDay);
        } catch (Exception e) {
            return 0;
        }
    }

    private static long numberOf(Object result) {
        if (result instanceof Number) return ((Number) result).longValue();
        throw new ClassCastException("Expected a Number, got " + (result == null ? "null" : result.getClass().getName()));
    }

    public static int getSeasonOrdinal() {
        refresh();
        return cachedSeasonOrdinal;
    }

    public static int getSubSeasonOrdinal() {
        refresh();
        return cachedSubSeasonOrdinal;
    }

    public static int getSeasonCycleTicks() {
        refresh();
        return cachedSeasonCycleTicks;
    }

    public static int getSeasonDuration() {
        refresh();
        return cachedSeasonDuration;
    }

    public static int getTotalSeasonDuration() {
        refresh();
        return cachedTotalSeasonDuration;
    }
}
