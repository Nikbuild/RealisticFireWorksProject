package com.nick.realisticfirework.client;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Manages realistic spark spawning and lifecycle.
 * Spawns primary sparks in upper hemisphere, handles updates.
 */
public class RealisticSparkManager {

    private static final Random random = new Random();

    // Maximum number of primary sparks active at once - increased for dense spray effect
    public static final int MAX_PRIMARY_SPARKS = 200;

    // Spawn rate control - faster for more sparks
    private static final float SPAWN_INTERVAL = 0.008f;  // ~125 spawns per second base rate
    private static float spawnTimer = 0;

    /**
     * Spawn continuous sparks from burn point.
     * Creates the iconic sparkler effect with sparks shooting in upper hemisphere.
     */
    public static void spawnSparks(List<RealisticSpark> sparks, float burnY,
                                    Matrix4f modelToWorld, float deltaTime, float intensity) {
        spawnTimer += deltaTime;

        // Spawn rate based on intensity
        float adjustedInterval = SPAWN_INTERVAL / Math.max(0.1f, intensity);

        while (spawnTimer >= adjustedInterval && countPrimarySparks(sparks) < MAX_PRIMARY_SPARKS) {
            spawnTimer -= adjustedInterval;
            spawnPrimarySpark(sparks, burnY, modelToWorld, intensity);
        }
    }

    /**
     * Count only primary sparks (not including children)
     */
    private static int countPrimarySparks(List<RealisticSpark> sparks) {
        int count = 0;
        for (RealisticSpark spark : sparks) {
            if (spark.type == RealisticSpark.TYPE_PRIMARY) {
                count++;
            }
        }
        return count;
    }

    /**
     * Spawn a single primary spark shooting outward in full 360° sphere
     * Like real sparklers - sparks go in ALL directions, gravity curves them down
     */
    private static void spawnPrimarySpark(List<RealisticSpark> sparks, float burnY,
                                           Matrix4f modelToWorld, float intensity) {
        // Model space coordinates of burn point with slight randomness
        float mx = 0.5f + (random.nextFloat() - 0.5f) * 0.012f;
        float my = burnY;
        float mz = 0.5f + (random.nextFloat() - 0.5f) * 0.012f;

        // Direction: FULL SPHERE - sparks go in all directions like real sparklers
        // Use uniform distribution on sphere
        double theta = random.nextDouble() * Math.PI * 2;  // Full rotation around Y (0-360°)
        double phi = Math.acos(2.0 * random.nextDouble() - 1.0);  // Full sphere (0-180°)

        // Bias slightly upward - most sparks go up/out, fewer go straight down
        // This mimics how you hold a sparkler (pointing up)
        phi = phi * 0.85 + 0.1;  // Compress range slightly, shift up
        if (phi > Math.PI) phi = Math.PI;

        float sinPhi = (float)Math.sin(phi);
        float cosPhi = (float)Math.cos(phi);

        float dirX = (float)(Math.cos(theta) * sinPhi);
        float dirY = cosPhi;  // Can be negative (downward sparks)
        float dirZ = (float)(Math.sin(theta) * sinPhi);

        // Random perturbation for organic chaotic feel
        dirX += (random.nextFloat() - 0.5f) * 0.15f;
        dirY += (random.nextFloat() - 0.5f) * 0.1f;
        dirZ += (random.nextFloat() - 0.5f) * 0.15f;

        // Normalize
        float len = (float)Math.sqrt(dirX*dirX + dirY*dirY + dirZ*dirZ);
        dirX /= len;
        dirY /= len;
        dirZ /= len;

        // Speed: HIGH VARIATION - some sparks shoot fast and far, others are slow
        // This creates the varied trail lengths seen in real sparklers
        float speedBase = 0.6f + random.nextFloat() * 1.4f;  // 0.6-2.0 units/second
        // Occasional extra-fast spark
        if (random.nextFloat() < 0.15f) {
            speedBase *= 1.5f;  // 15% chance of 50% faster
        }

        // Transform position to world space
        Vector3f worldPos = new Vector3f(mx, my, mz);
        modelToWorld.transformPosition(worldPos);

        // Transform direction to world space
        Vector3f worldDir = new Vector3f(dirX, dirY, dirZ);
        modelToWorld.transformDirection(worldDir);
        worldDir.normalize();

        RealisticSpark spark = RealisticSpark.createPrimary(
            worldPos.x, worldPos.y, worldPos.z,
            worldDir.x, worldDir.y, worldDir.z,
            speedBase
        );

        sparks.add(spark);
    }

    /**
     * Update all sparks. Removes dead sparks.
     */
    public static void updateSparks(List<RealisticSpark> sparks, float deltaTime) {
        Iterator<RealisticSpark> it = sparks.iterator();
        while (it.hasNext()) {
            RealisticSpark spark = it.next();
            if (!spark.update(deltaTime)) {
                it.remove();
            }
        }
    }

    /**
     * Get all sparks as flat list (including children)
     */
    public static List<RealisticSpark> getAllSparks(List<RealisticSpark> primarySparks) {
        List<RealisticSpark> all = new ArrayList<>();
        for (RealisticSpark spark : primarySparks) {
            spark.collectAllSparks(all);
        }
        return all;
    }

    /**
     * Clear all sparks
     */
    public static void clearSparks(List<RealisticSpark> sparks) {
        sparks.clear();
        spawnTimer = 0;
    }
}
