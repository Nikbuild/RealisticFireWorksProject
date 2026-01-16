package com.nick.realisticfirework.client;

import org.joml.Matrix4f;

import java.util.Iterator;
import java.util.Random;

/**
 * Manages the lifecycle of sparks - spawning, updating, and removing.
 * Handles emission points and continuous spark generation.
 */
public class SparkLifecycleManager {

    private static final Random random = new Random();

    /**
     * Spawn spark bursts at emission points that have been reached.
     */
    public static void checkEmissionPoints(SparklerState state, float fadeFactor, Matrix4f currentPose) {
        synchronized (state.sparks) {
            for (int i = 0; i < SparklerState.NUM_EMISSION_POINTS; i++) {
                float pointPosition = (float) i / SparklerState.NUM_EMISSION_POINTS;
                if (!state.emissionPointsFired[i] && state.burnProgress >= pointPosition) {
                    state.emissionPointsFired[i] = true;
                    // Spawn a LARGE burst of sparks from this point (scaled by fade)
                    // This creates the characteristic "pop" when each section ignites
                    int burstCount = (int)((40 + random.nextInt(30)) * fadeFactor);
                    if (burstCount > 0) {
                        SparkSpawner.spawnSparkBurst(state, pointPosition, burstCount, currentPose);
                    }
                }
            }
        }
    }

    /**
     * Spawn continuous sparks in layered pattern (core, inner, outer, hair).
     * Creates the realistic sparkler effect with hundreds of wire-thin sparks.
     */
    public static void spawnContinuousSparks(SparklerState state, float fadeFactor, Matrix4f currentPose) {
        synchronized (state.sparks) {
            // Layer 1: CORE sparks - brilliant white center burst (few but bright)
            int coreSparks = (int)((8 + random.nextInt(6)) * fadeFactor);
            for (int i = 0; i < coreSparks && state.sparks.size() < SparkSpawner.MAX_SPARKS; i++) {
                SparkSpawner.spawnCoreSpark(state, currentPose);
            }

            // Layer 2: INNER sparks - medium range radiating wires
            int innerSparks = (int)((15 + random.nextInt(10)) * fadeFactor);
            for (int i = 0; i < innerSparks && state.sparks.size() < SparkSpawner.MAX_SPARKS; i++) {
                SparkSpawner.spawnInnerSpark(state, currentPose);
            }

            // Layer 3: OUTER sparks - long dramatic shooting wires
            int outerSparks = (int)((20 + random.nextInt(15)) * fadeFactor);
            for (int i = 0; i < outerSparks && state.sparks.size() < SparkSpawner.MAX_SPARKS; i++) {
                SparkSpawner.spawnOuterSpark(state, currentPose);
            }

            // Layer 4: HAIR sparks - ultra-thin dense filler for realistic volume
            int hairSparks = (int)((30 + random.nextInt(20)) * fadeFactor);
            for (int i = 0; i < hairSparks && state.sparks.size() < SparkSpawner.MAX_SPARKS; i++) {
                SparkSpawner.spawnHairSpark(state, currentPose);
            }
        }
    }

    /**
     * Update all existing sparks and remove dead ones.
     */
    public static void updateSparks(SparklerState state, float deltaTime) {
        synchronized (state.sparks) {
            Iterator<Spark> it = state.sparks.iterator();
            while (it.hasNext()) {
                Spark spark = it.next();
                if (!spark.update(deltaTime)) {
                    it.remove();
                }
            }
        }
    }

    /**
     * Clear all sparks (used when sparkler is unlit).
     */
    public static void clearSparks(SparklerState state) {
        synchronized (state.sparks) {
            state.sparks.clear();
        }
    }
}
