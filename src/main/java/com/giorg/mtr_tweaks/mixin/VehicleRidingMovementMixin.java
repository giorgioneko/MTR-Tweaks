package com.giorg.mtr_tweaks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.mtr.mod.client.VehicleRidingMovement;
import org.mtr.mod.render.PositionAndRotation;

@Mixin(value = VehicleRidingMovement.class, remap = false)
public class VehicleRidingMovementMixin {

    @ModifyVariable(
        method = "movePlayer(JJILorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;Lorg/mtr/mod/client/GangwayMovementPositions;Lorg/mtr/mod/client/GangwayMovementPositions;Lorg/mtr/mod/client/GangwayMovementPositions;Lorg/mtr/mod/render/PositionAndRotation;)V",
        at = @At("HEAD"),
        argsOnly = true,
        remap = false
    )
    private static PositionAndRotation mtrTweaks_modifyPositionAndRotation(
            PositionAndRotation positionAndRotation,
            long millisElapsed,
            long vehicleId,
            int carNumber
    ) {
        if (positionAndRotation == null) {
            return null;
        }

        // Find the vehicle with matching vehicleId
        org.mtr.mod.data.VehicleExtension vehicle = null;
        try {
            for (org.mtr.mod.data.VehicleExtension v : org.mtr.mod.client.MinecraftClientData.getInstance().vehicles) {
                if (v.getId() == vehicleId) {
                    vehicle = v;
                    break;
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        if (vehicle == null) {
            return positionAndRotation;
        }

        // Verify if it is an AIRPLANE
        if (vehicle.getTransportMode() != org.mtr.core.data.TransportMode.AIRPLANE) {
            return positionAndRotation;
        }

        // Determine if climbing or descending and get the target pitch
        try {
            org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList<?> carsAndPositions = vehicle.getVehicleCarsAndPositions();
            if (carNumber < carsAndPositions.size()) {
                org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair<?, ?> carData = 
                    (org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair<?, ?>) carsAndPositions.get(carNumber);
                org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList<?> bogiesList = 
                    (org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList<?>) carData.right();
                
                if (bogiesList != null && !bogiesList.isEmpty()) {
                    org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair<?, ?> bogiePair = 
                        (org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair<?, ?>) bogiesList.get(0);
                    org.mtr.core.tool.Vector b1 = (org.mtr.core.tool.Vector) bogiePair.left();
                    org.mtr.core.tool.Vector b2 = (org.mtr.core.tool.Vector) bogiePair.right();

                    org.mtr.core.tool.Vector frontBogie = b1;
                    org.mtr.core.tool.Vector rearBogie = b2;

                    if (vehicle.getReversed()) {
                        frontBogie = b2;
                        rearBogie = b1;
                    }

                    double dy = frontBogie.y - rearBogie.y;

                    String depotName = "";
                    try {
                        long depotId = vehicle.vehicleExtraData.getDepotId();
                        org.mtr.core.data.Depot depot = org.mtr.mod.client.MinecraftClientData.getInstance().depotIdMap.get(depotId);
                        if (depot != null) {
                            depotName = depot.getName();
                        }
                    } catch (Exception e) {
                        // Ignore
                    }

                    double targetPitch = 0.0;
                    boolean apply = false;
                    if (dy > 0.05) {
                        targetPitch = com.giorg.mtr_tweaks.MTRTweaks.getClimbPitch(depotName);
                        apply = true;
                    } else if (dy < -0.05) {
                        targetPitch = com.giorg.mtr_tweaks.MTRTweaks.getLandPitch(depotName);
                        apply = true;
                    }

                    if (apply) {
                        double targetPitchRadians = Math.toRadians(targetPitch);

                        PositionAndRotation newPos = new PositionAndRotation(
                            positionAndRotation.position,
                            positionAndRotation.yaw,
                            targetPitchRadians
                        );
                        MtrCameraTracker.cachedVehiclePitch = newPos.pitch;
                        MtrCameraTracker.cachedVehicleYaw = newPos.yaw;
                        MtrCameraTracker.lastRidingTick = System.currentTimeMillis();
                        return newPos;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        MtrCameraTracker.cachedVehiclePitch = positionAndRotation.pitch;
        MtrCameraTracker.cachedVehicleYaw = positionAndRotation.yaw;
        MtrCameraTracker.lastRidingTick = System.currentTimeMillis();
        return positionAndRotation;
    }
}
