package com.nick.realisticfirework.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Handles rendering of all spark visuals.
 * Renders main sparks, fragments, and micro sparks.
 */
public class SparkRenderer {

    /**
     * Render all sparks in the state.
     * Transforms world-space spark positions to model-space for rendering.
     */
    public static void renderAllSparks(VertexConsumer consumer, Matrix4f poseMat, Matrix4f worldToModel,
                                        SparklerState state,
                                        float u0, float u1, float v0, float v1, int light) {
        synchronized (state.sparks) {
            for (Spark spark : state.sparks) {
                renderSpark(consumer, poseMat, worldToModel, spark, u0, u1, v0, v1, light);
            }
        }
    }

    /**
     * Render a single spark with its fragments and micro sparks.
     */
    private static void renderSpark(VertexConsumer consumer, Matrix4f poseMat, Matrix4f worldToModel,
                                     Spark spark, float u0, float u1, float v0, float v1, int light) {
        int[] color = spark.getColor();

        // Transform world-space spark position back to model-space for rendering
        Vector3f modelPos = new Vector3f(spark.wx, spark.wy, spark.wz);
        worldToModel.transformPosition(modelPos);

        float sx = modelPos.x;
        float sy = modelPos.y;
        float sz = modelPos.z;

        // Calculate trail direction from velocity
        float speed = spark.getSpeed();
        if (speed < 0.01f) speed = 0.01f;

        Vector3f modelVel = new Vector3f(spark.vx / speed, spark.vy / speed, spark.vz / speed);
        worldToModel.transformDirection(modelVel);
        modelVel.normalize();

        // Wire-thin spark widths - brightness comes from quantity, not thickness
        float baseWidth = getSparkWidth(spark.sparkType);

        // Trail length based on speed and spark type - longer = more dramatic
        float trailLength = spark.length * (1.0f + speed * 0.8f);

        // Slightly thicker at head, thinner at tail for wire effect
        float headWidth = baseWidth * (1.2f - spark.life * 0.4f);

        // Calculate trail end point
        float endX = sx - modelVel.x * trailLength;
        float endY = sy - modelVel.y * trailLength;
        float endZ = sz - modelVel.z * trailLength;

        // Render the spark as a simple tapered wire line
        SparkLineRenderer.renderWireSpark(consumer, poseMat, sx, sy, sz, endX, endY, endZ,
                headWidth, spark.seed, u0, u1, v0, v1, light,
                color[0], color[1], color[2], color[3]);

        // Render fragments
        renderFragments(consumer, poseMat, worldToModel, spark, baseWidth, u0, u1, v0, v1, light);

        // Render micro sparks
        renderMicroSparks(consumer, poseMat, worldToModel, spark, baseWidth, u0, u1, v0, v1, light);
    }

    /**
     * Get the base width for a spark type.
     */
    private static float getSparkWidth(int sparkType) {
        if (sparkType == Spark.SPARK_CORE) {
            return 0.0012f;  // Core: slightly thicker for brightness
        } else if (sparkType == Spark.SPARK_INNER) {
            return 0.0008f;  // Inner: thin wire
        } else if (sparkType == Spark.SPARK_HAIR) {
            return 0.0004f;  // Hair: ultra-thin
        } else {
            return 0.0006f;  // Outer: thin wire
        }
    }

    /**
     * Render spark fragments.
     */
    private static void renderFragments(VertexConsumer consumer, Matrix4f poseMat, Matrix4f worldToModel,
                                         Spark spark, float baseWidth,
                                         float u0, float u1, float v0, float v1, int light) {
        for (SparkFragment frag : spark.fragments) {
            Vector3f fragPos = new Vector3f(frag.x, frag.y, frag.z);
            worldToModel.transformPosition(fragPos);

            int[] fragColor = frag.getColor();
            float fragWidth = baseWidth * 0.5f;
            float fragTrailLen = 0.015f + (1f - frag.life) * 0.02f;

            float fragSpeed = (float)Math.sqrt(frag.vx * frag.vx + frag.vy * frag.vy + frag.vz * frag.vz);
            if (fragSpeed < 0.01f) fragSpeed = 0.01f;
            Vector3f fragVel = new Vector3f(frag.vx / fragSpeed, frag.vy / fragSpeed, frag.vz / fragSpeed);
            worldToModel.transformDirection(fragVel);
            fragVel.normalize();

            SparkLineRenderer.renderWireSpark(consumer, poseMat,
                    fragPos.x, fragPos.y, fragPos.z,
                    fragPos.x - fragVel.x * fragTrailLen, fragPos.y - fragVel.y * fragTrailLen, fragPos.z - fragVel.z * fragTrailLen,
                    fragWidth, frag.seed, u0, u1, v0, v1, light,
                    fragColor[0], fragColor[1], fragColor[2], fragColor[3]);
        }
    }

    /**
     * Render micro sparks (tiny ember dots).
     */
    private static void renderMicroSparks(VertexConsumer consumer, Matrix4f poseMat, Matrix4f worldToModel,
                                           Spark spark, float baseWidth,
                                           float u0, float u1, float v0, float v1, int light) {
        for (MicroSpark micro : spark.microSparks) {
            Vector3f microPos = new Vector3f(micro.x, micro.y, micro.z);
            worldToModel.transformPosition(microPos);

            int[] microColor = micro.getColor();
            float microSize = baseWidth * 0.4f;

            SparkShapeRenderer.renderEmberDot(consumer, poseMat, microPos.x, microPos.y, microPos.z, microSize,
                    u0, u1, v0, v1, light, microColor[0], microColor[1], microColor[2], microColor[3]);
        }
    }
}
