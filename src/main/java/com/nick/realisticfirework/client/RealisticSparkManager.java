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

    // Maximum number of primary sparks active at once
    public static final int MAX_PRIMARY_SPARKS = 100;

    // Spawn rate control
    private static final float SPAWN_INTERVAL = 0.016f;  // ~60 spawns per second base rate
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
     * Spawn a single primary spark shooting into upper hemisphere
     */
    private static void spawnPrimarySpark(List<RealisticSpark> sparks, float burnY,
                                           Matrix4f modelToWorld, float intensity) {
        // Model space coordinates of burn point
        float mx = 0.5f + (random.nextFloat() - 0.5f) * 0.01f;
        float my = burnY;
        float mz = 0.5f + (random.nextFloat() - 0.5f) * 0.01f;

        // Direction: UPPER HEMISPHERE ONLY
        // Use uniform distribution on hemisphere (not sphere)
        double theta = random.nextDouble() * Math.PI * 2;  // Full rotation around Y
        double phi = random.nextDouble() * Math.PI * 0.5;  // 0 to 90 degrees from up (hemisphere)

        // Add slight bias toward more horizontal angles for better spread
        // This makes more sparks go outward rather than straight up
        phi = Math.pow(phi / (Math.PI * 0.5), 0.7) * Math.PI * 0.5;

        float sinPhi = (float)Math.sin(phi);
        float cosPhi = (float)Math.cos(phi);

        float dirX = (float)(Math.cos(theta) * sinPhi);
        float dirY = cosPhi;  // Always positive (upper hemisphere)
        float dirZ = (float)(Math.sin(theta) * sinPhi);

        // Small random perturbation for organic feel
        dirX += (random.nextFloat() - 0.5f) * 0.1f;
        dirY += random.nextFloat() * 0.05f;  // Only perturb upward
        dirZ += (random.nextFloat() - 0.5f) * 0.1f;

        // Normalize
        float len = (float)Math.sqrt(dirX*dirX + dirY*dirY + dirZ*dirZ);
        dirX /= len;
        dirY /= len;
        dirZ /= len;

        // Speed: moderate - these are heated metal particles, not light
        // They travel short distance before exploding
        float speed = 0.8f + random.nextFloat() * 0.6f;  // 0.8-1.4 units/second

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
            speed
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
