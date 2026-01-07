package com.euphoriapatches.euphoria_patcher.forge.mixin;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.integration.ShaderLoader;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.services.ShaderDetector;
import com.euphoriapatches.euphoria_patcher.util.UpdateChecker;
import com.euphoriapatches.euphoria_patcher.util.VersionComparator;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
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

    @Inject(method = "<init>", at = @At("RETURN"), remap = false, require = 0)
    private void onConstructor(CallbackInfo ci) {
        try {
            EuphoriaPatcher instance = EuphoriaPatcher.getInstance();
            ShaderDetector shaderDetector = instance.getShaderDetector();
            Path currentShaderPackPath = ShaderLoader.getCurrentShaderpackPath();

            String buttonText = "Support EP";
            int buttonColor = 0; // 1=Red, 2=Green, 3=Blue, 0=Purple

            boolean isUpdateAvailable = euphoriaPatcher$isUpdateAvailable(shaderDetector, currentShaderPackPath);
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

    @Inject(method = "m_6311_", at = @At("TAIL"), remap = false, require = 0)
    private void onRenderContent(@Coerce Object guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta, CallbackInfo ci) {
        euphoriaPatcher$renderTooltipImpl(guiGraphics);
    }

    @Unique
    private void euphoriaPatcher$renderTooltipImpl(Object guiGraphics) {
        try {
            Object utilityButtons = euphoriaPatcher$getFieldValue(this, "utilityButtons");
            Object resetButton = euphoriaPatcher$getFieldValue(this, "resetButton");

            if (utilityButtons == null) {
                return;
            }

            euphoriaPatcher$renderTooltip(guiGraphics, utilityButtons, resetButton);
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Error rendering tooltip: " + e.getMessage());
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
    private void euphoriaPatcher$renderTooltip(Object guiGraphics, Object utilityButtons, Object resetButton) throws Exception {
        Object minecraft = euphoriaPatcher$getMinecraftInstance();
        Object font = euphoriaPatcher$getFont(minecraft);

        Object children = utilityButtons.getClass().getMethod("children").invoke(utilityButtons);
        Iterable<?> childrenIterable = (Iterable<?>) children;

        Class<?> textButtonElementClass = Class.forName("net.irisshaders.iris.gui.element.IrisElementRow$TextButtonElement");

        for (Object child : childrenIterable) {
            if (textButtonElementClass.isInstance(child) && child != resetButton) {
                // Check if hovered: isHovered() -> isHovered
                boolean isHovered = (boolean) child.getClass().getMethod("isHovered").invoke(child);

                // Check if focused: isFocused() -> m_93696_
                boolean isFocused = false;
                try {
                    isFocused = (boolean) child.getClass().getMethod("m_93696_").invoke(child);
                } catch (Exception e) {
                    // isFocused might not exist, ignore
                }

                if (isHovered || isFocused) {
                    // Get tooltip text and color based on update availability
                    EuphoriaPatcher instance = EuphoriaPatcher.getInstance();
                    ShaderDetector shaderDetector = instance.getShaderDetector();
                    Path currentShaderPackPath = ShaderLoader.getCurrentShaderpackPath();
                    boolean isUpdateAvailable = euphoriaPatcher$isUpdateAvailable(shaderDetector, currentShaderPackPath);

                    String tooltipString = isUpdateAvailable ? "Update Euphoria Patches!" : "Support Euphoria Patches";
                    String colorFormatting = isUpdateAvailable ? "GREEN" : "LIGHT_PURPLE";

                    // Create tooltip text: Component.literal(text).withStyle(color)
                    Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
                    Class<?> mutableComponentClass = Class.forName("net.minecraft.network.chat.MutableComponent");
                    Class<?> chatFormattingClass = Class.forName("net.minecraft.ChatFormatting");
                    Object colorFormattingEnum = chatFormattingClass.getField(colorFormatting).get(null);
                    Object tooltipText = componentClass.getMethod("m_237113_", String.class).invoke(null, tooltipString);
                    tooltipText = mutableComponentClass.getMethod("m_130940_", chatFormattingClass).invoke(tooltipText, colorFormattingEnum);

                    // Get ScreenRectangle: getRectangle() -> m_264198_
                    Object rect = child.getClass().getMethod("m_264198_").invoke(child);

                    // Get ScreenDirection.RIGHT: RIGHT
                    Class<?> screenDirectionClass = Class.forName("net.minecraft.client.gui.navigation.ScreenDirection");
                    Object rightDirection = screenDirectionClass.getField("RIGHT").get(null);

                    // Get right bound: getBoundInDirection(ScreenDirection) -> m_264095_
                    int rightBound = (int) rect.getClass().getMethod("m_264095_", screenDirectionClass).invoke(rect, rightDirection);

                    // Get y position: position() -> then y()
                    Object position = rect.getClass().getMethod("f_263846_").invoke(rect);
                    int yPos = (int) position.getClass().getMethod("f_263694_").invoke(position);

                    // Font.width(FormattedText) -> m_92852_
                    Class<?> formattedTextClass = Class.forName("net.minecraft.network.chat.FormattedText");
                    int textWidth = (int) font.getClass().getMethod("m_92852_", formattedTextClass).invoke(font, tooltipText);
                    int tooltipX = rightBound - (textWidth + 10);
                    int tooltipY = yPos - 16;

                    final int finalTooltipX = tooltipX;
                    final int finalTooltipY = tooltipY;
                    final Object finalFont = font;
                    final Object finalGuiGraphics = guiGraphics;
                    final Object finalTooltipText = tooltipText;

                    Class<?> shaderPackScreenClass = Class.forName("net.irisshaders.iris.gui.screen.ShaderPackScreen");
                    Object renderQueue = shaderPackScreenClass.getField("TOP_LAYER_RENDER_QUEUE").get(null);
                    Class<?> guiUtilClass = Class.forName("net.irisshaders.iris.gui.GuiUtil");
                    Class<?> guiGraphicsClass = Class.forName("net.minecraft.client.gui.GuiGraphics");

                    Runnable renderTask = () -> {
                        try {
                            guiUtilClass.getMethod("drawTextPanel", finalFont.getClass(), guiGraphicsClass, componentClass, int.class, int.class)
                                .invoke(null, finalFont, finalGuiGraphics, finalTooltipText, finalTooltipX, finalTooltipY);
                        } catch (Exception e) {
                            euphoriaPatcher$debugLog("Error in render task: " + e.getMessage());
                        }
                    };
                    renderQueue.getClass().getMethod("add", Object.class).invoke(renderQueue, renderTask);
                }
                break;
            }
        }
    }

    @Unique
    private Object euphoriaPatcher$getMinecraftInstance() throws Exception {
        Class<?> minecraftClass;
        try {
            minecraftClass = Class.forName("C_3391_");
        } catch (ClassNotFoundException e) {
            minecraftClass = Class.forName("net.minecraft.client.Minecraft");
        }
        return minecraftClass.getMethod("m_91087_").invoke(null);
    }

    @Unique
    private Object euphoriaPatcher$getFont(Object minecraft) throws Exception {
        // Minecraft.font -> f_91062_
        return minecraft.getClass().getField("f_91062_").get(minecraft);
    }

    @Unique
    private boolean euphoriaPatcher$isUpdateAvailable(ShaderDetector shaderDetector, Path currentShaderPackPath) {
        return UpdateChecker.shouldUserUpdate() &&
                VersionComparator.isNewerVersion(UpdateChecker.getNewModVersion(), shaderDetector.readVersionFromPackJson(currentShaderPackPath));
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
