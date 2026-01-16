package com.nick.realisticfirework.client;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Handles spawning of different types of sparks for the sparkler effect.
 * Separates spark creation logic from rendering.
 */
public class SparkSpawner {

    public static final int MAX_SPARKS = 600;  // MASSIVE spark count for realistic dense effect

    private static final Random random = new Random();

    // Stick geometry constants
    private static final float STICK_BASE_Y = 0.1f;
    private static final float STICK_TIP_Y = 0.9f;
    private static final float STICK_X = 0.5f;
    private static final float STICK_Z = 0.5f;

    /**
     * Spawn a burst of sparks at a specific point along the stick (when emission point fires)
     * FULL SPHERICAL EMISSION for realistic sparkler burst effect
     */
    public static void spawnSparkBurst(SparklerState state, float pointPosition, int count, Matrix4f modelToWorld) {
        float y = STICK_TIP_Y - pointPosition * (STICK_TIP_Y - STICK_BASE_Y);

        for (int i = 0; i < count && state.sparks.size() < MAX_SPARKS; i++) {
            // Mix of all spark types in bursts for variety
            float typeRoll = random.nextFloat();
            int sparkType;
            if (typeRoll < 0.15f) {
                sparkType = Spark.SPARK_CORE;
            } else if (typeRoll < 0.35f) {
                sparkType = Spark.SPARK_INNER;
            } else if (typeRoll < 0.7f) {
                sparkType = Spark.SPARK_OUTER;
            } else {
                sparkType = Spark.SPARK_HAIR;
            }

            // FULL SPHERICAL emission - no bias
            double theta = random.nextDouble() * Math.PI * 2;
            double phi = Math.acos(2.0 * random.nextDouble() - 1.0);  // Uniform sphere

            float sinPhi = (float) Math.sin(phi);
            float cosPhi = (float) Math.cos(phi);

            float dirX = (float) (Math.cos(theta) * sinPhi);
            float dirY = cosPhi;
            float dirZ = (float) (Math.sin(theta) * sinPhi);

            // Small chaotic variation
            dirX += (random.nextFloat() - 0.5f) * 0.2f;
            dirY += (random.nextFloat() - 0.5f) * 0.2f;
            dirZ += (random.nextFloat() - 0.5f) * 0.2f;

            // Normalize
            float len = (float)Math.sqrt(dirX*dirX + dirY*dirY + dirZ*dirZ);
            dirX /= len; dirY /= len; dirZ /= len;

            // FAST speeds for dramatic burst
            float speed = 2.5f + random.nextFloat() * 4.0f;
            float mvx = dirX * speed;
            float mvy = dirY * speed;
            float mvz = dirZ * speed;

            float maxLife = 0.2f + random.nextFloat() * 0.4f;
            float length = 0.05f + random.nextFloat() * 0.1f;

            float angle = random.nextFloat() * (float)(Math.PI * 2);
            float radius = 0.015f;
            float mx = STICK_X + (float)Math.cos(angle) * radius;
            float mz = STICK_Z + (float)Math.sin(angle) * radius;

            // Transform position from model-space to world-space
            Vector3f worldPos = new Vector3f(mx, y, mz);
            modelToWorld.transformPosition(worldPos);

            // Transform velocity direction from model-space to world-space
            // Use transformDirection to only rotate the velocity, not translate it
            Vector3f worldVel = new Vector3f(mvx, mvy, mvz);
            modelToWorld.transformDirection(worldVel);

            state.sparks.add(new Spark(worldPos.x, worldPos.y, worldPos.z,
                                        worldVel.x, worldVel.y, worldVel.z,
                                        maxLife, length, 1.5f, sparkType));
        }
    }

    /**
     * CORE SPARKS - Brilliant white, FAST radiating wires
     * Creates the bright central burst - short but VERY fast
     */
    public static void spawnCoreSpark(SparklerState state, Matrix4f modelToWorld) {
        float meltProgress = state.getAverageMeltHeight();
        float my = STICK_TIP_Y - meltProgress * (STICK_TIP_Y - STICK_BASE_Y);

        // FULL SPHERICAL emission - sparks go in ALL directions
        // Use uniform sphere distribution for realistic radial pattern
        double theta = random.nextDouble() * Math.PI * 2;  // Full 360 degrees
        double phi = Math.acos(2.0 * random.nextDouble() - 1.0);  // Uniform sphere distribution

        float sinPhi = (float) Math.sin(phi);
        float cosPhi = (float) Math.cos(phi);

        // FAST speed for core - creates the bright center spray
        float speed = 3.5f + random.nextFloat() * 2.5f;

        // Pure spherical direction
        float dirX = (float) (Math.cos(theta) * sinPhi);
        float dirY = cosPhi;  // No bias - true spherical
        float dirZ = (float) (Math.sin(theta) * sinPhi);

        // Small chaotic perturbation for organic feel
        dirX += (random.nextFloat() - 0.5f) * 0.15f;
        dirY += (random.nextFloat() - 0.5f) * 0.15f;
        dirZ += (random.nextFloat() - 0.5f) * 0.15f;

        // Normalize and apply speed
        float len = (float)Math.sqrt(dirX*dirX + dirY*dirY + dirZ*dirZ);
        float mvx = (dirX / len) * speed;
        float mvy = (dirY / len) * speed;
        float mvz = (dirZ / len) * speed;

        // Short life - core sparks are fast and brief
        float maxLife = 0.15f + random.nextFloat() * 0.2f;
        // Short trail
        float length = 0.03f + random.nextFloat() * 0.04f;

        // Spawn at burn point with tiny offset
        float mx = STICK_X + (random.nextFloat() - 0.5f) * 0.006f;
        float mz = STICK_Z + (random.nextFloat() - 0.5f) * 0.006f;

        Vector3f worldPos = new Vector3f(mx, my, mz);
        modelToWorld.transformPosition(worldPos);

        Vector3f worldVel = new Vector3f(mvx, mvy, mvz);
        modelToWorld.transformDirection(worldVel);

        state.sparks.add(new Spark(worldPos.x, worldPos.y, worldPos.z,
                                    worldVel.x, worldVel.y, worldVel.z,
                                    maxLife, length, 3.0f, Spark.SPARK_CORE));
    }

    /**
     * INNER SPARKS - Brilliant white wire-like sparks, medium range
     * Full spherical emission with wire-thin trails
     */
    public static void spawnInnerSpark(SparklerState state, Matrix4f modelToWorld) {
        float meltProgress = state.getAverageMeltHeight();
        float my = STICK_TIP_Y - meltProgress * (STICK_TIP_Y - STICK_BASE_Y);

        // FULL SPHERICAL emission for realistic sparkler
        double theta = random.nextDouble() * Math.PI * 2;
        double phi = Math.acos(2.0 * random.nextDouble() - 1.0);  // Uniform sphere

        float sinPhi = (float) Math.sin(phi);
        float cosPhi = (float) Math.cos(phi);

        // Medium-fast speed for visible radiating wires
        float speed = 2.5f + random.nextFloat() * 2.0f;

        // Pure spherical direction
        float dirX = (float) (Math.cos(theta) * sinPhi);
        float dirY = cosPhi;
        float dirZ = (float) (Math.sin(theta) * sinPhi);

        // Small chaotic variation
        dirX += (random.nextFloat() - 0.5f) * 0.2f;
        dirY += (random.nextFloat() - 0.5f) * 0.2f;
        dirZ += (random.nextFloat() - 0.5f) * 0.2f;

        float len = (float)Math.sqrt(dirX*dirX + dirY*dirY + dirZ*dirZ);
        float mvx = (dirX / len) * speed;
        float mvy = (dirY / len) * speed;
        float mvz = (dirZ / len) * speed;

        // Medium life for visible travel
        float maxLife = 0.25f + random.nextFloat() * 0.35f;
        // Wire-thin trail
        float length = 0.04f + random.nextFloat() * 0.06f;

        float mx = STICK_X + (random.nextFloat() - 0.5f) * 0.008f;
        float mz = STICK_Z + (random.nextFloat() - 0.5f) * 0.008f;

        Vector3f worldPos = new Vector3f(mx, my, mz);
        modelToWorld.transformPosition(worldPos);

        Vector3f worldVel = new Vector3f(mvx, mvy, mvz);
        modelToWorld.transformDirection(worldVel);

        state.sparks.add(new Spark(worldPos.x, worldPos.y, worldPos.z,
                                    worldVel.x, worldVel.y, worldVel.z,
                                    maxLife, length, 2.2f, Spark.SPARK_INNER));
    }

    /**
     * OUTER SPARKS - Long dramatic radiating wire trails
     * These shoot FAR in full spherical pattern - the iconic sparkler look
     */
    public static void spawnOuterSpark(SparklerState state, Matrix4f modelToWorld) {
        float meltProgress = state.getAverageMeltHeight();
        float my = STICK_TIP_Y - meltProgress * (STICK_TIP_Y - STICK_BASE_Y);

        // FULL SPHERICAL emission - this creates the iconic sparkler burst
        double theta = random.nextDouble() * Math.PI * 2;
        double phi = Math.acos(2.0 * random.nextDouble() - 1.0);  // Uniform sphere

        float sinPhi = (float) Math.sin(phi);
        float cosPhi = (float) Math.cos(phi);

        // Pure spherical direction
        float dirX = (float) (Math.cos(theta) * sinPhi);
        float dirY = cosPhi;
        float dirZ = (float) (Math.sin(theta) * sinPhi);

        // Small variation for organic look
        dirX += (random.nextFloat() - 0.5f) * 0.25f;
        dirY += (random.nextFloat() - 0.5f) * 0.25f;
        dirZ += (random.nextFloat() - 0.5f) * 0.25f;

        // FAST speed for long radiating trails - the key to sparkler look
        float speed = 4.0f + random.nextFloat() * 3.5f;

        float len = (float)Math.sqrt(dirX*dirX + dirY*dirY + dirZ*dirZ);
        float mvx = (dirX / len) * speed;
        float mvy = (dirY / len) * speed;
        float mvz = (dirZ / len) * speed;

        // Long life for dramatic travel distance
        float maxLife = 0.4f + random.nextFloat() * 0.5f;
        // Long wire trail
        float length = 0.06f + random.nextFloat() * 0.08f;

        float angle = random.nextFloat() * (float)(Math.PI * 2);
        float radius = 0.01f;
        float mx = STICK_X + (float)Math.cos(angle) * radius;
        float mz = STICK_Z + (float)Math.sin(angle) * radius;

        Vector3f worldPos = new Vector3f(mx, my, mz);
        modelToWorld.transformPosition(worldPos);

        Vector3f worldVel = new Vector3f(mvx, mvy, mvz);
        modelToWorld.transformDirection(worldVel);

        state.sparks.add(new Spark(worldPos.x, worldPos.y, worldPos.z,
                                    worldVel.x, worldVel.y, worldVel.z,
                                    maxLife, length, 1.8f, Spark.SPARK_OUTER));
    }

    /**
     * HAIR SPARKS - Ultra-thin dense filler sparks
     * Creates the dense "hairy" appearance of real sparklers
     */
    public static void spawnHairSpark(SparklerState state, Matrix4f modelToWorld) {
        float meltProgress = state.getAverageMeltHeight();
        float my = STICK_TIP_Y - meltProgress * (STICK_TIP_Y - STICK_BASE_Y);

        // Full spherical emission
        double theta = random.nextDouble() * Math.PI * 2;
        double phi = Math.acos(2.0 * random.nextDouble() - 1.0);

        float sinPhi = (float) Math.sin(phi);
        float cosPhi = (float) Math.cos(phi);

        float dirX = (float) (Math.cos(theta) * sinPhi);
        float dirY = cosPhi;
        float dirZ = (float) (Math.sin(theta) * sinPhi);

        // Varied speeds for depth
        float speed = 1.5f + random.nextFloat() * 4.0f;

        float len = (float)Math.sqrt(dirX*dirX + dirY*dirY + dirZ*dirZ);
        float mvx = (dirX / len) * speed;
        float mvy = (dirY / len) * speed;
        float mvz = (dirZ / len) * speed;

        // Short life - hair sparks fade fast
        float maxLife = 0.1f + random.nextFloat() * 0.25f;
        // Very short trail
        float length = 0.02f + random.nextFloat() * 0.03f;

        float mx = STICK_X + (random.nextFloat() - 0.5f) * 0.006f;
        float mz = STICK_Z + (random.nextFloat() - 0.5f) * 0.006f;

        Vector3f worldPos = new Vector3f(mx, my, mz);
        modelToWorld.transformPosition(worldPos);

        Vector3f worldVel = new Vector3f(mvx, mvy, mvz);
        modelToWorld.transformDirection(worldVel);

        state.sparks.add(new Spark(worldPos.x, worldPos.y, worldPos.z,
                                    worldVel.x, worldVel.y, worldVel.z,
                                    maxLife, length, 1.5f, Spark.SPARK_HAIR));
    }
}
