package com.euphoriapatches.euphoria_patcher.forge.mixin;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.integration.ShaderLoader;
import com.euphoriapatches.euphoria_patcher.integration.sodium.SodiumDonationPrompt;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.services.ShaderDetector;
import com.euphoriapatches.euphoria_patcher.util.ReflectionUtils;
import com.euphoriapatches.euphoria_patcher.util.UserPersistentData;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Pseudo
@Mixin(targets = EuphoriaMixinPlugin.IRIS_SHADER_PACK_SCREEN_CLASS, remap = false)
public class SodiumMessagePopupMixin {

    @Unique
    private static final int euphoriaPatcher$MARGIN = 5;
    @Unique
    private static final int euphoriaPatcher$MIN_WIDTH = 200;
    @Unique
    private static final int euphoriaPatcher$PREFERRED_WIDTH = 250;
    @Unique
    private static final int euphoriaPatcher$LINE_HEIGHT = 11;   // font lineHeight (9) + Sodium's line spacing (2)
    @Unique
    private static final String euphoriaPatcher$URL = "https://euphoriapatches.com/support";
    @Unique
    private static boolean euphoriaPatcher$resolvedThisSession;

    @Unique
    private Object euphoriaPatcher$prompt;

    @Inject(method = "m_7856_", at = @At("TAIL"), require = 0)   // Screen#init
    private void euphoriaPatcher$initPrompt(CallbackInfo ci) {
        try {
            euphoriaPatcher$tryInitPrompt();
        } catch (Throwable t) {
            euphoriaPatcher$debugLog("Donation prompt setup failed, skipping: " + t);
        }
    }

    @Unique
    private void euphoriaPatcher$tryInitPrompt() {
        if (euphoriaPatcher$prompt != null && SodiumDonationPrompt.isShowing(euphoriaPatcher$prompt)) {
            SodiumDonationPrompt.relayout(euphoriaPatcher$prompt,
                    euphoriaPatcher$screenDim("width", "f_96543_"),
                    euphoriaPatcher$screenDim("height", "f_96544_"));
            return;
        }

        if (euphoriaPatcher$resolvedThisSession
                || !euphoriaPatcher$optionMenuOpen()
                || !euphoriaPatcher$isEuphoriaShaderActive()) {
            return;
        }
        euphoriaPatcher$resolvedThisSession = true;

        boolean sodium = SodiumDonationPrompt.isAvailable();
        boolean eligible = SodiumDonationPrompt.shouldShow();
        boolean noBlockingPacks = euphoriaPatcher$noBlockingShaderpacks();
        if (!sodium || !eligible || !noBlockingPacks) {
            euphoriaPatcher$debugLog("Donation prompt not shown this session: sodium=" + sodium
                    + ", eligible=" + eligible + ", noBlockingPacks=" + noBlockingPacks);
            return;
        }

        List<Object> lines = euphoriaPatcher$message();
        Object font = euphoriaPatcher$font();
        int boxWidth = euphoriaPatcher$boxWidth(font, lines);
        euphoriaPatcher$prompt = SodiumDonationPrompt.show(
                euphoriaPatcher$screenDim("width", "f_96543_"),
                euphoriaPatcher$screenDim("height", "f_96544_"),
                boxWidth, euphoriaPatcher$boxHeight(font, lines, boxWidth), lines,
                euphoriaPatcher$text("euphoria_patcher.donation.button"),
                () -> euphoriaPatcher$openUrl(),
                () -> UserPersistentData.save(
                        UserPersistentData.SaveData.of(UserPersistentData.DataField.CLICKED_SUPPORT_POPUP, true)));
        euphoriaPatcher$debugLog("Donation prompt " + (euphoriaPatcher$prompt != null ? "shown" : "failed to build"));
    }

    @Unique
    private boolean euphoriaPatcher$renderHookLogged;

    @Dynamic
    @Inject(method = "m_88315_", at = @At("TAIL"), require = 0)   // Screen#render
    private void euphoriaPatcher$renderPrompt(@Coerce Object guiGraphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!euphoriaPatcher$renderHookLogged) {
            euphoriaPatcher$renderHookLogged = true;
            euphoriaPatcher$debugLog("Render hook fired with " + (guiGraphics == null ? "null" : guiGraphics.getClass().getName()));
        }
        SodiumDonationPrompt.render(euphoriaPatcher$prompt, guiGraphics, mouseX, mouseY, delta);
    }

    // Mouse clicks are intercepted one level up, in SodiumMessagePopupContainerMixin - the Oculus
    // screen doesn't override mouseClicked, but its ContainerEventHandler parent does.

    @Dynamic
    @Inject(method = "m_7933_(III)Z", at = @At("HEAD"), cancellable = true, require = 0)   // Screen#keyPressed
    private void euphoriaPatcher$keyPressedLegacy(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (SodiumDonationPrompt.forwardInput(euphoriaPatcher$prompt,
                new Class<?>[]{int.class, int.class, int.class}, keyCode, scanCode, modifiers)) {
            cir.setReturnValue(true);
        }
    }

    // Minecraft glue

    @Unique
    private boolean euphoriaPatcher$optionMenuOpen() {
        return Boolean.TRUE.equals(ReflectionUtils.getFieldValue(this, "optionMenuOpen"));
    }

    @Unique
    private int euphoriaPatcher$screenDim(String mojmapName, String srgName) {
        Object value = ReflectionUtils.getFieldValue(this, mojmapName, srgName);
        return value instanceof Integer ? (Integer) value : 480;
    }

    @Unique
    private boolean euphoriaPatcher$isEuphoriaShaderActive() {
        try {
            ShaderDetector detector = EuphoriaPatcher.getInstance().getShaderDetector();
            return detector != null && detector.isEuphoriaPatchesShader(ShaderLoader.getCurrentShaderpackPath());
        } catch (Throwable t) {
            return false;
        }
    }

    @Unique
    private boolean euphoriaPatcher$noBlockingShaderpacks() {
        try {
            ShaderDetector detector = EuphoriaPatcher.getInstance().getShaderDetector();
            if (detector == null) {
                return true;
            }
            return detector.noDevVersionsInstalled()
                    && !detector.hasShaderpackNamed("ComplementaryReimagined-Testing");
        } catch (Throwable t) {
            return true;
        }
    }

    @Unique
    private List<Object> euphoriaPatcher$message() {
        List<Object> lines = new ArrayList<>();
        lines.add(euphoriaPatcher$text("euphoria_patcher.donation.line1"));
        lines.add(euphoriaPatcher$text("euphoria_patcher.donation.line2",
                euphoriaPatcher$coloured("euphoria_patcher.donation.line2.highlight", 0xe04ad4)));
        lines.add(euphoriaPatcher$text("euphoria_patcher.donation.line3",
                euphoriaPatcher$coloured("euphoria_patcher.donation.line3.highlight", 0x82dbfa)));
        lines.add(euphoriaPatcher$text("euphoria_patcher.donation.line4",
                euphoriaPatcher$coloured("euphoria_patcher.donation.line4.highlight", 0xFFA657)));
        lines.add(euphoriaPatcher$text("euphoria_patcher.donation.line5"));
        return lines;
    }

    /** {@code Component.translatable(key[, args])}, reflectively for Mojang-mapped or SRG runtimes. */
    @Unique
    private Object euphoriaPatcher$text(String key, Object... args) {
        for (String[] names : new String[][]{
                {"net.minecraft.network.chat.Component", "translatable", "translatable"},
                {"net.minecraft.network.chat.Component", "m_237115_", "m_237110_"}}) {
            try {
                Class<?> component = Class.forName(names[0]);
                return args.length == 0
                        ? component.getMethod(names[1], String.class).invoke(null, key)
                        : component.getMethod(names[2], String.class, Object[].class).invoke(null, key, args);
            } catch (Throwable ignored) {
            }
        }
        euphoriaPatcher$debugLog("Could not build translatable text for " + key);
        return null;
    }

    /**
     * Colours a translatable line. {@code MutableComponent.withColor(int)} only exists since MC 1.21;
     * older versions have to go through {@code Style} + {@code TextColor}.
     */
    @Unique
    private Object euphoriaPatcher$coloured(String key, int rgb) {
        Object text = euphoriaPatcher$text(key);
        if (text == null) {
            return null;
        }
        // MC 1.21+ : MutableComponent.withColor(int)
        Object direct = ReflectionUtils.invokeMethod(text, new String[]{"withColor"}, new Class<?>[]{int.class}, rgb);
        if (direct != null) {
            return direct;
        }
        // MC <= 1.20.x : text.setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)))
        try {
            Class<?> textColorClass = Class.forName("net.minecraft.network.chat.TextColor");
            Class<?> styleClass = Class.forName("net.minecraft.network.chat.Style");
            Object color = ReflectionUtils.invokeMethod(textColorClass, new String[]{"fromRgb", "m_131266_"},
                    new Class<?>[]{int.class}, rgb);
            Object emptyStyle = ReflectionUtils.getFieldValue(styleClass, "EMPTY", "f_131099_");
            Object style = ReflectionUtils.invokeMethod(emptyStyle, new String[]{"withColor", "m_131148_"},
                    new Class<?>[]{textColorClass}, color);
            Object styled = ReflectionUtils.invokeMethod(text, new String[]{"setStyle", "m_6270_"},
                    new Class<?>[]{styleClass}, style);
            return styled != null ? styled : text;
        } catch (Throwable t) {
            euphoriaPatcher$debugLog("Could not colour text " + key + ": " + t);
            return text;
        }
    }

    /** Box width fitted to the widest line, clamped to keep the button row fitting and stay readable. */
    @Unique
    private int euphoriaPatcher$boxWidth(Object font, List<Object> lines) {
        int widest = 0;
        for (Object line : lines) {
            widest = Math.max(widest, euphoriaPatcher$textWidth(font, line));
        }
        return Math.max(euphoriaPatcher$MIN_WIDTH,
                Math.min(euphoriaPatcher$PREFERRED_WIDTH, widest + euphoriaPatcher$MARGIN * 2));
    }

    /** Box height that fits the lines wrapped at {@code width}, plus Sodium's margins and button row. */
    @Unique
    private int euphoriaPatcher$boxHeight(Object font, List<Object> lines, int width) {
        int wrapWidth = width - euphoriaPatcher$MARGIN * 2;
        int height = euphoriaPatcher$MARGIN + euphoriaPatcher$MARGIN + 20 + 4;   // top + gap + button row + button margin
        for (Object line : lines) {
            int rows = Math.max(1, -Math.floorDiv(-euphoriaPatcher$textWidth(font, line), wrapWidth));
            height += rows * euphoriaPatcher$LINE_HEIGHT + 8;   // + Sodium's paragraph spacing
        }
        return height;
    }

    @Unique
    private Object euphoriaPatcher$font() {
        try {
            Class<?> minecraft = Class.forName("net.minecraft.client.Minecraft");
            Object instance = ReflectionUtils.invokeMethod(minecraft, new String[]{"getInstance", "m_91087_"}, new Class<?>[0]);
            return instance == null ? null : ReflectionUtils.getFieldValue(instance, "font", "f_91062_");
        } catch (Throwable t) {
            return null;
        }
    }

    @Unique
    private int euphoriaPatcher$textWidth(Object font, Object line) {
        Class<?> formattedText = ReflectionUtils.firstClass("net.minecraft.network.chat.FormattedText");
        if (font == null || line == null || formattedText == null) {
            return euphoriaPatcher$PREFERRED_WIDTH;   // unmeasurable -> assume it needs wrapping
        }
        Object width = ReflectionUtils.invokeMethod(font, new String[]{"width", "m_92852_"},
                new Class<?>[]{formattedText}, line);
        return width instanceof Integer ? (Integer) width : euphoriaPatcher$PREFERRED_WIDTH;
    }

    @Unique
    private void euphoriaPatcher$openUrl() {
        try {
            Class<?> util = Class.forName("net.minecraft.Util");
            Object platform = ReflectionUtils.invokeMethod(util, new String[]{"getPlatform", "m_137581_"}, new Class<?>[0]);
            ReflectionUtils.invokeMethod(platform, new String[]{"openUri", "m_137646_"}, new Class<?>[]{String.class}, euphoriaPatcher$URL);
        } catch (Throwable t) {
            euphoriaPatcher$debugLog("Failed to open " + euphoriaPatcher$URL + ": " + t);
        }
    }

    @Unique
    private static void euphoriaPatcher$debugLog(String message) {
        EuphoriaLogger.debugLog("[SodiumMessagePopupMixin] " + message);
    }
}
