package com.euphoriapatches.euphoria_patcher.fabric.mixin;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.integration.ShaderLoader;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.services.ShaderDetector;
import com.euphoriapatches.euphoria_patcher.util.UpdateChecker;
import com.euphoriapatches.euphoria_patcher.util.VersionComparator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

import static com.euphoriapatches.euphoria_patcher.fabric.mixin.EuphoriaMixinPlugin.IRIS_HEADER_ENTRY_CLASS;

@Debug(export = true)
@Pseudo
@Mixin(targets = IRIS_HEADER_ENTRY_CLASS, remap = false)
public class IrisHeaderEntryMixin {

    @Unique
    private static Boolean euphoriaPatcher$useYarnMappings = null;

    @Unique
    private static String euphoriaPatcher$EuphoriaURL = "https://euphoriapatches.com/support";

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void onConstructor(CallbackInfo ci) {
        try {
            EuphoriaPatcher instance = EuphoriaPatcher.getInstance();
            ShaderDetector shaderDetector = instance.getShaderDetector();
            Path currentShaderPackPath = ShaderLoader.getCurrentShaderpackPath();

            String buttonText = "Support EP";
            int buttonColor = 0; // 1=Red, 2=Green, 3=Blue , 0=Purple

            boolean isUpdateAvailable = UpdateChecker.isUpdateAvailable() && UpdateChecker.shouldUserUpdate() && // Global update check
                    // Check if EP update is newer than current selected shader pack version
                    VersionComparator.isNewerVersion(UpdateChecker.getNewModVersion(), shaderDetector.readVersionFromPackJson(currentShaderPackPath));
            if (isUpdateAvailable) {
                buttonText = "Update EP!";
                buttonColor = 2; // Green
                euphoriaPatcher$EuphoriaURL = EuphoriaPatcher.EP_DOWNLOAD_URL;
            }

            boolean secondCondition = shaderDetector.noDevVersionsInstalled() || isUpdateAvailable;

            if (shaderDetector.isEuphoriaPatchesShader(currentShaderPackPath) && secondCondition)
                euphoriaPatcher$addEPIrisButton(buttonText, buttonColor);
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Failed to add Iris EP button: " + e.getMessage());
            euphoriaPatcher$debugLog(EuphoriaLogger.getStackTrace(e));
        }
    }

    @Unique
    private void euphoriaPatcher$addEPIrisButton(String buttonText, int buttonColor) {
        try {
            if (euphoriaPatcher$useYarnMappings == null) {
                euphoriaPatcher$discoverMappingType();
            }

            Object utilityButtons = euphoriaPatcher$getFieldValue(this, "utilityButtons");
            Object screen = euphoriaPatcher$getFieldValue(this, "screen");

            if (utilityButtons == null) {
                euphoriaPatcher$debugLog("utilityButtons field not found");
                return;
            }

            if (euphoriaPatcher$useYarnMappings) {
                euphoriaPatcher$addEPIrisButtonYarn(utilityButtons, screen, buttonText, buttonColor);
            } else {
                euphoriaPatcher$addEPIrisButtonModern(utilityButtons, screen, buttonText, buttonColor);
            }
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Error in addEPIrisButton: " + e.getMessage());
            euphoriaPatcher$debugLog(EuphoriaLogger.getStackTrace(e));
        }
    }

    @Unique
    private void euphoriaPatcher$addEPIrisButtonYarn(Object utilityButtons, Object screen, String buttonTextLiteral, int buttonColor) throws Exception {
        Formatting buttonColorFormatting;
        switch (buttonColor) {
            case 1:
                buttonColorFormatting = Formatting.RED;
                break;
            case 2:
                buttonColorFormatting = Formatting.GREEN;
                break;
            case 3:
                buttonColorFormatting = Formatting.BLUE;
                break;
            default:
                buttonColorFormatting = Formatting.LIGHT_PURPLE;
                break;
        }
        MutableText buttonText = Text.literal(buttonTextLiteral).formatted(buttonColorFormatting);
        MinecraftClient minecraft = MinecraftClient.getInstance();
        Object supportEPButton = euphoriaPatcher$createIrisButton(buttonText, () -> euphoriaPatcher$handleSupportEPButtonClickYarn(minecraft, screen));
        euphoriaPatcher$addButtonToRow(utilityButtons, supportEPButton, 66);
        euphoriaPatcher$debugLog("Successfully added Iris EP button (Yarn)");
    }

    @Unique
    private void euphoriaPatcher$addEPIrisButtonModern(Object utilityButtons, Object screen, String buttonTextLiteral, int buttonColor) throws Exception {
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
        Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
        Object minecraft = minecraftClass.getMethod("getInstance").invoke(null);

        Class<?> componentClass = Class.forName("net.minecraft.network.chat.MutableComponent");
        Class<?> chatFormattingClass = Class.forName("net.minecraft.ChatFormatting");
        Object buttonColorFormattingEnum = chatFormattingClass.getField(buttonColorFormatting).get(null);

        Object buttonText = componentClass.getMethod("literal", String.class).invoke(null, buttonTextLiteral);
        buttonText = buttonText.getClass().getMethod("withStyle", chatFormattingClass).invoke(buttonText, buttonColorFormattingEnum);

        Object supportEPButton = euphoriaPatcher$createIrisButton(buttonText, () -> euphoriaPatcher$handleSupportEPButtonClickModern(minecraft, screen));
        euphoriaPatcher$addButtonToRow(utilityButtons, supportEPButton, 66);
        euphoriaPatcher$debugLog("Successfully added Iris EP button (Modern)");
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
    private void euphoriaPatcher$handleSupportEPButtonClickYarn(MinecraftClient minecraft, Object screen) {
        try {
            euphoriaPatcher$playButtonClickSound();
            ConfirmLinkScreen confirmScreen = new ConfirmLinkScreen(
                confirmed -> {
                    if (confirmed) {
                        euphoriaPatcher$openUrlYarn();
                    }
                    minecraft.setScreen((Screen) screen);
                },
                euphoriaPatcher$EuphoriaURL,
                true
            );
            minecraft.setScreen(confirmScreen);
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Error handling button click (Yarn): " + e.getMessage());
            euphoriaPatcher$debugLog(EuphoriaLogger.getStackTrace(e));
        }
    }

    @Unique
    private void euphoriaPatcher$handleSupportEPButtonClickModern(Object minecraft, Object screen) {
        try {
            euphoriaPatcher$playButtonClickSound();
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
                            euphoriaPatcher$openUrlModern();
                        }
                        euphoriaPatcher$setScreenModern(minecraft, screen);
                    }
                    return null;
                }
            );

            Object confirmScreen = constructor.newInstance(booleanConsumer, euphoriaPatcher$EuphoriaURL, true);
            euphoriaPatcher$setScreenModern(minecraft, confirmScreen);
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Error handling button click (Modern): " + e.getMessage());
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
    private void euphoriaPatcher$openUrlYarn() {
        try {
            Util.getOperatingSystem().open(euphoriaPatcher$EuphoriaURL);
            euphoriaPatcher$debugLog("Successfully opened URL (Yarn)");
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Failed to open URL (Yarn): " + e.getMessage());
        }
    }

    @Unique
    private void euphoriaPatcher$openUrlModern() {
        try {
            Class<?> utilClass = Class.forName("net.minecraft.Util");
            Object platform = utilClass.getMethod("getPlatform").invoke(null);
            platform.getClass().getMethod("openUri", String.class).invoke(platform, euphoriaPatcher$EuphoriaURL);
            euphoriaPatcher$debugLog("Successfully opened URL (Modern)");
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Failed to open URL (Modern): " + e.getMessage());
        }
    }

    @Unique
    private void euphoriaPatcher$setScreenModern(Object minecraft, Object screen) throws Exception {
        Class<?> screenClass = Class.forName("net.minecraft.client.gui.screens.Screen");
        minecraft.getClass().getMethod("setScreen", screenClass).invoke(minecraft, screen);
    }

    @Unique
    private void euphoriaPatcher$discoverMappingType() {
        try {
            MinecraftClient.getInstance();
            euphoriaPatcher$useYarnMappings = true;
            euphoriaPatcher$debugLog("Using Yarn mappings");
        } catch (Throwable t) {
            euphoriaPatcher$useYarnMappings = false;
            euphoriaPatcher$debugLog("Using Modern (Mojmap) mappings");
        }
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
