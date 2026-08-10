package com.euphoriapatches.euphoria_patcher.integration.uniforms;

import com.euphoriapatches.euphoria_patcher.integration.Target;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

// Iris-side implementation of UniformDeclarer
@SuppressWarnings("unused")
public class IrisUniformBridge implements UniformDeclarer {

    public enum Frequency {
        ONCE, PER_TICK, PER_FRAME, CUSTOM
    }

    private static boolean initialized = false;
    private static final Object[] irisFrequencies = new Object[4];
    private static final Map<String, Method> methodCache = new HashMap<>();
    // Target vector class expected per uniform method (e.g., Vector3f), extracted directly
    // from Iris's live method signature to handle JOML package changes across Iris versions.
    private static final Map<String, Class<?>> vectorClasses = new HashMap<>();

    private final Object uniforms;

    public IrisUniformBridge(Object uniforms) {
        this.uniforms = uniforms;
    }

    public static void addEuphoriaUniforms(Object uniforms, Target target) {
        if (!initialized) {
            init(uniforms, target);
        }

        EuphoriaUniforms.declareAll(new IrisUniformBridge(uniforms));
        debugLog("Declared Euphoria uniforms for " + target);
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
                    // debugLog("Cached uniform method '" + signature + "' -> " + method);

                    // For vector uniforms (Supplier<Vector2f/3f/...>), read the actual vector
                    // class Iris expects straight off its own method signature instead of
                    // guessing it from Target/package naming.
                    if (paramType == Supplier.class) {
                        Type genericParameterType = method.getGenericParameterTypes()[2];
                        if (genericParameterType instanceof ParameterizedType) {
                            Type[] actualTypeArguments = ((ParameterizedType) genericParameterType).getActualTypeArguments();
                            if (actualTypeArguments.length == 1 && actualTypeArguments[0] instanceof Class) {
                                Class<?> vectorClass = (Class<?>) actualTypeArguments[0];
                                vectorClasses.put(name, vectorClass);
                                // debugLog("Resolved vector class for '" + name + "': " + vectorClass.getName());
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

    private void invoke(String methodName, String supplierTypeName, Frequency freq, String name, Object supplier) {
        Object irisFreq = irisFrequencies[freq.ordinal()];
        if (irisFreq == null) return;

        Method targetMethod = methodCache.get(methodName + "_" + supplierTypeName);
        if (targetMethod != null) {
            try {
                targetMethod.invoke(uniforms, irisFreq, name, supplier);
            } catch (Exception e) {
                debugLog("Failed to invoke " + methodName + " for uniform '" + name + "': " + e.getMessage());
            }
        } else {
            debugLog("Method not found in cache: " + methodName + " with parameter " + supplierTypeName);
        }
    }

    // --- Primitive Overloads ---

    public void uniform1i(Frequency freq, String name, IntSupplier value) {
        invoke("uniform1i", "IntSupplier", freq, name, value);
    }

    public void uniform1b(Frequency freq, String name, BooleanSupplier value) {
        invoke("uniform1b", "BooleanSupplier", freq, name, value);
    }

    // Note: Iris provides standard JDK Int/Double overloads for uniform1f because
    // standard Java 8 lacks a primitive FloatSupplier. Use these to pass float data without allocation overhead.
    public void uniform1f(Frequency freq, String name, DoubleSupplier value) {
        invoke("uniform1f", "DoubleSupplier", freq, name, value);
    }

    public void uniform1f(Frequency freq, String name, IntSupplier value) {
        invoke("uniform1f", "IntSupplier", freq, name, value);
    }

    // --- Vector Overloads ---
    // Sets vectors via primitives directly into Iris's runtime JOML class,
    // avoiding compile-time dependencies (signature resolved via UniformHelper.vectorClasses).

    public void uniform2f(Frequency freq, String name, DoubleSupplier x, DoubleSupplier y) {
        IrisVectorBridge.ReusableVector2f bridge = new IrisVectorBridge.ReusableVector2f(vectorClasses.get("uniform2f"));
        Supplier<Object> runtimeSupplier = () -> {
            bridge.update((float) x.getAsDouble(), (float) y.getAsDouble());
            return bridge.getActualIrisVector();
        };
        invoke("uniform2f", "Supplier", freq, name, runtimeSupplier);
    }

    public void uniform2i(Frequency freq, String name, IntSupplier x, IntSupplier y) {
        IrisVectorBridge.ReusableVector2i bridge = new IrisVectorBridge.ReusableVector2i(vectorClasses.get("uniform2i"));
        Supplier<Object> runtimeSupplier = () -> {
            bridge.update(x.getAsInt(), y.getAsInt());
            return bridge.getActualIrisVector();
        };
        invoke("uniform2i", "Supplier", freq, name, runtimeSupplier);
    }

    public void uniform3f(Frequency freq, String name, DoubleSupplier x, DoubleSupplier y, DoubleSupplier z) {
        IrisVectorBridge.ReusableVector3f bridge = new IrisVectorBridge.ReusableVector3f(vectorClasses.get("uniform3f"));
        Supplier<Object> runtimeSupplier = () -> {
            bridge.update((float) x.getAsDouble(), (float) y.getAsDouble(), (float) z.getAsDouble());
            return bridge.getActualIrisVector();
        };
        invoke("uniform3f", "Supplier", freq, name, runtimeSupplier);
    }

    public void uniform3i(Frequency freq, String name, IntSupplier x, IntSupplier y, IntSupplier z) {
        IrisVectorBridge.ReusableVector3i bridge = new IrisVectorBridge.ReusableVector3i(vectorClasses.get("uniform3i"));
        Supplier<Object> runtimeSupplier = () -> {
            bridge.update(x.getAsInt(), y.getAsInt(), z.getAsInt());
            return bridge.getActualIrisVector();
        };
        invoke("uniform3i", "Supplier", freq, name, runtimeSupplier);
    }

    public void uniform3d(Frequency freq, String name, DoubleSupplier x, DoubleSupplier y, DoubleSupplier z) {
        IrisVectorBridge.ReusableVector3d bridge = new IrisVectorBridge.ReusableVector3d(vectorClasses.get("uniform3d"));
        Supplier<Object> runtimeSupplier = () -> {
            bridge.update(x.getAsDouble(), y.getAsDouble(), z.getAsDouble());
            return bridge.getActualIrisVector();
        };
        invoke("uniform3d", "Supplier", freq, name, runtimeSupplier);
    }

    public void uniform4f(Frequency freq, String name, DoubleSupplier x, DoubleSupplier y, DoubleSupplier z, DoubleSupplier w) {
        IrisVectorBridge.ReusableVector4f bridge = new IrisVectorBridge.ReusableVector4f(vectorClasses.get("uniform4f"));
        Supplier<Object> runtimeSupplier = () -> {
            bridge.update((float) x.getAsDouble(), (float) y.getAsDouble(), (float) z.getAsDouble(), (float) w.getAsDouble());
            return bridge.getActualIrisVector();
        };
        invoke("uniform4f", "Supplier", freq, name, runtimeSupplier);
    }

    // --- UniformDeclarer implementation ---
    // EuphoriaUniforms.declareAll only needs PER_FRAME, so these just forward to the
    // frequency-taking overloads above.

    @Override
    public void uniform1b(String name, BooleanSupplier value) {
        uniform1b(Frequency.PER_FRAME, name, value);
    }

    @Override
    public void uniform1i(String name, IntSupplier value) {
        uniform1i(Frequency.PER_FRAME, name, value);
    }

    @Override
    public void uniform1f(String name, DoubleSupplier value) {
        uniform1f(Frequency.PER_FRAME, name, value);
    }

    @Override
    public void uniform2f(String name, DoubleSupplier x, DoubleSupplier y) {
        uniform2f(Frequency.PER_FRAME, name, x, y);
    }

    @Override
    public void uniform2i(String name, IntSupplier x, IntSupplier y) {
        uniform2i(Frequency.PER_FRAME, name, x, y);
    }

    @Override
    public void uniform3f(String name, DoubleSupplier x, DoubleSupplier y, DoubleSupplier z) {
        uniform3f(Frequency.PER_FRAME, name, x, y, z);
    }

    @Override
    public void uniform3i(String name, IntSupplier x, IntSupplier y, IntSupplier z) {
        uniform3i(Frequency.PER_FRAME, name, x, y, z);
    }

    @Override
    public void uniform3d(String name, DoubleSupplier x, DoubleSupplier y, DoubleSupplier z) {
        uniform3d(Frequency.PER_FRAME, name, x, y, z);
    }

    @Override
    public void uniform4f(String name, DoubleSupplier x, DoubleSupplier y, DoubleSupplier z, DoubleSupplier w) {
        uniform4f(Frequency.PER_FRAME, name, x, y, z, w);
    }

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[IrisUniformBridge] " + message);
    }
}
