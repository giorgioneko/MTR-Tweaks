package com.giorg.mtr_tweaks;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mtr.core.data.Position;
import org.mtr.core.operation.DataRequest;
import org.mtr.mod.Init;
import org.mtr.mod.InitClient;
import org.mtr.mod.block.BlockNode;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.packet.PacketRequestData;

/**
 * MTR-Tweaks
 *
 * Current fixes (without touching the main MTR mod):
 * 1. Vivecraft Compatibility  — VehicleRidingMovementMixin.java
 * 2. Missing Track Auto-Resync — handled here via a Forge ClientTickEvent
 */
@Mod("mtr_tweaks")
public class MTRTweaks {

    public static final Logger LOGGER = LogManager.getLogger("mtr_tweaks");

    private static final int RESYNC_INTERVAL_TICKS = 100; // ~5 seconds at 20 TPS
    private static final int SCAN_RADIUS = 48;            // blocks around the player

    private int resyncCooldown = 0;

    public MTRTweaks() {
        // Register ourselves for Forge events so our @SubscribeEvent methods are called
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("MTR-Tweaks loaded! Vivecraft fix + Track resync active.");
    }

    /**
     * Track Resync Fix — runs every client tick.
     *
     * ROOT CAUSE:
     * MTR tracks are stored as Rail objects. The server syncs rail "nodes" (the endpoint blocks)
     * and "connections" (the actual Rail data) separately. Due to chunk loading timing, sometimes
     * the BlockNode blocks arrive at the client but the Rail connection data does not.
     * The client silently skips rendering the rail, leaving invisible tracks.
     *
     * THE FIX:
     * Every ~5 seconds, scan a radius around the player for BlockNode blocks that exist in the
     * world but have no corresponding entry in MinecraftClientData.positionsToRail. If orphaned
     * nodes are found, send a DataRequest to the server to push the missing rail data again.
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        // Only run at the end of the tick to avoid double-processing
        if (event.phase != TickEvent.Phase.END) return;

        if (resyncCooldown > 0) {
            resyncCooldown--;
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;

        BlockPos playerPos = minecraft.player.blockPosition();
        MinecraftClientData clientData = MinecraftClientData.getInstance();

        boolean foundOrphan = false;

        // Scan the area around the player for orphaned node blocks.
        // Step by 4 to keep the scan cheap (we don't need to check every single block).
        outerLoop:
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx += 4) {
            for (int dy = -32; dy <= 32; dy += 4) {
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz += 4) {
                    BlockPos checkPos = playerPos.offset(dx, dy, dz);

                    // Skip unloaded chunks to avoid triggering a chunk load
                    if (!minecraft.level.isLoaded(checkPos)) continue;

                    BlockState blockState = minecraft.level.getBlockState(checkPos);

                    // Is this an MTR rail node block?
                    if (!(blockState.getBlock() instanceof BlockNode)) continue;

                    // Convert Minecraft BlockPos to MTR Position using Init helper
                    org.mtr.mapping.holder.BlockPos mtrBlockPos = Init.newBlockPos(
                        checkPos.getX(), checkPos.getY(), checkPos.getZ()
                    );
                    Position mtrPosition = Init.blockPosToPosition(mtrBlockPos);

                    // If no rail data exists for this node, it's orphaned
                    boolean hasRailData = clientData.positionsToRail.containsKey(mtrPosition)
                        && !clientData.positionsToRail.get(mtrPosition).isEmpty();

                    if (!hasRailData) {
                        foundOrphan = true;
                        break outerLoop;
                    }
                }
            }
        }

        if (foundOrphan) {
            try {
                // Build a data request centered on the player with our scan radius.
                // The server will respond with all rail data for that area.
                org.mtr.mapping.holder.BlockPos mtrPlayerPos = Init.newBlockPos(
                    playerPos.getX(), playerPos.getY(), playerPos.getZ()
                );
                Position playerMtrPosition = Init.blockPosToPosition(mtrPlayerPos);

                DataRequest dataRequest = new DataRequest(
                    minecraft.player.getUUID(),
                    playerMtrPosition,
                    (long) SCAN_RADIUS
                );
                InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketRequestData(dataRequest));
                LOGGER.debug("MTR-Tweaks: Requested track data resync (found orphaned node blocks)");
            } catch (Exception e) {
                // DataRequest API changed between MTR versions — log and skip gracefully
                LOGGER.debug("MTR-Tweaks: Track resync skipped (DataRequest API mismatch): " + e.getMessage());
            }

            // Cooldown regardless of success/failure — don't hammer the server
            resyncCooldown = RESYNC_INTERVAL_TICKS;
        }
    }
}
