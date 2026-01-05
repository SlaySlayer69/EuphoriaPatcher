package com.euphoriapatches.euphoria_patcher.forge.mixin;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.integration.ShaderLoader;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.services.ShaderDetector;
import com.euphoriapatches.euphoria_patcher.util.UpdateChecker;
import com.euphoriapatches.euphoria_patcher.util.VersionComparator;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

import static com.euphoriapatches.euphoria_patcher.forge.mixin.EuphoriaMixinPlugin.IRIS_HEADER_ENTRY_CLASS;

@Debug(export = true)
@Pseudo
@Mixin(targets = IRIS_HEADER_ENTRY_CLASS, remap = false)
public class IrisHeaderEntryMixin {

    @Unique
    private static String euphoriaPatcher$EuphoriaURL = "https://euphoriapatches.com/support";

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void onConstructor(CallbackInfo ci) {
        try {
            EuphoriaPatcher instance = EuphoriaPatcher.getInstance();
            ShaderDetector shaderDetector = instance.getShaderDetector();
            Path currentShaderPackPath = ShaderLoader.getCurrentShaderpackPath();

            String buttonText = "Support EP";
            int buttonColor = 0; // 1=Red, 2=Green, 3=Blue, 0=Purple

            boolean isUpdateAvailable = UpdateChecker.isUpdateAvailable() && UpdateChecker.shouldUserUpdate() && // Global update check
                // Check if EP update is newer than current selected shader pack version
                VersionComparator.isNewerVersion(UpdateChecker.getNewModVersion(), shaderDetector.readVersionFromPackJson(currentShaderPackPath));
            if (isUpdateAvailable) {
                buttonText = "Update EP!";
                buttonColor = 2; // Green
                euphoriaPatcher$EuphoriaURL = EuphoriaPatcher.EP_DOWNLOAD_URL;
            }

            boolean secondCondition = shaderDetector.noDevVersionsInstalled() || isUpdateAvailable;

            if (shaderDetector.isEuphoriaPatchesShader(ShaderLoader.getCurrentShaderpackPath()) && secondCondition)
                euphoriaPatcher$addEPIrisButton(buttonText, buttonColor);
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Failed to add Iris EP button: " + e.getMessage());
            euphoriaPatcher$debugLog(EuphoriaLogger.getStackTrace(e));
        }
    }

    @Unique
    private void euphoriaPatcher$addEPIrisButton(String buttonTextLiteral, int buttonColor) {
        try {
            Object utilityButtons = euphoriaPatcher$getFieldValue(this, "utilityButtons");
            Object screen = euphoriaPatcher$getFieldValue(this, "screen");

            if (utilityButtons == null) {
                euphoriaPatcher$debugLog("utilityButtons field not found");
                return;
            }

            String buttonColorFormatting;
            switch (buttonColor) {
                case 1:
                    buttonColorFormatting = "RED";
                    break;
                case 2:
                    buttonColorFormatting = "GREEN";
                    break;
                case 3:
                    buttonColorFormatting = "BLUE";
                    break;
                default:
                    buttonColorFormatting = "LIGHT_PURPLE";
                    break;
            }

            // Try to get Minecraft class - try both SRG and MCP names
            Class<?> minecraftClass = null;
            try {
                minecraftClass = Class.forName("C_3391_");
            } catch (ClassNotFoundException e) {
                minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            }
            Object minecraft = minecraftClass.getMethod("m_91087_").invoke(null);

            // Get Component and ChatFormatting classes
            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
            Class<?> mutableComponentClass = Class.forName("net.minecraft.network.chat.MutableComponent");
            Class<?> chatFormattingClass = Class.forName("net.minecraft.ChatFormatting");
            Object buttonColorFormattingEnum = chatFormattingClass.getField(buttonColorFormatting).get(null);

            // Create button text: Component.literal(text).withStyle(formatting)
            Object buttonText = componentClass.getMethod("m_237113_", String.class).invoke(null, buttonTextLiteral);
            buttonText = mutableComponentClass.getMethod("m_130940_", chatFormattingClass).invoke(buttonText, buttonColorFormattingEnum);

            Object supportEPButton = euphoriaPatcher$createIrisButton(buttonText, () -> euphoriaPatcher$handleSupportEPButtonClick(minecraft, screen));
            euphoriaPatcher$addButtonToRow(utilityButtons, supportEPButton, 66);
            euphoriaPatcher$debugLog("Successfully added Iris EP button");
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Error in addEPIrisButton: " + e.getMessage());
            euphoriaPatcher$debugLog(EuphoriaLogger.getStackTrace(e));
        }
    }

    @Unique
    private Object euphoriaPatcher$createIrisButton(Object buttonText, Runnable action) throws Exception {
        Class<?> textButtonElementClass = Class.forName("net.irisshaders.iris.gui.element.IrisElementRow$TextButtonElement");

        for (java.lang.reflect.Constructor<?> ctor : textButtonElementClass.getConstructors()) {
            Class<?>[] paramTypes = ctor.getParameterTypes();
            if (paramTypes.length == 2) {
                Class<?> actionClass = paramTypes[1];
                Object actionProxy = java.lang.reflect.Proxy.newProxyInstance(
                    actionClass.getClassLoader(),
                    new Class<?>[] { actionClass },
                    (proxy, method, args) -> {
                        action.run();
                        return true;
                    }
                );
                return ctor.newInstance(buttonText, actionProxy);
            }
        }
        throw new Exception("Could not find TextButtonElement constructor");
    }

    @Unique
    private void euphoriaPatcher$addButtonToRow(Object utilityButtons, Object button, int width) throws Exception {
        for (java.lang.reflect.Method method : utilityButtons.getClass().getMethods()) {
            if (method.getName().equals("add") && method.getParameterCount() == 2) {
                Class<?>[] paramTypes = method.getParameterTypes();
                if (paramTypes[1] == int.class || paramTypes[1] == Integer.class) {
                    method.invoke(utilityButtons, button, width);
                    return;
                }
            }
        }
        throw new Exception("Could not find add method on utilityButtons");
    }

    @Unique
    private void euphoriaPatcher$handleSupportEPButtonClick(Object minecraft, Object screen) {
        try {
            euphoriaPatcher$playButtonClickSound();

            // Get ConfirmLinkScreen class
            Class<?> confirmLinkScreenClass = Class.forName("net.minecraft.client.gui.screens.ConfirmLinkScreen");

            java.lang.reflect.Constructor<?> constructor = null;
            Class<?> consumerClass = null;

            for (java.lang.reflect.Constructor<?> ctor : confirmLinkScreenClass.getConstructors()) {
                Class<?>[] paramTypes = ctor.getParameterTypes();
                if (paramTypes.length == 3 && paramTypes[1] == String.class && paramTypes[2] == boolean.class) {
                    constructor = ctor;
                    consumerClass = paramTypes[0];
                    break;
                }
            }

            if (constructor == null) {
                euphoriaPatcher$debugLog("Could not find ConfirmLinkScreen constructor");
                return;
            }

            Object booleanConsumer = java.lang.reflect.Proxy.newProxyInstance(
                consumerClass.getClassLoader(),
                new Class<?>[] { consumerClass },
                (proxy, method, args) -> {
                    if (args != null && args.length > 0 && args[0] instanceof Boolean) {
                        boolean confirmed = (boolean) args[0];
                        if (confirmed) {
                            euphoriaPatcher$openUrl();
                        }
                        euphoriaPatcher$setScreen(minecraft, screen);
                    }
                    return null;
                }
            );

            Object confirmScreen = constructor.newInstance(booleanConsumer, euphoriaPatcher$EuphoriaURL, true);
            euphoriaPatcher$setScreen(minecraft, confirmScreen);
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Error handling button click: " + e.getMessage());
            euphoriaPatcher$debugLog(EuphoriaLogger.getStackTrace(e));
        }
    }

    @Unique
    private void euphoriaPatcher$playButtonClickSound() {
        try {
            Class<?> guiUtilClass = Class.forName("net.irisshaders.iris.gui.GuiUtil");
            guiUtilClass.getMethod("playButtonClickSound").invoke(null);
        } catch (Exception e) {
            // GuiUtil might not exist in all versions, skip sound
        }
    }

    @Unique
    private void euphoriaPatcher$openUrl() {
        try {
            // Get Util class
            Class<?> utilClass = Class.forName("net.minecraft.Util");
            // Call Util.getPlatform() -> m_137581_
            Object platform = utilClass.getMethod("m_137581_").invoke(null);

            // Try openUri method first (might be readable name even in SRG)
            try {
                platform.getClass().getMethod("openUri", String.class).invoke(platform, euphoriaPatcher$EuphoriaURL);
            } catch (NoSuchMethodException e) {
                // If that fails, try with m_137648_ and URI parameter
                Class<?> uriClass = Class.forName("java.net.URI");
                Object uri = uriClass.getConstructor(String.class).newInstance(euphoriaPatcher$EuphoriaURL);
                platform.getClass().getMethod("m_137648_", uriClass).invoke(platform, uri);
            }
            euphoriaPatcher$debugLog("Successfully opened URL");
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Failed to open URL: " + e.getMessage());
        }
    }

    @Unique
    private void euphoriaPatcher$setScreen(Object minecraft, Object screen) throws Exception {
        // Get Screen class
        Class<?> screenClass = Class.forName("net.minecraft.client.gui.screens.Screen");
        // Call Minecraft.setScreen(Screen) -> m_91152_
        minecraft.getClass().getMethod("m_91152_", screenClass).invoke(minecraft, screen);
    }

    @Unique
    private Object euphoriaPatcher$getFieldValue(Object obj, String fieldName) {
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            try {
                java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(obj);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            } catch (Exception e) {
                euphoriaPatcher$debugLog("Error accessing field " + fieldName + ": " + e.getMessage());
                return null;
            }
        }
        euphoriaPatcher$debugLog("Field " + fieldName + " not found in class hierarchy");
        return null;
    }

    @Unique
    private void euphoriaPatcher$debugLog(String message) {
        EuphoriaLogger.debugLog("[IrisHeaderEntryMixin] " + message);
    }
}
