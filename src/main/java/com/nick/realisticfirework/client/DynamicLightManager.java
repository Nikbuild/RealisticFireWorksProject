package com.nick.realisticfirework.client;

import com.nick.realisticfirework.data.SparklerData;
import com.nick.realisticfirework.items.ItemSparkler;
import com.nick.realisticfirework.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.nick.realisticfirework.RealisticFireworkMod;

/**
 * Manages dynamic lighting for held sparklers by placing and removing invisible light blocks.
 *
 * This system tracks the ACTUAL world-space position of the sparkler's burning tip,
 * calculated from the rendering transformation matrix. This gives accurate lighting
 * that follows the item position rather than just the player position.
 *
 * The tip position is updated during rendering by SparklerSpecialRenderer via
 * SparklerTipPositionTracker, and consumed here for light block placement.
 */
public class DynamicLightManager {

    // Track which light blocks we've placed for each sparkler (by sparkler UUID)
    private static final Map<UUID, Set<BlockPos>> sparklerLightPositions = new ConcurrentHashMap<>();

    // Track the last known tip position to avoid unnecessary updates
    private static final Map<UUID, BlockPos> lastTipPositions = new ConcurrentHashMap<>();

    // Light level for the sparkler (15 is max, like glowstone)
    private static final int SPARKLER_LIGHT_LEVEL = 15;

    // How often to update lights (in ticks) - less frequent to allow light propagation
    private static final int UPDATE_INTERVAL = 5;  // Every 5 ticks (4 times per second)

    // Minimum distance (squared) to move before updating light position
    private static final double MIN_MOVE_DISTANCE_SQ = 0.5 * 0.5;  // 0.5 blocks

    // Tick counter per sparkler
    private static final Map<UUID, Integer> tickCounters = new ConcurrentHashMap<>();

    // Fallback: Track entity positions for when tip position isn't available
    private static final Map<UUID, BlockPos> lastEntityPositions = new ConcurrentHashMap<>();

    /**
     * Update dynamic lighting for an entity holding a lit sparkler.
     * Uses the precise sparkler tip position calculated during rendering when available,
     * falls back to entity position otherwise.
     *
     * @param level The world level
     * @param entity The entity holding the sparkler
     */
    public static void updateLightForEntity(Level level, Entity entity) {
        if (level.isClientSide()) {
            return; // Only place blocks on server side
        }

        // Get the sparkler item to find its UUID
        ItemStack sparklerStack = getHeldLitSparkler(entity);
        if (sparklerStack == null || sparklerStack.isEmpty()) {
            return;
        }

        SparklerData data = sparklerStack.get(ModDataComponents.SPARKLER.get());
        if (data == null || !data.isLit()) {
            return;
        }

        // Debug: Log that we found a lit sparkler
        RealisticFireworkMod.LOGGER.debug("SPARKLER LIGHT: Found lit sparkler on entity {}", entity.getName().getString());

        UUID sparklerUUID = data.uniqueId();
        long currentTick = level.getGameTime();

        // Check if sparkler light is fully burned (delayed 3 seconds after visual burn)
        if (data.isLightFullyBurned(currentTick)) {
            removeLightsForSparkler(level, sparklerUUID);
            return;
        }

        // Calculate light level based on delayed burn progress fade
        // Light stays on 3 seconds longer than visuals
        float fadeFactor = data.getLightFadeFactor(currentTick);
        int lightLevel = (int)(SPARKLER_LIGHT_LEVEL * fadeFactor);

        // If light level is 0, remove the lights
        if (lightLevel <= 0) {
            removeLightsForSparkler(level, sparklerUUID);
            return;
        }

        // Throttle updates
        int tick = tickCounters.getOrDefault(sparklerUUID, 0) + 1;
        tickCounters.put(sparklerUUID, tick);
        if (tick % UPDATE_INTERVAL != 0) {
            return;
        }

        // Calculate position based on entity position and orientation
        // Note: SparklerTipPositionTracker is client-side only, so on server
        // we always use the calculated fallback position
        BlockPos tipPos = calculateFallbackPosition(entity);

        // Check if we've moved significantly from last position
        BlockPos lastPos = lastTipPositions.get(sparklerUUID);
        if (lastPos != null) {
            double distSq = lastPos.distSqr(tipPos);
            if (distSq < MIN_MOVE_DISTANCE_SQ) {
                // Haven't moved enough, just refresh the lights without moving them
                Set<BlockPos> existingPositions = sparklerLightPositions.get(sparklerUUID);
                if (existingPositions != null && !existingPositions.isEmpty()) {
                    // Update light levels on existing positions without moving
                    for (BlockPos pos : existingPositions) {
                        placeLight(level, pos, lightLevel);
                    }
                    return;
                }
            }
        }

        // Debug: Log entity position vs calculated tip position
        RealisticFireworkMod.LOGGER.info("SERVER LIGHT: Entity at {} -> tip at {} (moved from {})",
            entity.position(), tipPos, lastPos);

        lastTipPositions.put(sparklerUUID, tipPos);

        // Get or create the set of light positions for this sparkler
        Set<BlockPos> lightPositions = sparklerLightPositions.computeIfAbsent(sparklerUUID, k -> ConcurrentHashMap.newKeySet());

        // Calculate new light positions around the tip
        Set<BlockPos> newPositions = calculateLightPositions(level, tipPos);

        // Remove old lights that are no longer needed
        for (BlockPos oldPos : new HashSet<>(lightPositions)) {
            if (!newPositions.contains(oldPos)) {
                removeLight(level, oldPos);
                lightPositions.remove(oldPos);
            }
        }

        // Place/update lights with faded light level
        for (BlockPos newPos : newPositions) {
            placeLight(level, newPos, lightLevel);
            lightPositions.add(newPos);
        }
    }

    /**
     * Calculate the best positions for light blocks around the tip.
     * Places a CLUSTER of lights for maximum brightness - like a sun.
     */
    private static Set<BlockPos> calculateLightPositions(Level level, BlockPos tipPos) {
        Set<BlockPos> positions = new HashSet<>();

        // Place lights in a cluster around the tip for maximum effect
        // Center light
        if (canPlaceLight(level, tipPos)) {
            positions.add(tipPos);
        }

        // Surrounding lights - create a bright cluster
        BlockPos[] offsets = {
            tipPos.above(),
            tipPos.below(),
            tipPos.north(),
            tipPos.south(),
            tipPos.east(),
            tipPos.west()
        };

        for (BlockPos offset : offsets) {
            if (canPlaceLight(level, offset)) {
                positions.add(offset);
            }
        }

        // If we couldn't place the center, try to place at least something
        if (positions.isEmpty()) {
            for (BlockPos offset : BlockPos.betweenClosed(tipPos.offset(-2, -2, -2), tipPos.offset(2, 2, 2))) {
                if (canPlaceLight(level, offset)) {
                    positions.add(offset.immutable());
                    if (positions.size() >= 3) break; // Get at least 3 lights
                }
            }
        }

        return positions;
    }

    /**
     * Calculate the position where the sparkler tip should be based on player position and view.
     * This places the light at the extended hand position in front of the player.
     * Accounts for right-hand positioning with proper bias.
     */
    private static BlockPos calculateFallbackPosition(Entity entity) {
        // Get entity position (feet position)
        Vec3 entityPos = entity.position();

        // Get player's look direction
        float yaw = entity.getYRot();
        float pitch = entity.getXRot();
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);

        // Calculate direction vectors
        double cosYaw = Math.cos(yawRad);
        double sinYaw = Math.sin(yawRad);
        double cosPitch = Math.cos(pitchRad);
        double sinPitch = Math.sin(pitchRad);

        // Forward vector (where player is looking)
        double forwardX = -sinYaw * cosPitch;
        double forwardY = -sinPitch;
        double forwardZ = cosYaw * cosPitch;

        // Right vector (perpendicular to forward, in horizontal plane)
        double rightX = cosYaw;
        double rightZ = sinYaw;

        // Sparkler positioning:
        // - Forward: about 0.4 blocks in front (not fully extended, held at angle)
        // - Right: about 0.35 blocks to the right (right hand)
        // - The sparkler tip extends further based on pitch
        double forwardOffset = 0.4;    // How far in front
        double rightOffset = 0.35;     // How far to the right (main hand)
        double sparklerExtend = 0.5;   // Additional reach from sparkler angle

        // When looking down, sparkler tip goes more forward
        // When looking up, sparkler tip goes more up
        double pitchFactor = Math.max(0, -sinPitch);  // 0 when level, 1 when looking straight down

        // Eye height offset (player eyes are at ~1.62 blocks above feet)
        double eyeHeight = 1.4;

        // Calculate final position
        double tipX = entityPos.x
            + forwardX * (forwardOffset + sparklerExtend * cosPitch)  // Forward component
            + rightX * rightOffset;                                     // Right hand offset

        double tipY = entityPos.y + eyeHeight
            + forwardY * sparklerExtend                                // Vertical from looking up/down
            - 0.2;                                                      // Slightly below eye level (hand position)

        double tipZ = entityPos.z
            + forwardZ * (forwardOffset + sparklerExtend * cosPitch)  // Forward component
            + rightZ * rightOffset;                                     // Right hand offset

        return BlockPos.containing(tipX, tipY, tipZ);
    }

    /**
     * Get the lit sparkler item from an entity's hands.
     */
    private static ItemStack getHeldLitSparkler(Entity entity) {
        if (!(entity instanceof Player player)) {
            return null;
        }

        // Check main hand first
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof ItemSparkler && ItemSparkler.isLit(mainHand)) {
            return mainHand;
        }

        // Check offhand
        ItemStack offHand = player.getOffhandItem();
        if (offHand.getItem() instanceof ItemSparkler && ItemSparkler.isLit(offHand)) {
            return offHand;
        }

        return null;
    }

    /**
     * Remove all dynamic lights for an entity (when sparkler goes out or is unequipped).
     */
    public static void removeLightsForEntity(Level level, Entity entity) {
        if (level == null || level.isClientSide()) {
            return;
        }

        // Get all sparklers this entity might have had
        ItemStack sparkler = getHeldLitSparkler(entity);
        if (sparkler != null && !sparkler.isEmpty()) {
            SparklerData data = sparkler.get(ModDataComponents.SPARKLER.get());
            if (data != null) {
                removeLightsForSparkler(level, data.uniqueId());
                return;
            }
        }

        // If we can't find the specific sparkler, clean up any lights associated with entity position
        // This handles cases where the item was already removed
        cleanupLightsNearEntity(level, entity);
    }

    /**
     * Remove lights for a specific sparkler by UUID.
     */
    public static void removeLightsForSparkler(Level level, UUID sparklerUUID) {
        if (level == null || level.isClientSide()) {
            return;
        }

        Set<BlockPos> lightPositions = sparklerLightPositions.remove(sparklerUUID);
        lastTipPositions.remove(sparklerUUID);
        tickCounters.remove(sparklerUUID);
        SparklerTipPositionTracker.removeTipPosition(sparklerUUID);

        if (lightPositions != null) {
            for (BlockPos pos : lightPositions) {
                removeLight(level, pos);
            }
        }
    }

    /**
     * Clean up any lights near an entity (fallback cleanup).
     */
    private static void cleanupLightsNearEntity(Level level, Entity entity) {
        BlockPos entityPos = entity.blockPosition();

        // Search for and remove any tracked lights near the entity
        Iterator<Map.Entry<UUID, Set<BlockPos>>> iter = sparklerLightPositions.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<UUID, Set<BlockPos>> entry = iter.next();
            Set<BlockPos> positions = entry.getValue();

            // Check if any of these lights are near the entity
            boolean foundNearby = false;
            for (BlockPos pos : positions) {
                if (pos.distSqr(entityPos) < 25) { // Within 5 blocks
                    foundNearby = true;
                    break;
                }
            }

            if (foundNearby) {
                // Remove all lights for this sparkler
                for (BlockPos pos : positions) {
                    removeLight(level, pos);
                }
                iter.remove();
                lastTipPositions.remove(entry.getKey());
                tickCounters.remove(entry.getKey());
                SparklerTipPositionTracker.removeTipPosition(entry.getKey());
            }
        }
    }

    /**
     * Check if we can place a light block at this position.
     */
    private static boolean canPlaceLight(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        // Can place in air or if it's already our light block
        return state.isAir() || (state.getBlock() == Blocks.LIGHT);
    }

    /**
     * Place an invisible light block at the position with specified light level.
     */
    private static void placeLight(Level level, BlockPos pos, int lightLevel) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getBlock() == Blocks.LIGHT) {
            // Create light block with the specified level (0-15)
            // LightBlock uses BlockStateProperties.LEVEL for its light level property
            int clampedLevel = Math.max(0, Math.min(15, lightLevel));
            BlockState lightState = Blocks.LIGHT.defaultBlockState()
                .setValue(BlockStateProperties.LEVEL, clampedLevel);

            // Use flag 3 (update + notify neighbors) to ensure block update propagates
            level.setBlock(pos, lightState, 3);

            // Force immediate light update
            level.getLightEngine().checkBlock(pos);

            RealisticFireworkMod.LOGGER.info("SPARKLER LIGHT: Placed invisible light level {} at {}", clampedLevel, pos);
        }
    }

    /**
     * Place an invisible light block at the position with default light level.
     */
    private static void placeLight(Level level, BlockPos pos) {
        placeLight(level, pos, SPARKLER_LIGHT_LEVEL);
    }

    /**
     * Remove a light block we placed.
     */
    private static void removeLight(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() == Blocks.LIGHT) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.getLightEngine().checkBlock(pos);
            RealisticFireworkMod.LOGGER.info("SPARKLER LIGHT: Removed light at {}", pos);
        }
    }

    /**
     * Clean up all lights (call on world unload or mod shutdown).
     */
    public static void cleanupAllLights(Level level) {
        if (level != null && !level.isClientSide()) {
            for (Map.Entry<UUID, Set<BlockPos>> entry : sparklerLightPositions.entrySet()) {
                for (BlockPos pos : entry.getValue()) {
                    removeLight(level, pos);
                }
            }
        }

        sparklerLightPositions.clear();
        lastTipPositions.clear();
        lastEntityPositions.clear();
        tickCounters.clear();
        SparklerTipPositionTracker.clearAll();
    }

    /**
     * Check if a sparkler currently has active lights.
     */
    public static boolean hasActiveLights(UUID sparklerUUID) {
        Set<BlockPos> positions = sparklerLightPositions.get(sparklerUUID);
        return positions != null && !positions.isEmpty();
    }

    /**
     * Check if an entity has any active lights (by checking all its potential sparklers).
     */
    public static boolean entityHasActiveLights(Entity entity) {
        ItemStack sparkler = getHeldLitSparkler(entity);
        if (sparkler != null && !sparkler.isEmpty()) {
            SparklerData data = sparkler.get(ModDataComponents.SPARKLER.get());
            if (data != null) {
                return hasActiveLights(data.uniqueId());
            }
        }
        return false;
    }

    /**
     * Update dynamic lighting for a dropped item entity containing a lit sparkler.
     *
     * @param level The world level
     * @param itemEntity The dropped item entity
     */
    public static void updateLightForItemEntity(Level level, ItemEntity itemEntity) {
        if (level.isClientSide()) {
            return;
        }

        ItemStack stack = itemEntity.getItem();
        SparklerData data = stack.get(ModDataComponents.SPARKLER.get());
        if (data == null || !data.isLit()) {
            return;
        }

        UUID sparklerUUID = data.uniqueId();
        long currentTick = level.getGameTime();

        // Check if sparkler light is fully burned (delayed 3 seconds after visual burn)
        if (data.isLightFullyBurned(currentTick)) {
            removeLightsForSparkler(level, sparklerUUID);
            return;
        }

        // Calculate light level based on delayed burn progress fade
        // Light stays on 3 seconds longer than visuals
        float fadeFactor = data.getLightFadeFactor(currentTick);
        int lightLevel = (int)(SPARKLER_LIGHT_LEVEL * fadeFactor);

        // If light level is 0, remove the lights
        if (lightLevel <= 0) {
            removeLightsForSparkler(level, sparklerUUID);
            return;
        }

        // Throttle updates
        int tick = tickCounters.getOrDefault(sparklerUUID, 0) + 1;
        tickCounters.put(sparklerUUID, tick);
        if (tick % UPDATE_INTERVAL != 0) {
            return;
        }

        // Use the item entity's position directly
        BlockPos itemPos = itemEntity.blockPosition();

        lastTipPositions.put(sparklerUUID, itemPos);

        // Get or create the set of light positions for this sparkler
        Set<BlockPos> lightPositions = sparklerLightPositions.computeIfAbsent(sparklerUUID, k -> ConcurrentHashMap.newKeySet());

        // Calculate new light positions
        Set<BlockPos> newPositions = calculateLightPositions(level, itemPos);

        // Remove old lights
        for (BlockPos oldPos : new HashSet<>(lightPositions)) {
            if (!newPositions.contains(oldPos)) {
                removeLight(level, oldPos);
                lightPositions.remove(oldPos);
            }
        }

        // Place/update lights with faded light level
        for (BlockPos newPos : newPositions) {
            placeLight(level, newPos, lightLevel);
            lightPositions.add(newPos);
        }
    }
}
