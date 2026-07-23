package com.euphoriapatches.euphoria_patcher.integration.uniforms;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

// OptiFine's ShaderUniforms only offers make1i/make2i/make4i/make1f/make3f/make4f - there's no
// vec2-float, ivec3, or double type. Those pad up into the next supported type with the extra
// component(s) fixed at zero: uniform2f -> make3f (z always 0), uniform3i -> make4i (w always 0),
// uniform3d -> make3f (float precision instead of double).
public final class OptifineUniformBridge implements UniformDeclarer {
    private static final String SHADERS_CLASS_NAME = "net.optifine.shaders.Shaders";

    private static boolean declared = false;
    private static boolean ready = false;

    private static Object shaderUniformsInstance;
    private static Method make1i, make2i, make4i, make1f, make3f, make4f;

    private static final Map<String, Runnable> updaters = new LinkedHashMap<>();

    private OptifineUniformBridge() {}

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[OptifineUniformBridge] " + message);
    }

    private static synchronized void ensureReady() {
        if (ready) return;
        try {
            Class<?> shadersClass = Class.forName(SHADERS_CLASS_NAME);
            Field shaderUniformsField = shadersClass.getDeclaredField("shaderUniforms");
            shaderUniformsField.setAccessible(true);
            shaderUniformsInstance = shaderUniformsField.get(null);

            Class<?> shaderUniformsClass = shaderUniformsInstance.getClass();
            make1i = shaderUniformsClass.getMethod("make1i", String.class);
            make2i = shaderUniformsClass.getMethod("make2i", String.class);
            make4i = shaderUniformsClass.getMethod("make4i", String.class);
            make1f = shaderUniformsClass.getMethod("make1f", String.class);
            make3f = shaderUniformsClass.getMethod("make3f", String.class);
            make4f = shaderUniformsClass.getMethod("make4f", String.class);
            ready = true;
            debugLog("Bound to OptiFine's ShaderUniforms registry");
        } catch (Exception e) {
            debugLog("Failed to bind to OptiFine's ShaderUniforms: " + e.getMessage());
        }
    }

    // Called every time OptiFine pushes its own built-in uniforms to the currently bound
    // program (see OptifineShadersUniformsMixin), so our values stay in sync the same way.
    public static void updateEuphoriaUniforms() {
        if (!declared) {
            declared = true;
            ensureReady();
            if (ready) {
                EuphoriaUniforms.declareAll(new OptifineUniformBridge());
                debugLog("Declared Euphoria uniforms for OPTIFINE");
            }
        }
        if (!ready) return;

        for (Runnable updater : updaters.values()) {
            updater.run();
        }
    }

    private static Object makeUniform(Method makeMethod, String name) {
        try {
            return makeMethod.invoke(shaderUniformsInstance, name);
        } catch (Exception e) {
            debugLog("Failed to create OptiFine uniform '" + name + "': " + e.getMessage());
            return null;
        }
    }

    private static MethodHandle setValueHandle(Object uniform, Class<?>... paramTypes) {
        try {
            Method setValue = uniform.getClass().getMethod("setValue", paramTypes);
            return MethodHandles.lookup().unreflect(setValue).bindTo(uniform);
        } catch (Exception e) {
            debugLog("Failed to bind setValue on " + uniform.getClass().getName() + ": " + e.getMessage());
            return null;
        }
    }

    private static void invokeSetValue(MethodHandle setValue, String name, Object... args) {
        try {
            setValue.invokeWithArguments(args);
        } catch (Throwable t) {
            debugLog("Failed to update OptiFine uniform '" + name + "': " + t.getMessage());
        }
    }

    @Override
    public void uniform1b(String name, BooleanSupplier value) {
        Object uniform = makeUniform(make1i, name);
        if (uniform == null) return;
        MethodHandle setValue = setValueHandle(uniform, int.class);
        if (setValue == null) return;
        updaters.put(name, () -> invokeSetValue(setValue, name, value.getAsBoolean() ? 1 : 0));
    }

    @Override
    public void uniform1i(String name, IntSupplier value) {
        Object uniform = makeUniform(make1i, name);
        if (uniform == null) return;
        MethodHandle setValue = setValueHandle(uniform, int.class);
        if (setValue == null) return;
        updaters.put(name, () -> invokeSetValue(setValue, name, value.getAsInt()));
    }

    @Override
    public void uniform1f(String name, DoubleSupplier value) {
        Object uniform = makeUniform(make1f, name);
        if (uniform == null) return;
        MethodHandle setValue = setValueHandle(uniform, float.class);
        if (setValue == null) return;
        updaters.put(name, () -> invokeSetValue(setValue, name, (float) value.getAsDouble()));
    }

    @Override
    public void uniform2f(String name, DoubleSupplier x, DoubleSupplier y) {
        // No vec2-float type on OptiFine - pad into a vec3 with z fixed at 0.
        Object uniform = makeUniform(make3f, name);
        if (uniform == null) return;
        MethodHandle setValue = setValueHandle(uniform, float.class, float.class, float.class);
        if (setValue == null) return;
        updaters.put(name, () -> invokeSetValue(setValue, name,
                (float) x.getAsDouble(), (float) y.getAsDouble(), 0.0f));
    }

    @Override
    public void uniform2i(String name, IntSupplier x, IntSupplier y) {
        Object uniform = makeUniform(make2i, name);
        if (uniform == null) return;
        MethodHandle setValue = setValueHandle(uniform, int.class, int.class);
        if (setValue == null) return;
        updaters.put(name, () -> invokeSetValue(setValue, name, x.getAsInt(), y.getAsInt()));
    }

    @Override
    public void uniform3f(String name, DoubleSupplier x, DoubleSupplier y, DoubleSupplier z) {
        Object uniform = makeUniform(make3f, name);
        if (uniform == null) return;
        MethodHandle setValue = setValueHandle(uniform, float.class, float.class, float.class);
        if (setValue == null) return;
        updaters.put(name, () -> invokeSetValue(setValue, name,
                (float) x.getAsDouble(), (float) y.getAsDouble(), (float) z.getAsDouble()));
    }

    @Override
    public void uniform3i(String name, IntSupplier x, IntSupplier y, IntSupplier z) {
        // No ivec3 type on OptiFine - pad into an ivec4 with w fixed at 0.
        Object uniform = makeUniform(make4i, name);
        if (uniform == null) return;
        MethodHandle setValue = setValueHandle(uniform, int.class, int.class, int.class, int.class);
        if (setValue == null) return;
        updaters.put(name, () -> invokeSetValue(setValue, name, x.getAsInt(), y.getAsInt(), z.getAsInt(), 0));
    }

    @Override
    public void uniform3d(String name, DoubleSupplier x, DoubleSupplier y, DoubleSupplier z) {
        // OptiFine has no double uniform type at all - fall back to float precision.
        Object uniform = makeUniform(make3f, name);
        if (uniform == null) return;
        MethodHandle setValue = setValueHandle(uniform, float.class, float.class, float.class);
        if (setValue == null) return;
        updaters.put(name, () -> invokeSetValue(setValue, name,
                (float) x.getAsDouble(), (float) y.getAsDouble(), (float) z.getAsDouble()));
    }

    @Override
    public void uniform4f(String name, DoubleSupplier x, DoubleSupplier y, DoubleSupplier z, DoubleSupplier w) {
        Object uniform = makeUniform(make4f, name);
        if (uniform == null) return;
        MethodHandle setValue = setValueHandle(uniform, float.class, float.class, float.class, float.class);
        if (setValue == null) return;
        updaters.put(name, () -> invokeSetValue(setValue, name,
                (float) x.getAsDouble(), (float) y.getAsDouble(), (float) z.getAsDouble(), (float) w.getAsDouble()));
    }
}
