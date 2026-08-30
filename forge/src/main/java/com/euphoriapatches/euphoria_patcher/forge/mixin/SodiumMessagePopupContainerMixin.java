package com.euphoriapatches.euphoria_patcher.forge.mixin;

import com.euphoriapatches.euphoria_patcher.integration.sodium.SodiumDonationPrompt;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// //No iris tooltips when the donation popup is up
// And also capture mouse clicks to be able to click the buttons
@Pseudo
@Mixin(targets = "net.minecraft.client.gui.components.AbstractSelectionList", remap = false)
public class SodiumMessagePopupContainerMixin {

    @Dynamic
    @Inject(method = "m_6375_(DDI)Z", at = @At("HEAD"), cancellable = true, require = 0)   // AbstractSelectionList#mouseClicked
    private void euphoriaPatcher$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (SodiumDonationPrompt.forwardInput(new Class<?>[]{double.class, double.class, int.class}, mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Dynamic
    @ModifyVariable(method = "m_88315_", at = @At("HEAD"), ordinal = 0, argsOnly = true, remap = false, require = 0)
    private int euphoriaPatcher$hideHoverX(int mouseX) {
        return SodiumDonationPrompt.isShowing() ? -1 : mouseX;
    }

    @Dynamic
    @ModifyVariable(method = "m_88315_", at = @At("HEAD"), ordinal = 1, argsOnly = true, remap = false, require = 0)
    private int euphoriaPatcher$hideHoverY(int mouseY) {
        return SodiumDonationPrompt.isShowing() ? -1 : mouseY;
    }
}
