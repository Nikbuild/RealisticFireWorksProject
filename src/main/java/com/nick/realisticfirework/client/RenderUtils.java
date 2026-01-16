package com.nick.realisticfirework.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * Utility class for common rendering operations.
 * Contains methods for rendering caps, streaks, glow spheres, and other primitives.
 */
public class RenderUtils {

    /**
     * Linear interpolation helper
     */
    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * Render a glowing sphere effect using billboard quads
     * Creates a soft, glowing appearance at the specified position
     */
    public static void renderGlowSphere(VertexConsumer consumer, Matrix4f pose,
                                   float x, float y, float z, float radius,
                                   float uMid, float vMid, int light,
                                   int r, int g, int b, int a) {
        // Render multiple billboard quads facing different directions for a spherical glow effect
        // This creates a volumetric-looking glow that's visible from all angles

        // XY plane (facing Z)
        renderGlowQuad(consumer, pose, x, y, z, radius, uMid, vMid, light, r, g, b, a, 0, 0, 1);
        renderGlowQuad(consumer, pose, x, y, z, radius, uMid, vMid, light, r, g, b, a, 0, 0, -1);

        // XZ plane (facing Y)
        renderGlowQuad(consumer, pose, x, y, z, radius, uMid, vMid, light, r, g, b, a, 0, 1, 0);
        renderGlowQuad(consumer, pose, x, y, z, radius, uMid, vMid, light, r, g, b, a, 0, -1, 0);

        // YZ plane (facing X)
        renderGlowQuad(consumer, pose, x, y, z, radius, uMid, vMid, light, r, g, b, a, 1, 0, 0);
        renderGlowQuad(consumer, pose, x, y, z, radius, uMid, vMid, light, r, g, b, a, -1, 0, 0);

        // Diagonal planes for smoother appearance
        float d = 0.707f; // sqrt(2)/2 for 45-degree diagonals
        renderGlowQuad(consumer, pose, x, y, z, radius, uMid, vMid, light, r, g, b, a, d, 0, d);
        renderGlowQuad(consumer, pose, x, y, z, radius, uMid, vMid, light, r, g, b, a, d, 0, -d);
        renderGlowQuad(consumer, pose, x, y, z, radius, uMid, vMid, light, r, g, b, a, -d, 0, d);
        renderGlowQuad(consumer, pose, x, y, z, radius, uMid, vMid, light, r, g, b, a, -d, 0, -d);
    }

    /**
     * Render a single glow quad facing a specific direction
     */
    public static void renderGlowQuad(VertexConsumer consumer, Matrix4f pose,
                                 float x, float y, float z, float radius,
                                 float uMid, float vMid, int light,
                                 int r, int g, int b, int a,
                                 float nx, float ny, float nz) {
        // Create two perpendicular vectors to the normal for the quad
        float upX, upY, upZ;
        if (Math.abs(ny) > 0.9f) {
            // Normal is pointing mostly up/down, use X as "up"
            upX = 1; upY = 0; upZ = 0;
        } else {
            // Use Y as "up"
            upX = 0; upY = 1; upZ = 0;
        }

        // Cross product: right = up × normal
        float rightX = upY * nz - upZ * ny;
        float rightY = upZ * nx - upX * nz;
        float rightZ = upX * ny - upY * nx;

        // Normalize right vector
        float rightLen = (float)Math.sqrt(rightX * rightX + rightY * rightY + rightZ * rightZ);
        if (rightLen > 0.001f) {
            rightX /= rightLen;
            rightY /= rightLen;
            rightZ /= rightLen;
        }

        // Cross product: actualUp = normal × right
        float actualUpX = ny * rightZ - nz * rightY;
        float actualUpY = nz * rightX - nx * rightZ;
        float actualUpZ = nx * rightY - ny * rightX;

        // Scale by radius
        rightX *= radius;
        rightY *= radius;
        rightZ *= radius;
        actualUpX *= radius;
        actualUpY *= radius;
        actualUpZ *= radius;

        // Four corners of the quad
        float x1 = x - rightX - actualUpX;
        float y1 = y - rightY - actualUpY;
        float z1 = z - rightZ - actualUpZ;

        float x2 = x + rightX - actualUpX;
        float y2 = y + rightY - actualUpY;
        float z2 = z + rightZ - actualUpZ;

        float x3 = x + rightX + actualUpX;
        float y3 = y + rightY + actualUpY;
        float z3 = z + rightZ + actualUpZ;

        float x4 = x - rightX + actualUpX;
        float y4 = y - rightY + actualUpY;
        float z4 = z - rightZ + actualUpZ;

        // Front face
        consumer.addVertex(pose, x1, y1, z1)
              .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(nx, ny, nz);
        consumer.addVertex(pose, x2, y2, z2)
              .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(nx, ny, nz);
        consumer.addVertex(pose, x3, y3, z3)
              .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(nx, ny, nz);
        consumer.addVertex(pose, x4, y4, z4)
              .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(nx, ny, nz);

        // Back face (reversed winding for double-sided)
        consumer.addVertex(pose, x4, y4, z4)
              .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(-nx, -ny, -nz);
        consumer.addVertex(pose, x3, y3, z3)
              .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(-nx, -ny, -nz);
        consumer.addVertex(pose, x2, y2, z2)
              .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(-nx, -ny, -nz);
        consumer.addVertex(pose, x1, y1, z1)
              .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(-nx, -ny, -nz);
    }

    /**
     * Render a cap (top or bottom face) double-sided
     */
    public static void renderCapDoubleSided(VertexConsumer consumer, Matrix4f pose,
                                       float x1, float y1, float z1,  // corner 1
                                       float x2, float y2, float z2,  // corner 2
                                       float x3, float y3, float z3,  // corner 3
                                       float x4, float y4, float z4,  // corner 4
                                       float u0, float u1, float v0, float v1,
                                       int light, float nx, float ny, float nz) {
        int r = 255, g = 255, b = 255, a = 255;

        // Front face
        consumer.addVertex(pose, x1, y1, z1)
              .setColor(r, g, b, a).setUv(u0, v0).setOverlay(0).setLight(light).setNormal(nx, ny, nz);
        consumer.addVertex(pose, x2, y2, z2)
              .setColor(r, g, b, a).setUv(u1, v0).setOverlay(0).setLight(light).setNormal(nx, ny, nz);
        consumer.addVertex(pose, x3, y3, z3)
              .setColor(r, g, b, a).setUv(u1, v1).setOverlay(0).setLight(light).setNormal(nx, ny, nz);
        consumer.addVertex(pose, x4, y4, z4)
              .setColor(r, g, b, a).setUv(u0, v1).setOverlay(0).setLight(light).setNormal(nx, ny, nz);

        // Back face (reversed winding)
        consumer.addVertex(pose, x4, y4, z4)
              .setColor(r, g, b, a).setUv(u0, v1).setOverlay(0).setLight(light).setNormal(-nx, -ny, -nz);
        consumer.addVertex(pose, x3, y3, z3)
              .setColor(r, g, b, a).setUv(u1, v1).setOverlay(0).setLight(light).setNormal(-nx, -ny, -nz);
        consumer.addVertex(pose, x2, y2, z2)
              .setColor(r, g, b, a).setUv(u1, v0).setOverlay(0).setLight(light).setNormal(-nx, -ny, -nz);
        consumer.addVertex(pose, x1, y1, z1)
              .setColor(r, g, b, a).setUv(u0, v0).setOverlay(0).setLight(light).setNormal(-nx, -ny, -nz);
    }

    /**
     * Render an elongated streak quad between two points
     */
    public static void renderStreak(VertexConsumer consumer, Matrix4f pose,
                              float x1, float y1, float z1,  // head
                              float x2, float y2, float z2,  // tail
                              float width,
                              float u0, float u1, float v0, float v1,
                              int light, int r, int g, int b, int a) {

        // Direction along the streak
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;

        // We need a perpendicular vector for width
        float upX = 0, upY = 1, upZ = 0;

        // Cross product: perpendicular = up × direction
        float perpX = upY * dz - upZ * dy;
        float perpY = upZ * dx - upX * dz;
        float perpZ = upX * dy - upY * dx;

        // Normalize perpendicular
        float perpLen = (float) Math.sqrt(perpX * perpX + perpY * perpY + perpZ * perpZ);
        if (perpLen < 0.001f) {
            upX = 1; upY = 0; upZ = 0;
            perpX = upY * dz - upZ * dy;
            perpY = upZ * dx - upX * dz;
            perpZ = upX * dy - upY * dx;
            perpLen = (float) Math.sqrt(perpX * perpX + perpY * perpY + perpZ * perpZ);
        }

        if (perpLen > 0.001f) {
            perpX /= perpLen;
            perpY /= perpLen;
            perpZ /= perpLen;
        }

        perpX *= width;
        perpY *= width;
        perpZ *= width;

        // Tail color (dimmer, more orange)
        int tailR = r;
        int tailG = Math.max(0, g - 50);
        int tailB = Math.max(0, b - 100);
        int tailA = a / 2;

        // Four corners of the streak quad
        consumer.addVertex(pose, x1 - perpX, y1 - perpY, z1 - perpZ)
              .setColor(r, g, b, a).setUv(u0, v0).setOverlay(0).setLight(light).setNormal(0, 1, 0);
        consumer.addVertex(pose, x1 + perpX, y1 + perpY, z1 + perpZ)
              .setColor(r, g, b, a).setUv(u1, v0).setOverlay(0).setLight(light).setNormal(0, 1, 0);
        consumer.addVertex(pose, x2 + perpX, y2 + perpY, z2 + perpZ)
              .setColor(tailR, tailG, tailB, tailA).setUv(u1, v1).setOverlay(0).setLight(light).setNormal(0, 1, 0);
        consumer.addVertex(pose, x2 - perpX, y2 - perpY, z2 - perpZ)
              .setColor(tailR, tailG, tailB, tailA).setUv(u0, v1).setOverlay(0).setLight(light).setNormal(0, 1, 0);

        // Second perpendicular plane for visibility from all angles
        float perp2X = dy * perpZ - dz * perpY;
        float perp2Y = dz * perpX - dx * perpZ;
        float perp2Z = dx * perpY - dy * perpX;

        float perp2Len = (float) Math.sqrt(perp2X * perp2X + perp2Y * perp2Y + perp2Z * perp2Z);
        if (perp2Len > 0.001f) {
            perp2X = (perp2X / perp2Len) * width;
            perp2Y = (perp2Y / perp2Len) * width;
            perp2Z = (perp2Z / perp2Len) * width;

            consumer.addVertex(pose, x1 - perp2X, y1 - perp2Y, z1 - perp2Z)
                  .setColor(r, g, b, a).setUv(u0, v0).setOverlay(0).setLight(light).setNormal(1, 0, 0);
            consumer.addVertex(pose, x1 + perp2X, y1 + perp2Y, z1 + perp2Z)
                  .setColor(r, g, b, a).setUv(u1, v0).setOverlay(0).setLight(light).setNormal(1, 0, 0);
            consumer.addVertex(pose, x2 + perp2X, y2 + perp2Y, z2 + perp2Z)
                  .setColor(tailR, tailG, tailB, tailA).setUv(u1, v1).setOverlay(0).setLight(light).setNormal(1, 0, 0);
            consumer.addVertex(pose, x2 - perp2X, y2 - perp2Y, z2 - perp2Z)
                  .setColor(tailR, tailG, tailB, tailA).setUv(u0, v1).setOverlay(0).setLight(light).setNormal(1, 0, 0);
        }
    }
}
