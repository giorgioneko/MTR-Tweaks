package com.giorg.mtr_tweaks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.mtr.core.path.SidingPathFinder;

@Mixin(value = SidingPathFinder.class, remap = false)
public class SidingPathFinderMixin {

    @Shadow(remap = false)
    public org.mtr.core.data.SavedRailBase<?, ?> startSavedRail;

    @Shadow(remap = false)
    public org.mtr.core.data.SavedRailBase<?, ?> endSavedRail;

    @Unique
    private String mtrTweaks_resolvedDepotName = "";

    @Inject(
        method = "<init>(Lorg/mtr/core/data/Data;Lorg/mtr/core/data/SavedRailBase;Lorg/mtr/core/data/SavedRailBase;I)V",
        at = @At("TAIL"),
        remap = false
    )
    private void mtrTweaks_onInit(org.mtr.core.data.Data data, org.mtr.core.data.SavedRailBase<?, ?> startSavedRail, org.mtr.core.data.SavedRailBase<?, ?> endSavedRail, int stopIndex, CallbackInfo ci) {
        try {
            if (data != null && data.depots != null) {
                for (org.mtr.core.data.Depot depot : data.depots) {
                    if (startSavedRail != null && startSavedRail instanceof org.mtr.core.data.Siding) {
                        if (depot.savedRails.contains((org.mtr.core.data.Siding) startSavedRail)) {
                            this.mtrTweaks_resolvedDepotName = depot.getName();
                            break;
                        }
                    }
                    if (endSavedRail != null && endSavedRail instanceof org.mtr.core.data.Siding) {
                        if (depot.savedRails.contains((org.mtr.core.data.Siding) endSavedRail)) {
                            this.mtrTweaks_resolvedDepotName = depot.getName();
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        
        if (!this.mtrTweaks_resolvedDepotName.isEmpty()) {
            com.giorg.mtr_tweaks.MTRTweaks.LOGGER.info("[MTR-Tweaks] Pathfinding SidingPathFinder initialized for Depot='" + this.mtrTweaks_resolvedDepotName + "'");
        }
    }

    @Unique
    private String mtrTweaks_getDepotName() {
        return this.mtrTweaks_resolvedDepotName;
    }

    // Modify climb ratio constants (ordinals 0 and 1)
    @ModifyConstant(
        method = "tick",
        constant = @Constant(doubleValue = 4.0D, ordinal = 0),
        remap = false
    )
    private double mtrTweaks_modifyClimbRatio0(double original) {
        return mtrTweaks_getClimbRatio();
    }

    @ModifyConstant(
        method = "tick",
        constant = @Constant(doubleValue = 4.0D, ordinal = 1),
        remap = false
    )
    private double mtrTweaks_modifyClimbRatio1(double original) {
        return mtrTweaks_getClimbRatio();
    }

    // Modify landing ratio constants (ordinals 2 and 3)
    @ModifyConstant(
        method = "tick",
        constant = @Constant(doubleValue = 4.0D, ordinal = 2),
        remap = false
    )
    private double mtrTweaks_modifyLandRatio2(double original) {
        return mtrTweaks_getLandRatio();
    }

    @ModifyConstant(
        method = "tick",
        constant = @Constant(doubleValue = 4.0D, ordinal = 3),
        remap = false
    )
    private double mtrTweaks_modifyLandRatio3(double original) {
        return mtrTweaks_getLandRatio();
    }

    @Unique
    private double mtrTweaks_getClimbRatio() {
        String depotName = mtrTweaks_getDepotName();
        float climbPitch = com.giorg.mtr_tweaks.MTRTweaks.getClimbPitch(depotName);
        if (climbPitch > 1.0f && climbPitch < 60.0f) {
            return 2.0 / Math.tan(Math.toRadians(climbPitch));
        }
        return 4.0;
    }

    @Unique
    private double mtrTweaks_getLandRatio() {
        String depotName = mtrTweaks_getDepotName();
        float landPitch = Math.abs(com.giorg.mtr_tweaks.MTRTweaks.getLandPitch(depotName));
        if (landPitch > 1.0f && landPitch < 60.0f) {
            return 2.0 / Math.tan(Math.toRadians(landPitch));
        }
        return 4.0;
    }
}
