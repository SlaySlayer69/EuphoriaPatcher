package com.euphoriapatches.euphoria_patcher.forge;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class GameRuleChecker {

    private static GameRuleChecker instance;
    public static GameRuleChecker getInstance() {
        if (instance == null) instance = new GameRuleChecker();
        return instance;
    }

    private enum Strategy { UNKNOWN, CLIENT, FAILED }

    private Strategy strategy = Strategy.UNKNOWN;

    // Cached reflection
    private Object cachedRuleKey = null;
    private Method cachedGetBoolean = null;
    private Method cachedGetGameRules = null;
    private Field  cachedLevelField = null;
    private Method cachedGetInstance = null;
    private Class<?> cachedMinecraftClass = null;

    public boolean isTimeAdvancing() {
        if (strategy == Strategy.UNKNOWN) discover();
        try {
            return query();
        } catch (Throwable t) {
            debugLog("Query failed mid-session: " + t.getMessage());
            strategy = Strategy.UNKNOWN;
            resetCache();
            return true;
        }
    }

    private boolean query() throws Throwable {
        if (strategy == Strategy.CLIENT) return client();
        return true;
    }

    private void discover() {
        debugLog("Discovering gamerule strategy...");
        for (Strategy s : new Strategy[]{ Strategy.CLIENT }) {
            strategy = s;
            try {
                query();
                debugLog("Strategy found: " + s);
                return;
            } catch (Throwable t) {
                debugLog(s + " failed: " + t.getMessage());
                resetCache();
            }
        }
        debugLog("All strategies failed");
        strategy = Strategy.FAILED;
    }

    private boolean client() throws Throwable {
        if (cachedMinecraftClass == null) {
            cachedMinecraftClass = Class.forName("net.minecraft.client.Minecraft");
            cachedGetInstance    = cachedMinecraftClass.getMethod("m_91087_");
            cachedLevelField     = cachedMinecraftClass.getField("f_91073_");
        }

        Object minecraft = cachedGetInstance.invoke(null);
        if (minecraft == null) return true;

        Object level = cachedLevelField.get(minecraft);
        if (level == null) return true;

        if (cachedGetGameRules == null)
            cachedGetGameRules = level.getClass().getMethod("m_46469_");

        Object gameRules = cachedGetGameRules.invoke(level);

        if (cachedRuleKey == null) {
            Class<?> gameRulesClass = Class.forName("net.minecraft.world.level.GameRules");
            cachedRuleKey = ReflectionUtils.getFieldValue(gameRulesClass, "f_46140_");
        }

        if (cachedGetBoolean == null)
            cachedGetBoolean = gameRules.getClass().getMethod("m_46207_", cachedRuleKey.getClass());

        return (boolean) cachedGetBoolean.invoke(gameRules, cachedRuleKey);
    }

    private void resetCache() {
        cachedRuleKey        = null;
        cachedGetBoolean     = null;
        cachedGetGameRules   = null;
        cachedLevelField     = null;
        cachedGetInstance    = null;
        cachedMinecraftClass = null;
    }

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[GameRuleChecker] " + message);
    }
}