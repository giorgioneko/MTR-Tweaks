package com.giorg.mtr_tweaks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.mtr.mod.client.VehicleRidingMovement;
import org.mtr.mod.render.PositionAndRotation;
import com.giorg.mtr_tweaks.MtrCameraTracker;

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

                    double dx = frontBogie.x - rearBogie.x;
                    double dy = frontBogie.y - rearBogie.y;
                    double dz = frontBogie.z - rearBogie.z;
                    double horizontalDist = Math.sqrt(dx * dx + dz * dz);

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

                    if (depotName == null || depotName.isEmpty()) {
                        try {
                            java.lang.reflect.Field sidingField = org.mtr.core.data.Vehicle.class.getDeclaredField("siding");
                            sidingField.setAccessible(true);
                            org.mtr.core.data.Siding siding = (org.mtr.core.data.Siding) sidingField.get(vehicle);
                            if (siding != null) {
                                depotName = siding.getDepotName();
                            }
                        } catch (Exception e) {
                            // Ignore
                        }
                    }

                    double targetPitch = 0.0;
                    double extraPitch = 0.0;
                    boolean apply = false;

                    if (horizontalDist > 0.1) {
                        double currentSlopePitchDegrees = Math.toDegrees(Math.atan2(dy, horizontalDist));

                        if (dy > 0.05) {
                            targetPitch = com.giorg.mtr_tweaks.MTRTweaks.getClimbPitch(depotName);
                            extraPitch = -(targetPitch - currentSlopePitchDegrees);
                            apply = true;
                        } else if (dy < -0.05) {
                            targetPitch = com.giorg.mtr_tweaks.MTRTweaks.getLandPitch(depotName);
                            extraPitch = -(targetPitch - currentSlopePitchDegrees);
                            apply = true;
                        }

                        if (apply && Math.abs(extraPitch) > 0.01) {
                            double theta = Math.toRadians(extraPitch);
                            
                            double pivotX = (frontBogie.x + rearBogie.x) / 2.0;
                            double pivotY = (frontBogie.y + rearBogie.y) / 2.0;
                            double pivotZ = (frontBogie.z + rearBogie.z) / 2.0;
                            
                            double ux = dx / horizontalDist;
                            double uz = dz / horizontalDist;
                            
                            org.mtr.core.tool.Vector p = positionAndRotation.position;
                            double dxP = p.x - pivotX;
                            double dyP = p.y - pivotY;
                            double dzP = p.z - pivotZ;
                            
                            double localZ = dxP * ux + dzP * uz;
                            double localY = dyP;
                            
                            double newLocalY = localY * Math.cos(theta) - localZ * Math.sin(theta);
                            double newLocalZ = localY * Math.sin(theta) + localZ * Math.cos(theta);
                            
                            double deltaY = newLocalY - localY;
                            double deltaZ = newLocalZ - localZ;
                            
                            double shiftX = deltaZ * ux;
                            double shiftY = deltaY;
                            double shiftZ = deltaZ * uz;
                            
                            org.mtr.core.tool.Vector shiftedPosition = new org.mtr.core.tool.Vector(
                                p.x + shiftX,
                                p.y + shiftY,
                                p.z + shiftZ
                            );

                            double targetPitchRadians = Math.toRadians(targetPitch);

                            PositionAndRotation newPos = new PositionAndRotation(
                                shiftedPosition,
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
