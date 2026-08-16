package com.euphoriapatches.euphoria_patcher.features.steganography;

import com.euphoriapatches.euphoria_patcher.config.ConfigHandler;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.ReflectionUtils;
import com.euphoriapatches.euphoria_patcher.util.mod.ModLoaderSpecifics;

import java.io.File;
import java.lang.reflect.Method;

public final class NativeImageEmbedHelper {
    private NativeImageEmbedHelper() {
    }

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[NativeImageEmbedHelper] " + message);
    }

    public static boolean isNotScreenshotFile(File file) {
        if (file == null) return true;
        File parent = file.getParentFile();
        return parent == null || !"screenshots".equalsIgnoreCase(parent.getName());
    }

    /**
     * Embeds the current shaderpack's settings (if any) into the screenshot's pixels
     * via LSB steganography, right before it's serialized to disk.
     *
     * @param nativeImage The NativeImage instance
     * @param file        The file to which the image is being written
     */
    public static void embed(Object nativeImage, File file) {
        if (isNotScreenshotFile(file)) return;

        if (ConfigHandler.EmbedShaderSettingsMode.DISABLED.equals(ConfigHandler.doEmbedShaderSettingsInScreenshots)) {
            debugLog("Screenshot settings embedding is disabled in config, skipping embed");
            return;
        }

        try {
            Class<?> clazz = nativeImage.getClass();

            String[] getterNames;
            String[] setterNames;
            String[] widthNames;
            String[] heightNames;

            if (ModLoaderSpecifics.isInstance(ModLoaderSpecifics.FORGE)) {
                getterNames = new String[]{"m_84985_"};
                setterNames = new String[]{"m_84988_"};
                widthNames = new String[]{"m_84982_"};
                heightNames = new String[]{"m_85084_"};
            } else if (ModLoaderSpecifics.isInstance(ModLoaderSpecifics.NEOFORGE)) {
                getterNames = new String[]{"getPixelRGBA", "getPixelABGR"};
                setterNames = new String[]{"setPixelRGBA", "setPixelABGR"};
                widthNames = new String[]{"getWidth"};
                heightNames = new String[]{"getHeight"};
            } else {
                getterNames = new String[]{"getPixelABGR", "method_4315"};
                setterNames = new String[]{"setPixelABGR", "method_4305"};
                widthNames = new String[]{"getWidth", "method_4307"};
                heightNames = new String[]{"getHeight", "method_4323"};
            }

            Method getter = ReflectionUtils.tryDeclaredMethods(clazz, new Class<?>[]{int.class, int.class}, getterNames);
            Method setter = ReflectionUtils.tryMethods(clazz, new Class<?>[]{int.class, int.class, int.class}, setterNames);
            Method getWidth = ReflectionUtils.tryMethods(clazz, widthNames);
            Method getHeight = ReflectionUtils.tryMethods(clazz, heightNames);

            int width = (int) getWidth.invoke(nativeImage);
            int height = (int) getHeight.invoke(nativeImage);

            //Needs to happen before we embed our data
            if (ConfigHandler.EmbedShaderSettingsMode.DEBUG.equals(ConfigHandler.doEmbedShaderSettingsInScreenshots)) {
                ShaderSteganography.writeOriginalPixelSnapshot(nativeImage, width, height, getter, file.toPath());
            }

            boolean embedded = ShaderSteganography.embedCurrentShaderSettings(nativeImage, width, height, getter, setter);
            debugLog("Embed attempt finished (embedded=" + embedded + ") for " + width + "x" + height + " image");
        } catch (Throwable t) {
            debugLog("Failed to resolve pixel accessors, skipping embed: " + t);
        }
    }

    /**
     * Called after the real screenshot has finished writing to disk. In "debug" mode only,
     * generates "-debug.png" visualization of the exact same embedded bits - see
     * {@link ShaderSteganography#writeDebugVisualization}. No-op in every other mode.
     */
    public static void createDebugScreenshot(File file) {
        if (!ConfigHandler.EmbedShaderSettingsMode.DEBUG.equals(ConfigHandler.doEmbedShaderSettingsInScreenshots)) {
            return;
        }
        if (isNotScreenshotFile(file)) return;

        ShaderSteganography.writeDebugVisualization(file.toPath());
    }
}
