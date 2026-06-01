package com.giorg.mtr_tweaks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.mtr.mod.render.StoredMatrixTransformations;
import com.giorg.mtr_tweaks.IPitchedTransformations;

@Mixin(value = StoredMatrixTransformations.class, remap = false)
public class StoredMatrixTransformationsMixin implements IPitchedTransformations {

    @Unique
    private boolean mtrTweaks_isPitched = false;

    @Override
    public boolean mtrTweaks_isPitched() {
        return this.mtrTweaks_isPitched;
    }

    @Override
    public void mtrTweaks_setPitched(boolean pitched) {
        this.mtrTweaks_isPitched = pitched;
    }
}
