package com.giorg.mtr_tweaks.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.Minecraft;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow private Entity entity;
    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow private net.minecraft.world.phys.Vec3 position;

    @Inject(method = "setup", at = @At("TAIL"))
    private void adjustMTRVehicleCamera(BlockGetter area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (!thirdPerson && this.entity == Minecraft.getInstance().player) {
            if (MtrCameraTracker.isRidingActive()) {
                double pitch = MtrCameraTracker.cachedVehiclePitch;
                if (Math.abs(pitch) > 0.001) {
                    double eyeHeight = this.entity.getEyeHeight();
                    
                    // We must apply the vehicle's pitch to the vertical eye height offset!
                    double yOffsetDiff = eyeHeight * Math.cos(pitch) - eyeHeight;
                    double zOffsetLocal = -eyeHeight * Math.sin(pitch);
                    
                    double yaw = MtrCameraTracker.cachedVehicleYaw;
                    // Rotate the local Z difference around the vehicle's yaw
                    double xWorldDiff = zOffsetLocal * Math.sin(yaw);
                    double zWorldDiff = zOffsetLocal * Math.cos(yaw);
                    
                    this.setPosition(this.position.x() + xWorldDiff, this.position.y() + yOffsetDiff, this.position.z() + zWorldDiff);
                }
            }
        }
    }
}
