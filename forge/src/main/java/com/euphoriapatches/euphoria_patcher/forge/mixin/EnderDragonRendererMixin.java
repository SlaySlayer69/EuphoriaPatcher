package com.euphoriapatches.euphoria_patcher.forge.mixin;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.ReflectionUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static com.euphoriapatches.euphoria_patcher.forge.mixin.EuphoriaMixinPlugin.ENDER_DRAGON_RENDERER_CLASS;

// This Mixin is responsible for giving the dragon death beams and crystal beams an entity ID
// This one works from 1.20.1 (or earlier, didn't test) until 1.21.10
@SuppressWarnings("MixinAnnotationTarget")
@Pseudo
@Mixin(targets = ENDER_DRAGON_RENDERER_CLASS)
public class EnderDragonRendererMixin {

    @Unique private static final String euphoriaPatcher$DEATH_RAYS_ID = "dragon_death_rays";
    @Unique private static final String euphoriaPatcher$CRYSTAL_BEAM_ID = "end_crystal_beam";

    // Iris shared state
    @Unique private static boolean euphoriaPatcher$irisResolved = false;
    @Unique private static Object euphoriaPatcher$capturedRenderingState = null;
    @Unique private static Object euphoriaPatcher$entityIds = null;
    @Unique private static String euphoriaPatcher$namespacedIdClass = null;
    @Unique private static Method euphoriaPatcher$getCurrentEntity = null;
    @Unique private static Method euphoriaPatcher$setCurrentEntity = null;
    @Unique private static Method euphoriaPatcher$runFallbackListener = null;

    // Per-entity cached IDs
    @Unique private static int euphoriaPatcher$deathRaysEntityId = Integer.MIN_VALUE;
    @Unique private static int euphoriaPatcher$crystalBeamEntityId = Integer.MIN_VALUE;

    // Install flags
    @Unique private static boolean euphoriaPatcher$injectedRaysAlready = false;
    @Unique private static boolean euphoriaPatcher$injectedCrystalAlready = false;

    // Depth tracking for nested calls
    @Unique private static int euphoriaPatcher$depth = 0;
    @Unique private static int euphoriaPatcher$backupEntityId = -1;

    // Before 1.21 the death beam code is directly in the render method and uses the lightning render layer:
    // VertexConsumer consumer = vertexConsumerProvider.getBuffer(RenderLayer.getLightning());
    @Inject(method = "m_7392_", at = @At(value = "INVOKE", target = "net/minecraft/client/renderer/RenderType.m_110502_()Lnet/minecraft/client/renderer/RenderType;", shift = At.Shift.AFTER), remap = false, require = 0)
    private void euphoriaPatcher$installDeathRayHooks(CallbackInfo ci) {
        if(euphoriaPatcher$injectedRaysAlready) return;
        euphoriaPatcher$injectedRaysAlready = true;

        try {
            Object lightning = ReflectionUtils.getFieldValue("net.minecraft.client.renderer.RenderType", "f_110387_");
            boolean wrapped = euphoriaPatcher$wrapRenderLayer(lightning);
            euphoriaPatcher$debugLog("installed death-ray entity id hooks on the lightning render layer: " + wrapped);
        } catch (Throwable t) {
            euphoriaPatcher$debugLog("install failed: " + t);
        }
    }

    // Oculus does not have end_crystal_beam entity ID
    @Inject(method = "m_114187_", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;m_85836_()V"), remap = false, require = 0)
    private static void euphoriaPatcher$beginCrystalBeam(float p0, float p1, float p2, float p3, int p4, @Coerce Object poseStack, @Coerce Object bufferSource, int p7, CallbackInfo ci) {
        euphoriaPatcher$beginEntityOverride(euphoriaPatcher$CRYSTAL_BEAM_ID);
    }

    @Inject(method = "m_114187_", at = @At("RETURN"), remap = false, require = 0)
    private static void euphoriaPatcher$endCrystalBeam(float p0, float p1, float p2, float p3, int p4, @Coerce Object poseStack, @Coerce Object bufferSource, int p7, CallbackInfo ci) {
        euphoriaPatcher$endEntityOverride();
    }

    @Unique
    private static boolean euphoriaPatcher$wrapRenderLayer(Object renderLayer) {
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
                euphoriaPatcher$beginEntityOverride(EnderDragonRendererMixin.euphoriaPatcher$DEATH_RAYS_ID);
            });
            endField.set(renderLayer, (Runnable) () -> {
                euphoriaPatcher$endEntityOverride();
                if (originalEnd != null) originalEnd.run();
            });
            return true;
        } catch (Throwable t) {
            euphoriaPatcher$debugLog("wrapRenderLayer failed: " + t);
            return false;
        }
    }

    @Unique
    private static void euphoriaPatcher$beginEntityOverride(String entityName) {
        int id = euphoriaPatcher$resolveEntityId(entityName);
        if (id == Integer.MIN_VALUE || euphoriaPatcher$capturedRenderingState == null) return;
        try {
            if (euphoriaPatcher$depth == 0) {
                euphoriaPatcher$backupEntityId = (int) euphoriaPatcher$getCurrentEntity.invoke(euphoriaPatcher$capturedRenderingState);
            }
            euphoriaPatcher$depth++;
            euphoriaPatcher$setCurrentEntity.invoke(euphoriaPatcher$capturedRenderingState, id);
            euphoriaPatcher$runFallbackListener.invoke(null);
        } catch (Throwable t) {
            euphoriaPatcher$debugLog("begin error: " + t);
        }
    }

    @Unique
    private static void euphoriaPatcher$endEntityOverride() {
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
    private static boolean euphoriaPatcher$resolveIrisShared() {
        if (euphoriaPatcher$irisResolved) return true;
        try {
            Object worldRenderingSettings = ReflectionUtils.getFieldValue("net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings", "INSTANCE");
            if (worldRenderingSettings == null)
                worldRenderingSettings = ReflectionUtils.getFieldValue("net.coderbot.iris.block_rendering.BlockRenderingSettings", "INSTANCE");
            if (worldRenderingSettings == null) return false;

            Object capturedRenderingState = ReflectionUtils.getFieldValue("net.irisshaders.iris.uniforms.CapturedRenderingState", "INSTANCE");
            if (capturedRenderingState == null)
                capturedRenderingState = ReflectionUtils.getFieldValue("net.coderbot.iris.uniforms.CapturedRenderingState", "INSTANCE");
            if (capturedRenderingState == null) return false;

            Object entityIds = worldRenderingSettings.getClass().getMethod("getEntityIds").invoke(worldRenderingSettings);
            if (entityIds == null) return false;

            String namespacedIdClass = ReflectionUtils.checkClassExists("net.irisshaders.iris.shaderpack.materialmap.NamespacedId")
                    ? "net.irisshaders.iris.shaderpack.materialmap.NamespacedId"
                    : "net.coderbot.iris.shaderpack.materialmap.NamespacedId";

            String gbufferProgramsClass = ReflectionUtils.checkClassExists("net.irisshaders.iris.layer.GbufferPrograms")
                    ? "net.irisshaders.iris.layer.GbufferPrograms"
                    : "net.coderbot.iris.layer.GbufferPrograms";

            euphoriaPatcher$getCurrentEntity = capturedRenderingState.getClass().getMethod("getCurrentRenderedEntity");
            euphoriaPatcher$setCurrentEntity = capturedRenderingState.getClass().getMethod("setCurrentEntity", int.class);
            euphoriaPatcher$runFallbackListener = Class.forName(gbufferProgramsClass).getMethod("runFallbackEntityListener");
            euphoriaPatcher$capturedRenderingState = capturedRenderingState;
            euphoriaPatcher$entityIds = entityIds;
            euphoriaPatcher$namespacedIdClass = namespacedIdClass;
            euphoriaPatcher$irisResolved = true;
            return true;
        } catch (Throwable t) {
            euphoriaPatcher$debugLog("resolveIrisShared error: " + t);
            return false;
        }
    }

    @Unique
    private static int euphoriaPatcher$resolveEntityId(String entityName) {
        if (entityName.equals(euphoriaPatcher$DEATH_RAYS_ID) && euphoriaPatcher$deathRaysEntityId != Integer.MIN_VALUE)
            return euphoriaPatcher$deathRaysEntityId;
        if (entityName.equals(euphoriaPatcher$CRYSTAL_BEAM_ID) && euphoriaPatcher$crystalBeamEntityId != Integer.MIN_VALUE)
            return euphoriaPatcher$crystalBeamEntityId;

        if (!euphoriaPatcher$resolveIrisShared()) return Integer.MIN_VALUE;
        try {
            Object namespacedId = Class.forName(euphoriaPatcher$namespacedIdClass)
                    .getConstructor(String.class, String.class).newInstance("minecraft", entityName);
            int id = (int) euphoriaPatcher$entityIds.getClass().getMethod("applyAsInt", Object.class).invoke(euphoriaPatcher$entityIds, namespacedId);
            euphoriaPatcher$debugLog("resolved '" + entityName + "' entity id = " + id);

            if (entityName.equals(euphoriaPatcher$DEATH_RAYS_ID)) euphoriaPatcher$deathRaysEntityId = id;
            else if (entityName.equals(euphoriaPatcher$CRYSTAL_BEAM_ID)) euphoriaPatcher$crystalBeamEntityId = id;

            return id;
        } catch (Throwable t) {
            euphoriaPatcher$debugLog("resolveEntityId('" + entityName + "') error: " + t);
            return Integer.MIN_VALUE;
        }
    }

    @Unique
    private static void euphoriaPatcher$debugLog(String message) {
        EuphoriaLogger.debugLog("[EnderDragonRendererMixin] " + message);
    }
}
