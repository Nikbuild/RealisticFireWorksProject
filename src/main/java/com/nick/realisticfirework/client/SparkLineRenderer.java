package com.nick.realisticfirework.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * Renders sparks as GL_LINES for thin, realistic appearance.
 * The shader adds glow around these naturally thin lines.
 */
public class SparkLineRenderer {

    /**
     * Render a spark as an actual LINE primitive (not a quad).
     * This creates a true 1-2 pixel wide line that the shader can glow.
     *
     * NOTE: This requires using SparkRenderTypes.SPARK_LINES as the RenderType,
     * which uses VertexFormat.Mode.LINES
     */
    public static void renderSparkLine(VertexConsumer consumer, Matrix4f pose,
                                       float x1, float y1, float z1,  // start point
                                       float x2, float y2, float z2,  // end point
                                       int r, int g, int b, int a) {

        // For GL_LINES, we just need two vertices - start and end
        // The GPU draws a thin line between them
        consumer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a);
        consumer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a);
    }

    /**
     * Render a spark with head-to-tail fade using multiple line segments.
     */
    public static void renderSparkLineWithFade(VertexConsumer consumer, Matrix4f pose,
                                                float x1, float y1, float z1,  // head (bright)
                                                float x2, float y2, float z2,  // tail (faded)
                                                int r, int g, int b, int headAlpha, int tailAlpha,
                                                int segments) {

        float dx = (x2 - x1) / segments;
        float dy = (y2 - y1) / segments;
        float dz = (z2 - z1) / segments;

        for (int i = 0; i < segments; i++) {
            float t1 = (float) i / segments;
            float t2 = (float) (i + 1) / segments;

            float sx = x1 + dx * i;
            float sy = y1 + dy * i;
            float sz = z1 + dz * i;

            float ex = x1 + dx * (i + 1);
            float ey = y1 + dy * (i + 1);
            float ez = z1 + dz * (i + 1);

            // Interpolate alpha
            int a1 = (int) (headAlpha * (1 - t1) + tailAlpha * t1);
            int a2 = (int) (headAlpha * (1 - t2) + tailAlpha * t2);
            int avgAlpha = (a1 + a2) / 2;

            consumer.addVertex(pose, sx, sy, sz).setColor(r, g, b, a1);
            consumer.addVertex(pose, ex, ey, ez).setColor(r, g, b, a2);
        }
    }

    // ============ LEGACY QUAD-BASED METHODS (for compatibility) ============
    // These are kept for any code that still calls them, but the new line-based
    // methods above should be used for the best visual result.

    /**
     * Legacy: Render a spark as a quad (kept for compatibility)
     */
    public static void renderWireSpark(VertexConsumer consumer, Matrix4f pose,
                                  float x1, float y1, float z1,
                                  float x2, float y2, float z2,
                                  float width, long seed,
                                  float u0, float u1, float v0, float v1,
                                  int light, int r, int g, int b, int a) {

        // Direction
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float length = (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (length < 0.0001f) return;

        float dirX = dx / length;
        float dirY = dy / length;
        float dirZ = dz / length;

        // Perpendicular
        float perpX, perpY, perpZ;
        if (Math.abs(dirY) < 0.9f) {
            perpX = -dirZ;
            perpY = 0;
            perpZ = dirX;
        } else {
            perpX = 0;
            perpY = dirZ;
            perpZ = -dirY;
        }
        float perpLen = (float) Math.sqrt(perpX*perpX + perpY*perpY + perpZ*perpZ);
        if (perpLen > 0.001f) {
            perpX /= perpLen;
            perpY /= perpLen;
            perpZ /= perpLen;
        }

        // Very thin quad
        float thinWidth = width * 0.15f;
        float tailWidth = thinWidth * 0.1f;
        int tailAlpha = (int)(a * 0.05f);

        consumer.addVertex(pose, x1 + perpX * thinWidth, y1 + perpY * thinWidth, z1 + perpZ * thinWidth)
                .setColor(r, g, b, a).setUv(u0, v0).setOverlay(0).setLight(light).setNormal(0, 1, 0);
        consumer.addVertex(pose, x1 - perpX * thinWidth, y1 - perpY * thinWidth, z1 - perpZ * thinWidth)
                .setColor(r, g, b, a).setUv(u1, v0).setOverlay(0).setLight(light).setNormal(0, 1, 0);
        consumer.addVertex(pose, x2 - perpX * tailWidth, y2 - perpY * tailWidth, z2 - perpZ * tailWidth)
                .setColor(r, g, b, tailAlpha).setUv(u1, v1).setOverlay(0).setLight(light).setNormal(0, 1, 0);
        consumer.addVertex(pose, x2 + perpX * tailWidth, y2 + perpY * tailWidth, z2 + perpZ * tailWidth)
                .setColor(r, g, b, tailAlpha).setUv(u0, v1).setOverlay(0).setLight(light).setNormal(0, 1, 0);
    }

    public static void renderComplexSpark(VertexConsumer consumer, Matrix4f pose,
                                     float x1, float y1, float z1,
                                     float x2, float y2, float z2,
                                     float width, long seed,
                                     float u0, float u1, float v0, float v1,
                                     int light, int r, int g, int b, int a) {
        renderWireSpark(consumer, pose, x1, y1, z1, x2, y2, z2,
                       width, seed, u0, u1, v0, v1, light, r, g, b, a);
    }

    public static void renderThinStringSegment(VertexConsumer consumer, Matrix4f pose,
                                          float x1, float y1, float z1, float w1,
                                          float x2, float y2, float z2, float w2,
                                          float perpX, float perpY, float perpZ,
                                          float perp2X, float perp2Y, float perp2Z,
                                          float u0, float u1, float v0, float v1, int light,
                                          int r1, int g1, int b1, int a1,
                                          int r2, int g2, int b2, int a2) {

        float thinW1 = Math.min(w1, 0.001f);
        float thinW2 = Math.min(w2, 0.0005f);

        consumer.addVertex(pose, x1 - perpX * thinW1, y1 - perpY * thinW1, z1 - perpZ * thinW1)
              .setColor(r1, g1, b1, a1).setUv(u0, v0).setOverlay(0).setLight(light).setNormal(0, 1, 0);
        consumer.addVertex(pose, x1 + perpX * thinW1, y1 + perpY * thinW1, z1 + perpZ * thinW1)
              .setColor(r1, g1, b1, a1).setUv(u1, v0).setOverlay(0).setLight(light).setNormal(0, 1, 0);
        consumer.addVertex(pose, x2 + perpX * thinW2, y2 + perpY * thinW2, z2 + perpZ * thinW2)
              .setColor(r2, g2, b2, a2).setUv(u1, v1).setOverlay(0).setLight(light).setNormal(0, 1, 0);
        consumer.addVertex(pose, x2 - perpX * thinW2, y2 - perpY * thinW2, z2 - perpZ * thinW2)
              .setColor(r2, g2, b2, a2).setUv(u0, v1).setOverlay(0).setLight(light).setNormal(0, 1, 0);
    }
}
