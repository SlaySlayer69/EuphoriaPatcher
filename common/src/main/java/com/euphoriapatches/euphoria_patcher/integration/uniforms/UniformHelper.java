package com.euphoriapatches.euphoria_patcher.integration.uniforms;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.euphoriapatches.euphoria_patcher.integration.Target;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.mod.ModLoaderSpecifics;

public class UniformHelper {

    public enum Frequency {
        ONCE, PER_TICK, PER_FRAME, CUSTOM
    }

    private static boolean initialized = false;
    public static final Object[] irisFrequencies = new Object[4];
    public static final Map<String, Method> methodCache = new HashMap<>();
    public static final Map<String, Class<?>> vectorClasses = new HashMap<>();

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[UniformHelper] " + message);
    }

    private static void init(Object uniforms, Target target) {
        try {
            String packageName = (target == Target.IRIS_LEGACY) ? "net.coderbot.iris" : "net.irisshaders.iris";
            Class<?> enumClass = Class.forName(packageName + ".gl.uniform.UniformUpdateFrequency");

            // Cache Iris enum constants mapped to our internal Frequency enum
            Object[] constants = enumClass.getEnumConstants();
            for (Object constant : constants) {
                String name = ((Enum<?>) constant).name();
                try {
                    Frequency freq = Frequency.valueOf(name);
                    irisFrequencies[freq.ordinal()] = constant;
                } catch (IllegalArgumentException ignored) {}
            }

            // Reflectively cache all uniform registration methods
            for (Method method : uniforms.getClass().getMethods()) {
                String name = method.getName();
                if (name.startsWith("uniform") && method.getParameterCount() == 3) {
                    Class<?> paramType = method.getParameterTypes()[2];
                    String signature = name + "_" + paramType.getSimpleName();
                    methodCache.put(signature, method);
                    debugLog("Cached uniform method '" + signature + "' -> " + method);

                    // For vector uniforms (Supplier<Vector2f/3f/...>), read the actual vector class Iris expects
                    if (paramType == Supplier.class) {
                        Type genericParameterType = method.getGenericParameterTypes()[2];
                        if (genericParameterType instanceof ParameterizedType) {
                            Type[] actualTypeArguments = ((ParameterizedType) genericParameterType).getActualTypeArguments();
                            if (actualTypeArguments.length == 1 && actualTypeArguments[0] instanceof Class) {
                                Class<?> vectorClass = (Class<?>) actualTypeArguments[0];
                                vectorClasses.put(name, vectorClass);
                                debugLog("Resolved vector class for '" + name + "': " + vectorClass.getName());
                            }
                        }
                    }
                }
            }

            initialized = true;
            debugLog("Initialized full uniform reflection cache for " + target);
        } catch (Exception e) {
            debugLog("Failed to initialize uniform reflection cache: " + e.getMessage());
        }
    }

    public static void addEuphoriaUniforms(Object uniforms, Target target) {
        if (!initialized) {
            init(uniforms, target);
        }

        Registrar registrar = new Registrar(uniforms);

        registrar.uniform1b(Frequency.PER_FRAME, "euphoriaPatchesIsDayAdvancing", ModLoaderSpecifics::isTimeAdvancingStatic);
        debugLog("Registered 'euphoriaPatchesIsDayAdvancing' with PER_FRAME frequency. Uniform type: boolean");

        registrar.uniform1i(Frequency.PER_FRAME, "euphoriaPatchesCurrentDayMillis", () -> (int) (System.currentTimeMillis() % 86400000));
        debugLog("Registered 'euphoriaPatchesCurrentDayMillis' with PER_FRAME frequency. Uniform type: int");

        registrar.uniform1i(Frequency.PER_FRAME, "euphoriaPatchesCurrentDayMillisLocal", UniformHelper::msSinceMidnightLocal);
        debugLog("Registered 'euphoriaPatchesCurrentDayMillisLocal' with PER_FRAME frequency. Uniform type: int");

        registrar.uniform3f(Frequency.PER_FRAME, "euphoriaPatchesTime",
                () -> LocalDateTime.now().getHour(),
                () -> LocalDateTime.now().getMinute(),
                () -> LocalDateTime.now().getSecond());
        debugLog("Registered 'euphoriaPatchesTime' with PER_FRAME frequency. Uniform type: vec3");
    }

    private static int msSinceMidnightLocal() {
        LocalDateTime now = LocalDateTime.now();
        return (now.getHour() * 3600000) + (now.getMinute() * 60000) + (now.getSecond() * 1000) + (now.getNano() / 1000000);
    }
}
