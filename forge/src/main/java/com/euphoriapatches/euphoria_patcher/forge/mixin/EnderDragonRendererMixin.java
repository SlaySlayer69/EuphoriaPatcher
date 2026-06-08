package com.euphoriapatches.euphoria_patcher.forge.mixin;

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

import static com.euphoriapatches.euphoria_patcher.forge.mixin.EuphoriaMixinPlugin.ENDER_DRAGON_RENDERER_CLASS;

// This Mixin is responsible for giving the dragon death beams an entity ID
// This one works from 1.18.2 (or earlier, didn't test) until 1.21.10
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

    // Before 1.21 the death beam code is directly in the render method and uses the lightning render layer:
    //VertexConsumer consumer = vertexConsumerProvider.getBuffer(RenderLayer.getLightning());
    @Inject(method = "m_7392_", at = @At(value = "INVOKE", target = "net/minecraft/client/renderer/RenderType.m_110502_()Lnet/minecraft/client/renderer/RenderType;", shift = At.Shift.AFTER), remap = false, require = 0)
    private void euphoriaPatcher$installDeathRayHooksPre121(CallbackInfo ci) {
        if (euphoriaPatcher$injectedAlready) return;
        euphoriaPatcher$injectedAlready = true;

        try {
            Object lightning = ReflectionUtils.getFieldValue("net.minecraft.client.renderer.RenderType", "f_110387_");

            boolean wrapped = euphoriaPatcher$wrapActions(lightning);
            euphoriaPatcher$debugLog("installed death-ray entity id hooks on the lightning render layer: " + wrapped);
        } catch (Throwable t) {
            euphoriaPatcher$debugLog("install failed: " + t);
        }
    }

    @Unique
    private static boolean euphoriaPatcher$wrapActions(Object renderLayer) {
        if (renderLayer == null) return false;
        try {
            Class<?> renderPhase = Class.forName("net.minecraft.client.renderer.RenderStateShard");
            Field beginField = renderPhase.getDeclaredField("f_110131_");
            Field endField = renderPhase.getDeclaredField("f_110132_");
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
            if (worldRenderingSettings == null)
                worldRenderingSettings = ReflectionUtils.getFieldValue("net.coderbot.iris.block_rendering.BlockRenderingSettings", "INSTANCE");
            if (worldRenderingSettings == null) return false;

            Object entityIds = worldRenderingSettings.getClass().getMethod("getEntityIds").invoke(worldRenderingSettings);
            if (entityIds == null) return false;

            Object capturedRenderingState = ReflectionUtils.getFieldValue("net.irisshaders.iris.uniforms.CapturedRenderingState", "INSTANCE");
            if (capturedRenderingState == null)
                capturedRenderingState = ReflectionUtils.getFieldValue("net.coderbot.iris.uniforms.CapturedRenderingState", "INSTANCE");
            if (capturedRenderingState == null) return false;

            String namespacedIdClass = ReflectionUtils.checkClassExists("net.irisshaders.iris.shaderpack.materialmap.NamespacedId")
                    ? "net.irisshaders.iris.shaderpack.materialmap.NamespacedId"
                    : "net.coderbot.iris.shaderpack.materialmap.NamespacedId";
            Object deathRaysId = Class.forName(namespacedIdClass)
                    .getConstructor(String.class, String.class).newInstance("minecraft", euphoriaPatcher$DEATH_RAYS_ID);

            int id = (int) entityIds.getClass().getMethod("applyAsInt", Object.class).invoke(entityIds, deathRaysId);

            String gbufferProgramsClass = ReflectionUtils.checkClassExists("net.irisshaders.iris.layer.GbufferPrograms")
                    ? "net.irisshaders.iris.layer.GbufferPrograms"
                    : "net.coderbot.iris.layer.GbufferPrograms";

            euphoriaPatcher$getCurrentEntity = capturedRenderingState.getClass().getMethod("getCurrentRenderedEntity");
            euphoriaPatcher$setCurrentEntity = capturedRenderingState.getClass().getMethod("setCurrentEntity", int.class);
            euphoriaPatcher$runFallbackListener = Class.forName(gbufferProgramsClass).getMethod("runFallbackEntityListener");

            euphoriaPatcher$capturedRenderingState = capturedRenderingState;
            euphoriaPatcher$deathRaysEntityId = id;
            euphoriaPatcher$debugLog("resolved dragon_death_rays entity id = " + id);
            return true;
        } catch (Throwable t) {
            euphoriaPatcher$debugLog("resolveIris error: " + t);
            return false;
        }
    }

    @Unique
    private static void euphoriaPatcher$debugLog(String message) {
        EuphoriaLogger.debugLog("[EnderDragonRendererMixin] " + message);
    }
}
