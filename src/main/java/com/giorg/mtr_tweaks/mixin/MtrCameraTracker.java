package com.giorg.mtr_tweaks.mixin;

public class MtrCameraTracker {
    public static double cachedVehiclePitch = 0.0;
    public static double cachedVehicleYaw = 0.0;
    public static long lastRidingTick = 0;

    public static boolean isRidingActive() {
        // Allow a 1-second grace period since movePlayer might not be called every single microsecond
        return (System.currentTimeMillis() - lastRidingTick) < 1000;
    }
}
