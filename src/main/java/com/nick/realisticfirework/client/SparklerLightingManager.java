package com.nick.realisticfirework.client;

import com.nick.realisticfirework.client.lighting.DynamicLightingEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Manages dynamic lighting for sparklers.
 * Handles light updates for the burning tip and individual sparks.
 */
public class SparklerLightingManager {

    /**
     * Update lighting for a fully burned sparkler (fade out sequence).
     * @return true if the sparkler light was processed, false if player is null
     */
    public static boolean updateFullyBurnedLighting(UUID sparklerUUID, SparklerState state,
                                                     Matrix4f currentPose, long timeSinceBurned) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;

        float stickTipY = 0.9f;
        float stickBaseY = 0.1f;
        float burnY = stickTipY - state.getAverageMeltHeight() * (stickTipY - stickBaseY);
        Vector3f tipModel = new Vector3f(0.5f, burnY, 0.5f);
        currentPose.transformPosition(tipModel);
        Vec3 lightPos = new Vec3(tipModel.x, tipModel.y, tipModel.z);

        if (timeSinceBurned < 10000) {
            // First 10 seconds: keep light at full brightness
            DynamicLightingEngine.getInstance().updateSparklerLight(sparklerUUID, lightPos, 14);
        } else if (timeSinceBurned < 11000) {
            // Next 1 second: fade light from 14 to 0
            float fadeProgress = (timeSinceBurned - 10000) / 1000.0f;
            int lightLevel = (int)(14 * (1.0f - fadeProgress));
            if (lightLevel > 0) {
                DynamicLightingEngine.getInstance().updateSparklerLight(sparklerUUID, lightPos, lightLevel);
            } else {
                DynamicLightingEngine.getInstance().removeSparklerLight(sparklerUUID);
            }
        } else {
            // After 11 seconds: remove light
            DynamicLightingEngine.getInstance().removeSparklerLight(sparklerUUID);
        }
        return true;
    }

    /**
     * Update lighting for an actively burning sparkler.
     * @return true if the sparkler light was processed, false if player is null
     */
    public static boolean updateBurningLighting(UUID sparklerUUID, SparklerState state, Matrix4f currentPose) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;

        // Calculate world position of the burn point
        float stickTipY = 0.9f;
        float stickBaseY = 0.1f;
        float burnY = stickTipY - state.getAverageMeltHeight() * (stickTipY - stickBaseY);
        Vector3f tipModel = new Vector3f(0.5f, burnY, 0.5f);
        currentPose.transformPosition(tipModel);

        // Check if the transformed position is reasonable (within ~10 blocks of player)
        Vec3 playerPos = mc.player.position();
        Vec3 lightPos = new Vec3(tipModel.x, tipModel.y, tipModel.z);
        double distance = playerPos.distanceTo(lightPos);

        // Keep light at full brightness during burn - it only fades AFTER visuals are done
        int lightLevel = 14;
        if (lightLevel > 0) {
            if (distance < 10.0) {
                // Position looks valid, use it
                DynamicLightingEngine.getInstance().updateSparklerLight(sparklerUUID, lightPos, lightLevel);
            } else {
                // Fallback: place light at player's hand position
                Vec3 handPos = playerPos.add(0, 1.0, 0);  // Approximate hand height
                DynamicLightingEngine.getInstance().updateSparklerLight(sparklerUUID, handPos, lightLevel);
            }
        } else {
            // Light faded to 0 - remove it immediately
            DynamicLightingEngine.getInstance().removeSparklerLight(sparklerUUID);
        }
        return true;
    }

    /**
     * Update lighting for individual sparks (creates light scatter effect).
     */
    public static void updateSparkLighting(SparklerState state, int maxSparkLights) {
        int sparkLightCount = 0;
        synchronized (state.sparks) {
            for (Spark spark : state.sparks) {
                if (sparkLightCount >= maxSparkLights) break;
                // Only track bright, young sparks
                if (spark.life < 0.5f && spark.brightness > 1.0f) {
                    // Determine light level based on spark type and age
                    int luminance = spark.sparkType == Spark.SPARK_CORE ? 10 :
                                   (spark.sparkType == Spark.SPARK_INNER ? 7 : 5);
                    // Fade with age
                    luminance = (int)(luminance * (1.0f - spark.life * 1.5f));
                    if (luminance > 0) {
                        DynamicLightingEngine.getInstance().addSparkLight(
                            new Vec3(spark.wx, spark.wy, spark.wz),
                            luminance,
                            100  // Short lifetime - sparks update rapidly
                        );
                        sparkLightCount++;
                    }
                }
            }
        }
    }

    /**
     * Remove all lighting for an unlit sparkler.
     */
    public static void removeLighting(UUID sparklerUUID) {
        DynamicLightingEngine.getInstance().removeSparklerLight(sparklerUUID);
    }
}
