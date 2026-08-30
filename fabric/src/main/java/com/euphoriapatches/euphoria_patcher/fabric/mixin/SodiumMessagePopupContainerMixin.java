package com.euphoriapatches.euphoria_patcher.fabric.mixin;

import com.euphoriapatches.euphoria_patcher.integration.sodium.SodiumDonationPrompt;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

//No iris tooltips when the donation popup is up
@Pseudo
@Mixin(targets = "net.minecraft.client.gui.components.AbstractSelectionList", remap = false)
public class SodiumMessagePopupContainerMixin {

    // extractWidgetRenderState() on 26.x, rest there for good measure
    @Dynamic
    @ModifyVariable(method = {"render", "renderWidget", "extractWidgetRenderState"}, at = @At("HEAD"), ordinal = 0, argsOnly = true, remap = false, require = 0)
    private int euphoriaPatcher$hideHoverX(int mouseX) {
        return SodiumDonationPrompt.isShowing() ? -1 : mouseX;
    }

    @Dynamic
    @ModifyVariable(method = {"render", "renderWidget", "extractWidgetRenderState"}, at = @At("HEAD"), ordinal = 1, argsOnly = true, remap = false, require = 0)
    private int euphoriaPatcher$hideHoverY(int mouseY) {
        return SodiumDonationPrompt.isShowing() ? -1 : mouseY;
    }
}
