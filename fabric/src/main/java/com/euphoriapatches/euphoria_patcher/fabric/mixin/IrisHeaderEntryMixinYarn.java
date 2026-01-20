package com.euphoriapatches.euphoria_patcher.fabric.mixin;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.integration.ShaderLoader;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.services.ShaderDetector;
import com.euphoriapatches.euphoria_patcher.util.ShaderData;
import com.euphoriapatches.euphoria_patcher.util.UpdateChecker;
import com.euphoriapatches.euphoria_patcher.util.VersionComparator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.navigation.NavigationDirection;
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
public class IrisHeaderEntryMixinYarn {

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

    @Inject(method = "method_25343(Lnet/minecraft/class_332;IIIIIIIZF)V", at = @At("TAIL"), remap = false, require = 0)
    private void onRenderContent10Params(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta, CallbackInfo ci) {
        euphoriaPatcher$renderTooltipImpl(context);
        euphoriaPatcher$recolorEPButtonWhileShift();
    }

    @Inject(method = "method_25343(Lnet/minecraft/class_332;IIZF)V", at = @At("TAIL"), remap = false, require = 0)
    private void onRenderContent5Params(DrawContext context, int mouseX, int mouseY, boolean hovered, float tickDelta, CallbackInfo ci) {
        euphoriaPatcher$renderTooltipImpl(context);
        euphoriaPatcher$recolorEPButtonWhileShift();
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
    private void euphoriaPatcher$renderTooltipImpl(DrawContext guiGraphics) {
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
    private boolean euphoriaPatcher$isShiftDown() {
        try {
            // Try MinecraftClient.isShiftPressed() first (class_310.method_74187)
            MinecraftClient minecraft = MinecraftClient.getInstance();
            try {
                Class<?> minecraftClass = Class.forName("net.minecraft.class_310");
                java.lang.reflect.Method method = minecraftClass.getMethod("method_74187");
                return (boolean) method.invoke(minecraft);
            } catch (Exception e) {
                // Fallback to Screen.hasShiftDown() (class_437.method_25442)
                Object screen = euphoriaPatcher$getFieldValue(this, "screen");
                if (screen != null) {
                    Class<?> screenClass = Class.forName("net.minecraft.class_437");
                    java.lang.reflect.Method method = screenClass.getMethod("method_25442");
                    return (boolean) method.invoke(screen);
                }
            }
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Error detecting shift key: " + e.getMessage());
        }
        return false;
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

            MutableText buttonText;
            if (isUpdateAvailable) {
                // Update available: always green, ignore shift
                buttonText = Text.literal("Update EP!").formatted(Formatting.GREEN);
            } else if (shiftDown) {
                // No update, shift held: red
                buttonText = Text.literal("Support EP").formatted(Formatting.RED);
            } else {
                // No update, normal: purple
                buttonText = Text.literal("Support EP").formatted(Formatting.LIGHT_PURPLE);
            }

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
            Object supportEPButton = euphoriaPatcher$createIrisButton(buttonText, () -> euphoriaPatcher$handleSupportEPButtonClick(minecraft, screen));
            euphoriaPatcher$addButtonToRow(utilityButtons, supportEPButton, 66);
            euphoriaPatcher$debugLog("Successfully added Iris EP button (Yarn)");
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Error in addEPIrisButton: " + e.getMessage());
            euphoriaPatcher$debugLog(EuphoriaLogger.getStackTrace(e));
        }
    }

    @Unique
    private void euphoriaPatcher$renderTooltip(DrawContext guiGraphics, Object utilityButtons, Object resetButton) throws Exception {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;

        Object children = utilityButtons.getClass().getMethod("children").invoke(utilityButtons);
        Iterable<?> childrenIterable = (Iterable<?>) children;

        Class<?> textButtonElementClass = Class.forName("net.irisshaders.iris.gui.element.IrisElementRow$TextButtonElement");

        for (Object child : childrenIterable) {
            if (textButtonElementClass.isInstance(child) && child != resetButton) {
                Element elem = (Element) child;
                boolean isHovered = (boolean) child.getClass().getMethod("isHovered").invoke(child);
                boolean isFocused = elem.isFocused();

                if (isHovered || isFocused) {
                    // Track hover time and set permanent flag when threshold is reached
                    if (!euphoriaPatcher$hasShownExtendedTooltip) {
                        long currentTime = System.currentTimeMillis();
                        if (!euphoriaPatcher$isCurrentlyHovering) {
                            euphoriaPatcher$buttonHoverStartTime = currentTime;
                            euphoriaPatcher$isCurrentlyHovering = true;
                        }
                        long hoverDuration = currentTime - euphoriaPatcher$buttonHoverStartTime;
                        if (hoverDuration >= 1500) {
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
                    Formatting colorFormatting;

                    if (isUpdateAvailable) { // Has higher priority over shift key
                        tooltipString = "Update Euphoria Patches!";
                        colorFormatting = Formatting.GREEN;
                    } else if (shiftDown) {
                        tooltipString = "Remove Support Button? Requires re-entering the menu to show effect";
                        colorFormatting = Formatting.RED;
                    } else {
                        String removeString = "";
                        if (euphoriaPatcher$hasShownExtendedTooltip) {
                            removeString = " (SHIFT Click to Remove)";
                        }
                        tooltipString = "Support Euphoria Patches!" + removeString;
                        colorFormatting = Formatting.LIGHT_PURPLE;
                    }

                    MutableText tooltipText = Text.literal(tooltipString).formatted(colorFormatting);
                    // Get ScreenRect from Iris button
                    Object rect = child.getClass().getMethod("method_48202").invoke(child);
                    int rightBound = (int) rect.getClass().getMethod("method_48255", NavigationDirection.class).invoke(rect, NavigationDirection.RIGHT);

                    // Get y position
                    Object position = rect.getClass().getMethod("comp_1195").invoke(rect);
                    int yPos = (int) position.getClass().getMethod("comp_1194").invoke(position);
                    int textWidth = font.getWidth(tooltipText);
                    int tooltipX = rightBound - (textWidth + 10);
                    int tooltipY = yPos - 16;

                    final int finalTooltipX = tooltipX;
                    final int finalTooltipY = tooltipY;

                    Class<?> shaderPackScreenClass = Class.forName("net.irisshaders.iris.gui.screen.ShaderPackScreen");
                    Object renderQueue = shaderPackScreenClass.getField("TOP_LAYER_RENDER_QUEUE").get(null);
                    Class<?> guiUtilClass = Class.forName("net.irisshaders.iris.gui.GuiUtil");

                    Runnable renderTask = () -> {
                        try {
                            guiUtilClass.getMethod("drawTextPanel", TextRenderer.class, DrawContext.class, Text.class, int.class, int.class)
                                .invoke(null, font, guiGraphics, tooltipText, finalTooltipX, finalTooltipY);
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
    private void euphoriaPatcher$handleSupportEPButtonClick(MinecraftClient minecraft, Object screen) {
        try {
            euphoriaPatcher$playButtonClickSound();

            // Check if shift is held
            if (euphoriaPatcher$isShiftDown()) {
                euphoriaPatcher$debugLog("Pressed Shift while clicking EP button - removing button");
                ShaderData.save(ShaderData.SaveData.of(ShaderData.DataField.SUPPORT_EP_BUTTON, false));
                return;
            }

            // Normal click: Open support link
            ConfirmLinkScreen confirmScreen = new ConfirmLinkScreen(
                confirmed -> {
                    if (confirmed) {
                        euphoriaPatcher$openUrl();
                    }
                    minecraft.setScreen((Screen) screen);
                },
                euphoriaPatcher$EuphoriaURL,
                true
            );
            minecraft.setScreen(confirmScreen);
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
            Util.getOperatingSystem().open(euphoriaPatcher$EuphoriaURL);
            euphoriaPatcher$debugLog("Successfully opened URL");
        } catch (Exception e) {
            euphoriaPatcher$debugLog("Failed to open URL: " + e.getMessage());
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
        EuphoriaLogger.debugLog("[IrisHeaderEntryMixinYarn] " + message);
    }
}
