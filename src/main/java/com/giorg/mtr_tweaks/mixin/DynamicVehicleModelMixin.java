package com.giorg.mtr_tweaks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.mtr.mod.render.DynamicVehicleModel;
import org.mtr.mod.render.StoredMatrixTransformations;
import org.mtr.mod.data.VehicleExtension;
import org.mtr.core.data.TransportMode;

@Mixin(value = DynamicVehicleModel.class, remap = false)
public class DynamicVehicleModelMixin {

    @Inject(method = "render(Lorg/mtr/mod/render/StoredMatrixTransformations;Lorg/mtr/mod/data/VehicleExtension;I[IILorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;Z)V", at = @At("HEAD"), remap = false)
    private void mtrTweaks_onRender(
            StoredMatrixTransformations storedMatrixTransformations,
            VehicleExtension vehicle,
            int carNumber,
            int[] scrollingDisplayIndexTracker,
            int light,
            org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList openDoorways,
            boolean fromResourcePackCreator,
            CallbackInfo ci) {

        if (vehicle == null || storedMatrixTransformations == null) return;

        // Skip if this transformations object was already processed in this render frame pass
        if (storedMatrixTransformations instanceof com.giorg.mtr_tweaks.IPitchedTransformations) {
            com.giorg.mtr_tweaks.IPitchedTransformations pitched = (com.giorg.mtr_tweaks.IPitchedTransformations) storedMatrixTransformations;
            if (pitched.mtrTweaks_isPitched()) {
                return;
            }
            pitched.mtrTweaks_setPitched(true);
        }

        if (vehicle.getTransportMode() != TransportMode.AIRPLANE) {
            return;
        }

        // Apply pitch adjustment based on vertical bogie slope
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

                    // Leading and trailing bogies in the direction of travel
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

                    if (horizontalDist > 0.1) {
                        double currentSlopePitchDegrees = Math.toDegrees(Math.atan2(dy, horizontalDist));
                        
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

                        double extraPitch = 0.0;
                        double targetPitch = 0.0;
                        if (dy > 0.05) {
                            // Climbing (front is higher than rear)
                            targetPitch = com.giorg.mtr_tweaks.MTRTweaks.getClimbPitch(depotName);
                            extraPitch = -(targetPitch - currentSlopePitchDegrees);
                        } else if (dy < -0.05) {
                            // Descending (front is lower than rear)
                            targetPitch = com.giorg.mtr_tweaks.MTRTweaks.getLandPitch(depotName);
                            extraPitch = -(targetPitch - currentSlopePitchDegrees);
                        }

                        if (Math.abs(extraPitch) > 0.01) {
                            final double finalExtraPitch = extraPitch;
                            storedMatrixTransformations.add(graphicsHolder -> 
                                graphicsHolder.rotateXDegrees((float) finalExtraPitch)
                            );
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }
}
