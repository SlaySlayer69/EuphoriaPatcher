package com.euphoriapatches.euphoria_patcher.integration.uniforms;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class IrisVectorBridge {

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[IrisVectorBridge] " + message);
    }

    private static Class<?> resolveClass(String modernB64, String legacyB64) throws Exception {
        String modernTarget = new String(Base64.getDecoder().decode(modernB64), StandardCharsets.UTF_8);
        String legacyTarget = new String(Base64.getDecoder().decode(legacyB64), StandardCharsets.UTF_8);
        try {
            return Class.forName(modernTarget);
        } catch (ClassNotFoundException e) {
            return Class.forName(legacyTarget);
        }
    }

    // --- Vector2f ---
    public static class ReusableVector2f {
        private static Constructor<?> ctor;
        private static MethodHandle setterX, setterY;
        private static boolean ready = false;
        private final Object actualVector;

        static {
            try {
                // Base64 obfuscated strings to completely hide them from the Shadow plugin
                // "org.joml.Vector2f" and "net.coderbot.iris.vendor.joml.Vector2f"
                Class<?> cls = resolveClass("b3JnLmpvbWwuVmVjdG9yMmY=", "bmV0LmNvZGVyYm90LmlyaXMudmVuZG9yZWQuam9tbC5WZWN0b3IyZg==");
                ctor = cls.getConstructor();
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                setterX = lookup.unreflectSetter(cls.getField("x"));
                setterY = lookup.unreflectSetter(cls.getField("y"));
                ready = true;
            } catch (Exception e) { debugLog("Failed Vector2f bind: " + e.getMessage()); }
        }

        public ReusableVector2f() {
            try { this.actualVector = ctor.newInstance(); } catch (Exception e) { throw new RuntimeException(e); }
        }
        public void update(float x, float y) {
            if (!ready) return;
            try { setterX.invoke(this.actualVector, x); setterY.invoke(this.actualVector, y); } catch (Throwable ignored) {}
        }
        public Object getActualIrisVector() { return this.actualVector; }
    }

    // --- Vector2i ---
    public static class ReusableVector2i {
        private static Constructor<?> ctor;
        private static MethodHandle setterX, setterY;
        private static boolean ready = false;
        private final Object actualVector;

        static {
            try {
                // Base64 obfuscated strings to completely hide them from the Shadow plugin
                // "org.joml.Vector2i" and "net.coderbot.iris.vendor.joml.Vector2i"
                Class<?> cls = resolveClass("b3JnLmpvbWwuVmVjdG9yMmk=", "bmV0LmNvZGVyYm90LmlyaXMudmVuZG9yZWQuam9tbC5WZWN0b3IyaQ==");
                ctor = cls.getConstructor();
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                setterX = lookup.unreflectSetter(cls.getField("x"));
                setterY = lookup.unreflectSetter(cls.getField("y"));
                ready = true;
            } catch (Exception e) { debugLog("Failed Vector2i bind: " + e.getMessage()); }
        }

        public ReusableVector2i() {
            try { this.actualVector = ctor.newInstance(); } catch (Exception e) { throw new RuntimeException(e); }
        }
        public void update(int x, int y) {
            if (!ready) return;
            try { setterX.invoke(this.actualVector, x); setterY.invoke(this.actualVector, y); } catch (Throwable ignored) {}
        }
        public Object getActualIrisVector() { return this.actualVector; }
    }

    // --- Vector3f ---
    public static class ReusableVector3f {
        private static Constructor<?> ctor;
        private static MethodHandle setterX, setterY, setterZ;
        private static boolean ready = false;
        private final Object actualVector;

        static {
            try {
                // Base64 obfuscated strings to completely hide them from the Shadow plugin
                // "org.joml.Vector3f" and "net.coderbot.iris.vendor.joml.Vector3f"
                Class<?> cls = resolveClass("b3JnLmpvbWwuVmVjdG9yM2Y=", "bmV0LmNvZGVyYm90LmlyaXMudmVuZG9yZWQuam9tbC5WZWN0b3IzZg==");
                ctor = cls.getConstructor();
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                setterX = lookup.unreflectSetter(cls.getField("x"));
                setterY = lookup.unreflectSetter(cls.getField("y"));
                setterZ = lookup.unreflectSetter(cls.getField("z"));
                ready = true;
            } catch (Exception e) { debugLog("Failed Vector3f bind: " + e.getMessage()); }
        }

        public ReusableVector3f() {
            try { this.actualVector = ctor.newInstance(); } catch (Exception e) { throw new RuntimeException(e); }
        }
        public void update(float x, float y, float z) {
            if (!ready) return;
            try { setterX.invoke(this.actualVector, x); setterY.invoke(this.actualVector, y); setterZ.invoke(this.actualVector, z); } catch (Throwable ignored) {}
        }
        public Object getActualIrisVector() { return this.actualVector; }
    }

    // --- Vector3i ---
    public static class ReusableVector3i {
        private static Constructor<?> ctor;
        private static MethodHandle setterX, setterY, setterZ;
        private static boolean ready = false;
        private final Object actualVector;

        static {
            try {
                // Base64 obfuscated strings to completely hide them from the Shadow plugin
                // "org.joml.Vector3i" and "net.coderbot.iris.vendor.joml.Vector3i"
                Class<?> cls = resolveClass("b3JnLmpvbWwuVmVjdG9yM2k=", "bmV0LmNvZGVyYm90LmlyaXMudmVuZG9yZWQuam9tbC5WZWN0b3IzaQ==");
                ctor = cls.getConstructor();
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                setterX = lookup.unreflectSetter(cls.getField("x"));
                setterY = lookup.unreflectSetter(cls.getField("y"));
                setterZ = lookup.unreflectSetter(cls.getField("z"));
                ready = true;
            } catch (Exception e) { debugLog("Failed Vector3i bind: " + e.getMessage()); }
        }

        public ReusableVector3i() {
            try { this.actualVector = ctor.newInstance(); } catch (Exception e) { throw new RuntimeException(e); }
        }
        public void update(int x, int y, int z) {
            if (!ready) return;
            try { setterX.invoke(this.actualVector, x); setterY.invoke(this.actualVector, y); setterZ.invoke(this.actualVector, z); } catch (Throwable ignored) {}
        }
        public Object getActualIrisVector() { return this.actualVector; }
    }

    // --- Vector3d ---
    public static class ReusableVector3d {
        private static Constructor<?> ctor;
        private static MethodHandle setterX, setterY, setterZ;
        private static boolean ready = false;
        private final Object actualVector;

        static {
            try {
                // Base64 obfuscated strings to completely hide them from the Shadow plugin
                // "org.joml.Vector3d" and "net.coderbot.iris.vendor.joml.Vector3d"
                Class<?> cls = resolveClass("b3JnLmpvbWwuVmVjdG9yM2Q=", "bmV0LmNvZGVyYm90LmlyaXMudmVuZG9yZWQuam9tbC5WZWN0b3IzZA==");
                ctor = cls.getConstructor();
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                setterX = lookup.unreflectSetter(cls.getField("x"));
                setterY = lookup.unreflectSetter(cls.getField("y"));
                setterZ = lookup.unreflectSetter(cls.getField("z"));
                ready = true;
            } catch (Exception e) { debugLog("Failed Vector3d bind: " + e.getMessage()); }
        }

        public ReusableVector3d() {
            try { this.actualVector = ctor.newInstance(); } catch (Exception e) { throw new RuntimeException(e); }
        }
        public void update(double x, double y, double doubleZ) {
            if (!ready) return;
            try { setterX.invoke(this.actualVector, x); setterY.invoke(this.actualVector, y); setterZ.invoke(this.actualVector, doubleZ); } catch (Throwable ignored) {}
        }
        public Object getActualIrisVector() { return this.actualVector; }
    }

    // --- Vector4f ---
    public static class ReusableVector4f {
        private static Constructor<?> ctor;
        private static MethodHandle setterX, setterY, setterZ, setterW;
        private static boolean ready = false;
        private final Object actualVector;

        static {
            try {
                // Base64 obfuscated strings to completely hide them from the Shadow plugin
                // "org.joml.Vector4f" and "net.coderbot.iris.vendor.joml.Vector4f"
                Class<?> cls = resolveClass("b3JnLmpvbWwuVmVjdG9yNGY=", "bmV0LmNvZGVyYm90LmlyaXMudmVuZG9yZWQuam9tbC5WZWN0b3I0Zg==");
                ctor = cls.getConstructor();
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                setterX = lookup.unreflectSetter(cls.getField("x"));
                setterY = lookup.unreflectSetter(cls.getField("y"));
                setterZ = lookup.unreflectSetter(cls.getField("z"));
                setterW = lookup.unreflectSetter(cls.getField("w"));
                ready = true;
            } catch (Exception e) { debugLog("Failed Vector4f bind: " + e.getMessage()); }
        }

        public ReusableVector4f() {
            try { this.actualVector = ctor.newInstance(); } catch (Exception e) { throw new RuntimeException(e); }
        }
        public void update(float x, float y, float z, float w) {
            if (!ready) return;
            try { setterX.invoke(this.actualVector, x); setterY.invoke(this.actualVector, y); setterZ.invoke(this.actualVector, z); setterW.invoke(this.actualVector, w); } catch (Throwable ignored) {}
        }
        public Object getActualIrisVector() { return this.actualVector; }
    }
}
