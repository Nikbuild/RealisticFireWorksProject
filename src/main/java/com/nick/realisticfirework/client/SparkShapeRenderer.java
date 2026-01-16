package com.nick.realisticfirework.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * Handles rendering of spark shape primitives.
 * Contains simple geometric shapes used for spark visual effects.
 */
public class SparkShapeRenderer {

    /**
     * Render ember dot - small cross billboard for micro sparks
     */
    public static void renderEmberDot(VertexConsumer consumer, Matrix4f pose,
                                 float x, float y, float z, float size,
                                 float u0, float u1, float v0, float v1, int light,
                                 int r, int g, int b, int a) {
        // Small cross billboard
        consumer.addVertex(pose, x - size, y, z).setColor(r, g, b, a).setUv(u0, v0).setOverlay(0).setLight(light).setNormal(0, 1, 0);
        consumer.addVertex(pose, x + size, y, z).setColor(r, g, b, a).setUv(u1, v0).setOverlay(0).setLight(light).setNormal(0, 1, 0);
        consumer.addVertex(pose, x + size, y, z).setColor(r, g, b, a).setUv(u1, v1).setOverlay(0).setLight(light).setNormal(0, 1, 0);
        consumer.addVertex(pose, x - size, y, z).setColor(r, g, b, a).setUv(u0, v1).setOverlay(0).setLight(light).setNormal(0, 1, 0);

        consumer.addVertex(pose, x, y, z - size).setColor(r, g, b, a).setUv(u0, v0).setOverlay(0).setLight(light).setNormal(0, 1, 0);
        consumer.addVertex(pose, x, y, z + size).setColor(r, g, b, a).setUv(u1, v0).setOverlay(0).setLight(light).setNormal(0, 1, 0);
        consumer.addVertex(pose, x, y, z + size).setColor(r, g, b, a).setUv(u1, v1).setOverlay(0).setLight(light).setNormal(0, 1, 0);
        consumer.addVertex(pose, x, y, z - size).setColor(r, g, b, a).setUv(u0, v1).setOverlay(0).setLight(light).setNormal(0, 1, 0);
    }
}
