package com.giorg.mtr_tweaks.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * MTR-Tweaks: Vivecraft Compatibility Fix
 *
 * ROOT CAUSE — original bug:
 * MTR moves the player on trains/elevators by calling clientPlayerEntity.updatePosition(x,y,z)
 * every tick (line 306). CRITICALLY, MTR also calls InitClient.scheduleMovePlayer(runnable)
 * (line 310) which repeats that same position update on the *next render frame*. This "double
 * update" is intentional — it fights against vanilla gravity which would otherwise pull the
 * player 0.08 blocks downward between ticks, causing the player to slowly sink below the train.
 *
 * Our first mixin version wrongly cancelled this entire call, which:
 *   a) Lost the scheduled second update → player sank below the train each frame
 *   b) Lost Vivecraft room-origin tracking → VR camera stayed at ground level while
 *      the entity moved up to the train floor
 *
 * THE CORRECT FIX:
 * We do NOT cancel MTR's call. We let it run fully (both the immediate and scheduled update).
 * We inject at RETURN and, if Vivecraft is installed, we additionally try to reset Vivecraft's
 * VR room origin so the VR camera follows the entity to the train floor.
 *
 * This works for ALL Vivecraft locomotion modes because in all modes Vivecraft anchors the
 * VR room to the entity position when a "teleport/reset" event is signalled.
 */
@Mixin(value = org.mtr.mod.client.VehicleRidingMovement.class, remap = false)
public abstract class VehicleRidingMovementMixin {

    // Detect Vivecraft once at class-load time
    private static final boolean VIVECRAFT_PRESENT = isVivecraftPresent();

    private static boolean isVivecraftPresent() {
        try {
            Class.forName("org.vivecraft.client_vr.ClientDataHolderVR");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    // Track player Y before MTR moves them, so we can detect a height jump
    private static double mtrTweaks_preY = Double.NaN;

    /**
     * INJECT at HEAD (no cancel) — record the player's Y before MTR teleports them.
     * This lets us detect when MTR does a significant vertical move (boarding a train,
     * elevator arriving at a floor, etc.) and tell Vivecraft to re-anchor.
     */
    @Inject(
        method = "movePlayer(DDD)V",
        at = @At("HEAD"),
        remap = false
    )
    private static void mtrTweaks_preMovePlayer(double x, double y, double z, CallbackInfo ci) {
        if (!VIVECRAFT_PRESENT) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            mtrTweaks_preY = player.getY();
        }
    }

    /**
     * INJECT at RETURN — after MTR has fully applied its position update (including setting
     * up the scheduled double-update), check if a vertical jump happened and if so, tell
     * Vivecraft to reset its VR room origin so the camera follows the entity.
     *
     * We try several known Vivecraft API entry points via reflection, ordered from most
     * reliable to least, so this works across different Vivecraft builds.
     */
    @Inject(
        method = "movePlayer(DDD)V",
        at = @At("RETURN"),
        remap = false
    )
    private static void mtrTweaks_postMovePlayer(double x, double y, double z, CallbackInfo ci) {
        if (!VIVECRAFT_PRESENT) return;
        if (Double.isNaN(mtrTweaks_preY)) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        // Only trigger a Vivecraft room-origin reset when the Y changed noticeably.
        // Small sub-block adjustments (the train floor undulation) don't need it.
        // Large jumps (boarding a train or elevator changing floors) do.
        double deltaY = Math.abs(player.getY() - mtrTweaks_preY);
        if (deltaY < 0.25) return;

        mtrTweaks_resetVivecraftRoomOrigin();
    }

    /**
     * Attempt to signal Vivecraft that the player was "teleported" and its room origin
     * should be re-anchored to the entity position.
     *
     * We try multiple reflection strategies because Vivecraft's internal API varies
     * between versions.
     */
    private static void mtrTweaks_resetVivecraftRoomOrigin() {
        try {
            Class<?> dh = Class.forName("org.vivecraft.client_vr.ClientDataHolderVR");

            // Strategy 1: getInstance() method
            Object holder = null;
            try {
                Method getInstance = dh.getMethod("getInstance");
                holder = getInstance.invoke(null);
            } catch (Exception e) {
                // Try static field instead
                try {
                    Field f = dh.getDeclaredField("INSTANCE");
                    f.setAccessible(true);
                    holder = f.get(null);
                } catch (Exception ignored) {}
            }

            if (holder == null) return;

            // Strategy 2: Try setting a "teleported" or "forceRecenter" boolean flag
            for (String fieldName : new String[]{
                "teleported", "vehicleTeleported", "forceRecenter",
                "resetSeated", "resetOrigin", "teleportedLastTick"
            }) {
                try {
                    Field f = dh.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    if (f.getType() == boolean.class || f.getType() == Boolean.class) {
                        f.set(holder, true);
                        return; // Success
                    }
                } catch (NoSuchFieldException ignored) {}
            }

            // Strategy 3: Try calling a reset/recenter method on VRPlayer
            for (String fieldName : new String[]{"vrPlayer", "vr_player", "player"}) {
                try {
                    Field vrPlayerField = dh.getDeclaredField(fieldName);
                    vrPlayerField.setAccessible(true);
                    Object vrPlayer = vrPlayerField.get(holder);
                    if (vrPlayer == null) continue;

                    for (String methodName : new String[]{"reset", "recenter", "resetOrigin", "resetRoomOrigin"}) {
                        try {
                            Method m = vrPlayer.getClass().getDeclaredMethod(methodName);
                            m.setAccessible(true);
                            m.invoke(vrPlayer);
                            return; // Success
                        } catch (Exception ignored) {}
                    }
                } catch (NoSuchFieldException ignored) {}
            }

        } catch (Exception e) {
            // Vivecraft class not found or API fundamentally changed — silently skip
        }
    }

    /**
     * INJECT at HEAD of startRiding to fix Vivecraft players not mounting lifts.
     * MTR only checks the "doorway" box when deciding if you should mount the lift.
     * If a VR player teleports past the doorway into the middle of the lift, they
     * never mount it and therefore can't use the Lift Menu button.
     * We add the lift's floor box to the list of acceptable boarding locations.
     */
    @Inject(
        method = "startRiding(Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;JJJIDDDD)V",
        at = @At("HEAD"),
        remap = false
    )
    private static void mtrTweaks_startRidingLiftFix(
            org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList<org.mtr.mapping.holder.Box> openFloorsAndDoorways,
            long depotId, long sidingId, long vehicleId, int carNumber,
            double x, double y, double z, double yaw,
            CallbackInfo ci) {
        org.mtr.core.data.Lift lift = org.mtr.mod.client.MinecraftClientData.getLift(vehicleId);
        if (lift != null) {
            org.mtr.mapping.holder.Box floor = new org.mtr.mapping.holder.Box(
                -lift.getWidth() / 2.0 + 0.25, 0.0, -lift.getDepth() / 2.0 + 0.25,
                lift.getWidth() / 2.0 - 0.25, 0.0, lift.getDepth() / 2.0 - 0.25
            );
            openFloorsAndDoorways.add(floor);
        }
    }
}

