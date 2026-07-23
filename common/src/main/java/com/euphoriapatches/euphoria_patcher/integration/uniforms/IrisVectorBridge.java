package com.euphoriapatches.euphoria_patcher.integration.uniforms;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;

public class IrisVectorBridge {

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[IrisVectorBridge] " + message);
    }

    // Avoids version-dependent JOML class mismatches by reading the target vector class
    // directly from Iris's live UniformHelper signature.

    // --- Vector2f ---
    public static class ReusableVector2f {
        private static Class<?> resolvedForClass;
        private static Constructor<?> constructor;
        private static MethodHandle setterX, setterY;
        private static boolean ready = false;
        private final Object actualVector;

        private static synchronized void ensureResolved(Class<?> vectorClass) {
            if (resolvedForClass == vectorClass) return;
            resolvedForClass = vectorClass;
            ready = false;
            if (vectorClass == null) return;
            try {
                constructor = vectorClass.getConstructor();
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                setterX = lookup.unreflectSetter(vectorClass.getField("x"));
                setterY = lookup.unreflectSetter(vectorClass.getField("y"));
                ready = true;
                debugLog("Cached Vector2f binding for class: " + vectorClass.getName());
            } catch (Exception e) { debugLog("Failed Vector2f bind: " + e.getMessage()); }
        }

        public ReusableVector2f(Class<?> vectorClass) {
            ensureResolved(vectorClass);
            try { this.actualVector = ready ? constructor.newInstance() : null; } catch (Exception e) { throw new RuntimeException(e); }
        }
        public void update(float x, float y) {
            if (!ready) return;
            try { setterX.invoke(this.actualVector, x); setterY.invoke(this.actualVector, y); } catch (Throwable ignored) {}
        }
        public Object getActualIrisVector() { return this.actualVector; }
    }

    // --- Vector2i ---
    public static class ReusableVector2i {
        private static Class<?> resolvedForClass;
        private static Constructor<?> constructor;
        private static MethodHandle setterX, setterY;
        private static boolean ready = false;
        private final Object actualVector;

        private static synchronized void ensureResolved(Class<?> vectorClass) {
            if (resolvedForClass == vectorClass) return;
            resolvedForClass = vectorClass;
            ready = false;
            if (vectorClass == null) return;
            try {
                constructor = vectorClass.getConstructor();
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                setterX = lookup.unreflectSetter(vectorClass.getField("x"));
                setterY = lookup.unreflectSetter(vectorClass.getField("y"));
                ready = true;
                debugLog("Cached Vector2i binding for class: " + vectorClass.getName());
            } catch (Exception e) { debugLog("Failed Vector2i bind: " + e.getMessage()); }
        }

        public ReusableVector2i(Class<?> vectorClass) {
            ensureResolved(vectorClass);
            try { this.actualVector = ready ? constructor.newInstance() : null; } catch (Exception e) { throw new RuntimeException(e); }
        }
        public void update(int x, int y) {
            if (!ready) return;
            try { setterX.invoke(this.actualVector, x); setterY.invoke(this.actualVector, y); } catch (Throwable ignored) {}
        }
        public Object getActualIrisVector() { return this.actualVector; }
    }

    // --- Vector3f ---
    public static class ReusableVector3f {
        private static Class<?> resolvedForClass;
        private static Constructor<?> constructor;
        private static MethodHandle setterX, setterY, setterZ;
        private static boolean ready = false;
        private final Object actualVector;

        private static synchronized void ensureResolved(Class<?> vectorClass) {
            if (resolvedForClass == vectorClass) return;
            resolvedForClass = vectorClass;
            ready = false;
            if (vectorClass == null) return;
            try {
                constructor = vectorClass.getConstructor();
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                setterX = lookup.unreflectSetter(vectorClass.getField("x"));
                setterY = lookup.unreflectSetter(vectorClass.getField("y"));
                setterZ = lookup.unreflectSetter(vectorClass.getField("z"));
                ready = true;
                debugLog("Cached Vector3f binding for class: " + vectorClass.getName());
            } catch (Exception e) { debugLog("Failed Vector3f bind: " + e.getMessage()); }
        }

        public ReusableVector3f(Class<?> vectorClass) {
            ensureResolved(vectorClass);
            try { this.actualVector = ready ? constructor.newInstance() : null; } catch (Exception e) { throw new RuntimeException(e); }
        }
        public void update(float x, float y, float z) {
            if (!ready) return;
            try { setterX.invoke(this.actualVector, x); setterY.invoke(this.actualVector, y); setterZ.invoke(this.actualVector, z); } catch (Throwable ignored) {}
        }
        public Object getActualIrisVector() { return this.actualVector; }
    }

    // --- Vector3i ---
    public static class ReusableVector3i {
        private static Class<?> resolvedForClass;
        private static Constructor<?> constructor;
        private static MethodHandle setterX, setterY, setterZ;
        private static boolean ready = false;
        private final Object actualVector;

        private static synchronized void ensureResolved(Class<?> vectorClass) {
            if (resolvedForClass == vectorClass) return;
            resolvedForClass = vectorClass;
            ready = false;
            if (vectorClass == null) return;
            try {
                constructor = vectorClass.getConstructor();
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                setterX = lookup.unreflectSetter(vectorClass.getField("x"));
                setterY = lookup.unreflectSetter(vectorClass.getField("y"));
                setterZ = lookup.unreflectSetter(vectorClass.getField("z"));
                ready = true;
                debugLog("Cached Vector3i binding for class: " + vectorClass.getName());
            } catch (Exception e) { debugLog("Failed Vector3i bind: " + e.getMessage()); }
        }

        public ReusableVector3i(Class<?> vectorClass) {
            ensureResolved(vectorClass);
            try { this.actualVector = ready ? constructor.newInstance() : null; } catch (Exception e) { throw new RuntimeException(e); }
        }
        public void update(int x, int y, int z) {
            if (!ready) return;
            try { setterX.invoke(this.actualVector, x); setterY.invoke(this.actualVector, y); setterZ.invoke(this.actualVector, z); } catch (Throwable ignored) {}
        }
        public Object getActualIrisVector() { return this.actualVector; }
    }

    // --- Vector3d ---
    public static class ReusableVector3d {
        private static Class<?> resolvedForClass;
        private static Constructor<?> constructor;
        private static MethodHandle setterX, setterY, setterZ;
        private static boolean ready = false;
        private final Object actualVector;

        private static synchronized void ensureResolved(Class<?> vectorClass) {
            if (resolvedForClass == vectorClass) return;
            resolvedForClass = vectorClass;
            ready = false;
            if (vectorClass == null) return;
            try {
                constructor = vectorClass.getConstructor();
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                setterX = lookup.unreflectSetter(vectorClass.getField("x"));
                setterY = lookup.unreflectSetter(vectorClass.getField("y"));
                setterZ = lookup.unreflectSetter(vectorClass.getField("z"));
                ready = true;
                debugLog("Cached Vector3d binding for class: " + vectorClass.getName());
            } catch (Exception e) { debugLog("Failed Vector3d bind: " + e.getMessage()); }
        }

        public ReusableVector3d(Class<?> vectorClass) {
            ensureResolved(vectorClass);
            try { this.actualVector = ready ? constructor.newInstance() : null; } catch (Exception e) { throw new RuntimeException(e); }
        }
        public void update(double x, double y, double z) {
            if (!ready) return;
            try { setterX.invoke(this.actualVector, x); setterY.invoke(this.actualVector, y); setterZ.invoke(this.actualVector, z); } catch (Throwable ignored) {}
        }
        public Object getActualIrisVector() { return this.actualVector; }
    }

    // --- Vector4f ---
    public static class ReusableVector4f {
        private static Class<?> resolvedForClass;
        private static Constructor<?> constructor;
        private static MethodHandle setterX, setterY, setterZ, setterW;
        private static boolean ready = false;
        private final Object actualVector;

        private static synchronized void ensureResolved(Class<?> vectorClass) {
            if (resolvedForClass == vectorClass) return;
            resolvedForClass = vectorClass;
            ready = false;
            if (vectorClass == null) return;
            try {
                constructor = vectorClass.getConstructor();
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                setterX = lookup.unreflectSetter(vectorClass.getField("x"));
                setterY = lookup.unreflectSetter(vectorClass.getField("y"));
                setterZ = lookup.unreflectSetter(vectorClass.getField("z"));
                setterW = lookup.unreflectSetter(vectorClass.getField("w"));
                ready = true;
                debugLog("Cached Vector4f binding for class: " + vectorClass.getName());
            } catch (Exception e) { debugLog("Failed Vector4f bind: " + e.getMessage()); }
        }

        public ReusableVector4f(Class<?> vectorClass) {
            ensureResolved(vectorClass);
            try { this.actualVector = ready ? constructor.newInstance() : null; } catch (Exception e) { throw new RuntimeException(e); }
        }
        public void update(float x, float y, float z, float w) {
            if (!ready) return;
            try { setterX.invoke(this.actualVector, x); setterY.invoke(this.actualVector, y); setterZ.invoke(this.actualVector, z); setterW.invoke(this.actualVector, w); } catch (Throwable ignored) {}
        }
        public Object getActualIrisVector() { return this.actualVector; }
    }
}
