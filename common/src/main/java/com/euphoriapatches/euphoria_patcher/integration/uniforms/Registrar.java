package com.euphoriapatches.euphoria_patcher.integration.uniforms;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;

import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public class Registrar {
    private final Object uniforms;

    public Registrar(Object uniforms) {
        this.uniforms = uniforms;
    }

    private void invoke(String methodName, String supplierTypeName, UniformHelper.Frequency freq, String name, Object supplier) {
        Object irisFreq = UniformHelper.irisFrequencies[freq.ordinal()];
        if (irisFreq == null) return;

        Method targetMethod = UniformHelper.methodCache.get(methodName + "_" + supplierTypeName);
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

    public void uniform1i(UniformHelper.Frequency freq, String name, IntSupplier value) {
        invoke("uniform1i", "IntSupplier", freq, name, value);
    }

    public void uniform1b(UniformHelper.Frequency freq, String name, BooleanSupplier value) {
        invoke("uniform1b", "BooleanSupplier", freq, name, value);
    }

    // Note: Iris provides standard JDK Int/Double overloads for uniform1f because
    // standard Java 8 lacks a primitive FloatSupplier. Use these to pass float data without allocation overhead.
    public void uniform1f(UniformHelper.Frequency freq, String name, DoubleSupplier value) {
        invoke("uniform1f", "DoubleSupplier", freq, name, value);
    }

    public void uniform1f(UniformHelper.Frequency freq, String name, IntSupplier value) {
        invoke("uniform1f", "IntSupplier", freq, name, value);
    }

    // --- Vector Overloads ---
    // Sets vectors via primitives directly into Iris's runtime JOML class,
    // avoiding compile-time dependencies (signature resolved via UniformHelper.vectorClasses).

    public void uniform2f(UniformHelper.Frequency freq, String name, DoubleSupplier x, DoubleSupplier y) {
        IrisVectorBridge.ReusableVector2f bridge = new IrisVectorBridge.ReusableVector2f(UniformHelper.vectorClasses.get("uniform2f"));
        Supplier<Object> runtimeSupplier = () -> {
            bridge.update((float) x.getAsDouble(), (float) y.getAsDouble());
            return bridge.getActualIrisVector();
        };
        invoke("uniform2f", "Supplier", freq, name, runtimeSupplier);
    }

    public void uniform2i(UniformHelper.Frequency freq, String name, IntSupplier x, IntSupplier y) {
        IrisVectorBridge.ReusableVector2i bridge = new IrisVectorBridge.ReusableVector2i(UniformHelper.vectorClasses.get("uniform2i"));
        Supplier<Object> runtimeSupplier = () -> {
            bridge.update(x.getAsInt(), y.getAsInt());
            return bridge.getActualIrisVector();
        };
        invoke("uniform2i", "Supplier", freq, name, runtimeSupplier);
    }

    public void uniform3f(UniformHelper.Frequency freq, String name, DoubleSupplier x, DoubleSupplier y, DoubleSupplier z) {
        IrisVectorBridge.ReusableVector3f bridge = new IrisVectorBridge.ReusableVector3f(UniformHelper.vectorClasses.get("uniform3f"));
        Supplier<Object> runtimeSupplier = () -> {
            bridge.update((float) x.getAsDouble(), (float) y.getAsDouble(), (float) z.getAsDouble());
            return bridge.getActualIrisVector();
        };
        invoke("uniform3f", "Supplier", freq, name, runtimeSupplier);
    }

    public void uniform3i(UniformHelper.Frequency freq, String name, IntSupplier x, IntSupplier y, IntSupplier z) {
        IrisVectorBridge.ReusableVector3i bridge = new IrisVectorBridge.ReusableVector3i(UniformHelper.vectorClasses.get("uniform3i"));
        Supplier<Object> runtimeSupplier = () -> {
            bridge.update(x.getAsInt(), y.getAsInt(), z.getAsInt());
            return bridge.getActualIrisVector();
        };
        invoke("uniform3i", "Supplier", freq, name, runtimeSupplier);
    }

    public void uniform3d(UniformHelper.Frequency freq, String name, DoubleSupplier x, DoubleSupplier y, DoubleSupplier z) {
        IrisVectorBridge.ReusableVector3d bridge = new IrisVectorBridge.ReusableVector3d(UniformHelper.vectorClasses.get("uniform3d"));
        Supplier<Object> runtimeSupplier = () -> {
            bridge.update(x.getAsDouble(), y.getAsDouble(), z.getAsDouble());
            return bridge.getActualIrisVector();
        };
        invoke("uniform3d", "Supplier", freq, name, runtimeSupplier);
    }

    public void uniform4f(UniformHelper.Frequency freq, String name, DoubleSupplier x, DoubleSupplier y, DoubleSupplier z, DoubleSupplier w) {
        IrisVectorBridge.ReusableVector4f bridge = new IrisVectorBridge.ReusableVector4f(UniformHelper.vectorClasses.get("uniform4f"));
        Supplier<Object> runtimeSupplier = () -> {
            bridge.update((float) x.getAsDouble(), (float) y.getAsDouble(), (float) z.getAsDouble(), (float) w.getAsDouble());
            return bridge.getActualIrisVector();
        };
        invoke("uniform4f", "Supplier", freq, name, runtimeSupplier);
    }

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[Registrar] " + message);
    }
}
