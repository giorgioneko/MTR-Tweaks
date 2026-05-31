package com.giorg.mtr_tweaks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(org.mtr.mod.render.RenderVehicleHelper.class)
public class RenderVehicleHelperMixin {

    /**
     * MTR's default boxContains method only allows a Y deviation of 0.75 blocks.
     * In Vivecraft, the player's Y position can be offset by their physical height (up to 2 meters).
     * This causes the player to immediately dismount the vehicle or fail to board it because their head
     * is outside the 0.75 vertical bounding box of the floor.
     * We overwrite boxContains to relax the Y padding from 0.75 to 3.0.
     */
    @Inject(method = "boxContains", at = @At("HEAD"), remap = false, cancellable = true)
    private static void mtrTweaks_boxContains(org.mtr.mapping.holder.Box box, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        boolean xMatch = org.mtr.core.tool.Utilities.isBetween(x, box.getMinXMapped(), box.getMaxXMapped());
        boolean zMatch = org.mtr.core.tool.Utilities.isBetween(z, box.getMinZMapped(), box.getMaxZMapped());
        // Relax Y padding to 3.0 blocks so standing players in VR are still "inside" the floor
        boolean yMatch = org.mtr.core.tool.Utilities.isBetween(y, box.getMinYMapped(), box.getMaxYMapped(), 3.0);
        
        if (xMatch && yMatch && zMatch) {
            cir.setReturnValue(true);
        } else {
            cir.setReturnValue(false);
        }
    }
}
