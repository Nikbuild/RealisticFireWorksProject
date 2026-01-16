package com.nick.realisticfirework.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the world-space position of sparkler burning tips.
 * Updated during rendering (client-side) and used for dynamic lighting.
 *
 * The sparkler tip position is calculated from the transformation matrix
 * provided by the rendering pipeline, which includes all transformations
 * from model-space to world-space (player position, arm position, item transforms).
 */
public class SparklerTipPositionTracker {

    // Store the last known tip position for each sparkler (by UUID)
    private static final Map<UUID, Vec3> tipPositions = new ConcurrentHashMap<>();

    // Store the last update time to detect stale positions
    private static final Map<UUID, Long> lastUpdateTimes = new ConcurrentHashMap<>();

    // Position is considered stale after this many milliseconds
    private static final long STALE_THRESHOLD_MS = 500;

    // Sparkler model constants
    private static final float STICK_BASE_Y = 0.1f;
    private static final float STICK_TIP_Y = 0.9f;
    private static final float STICK_CENTER_X = 0.5f;
    private static final float STICK_CENTER_Z = 0.5f;
    private static final float TOTAL_HEIGHT = STICK_TIP_Y - STICK_BASE_Y;

    /**
     * Calculate and store the world-space position of the sparkler's burning tip.
     * Called from SparklerSpecialRenderer during rendering.
     *
     * @param sparklerUUID The unique ID of this sparkler instance
     * @param burnProgress How far the sparkler has burned (0.0 = unburned, 1.0 = fully burned)
     * @param modelToWorldMatrix The transformation matrix from model-space to world-space
     */
    public static void updateTipPosition(UUID sparklerUUID, float burnProgress, Matrix4f modelToWorldMatrix) {
        // Calculate the burn point Y position in model-space
        // As the sparkler burns, the tip moves down from STICK_TIP_Y towards STICK_BASE_Y
        float burnY = STICK_TIP_Y - burnProgress * TOTAL_HEIGHT;

        // Create the burn point in model-space coordinates
        Vector3f tipModel = new Vector3f(STICK_CENTER_X, burnY, STICK_CENTER_Z);

        // Transform from model-space to world-space using the provided matrix
        // This matrix includes: player position + player rotation + arm position + item transforms
        modelToWorldMatrix.transformPosition(tipModel);

        // Store the world-space position
        Vec3 worldPos = new Vec3(tipModel.x, tipModel.y, tipModel.z);
        tipPositions.put(sparklerUUID, worldPos);
        lastUpdateTimes.put(sparklerUUID, System.currentTimeMillis());
    }

    /**
     * Get the last known world-space position of a sparkler's burning tip.
     *
     * @param sparklerUUID The unique ID of the sparkler
     * @return The world-space position, or null if not tracked or stale
     */
    public static Vec3 getTipPosition(UUID sparklerUUID) {
        Long lastUpdate = lastUpdateTimes.get(sparklerUUID);
        if (lastUpdate == null) {
            return null;
        }

        // Check if the position is stale (sparkler not being rendered)
        if (System.currentTimeMillis() - lastUpdate > STALE_THRESHOLD_MS) {
            // Clean up stale entry
            tipPositions.remove(sparklerUUID);
            lastUpdateTimes.remove(sparklerUUID);
            return null;
        }

        return tipPositions.get(sparklerUUID);
    }

    /**
     * Get the block position for light placement based on the tip position.
     *
     * @param sparklerUUID The unique ID of the sparkler
     * @return The BlockPos for light placement, or null if position unknown
     */
    public static BlockPos getTipBlockPos(UUID sparklerUUID) {
        Vec3 tipPos = getTipPosition(sparklerUUID);
        if (tipPos == null) {
            return null;
        }
        return BlockPos.containing(tipPos);
    }

    /**
     * Remove tracking for a sparkler (when it burns out or is unequipped).
     *
     * @param sparklerUUID The unique ID of the sparkler
     */
    public static void removeTipPosition(UUID sparklerUUID) {
        tipPositions.remove(sparklerUUID);
        lastUpdateTimes.remove(sparklerUUID);
    }

    /**
     * Clear all tracked positions.
     */
    public static void clearAll() {
        tipPositions.clear();
        lastUpdateTimes.clear();
    }

    /**
     * Check if we have a valid (non-stale) position for a sparkler.
     *
     * @param sparklerUUID The unique ID of the sparkler
     * @return true if we have a valid position
     */
    public static boolean hasValidPosition(UUID sparklerUUID) {
        return getTipPosition(sparklerUUID) != null;
    }

    /**
     * Get the number of currently tracked sparklers.
     *
     * @return The count of tracked sparklers
     */
    public static int getTrackedCount() {
        return tipPositions.size();
    }
}
