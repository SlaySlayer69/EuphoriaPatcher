package com.euphoriapatches.euphoria_patcher.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import static com.euphoriapatches.euphoria_patcher.fabric.mixin.EuphoriaMixinPlugin.IRIS_ELEMENT_ROW_CLASS;

@Pseudo
@Mixin(targets = IRIS_ELEMENT_ROW_CLASS, remap = false)
public abstract class IrisElementRowMixin {

    @Shadow private int width;

    @Unique
    public int euphoriaPatcher$getWidth() {
        return this.width;
    }
}
