package com.giorg.mtr_tweaks;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
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
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.FloatArgumentType;

/**
 * MTR-Tweaks
 *
 * Current fixes (without touching the main MTR mod):
 * 1. Missing Track Auto-Resync — handled here via a Forge ClientTickEvent
 * 2. Plane Pitch Controller — configure plane pitch rendering during climbing/landing
 */
@Mod("mtr_tweaks")
public class MTRTweaks {

    public static final Logger LOGGER = LogManager.getLogger("mtr_tweaks");

    private static final int RESYNC_INTERVAL_TICKS = 100; // ~5 seconds at 20 TPS
    private static final int SCAN_RADIUS = 48;            // blocks around the player

    private int resyncCooldown = 0;

    // Configurable plane pitches per depot
    public static class PitchSettings {
        public float climb = 15f;
        public float land = -10f;

        public PitchSettings(float climb, float land) {
            this.climb = climb;
            this.land = land;
        }
    }

    public static final java.util.Map<String, PitchSettings> depotPitches = new java.util.HashMap<>();
    private static final java.io.File CONFIG_FILE = new java.io.File("config/mtr_tweaks_plane_pitch.json");

    public MTRTweaks() {
        // Register ourselves for Forge events so our @SubscribeEvent methods are called
        MinecraftForge.EVENT_BUS.register(this);
        loadConfig();
        LOGGER.info("MTR-Tweaks loaded! Track resync + Plane pitch active.");
    }

    public static void loadConfig() {
        depotPitches.clear();
        if (CONFIG_FILE.exists()) {
            try (java.io.FileReader reader = new java.io.FileReader(CONFIG_FILE)) {
                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("depots")) {
                    com.google.gson.JsonObject depotsJson = json.getAsJsonObject("depots");
                    for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : depotsJson.entrySet()) {
                        String name = entry.getKey();
                        com.google.gson.JsonObject settingsObj = entry.getValue().getAsJsonObject();
                        float climb = settingsObj.has("climb") ? settingsObj.get("climb").getAsFloat() : 15f;
                        float land = settingsObj.has("land") ? settingsObj.get("land").getAsFloat() : -10f;
                        depotPitches.put(name, new PitchSettings(climb, land));
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load config", e);
            }
        }
    }

    public static void saveConfig() {
        try {
            CONFIG_FILE.getParentFile().mkdirs();
            try (java.io.FileWriter writer = new java.io.FileWriter(CONFIG_FILE)) {
                com.google.gson.JsonObject json = new com.google.gson.JsonObject();
                com.google.gson.JsonObject depotsJson = new com.google.gson.JsonObject();
                for (java.util.Map.Entry<String, PitchSettings> entry : depotPitches.entrySet()) {
                    com.google.gson.JsonObject settingsObj = new com.google.gson.JsonObject();
                    settingsObj.addProperty("climb", entry.getValue().climb);
                    settingsObj.addProperty("land", entry.getValue().land);
                    depotsJson.add(entry.getKey(), settingsObj);
                }
                json.add("depots", depotsJson);
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save config", e);
        }
    }

    public static float getClimbPitch(String depotName) {
        if (depotName == null || depotName.isEmpty()) return 15f;
        PitchSettings settings = depotPitches.get(depotName);
        return settings != null ? settings.climb : 15f;
    }

    public static float getLandPitch(String depotName) {
        if (depotName == null || depotName.isEmpty()) return -10f;
        PitchSettings settings = depotPitches.get(depotName);
        return settings != null ? settings.land : -10f;
    }

    /**
     * Track Resync Fix — runs every client tick.
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

        // Enforce the 5-second interval immediately so we do not scan every tick when no orphans are found
        resyncCooldown = RESYNC_INTERVAL_TICKS;

        BlockPos playerPos = minecraft.player.blockPosition();
        MinecraftClientData clientData = MinecraftClientData.getInstance();

        boolean foundOrphan = false;

        // Scan the area around the player for orphaned node blocks.
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
                // DataRequest API changed between MTR versions
                LOGGER.debug("MTR-Tweaks: Track resync skipped (DataRequest API mismatch): " + e.getMessage());
            }

            // Cooldown regardless of success/failure
            resyncCooldown = RESYNC_INTERVAL_TICKS;
        }
    }
}
