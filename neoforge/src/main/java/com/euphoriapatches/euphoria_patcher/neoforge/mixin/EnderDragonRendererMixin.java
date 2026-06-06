package com.euphoriapatches.euphoria_patcher.neoforge.mixin;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.ReflectionUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static com.euphoriapatches.euphoria_patcher.neoforge.mixin.EuphoriaMixinPlugin.ENDER_DRAGON_RENDERER_CLASS;
import static com.euphoriapatches.euphoria_patcher.neoforge.mixin.EuphoriaMixinPlugin.RENDER_STATE_SHARD_CLASS;

// This Mixin is responsible for giving the dragon death beams an entity ID
// This one works from 1.21-1.21.10
@SuppressWarnings("MixinAnnotationTarget")
@Pseudo
@Mixin(targets = ENDER_DRAGON_RENDERER_CLASS)
public class EnderDragonRendererMixin {

    @Unique
    private static final String euphoriaPatcher$DEATH_RAYS_ID = "dragon_death_rays";

    @Unique
    private static boolean euphoriaPatcher$injectedAlready = false;
    @Unique
    private static Object euphoriaPatcher$capturedRenderingState = null;
    @Unique
    private static Method euphoriaPatcher$getCurrentEntity = null;
    @Unique
    private static Method euphoriaPatcher$setCurrentEntity = null;
    @Unique
    private static Method euphoriaPatcher$runFallbackListener = null;
    @Unique
    private static int euphoriaPatcher$deathRaysEntityId = Integer.MIN_VALUE;
    @Unique
    private static int euphoriaPatcher$depth = 0;
    @Unique
    private static int euphoriaPatcher$backupEntityId = -1;

    @Inject(method = {"renderRays" ,"submitRays"} , at = @At("HEAD"), remap = false, require = 0)
    private static void euphoriaPatcher$installDeathRayHooks(CallbackInfo ci) {
        if (euphoriaPatcher$injectedAlready) return;
        euphoriaPatcher$injectedAlready = true;

        try {
            Object dragonRays = ReflectionUtils.getFieldValue("net.minecraft.client.renderer.RenderType", "DRAGON_RAYS");
            Object dragonRaysDepth = ReflectionUtils.getFieldValue("net.minecraft.client.renderer.RenderType", "DRAGON_RAYS_DEPTH");

            int wrapped = 0;
            wrapped += euphoriaPatcher$wrapActions(dragonRays) ? 1 : 0;
            wrapped += euphoriaPatcher$wrapActions(dragonRaysDepth) ? 1 : 0;
            euphoriaPatcher$debugLog("installed death-ray entity id hooks on " + wrapped + " layer(s)");
        } catch (Throwable t) {
            euphoriaPatcher$debugLog("install failed: " + t);
        }
    }

    @Unique
    private static boolean euphoriaPatcher$wrapActions(Object renderLayer) {
        if (renderLayer == null) return false;
        try {
            Class<?> renderPhase = Class.forName(RENDER_STATE_SHARD_CLASS);
            Field beginField = renderPhase.getDeclaredField("setupState");
            Field endField = renderPhase.getDeclaredField("clearState");
            beginField.setAccessible(true);
            endField.setAccessible(true);

            final Runnable originalBegin = (Runnable) beginField.get(renderLayer);
            final Runnable originalEnd = (Runnable) endField.get(renderLayer);

            beginField.set(renderLayer, (Runnable) () -> {
                if (originalBegin != null) originalBegin.run();
                euphoriaPatcher$beginDeathRays();
            });
            endField.set(renderLayer, (Runnable) () -> {
                euphoriaPatcher$endDeathRays();
                if (originalEnd != null) originalEnd.run();
            });
            return true;
        } catch (Throwable t) {
            euphoriaPatcher$debugLog("wrapActions failed: " + t);
            return false;
        }
    }

    @Unique
    private static void euphoriaPatcher$beginDeathRays() {
        if (!euphoriaPatcher$resolveIris()) return;
        try {
            if (euphoriaPatcher$depth == 0) {
                euphoriaPatcher$backupEntityId = (int) euphoriaPatcher$getCurrentEntity.invoke(euphoriaPatcher$capturedRenderingState);
            }
            euphoriaPatcher$depth++;
            euphoriaPatcher$setCurrentEntity.invoke(euphoriaPatcher$capturedRenderingState, euphoriaPatcher$deathRaysEntityId);
            euphoriaPatcher$runFallbackListener.invoke(null);
        } catch (Throwable t) {
            euphoriaPatcher$debugLog("begin error: " + t);
        }
    }

    @Unique
    private static void euphoriaPatcher$endDeathRays() {
        if (euphoriaPatcher$capturedRenderingState == null || euphoriaPatcher$depth == 0) return;
        try {
            euphoriaPatcher$depth--;
            if (euphoriaPatcher$depth <= 0) {
                euphoriaPatcher$depth = 0;
                euphoriaPatcher$setCurrentEntity.invoke(euphoriaPatcher$capturedRenderingState, euphoriaPatcher$backupEntityId);
                euphoriaPatcher$runFallbackListener.invoke(null);
            }
        } catch (Throwable t) {
            euphoriaPatcher$debugLog("end error: " + t);
        }
    }

    @Unique
    private static boolean euphoriaPatcher$resolveIris() {
        if (euphoriaPatcher$deathRaysEntityId != Integer.MIN_VALUE && euphoriaPatcher$capturedRenderingState != null) {
            return true;
        }
        try {
            Object worldRenderingSettings = ReflectionUtils.getFieldValue("net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings", "INSTANCE");
            if (worldRenderingSettings == null) return false;
            Object entityIds = worldRenderingSettings.getClass().getMethod("getEntityIds").invoke(worldRenderingSettings);
            if (entityIds == null) return false;

            Object capturedRenderingState = ReflectionUtils.getFieldValue("net.irisshaders.iris.uniforms.CapturedRenderingState", "INSTANCE");
            if (capturedRenderingState == null) return false;

            Object deathRaysId = Class.forName("net.irisshaders.iris.shaderpack.materialmap.NamespacedId")
                    .getConstructor(String.class, String.class).newInstance("minecraft", euphoriaPatcher$DEATH_RAYS_ID);
            int id = (int) entityIds.getClass().getMethod("applyAsInt", Object.class).invoke(entityIds, deathRaysId);

            euphoriaPatcher$getCurrentEntity = capturedRenderingState.getClass().getMethod("getCurrentRenderedEntity");
            euphoriaPatcher$setCurrentEntity = capturedRenderingState.getClass().getMethod("setCurrentEntity", int.class);
            euphoriaPatcher$runFallbackListener = Class.forName("net.irisshaders.iris.layer.GbufferPrograms").getMethod("runFallbackEntityListener");

            euphoriaPatcher$capturedRenderingState = capturedRenderingState;
            euphoriaPatcher$deathRaysEntityId = id;
            euphoriaPatcher$debugLog("resolved dragon_death_rays entity id = " + id);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Unique
    private static void euphoriaPatcher$debugLog(String message) {
        EuphoriaLogger.debugLog("[EnderDragonRendererMixinYarn] " + message);
    }
}
