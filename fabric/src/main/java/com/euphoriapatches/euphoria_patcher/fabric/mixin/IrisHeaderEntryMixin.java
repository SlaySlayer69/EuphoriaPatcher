package com.euphoriapatches.euphoria_patcher.fabric.mixin;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.integration.ShaderLoader;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.services.ShaderDetector;
import com.euphoriapatches.euphoria_patcher.util.ShaderData;
import com.euphoriapatches.euphoria_patcher.util.UpdateChecker;
import com.euphoriapatches.euphoria_patcher.util.VersionComparator;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

import static com.euphoriapatches.euphoria_patcher.fabric.mixin.EuphoriaMixinPlugin.IRIS_HEADER_ENTRY_CLASS;

@Debug(export = true)
@Pseudo
@Mixin(targets = IRIS_HEADER_ENTRY_CLASS, remap = false)
public class IrisHeaderEntryMixin {

    @Unique
    private static String euphoriaPatcher$EuphoriaURL = "https://euphoriapatches.com/support";

    @Unique
    private long euphoriaPatcher$buttonHoverStartTime = 0;

    @Unique
    private boolean euphoriaPatcher$isCurrentlyHovering = false;

    @Unique
    private static boolean euphoriaPatcher$hasShownExtendedTooltip = false;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false, require = 0)
    private void onConstructor(CallbackInfo ci) {
        try {
            EuphoriaPatcher instance = EuphoriaPatcher.getInstance();
            ShaderDetector shaderDetector = instance.getShaderDetector();
            Path currentShaderPackPath = ShaderLoader.getCurrentShaderpackPath();

            String buttonText = "Support EP";
            int buttonColor = 0; // 1=Red, 2=Green, 3=Blue , 0=Purple

            boolean isUpdateAvailable = euphoriaPatcher$isUpdateAvailable(shaderDetector, currentShaderPackPath);
            if (isUpdateAvailable) {
                buttonText = "Update EP!";
                buttonColor = 2; // Green
                euphoriaPatcher$EuphoriaURL = EuphoriaPatcher.EP_DOWNLOAD_URL;
            } else {
                euphoriaPatcher$EuphoriaURL = "https://euphoriapatches.com/support";
            }

            if (euphoriaPatcher$shouldShowEPButton())
                euphoriaPatcher$addEPIrisButton(buttonText, buttonColor);
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Failed to add Iris EP button: " + e.getMessage());
            euphoriaPatcher$debugLog(EuphoriaLogger.getStackTrace(e));
        }
    }

    @Inject(method = "renderContent", at = @At("TAIL"), remap = false, require = 0)
    private void onRenderContent(@Coerce Object guiGraphics, int mouseX, int mouseY, boolean bl, float tickDelta, CallbackInfo ci) {
        try {
            Object utilityButtons = euphoriaPatcher$getFieldValue(this, "utilityButtons");
            Object resetButton = euphoriaPatcher$getFieldValue(this, "resetButton");

            if (utilityButtons == null) {
                return;
            }

            euphoriaPatcher$renderTooltip(guiGraphics, utilityButtons, resetButton);
            euphoriaPatcher$recolorEPButtonWhileShift();
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Error rendering tooltip: " + e.getMessage());
            euphoriaPatcher$debugLog(EuphoriaLogger.getStackTrace(e));
        }
    }

    @Unique
    private boolean euphoriaPatcher$shouldShowEPButton() {
        EuphoriaPatcher instance = EuphoriaPatcher.getInstance();
        ShaderDetector shaderDetector = instance.getShaderDetector();
        Path currentShaderPackPath = ShaderLoader.getCurrentShaderpackPath();

        boolean isUpdateAvailable = euphoriaPatcher$isUpdateAvailable(shaderDetector, currentShaderPackPath);

        ShaderData.PersistentShaderData data = ShaderData.load();
        boolean userAllowsSupportButton = false;
        if (data.supportEPButtonVisible == null || data.supportEPButtonVisible) {
            euphoriaPatcher$debugLog("User has not dismissed EP support button");
            userAllowsSupportButton = true;
        } else {
            euphoriaPatcher$debugLog("User has dismissed EP support button - not adding button");
        }
        boolean shouldShowSupportButton = shaderDetector.noDevVersionsInstalled() && userAllowsSupportButton;

        boolean secondCondition = shouldShowSupportButton || isUpdateAvailable;

        return shaderDetector.isEuphoriaPatchesShader(currentShaderPackPath) && secondCondition;
    }

    @Unique
    private boolean euphoriaPatcher$isShiftDown() {
        try {
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            Object minecraft = minecraftClass.getMethod("getInstance").invoke(null);
            return (boolean) minecraft.getClass().getMethod("hasShiftDown").invoke(minecraft);
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Error detecting shift key: " + e.getMessage());
            return false;
        }
    }

    @Unique
    private void euphoriaPatcher$recolorEPButtonWhileShift() {
        try {
            Object utilityButtons = euphoriaPatcher$getFieldValue(this, "utilityButtons");
            Object resetButton = euphoriaPatcher$getFieldValue(this, "resetButton");

            if (utilityButtons == null) {
                return;
            }

            // Check if update is available
            EuphoriaPatcher instance = EuphoriaPatcher.getInstance();
            ShaderDetector shaderDetector = instance.getShaderDetector();
            Path currentShaderPackPath = ShaderLoader.getCurrentShaderpackPath();
            boolean isUpdateAvailable = euphoriaPatcher$isUpdateAvailable(shaderDetector, currentShaderPackPath);

            boolean shiftDown = euphoriaPatcher$isShiftDown();

            String buttonTextLiteral;
            String buttonColorFormatting;
            if (isUpdateAvailable) {
                // Update available: always green, ignore shift
                buttonTextLiteral = "Update EP!";
                buttonColorFormatting = "GREEN";
            } else if (shiftDown) {
                // No update, shift held: red
                buttonTextLiteral = "Support EP";
                buttonColorFormatting = "RED";
            } else {
                // No update, normal: purple
                buttonTextLiteral = "Support EP";
                buttonColorFormatting = "LIGHT_PURPLE";
            }

            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
            Class<?> chatFormattingClass = Class.forName("net.minecraft.ChatFormatting");
            Object buttonColorFormattingEnum = chatFormattingClass.getField(buttonColorFormatting).get(null);
            Object buttonText = componentClass.getMethod("literal", String.class).invoke(null, buttonTextLiteral);
            buttonText = buttonText.getClass().getMethod("withStyle", chatFormattingClass).invoke(buttonText, buttonColorFormattingEnum);

            Object children = utilityButtons.getClass().getMethod("children").invoke(utilityButtons);
            Iterable<?> childrenIterable = (Iterable<?>) children;
            Class<?> textButtonElementClass = Class.forName("net.irisshaders.iris.gui.element.IrisElementRow$TextButtonElement");

            for (Object child : childrenIterable) {
                if (textButtonElementClass.isInstance(child) && child != resetButton) {
                    java.lang.reflect.Field textField = child.getClass().getField("text");
                    textField.setAccessible(true);
                    textField.set(child, buttonText);
                    break;
                }
            }
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Error recoloring EP button: " + e.getMessage());
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
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            Object minecraft = minecraftClass.getMethod("getInstance").invoke(null);

            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
            Class<?> chatFormattingClass = Class.forName("net.minecraft.ChatFormatting");
            Object buttonColorFormattingEnum = chatFormattingClass.getField(buttonColorFormatting).get(null);

            Object buttonText = componentClass.getMethod("literal", String.class).invoke(null, buttonTextLiteral);
            buttonText = buttonText.getClass().getMethod("withStyle", chatFormattingClass).invoke(buttonText, buttonColorFormattingEnum);

            Object supportEPButton = euphoriaPatcher$createIrisButton(buttonText, () -> euphoriaPatcher$handleSupportEPButtonClick(minecraft, screen));
            euphoriaPatcher$addButtonToRow(utilityButtons, supportEPButton, 66);
            euphoriaPatcher$debugLog("Successfully added Iris EP button (Modern)");
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

            // Check if shift is held
            if (euphoriaPatcher$isShiftDown()) {
                euphoriaPatcher$debugLog("Pressed Shift while clicking EP button - removing button");
                ShaderData.save(ShaderData.SaveData.of(ShaderData.DataField.SUPPORT_EP_BUTTON, false));
                return;
            }

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
    private void euphoriaPatcher$openUrl() {
        try {
            Class<?> utilClass;
            try {
                utilClass = Class.forName("net.minecraft.Util");
            } catch (ClassNotFoundException e) {
                utilClass = Class.forName("net.minecraft.util.Util");
            }
            Object platform = utilClass.getMethod("getPlatform").invoke(null);
            platform.getClass().getMethod("openUri", String.class).invoke(platform, euphoriaPatcher$EuphoriaURL);
            euphoriaPatcher$debugLog("Successfully opened URL (Modern)");
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Failed to open URL (Modern): " + e.getMessage());
        }
    }

    @Unique
    private void euphoriaPatcher$setScreen(Object minecraft, Object screen) throws Exception {
        Class<?> screenClass = Class.forName("net.minecraft.client.gui.screens.Screen");
        minecraft.getClass().getMethod("setScreen", screenClass).invoke(minecraft, screen);
    }

    @Unique
    private void euphoriaPatcher$renderTooltip(Object guiGraphics, Object utilityButtons, Object resetButton) throws Exception {
        // Get font: Minecraft.getInstance().font
        Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
        Object minecraft = minecraftClass.getMethod("getInstance").invoke(null);
        Object font = minecraft.getClass().getField("font").get(minecraft);

        // Get children from utilityButtons
        Object children = utilityButtons.getClass().getMethod("children").invoke(utilityButtons);
        Iterable<?> childrenIterable = (Iterable<?>) children;

        Class<?> textButtonElementClass = Class.forName("net.irisshaders.iris.gui.element.IrisElementRow$TextButtonElement");

        for (Object child : childrenIterable) {
            if (textButtonElementClass.isInstance(child) && child != resetButton) {
                // Check if hovered
                boolean isHovered = (boolean) child.getClass().getMethod("isHovered").invoke(child);

                // Check if focused
                boolean isFocused = false;
                try {
                    isFocused = (boolean) child.getClass().getMethod("isFocused").invoke(child);
                } catch (Exception e) {
                    // isFocused might not exist, ignore
                }

                if (isHovered || isFocused) {
                    // Track hover time and set permanent flag when threshold is reached
                    if (!euphoriaPatcher$hasShownExtendedTooltip) {
                        long currentTime = System.currentTimeMillis();
                        if (!euphoriaPatcher$isCurrentlyHovering) {
                            euphoriaPatcher$buttonHoverStartTime = currentTime;
                            euphoriaPatcher$isCurrentlyHovering = true;
                        }
                        long hoverDuration = currentTime - euphoriaPatcher$buttonHoverStartTime;
                        if (hoverDuration >= 1715) {
                            euphoriaPatcher$hasShownExtendedTooltip = true;
                        }
                    }

                    // Get tooltip text and color based on shift state and update availability
                    boolean shiftDown = euphoriaPatcher$isShiftDown();
                    EuphoriaPatcher instance = EuphoriaPatcher.getInstance();
                    ShaderDetector shaderDetector = instance.getShaderDetector();
                    Path currentShaderPackPath = ShaderLoader.getCurrentShaderpackPath();
                    boolean isUpdateAvailable = euphoriaPatcher$isUpdateAvailable(shaderDetector, currentShaderPackPath);

                    String tooltipString;
                    String colorFormatting;

                    if (isUpdateAvailable) { // Has higher priority over shift key
                        tooltipString = "Update Euphoria Patches!";
                        colorFormatting = "GREEN";
                    } else if (shiftDown) {
                        tooltipString = "Remove Support Button? Requires re-entering the menu to show effect";
                        colorFormatting = "RED";
                    } else {
                        String removeString = "";
                        if (euphoriaPatcher$hasShownExtendedTooltip) {
                            removeString = " (SHIFT Click to Remove)";
                        }
                        tooltipString = "Support Euphoria Patches!" + removeString;
                        colorFormatting = "LIGHT_PURPLE";
                    }

                    // Create tooltip text: Component.literal(text).withStyle(color)
                    Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
                    Class<?> formattedTextClass = Class.forName("net.minecraft.network.chat.FormattedText");
                    Class<?> chatFormattingClass = Class.forName("net.minecraft.ChatFormatting");
                    Object colorFormattingEnum = chatFormattingClass.getField(colorFormatting).get(null);
                    Object tooltipText = componentClass.getMethod("literal", String.class).invoke(null, tooltipString);
                    tooltipText = tooltipText.getClass().getMethod("withStyle", chatFormattingClass).invoke(tooltipText, colorFormattingEnum);

                    // Get rectangle and calculate tooltip position
                    Object rect = child.getClass().getMethod("getRectangle").invoke(child);
                    Class<?> screenDirectionClass = Class.forName("net.minecraft.client.gui.navigation.ScreenDirection");
                    Object rightDirection = screenDirectionClass.getField("RIGHT").get(null);
                    int rightBound = (int) rect.getClass().getMethod("getBoundInDirection", screenDirectionClass).invoke(rect, rightDirection);

                    Object position = rect.getClass().getMethod("position").invoke(rect);
                    int yPos = (int) position.getClass().getMethod("y").invoke(position);

                    int textWidth = (int) font.getClass().getMethod("width", formattedTextClass).invoke(font, tooltipText);
                    int tooltipX = rightBound - (textWidth + 10);
                    int tooltipY = yPos - 16;

                    // Make final copies for lambda
                    final Object finalFont = font;
                    final Object finalGuiGraphics = guiGraphics;
                    final Object finalTooltipText = tooltipText;
                    final int finalTooltipX = tooltipX;
                    final int finalTooltipY = tooltipY;

                    // Add to render queue
                    Class<?> shaderPackScreenClass = Class.forName("net.irisshaders.iris.gui.screen.ShaderPackScreen");
                    Object renderQueue = shaderPackScreenClass.getField("TOP_LAYER_RENDER_QUEUE").get(null);
                    Class<?> guiUtilClass = Class.forName("net.irisshaders.iris.gui.GuiUtil");

                    Runnable renderTask = () -> {
                        try {
                            guiUtilClass.getMethod("drawTextPanel", finalFont.getClass(), finalGuiGraphics.getClass(), componentClass, int.class, int.class)
                                .invoke(null, finalFont, finalGuiGraphics, finalTooltipText, finalTooltipX, finalTooltipY);
                        } catch (Exception e) {
                            euphoriaPatcher$debugLog("Error in render task: " + e.getMessage());
                        }
                    };
                    renderQueue.getClass().getMethod("add", Object.class).invoke(renderQueue, renderTask);
                } else {
                    // Reset hover tracking (but keep extended tooltip flag)
                    euphoriaPatcher$isCurrentlyHovering = false;
                }
                break;
            }
        }
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
        EuphoriaLogger.debugLog("[IrisHeaderEntryMixinModern] " + message);
    }
}
