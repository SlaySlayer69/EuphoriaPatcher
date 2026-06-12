package com.euphoriapatches.euphoria_patcher.integration.uniforms;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import org.joml.*;

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

        Method m = UniformHelper.methodCache.get(methodName + "_" + supplierTypeName);
        if (m != null) {
            try {
                m.invoke(uniforms, irisFreq, name, supplier);
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

    // --- JOML Vector & Object Supplier Overloads ---

    public void uniform2f(UniformHelper.Frequency freq, String name, Supplier<Vector2f> shadowedVectorSupplier) {
        IrisVectorBridge.ReusableVector2f bridge = new IrisVectorBridge.ReusableVector2f();
        Supplier<Object> runtimeSupplier = () -> {
            Vector2f internalMath = shadowedVectorSupplier.get();
            if (internalMath != null) {
                bridge.update(internalMath.x(), internalMath.y());
            }
            return bridge.getActualIrisVector();
        };
        invoke("uniform2f", "Supplier", freq, name, runtimeSupplier);
    }

    public void uniform2i(UniformHelper.Frequency freq, String name, Supplier<Vector2i> shadowedVectorSupplier) {
        IrisVectorBridge.ReusableVector2i bridge = new IrisVectorBridge.ReusableVector2i();
        Supplier<Object> runtimeSupplier = () -> {
            Vector2i internalMath = shadowedVectorSupplier.get();
            if (internalMath != null) {
                bridge.update(internalMath.x(), internalMath.y());
            }
            return bridge.getActualIrisVector();
        };
        invoke("uniform2i", "Supplier", freq, name, runtimeSupplier);
    }

    public void uniform3f(UniformHelper.Frequency freq, String name, Supplier<Vector3f> shadowedVectorSupplier) {
        IrisVectorBridge.ReusableVector3f bridge = new IrisVectorBridge.ReusableVector3f();
        Supplier<Object> runtimeSupplier = () -> {
            Vector3f internalMath = shadowedVectorSupplier.get();
            if (internalMath != null) {
                bridge.update(internalMath.x(), internalMath.y(), internalMath.z());
            }
            return bridge.getActualIrisVector();
        };
        invoke("uniform3f", "Supplier", freq, name, runtimeSupplier);
    }

    public void uniform3i(UniformHelper.Frequency freq, String name, Supplier<Vector3i> shadowedVectorSupplier) {
        IrisVectorBridge.ReusableVector3i bridge = new IrisVectorBridge.ReusableVector3i();
        Supplier<Object> runtimeSupplier = () -> {
            Vector3i internalMath = shadowedVectorSupplier.get();
            if (internalMath != null) {
                bridge.update(internalMath.x(), internalMath.y(), internalMath.z());
            }
            return bridge.getActualIrisVector();
        };
        invoke("uniform3i", "Supplier", freq, name, runtimeSupplier);
    }

    public void uniform3d(UniformHelper.Frequency freq, String name, Supplier<Vector3d> shadowedVectorSupplier) {
        IrisVectorBridge.ReusableVector3d bridge = new IrisVectorBridge.ReusableVector3d();
        Supplier<Object> runtimeSupplier = () -> {
            Vector3d internalMath = shadowedVectorSupplier.get();
            if (internalMath != null) {
                bridge.update(internalMath.x(), internalMath.y(), internalMath.z());
            }
            return bridge.getActualIrisVector();
        };
        invoke("uniform3d", "Supplier", freq, name, runtimeSupplier);
    }

    public void uniform4f(UniformHelper.Frequency freq, String name, Supplier<Vector4f> shadowedVectorSupplier) {
        IrisVectorBridge.ReusableVector4f bridge = new IrisVectorBridge.ReusableVector4f();
        Supplier<Object> runtimeSupplier = () -> {
            Vector4f internalMath = shadowedVectorSupplier.get();
            if (internalMath != null) {
                bridge.update(internalMath.x(), internalMath.y(), internalMath.z(), internalMath.w());
            }
            return bridge.getActualIrisVector();
        };
        invoke("uniform4f", "Supplier", freq, name, runtimeSupplier);
    }

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[Registrar] " + message);
    }
}
