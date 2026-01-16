package com.nick.realisticfirework.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Renders realistic sparks as THIN TUBES (camera-facing quads).
 *
 * Each spark is a single thin quad that always faces the camera,
 * creating the appearance of a thin glowing tube/line.
 *
 * NOT cylinders, NOT spheres - just simple thin quads.
 */
public class RealisticSparkRenderer {

    /**
     * Render all sparks as thin camera-facing tubes
     */
    public static void renderAllSparks(VertexConsumer solidConsumer, VertexConsumer glowConsumer,
                                        Matrix4f pose, List<RealisticSpark> primarySparks,
                                        Matrix4f worldToModel) {
        // Get camera position for billboard calculation
        Vec3 cameraPos = getCameraPosition();

        // Get flat list of all sparks (primaries + children)
        List<RealisticSpark> allSparks = RealisticSparkManager.getAllSparks(primarySparks);

        for (RealisticSpark spark : allSparks) {
            renderSparkAsThinTube(solidConsumer, pose, spark, worldToModel, cameraPos);
        }
    }

    /**
     * Render a single spark as a thin camera-facing quad (tube appearance)
     */
    private static void renderSparkAsThinTube(VertexConsumer consumer, Matrix4f pose,
                                               RealisticSpark spark, Matrix4f worldToModel,
                                               Vec3 cameraPos) {
        // Get spark properties
        float[] color = spark.getColor();
        float r = color[0], g = color[1], b = color[2], a = color[3];

        // Skip if too faded
        if (a < 0.05f) return;

        // Get head and tail positions (world space)
        float headX = spark.getHeadX();
        float headY = spark.getHeadY();
        float headZ = spark.getHeadZ();
        float tailX = spark.getTailX();
        float tailY = spark.getTailY();
        float tailZ = spark.getTailZ();

        // Transform to model space
        float[] headWorld = {headX, headY, headZ, 1};
        float[] tailWorld = {tailX, tailY, tailZ, 1};
        float[] headModel = transformPoint(worldToModel, headWorld);
        float[] tailModel = transformPoint(worldToModel, tailWorld);

        float hx = headModel[0], hy = headModel[1], hz = headModel[2];
        float tx = tailModel[0], ty = tailModel[1], tz = tailModel[2];

        // Spark line direction
        float dx = hx - tx;
        float dy = hy - ty;
        float dz = hz - tz;
        float length = (float)Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (length < 0.0001f) return;

        // Normalize direction
        float dirX = dx / length;
        float dirY = dy / length;
        float dirZ = dz / length;

        // Transform camera to model space
        float[] camWorld = {(float)cameraPos.x, (float)cameraPos.y, (float)cameraPos.z, 1};
        float[] camModel = transformPoint(worldToModel, camWorld);

        // Midpoint of spark
        float midX = (hx + tx) * 0.5f;
        float midY = (hy + ty) * 0.5f;
        float midZ = (hz + tz) * 0.5f;

        // View direction from camera to spark midpoint
        float viewX = midX - camModel[0];
        float viewY = midY - camModel[1];
        float viewZ = midZ - camModel[2];
        float viewLen = (float)Math.sqrt(viewX*viewX + viewY*viewY + viewZ*viewZ);
        if (viewLen < 0.0001f) return;
        viewX /= viewLen;
        viewY /= viewLen;
        viewZ /= viewLen;

        // Cross product: spark direction × view direction = perpendicular for quad width
        float perpX = dirY * viewZ - dirZ * viewY;
        float perpY = dirZ * viewX - dirX * viewZ;
        float perpZ = dirX * viewY - dirY * viewX;
        float perpLen = (float)Math.sqrt(perpX*perpX + perpY*perpY + perpZ*perpZ);

        // If spark is pointing at camera, use fallback perpendicular
        if (perpLen < 0.0001f) {
            if (Math.abs(dirY) < 0.9f) {
                perpX = 0; perpY = 1; perpZ = 0;
            } else {
                perpX = 1; perpY = 0; perpZ = 0;
            }
            // Re-cross to get proper perpendicular
            float newX = dirY * perpZ - dirZ * perpY;
            float newY = dirZ * perpX - dirX * perpZ;
            float newZ = dirX * perpY - dirY * perpX;
            perpX = newX; perpY = newY; perpZ = newZ;
            perpLen = (float)Math.sqrt(perpX*perpX + perpY*perpY + perpZ*perpZ);
        }

        if (perpLen > 0.0001f) {
            perpX /= perpLen;
            perpY /= perpLen;
            perpZ /= perpLen;
        }

        // THIN width - this is the key to thin tubes
        // Primary sparks: 0.002, Secondary: 0.001
        float width = spark.type == RealisticSpark.TYPE_PRIMARY ? 0.002f : 0.001f;

        float headWidth = width;
        float tailWidth = width * 0.3f;  // Taper to point at tail

        // Calculate quad corners
        float h1x = hx - perpX * headWidth;
        float h1y = hy - perpY * headWidth;
        float h1z = hz - perpZ * headWidth;

        float h2x = hx + perpX * headWidth;
        float h2y = hy + perpY * headWidth;
        float h2z = hz + perpZ * headWidth;

        float t1x = tx - perpX * tailWidth;
        float t1y = ty - perpY * tailWidth;
        float t1z = tz - perpZ * tailWidth;

        float t2x = tx + perpX * tailWidth;
        float t2y = ty + perpY * tailWidth;
        float t2z = tz + perpZ * tailWidth;

        // Head is bright, tail fades
        float headAlpha = a;
        float tailAlpha = a * 0.15f;

        // Render single quad (front face)
        consumer.addVertex(pose, h1x, h1y, h1z).setColor(r, g, b, headAlpha);
        consumer.addVertex(pose, h2x, h2y, h2z).setColor(r, g, b, headAlpha);
        consumer.addVertex(pose, t2x, t2y, t2z).setColor(r, g, b, tailAlpha);
        consumer.addVertex(pose, t1x, t1y, t1z).setColor(r, g, b, tailAlpha);

        // Render back face (visible from other side)
        consumer.addVertex(pose, h2x, h2y, h2z).setColor(r, g, b, headAlpha);
        consumer.addVertex(pose, h1x, h1y, h1z).setColor(r, g, b, headAlpha);
        consumer.addVertex(pose, t1x, t1y, t1z).setColor(r, g, b, tailAlpha);
        consumer.addVertex(pose, t2x, t2y, t2z).setColor(r, g, b, tailAlpha);
    }

    /**
     * Get camera position in world space
     */
    private static Vec3 getCameraPosition() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer != null && mc.gameRenderer.getMainCamera() != null) {
            return mc.gameRenderer.getMainCamera().getPosition();
        }
        if (mc.player != null) {
            return mc.player.getEyePosition();
        }
        return Vec3.ZERO;
    }

    /**
     * Transform a point by a matrix
     */
    private static float[] transformPoint(Matrix4f mat, float[] point) {
        float x = point[0], y = point[1], z = point[2], w = point[3];
        float[] result = new float[4];
        result[0] = mat.m00() * x + mat.m10() * y + mat.m20() * z + mat.m30() * w;
        result[1] = mat.m01() * x + mat.m11() * y + mat.m21() * z + mat.m31() * w;
        result[2] = mat.m02() * x + mat.m12() * y + mat.m22() * z + mat.m32() * w;
        result[3] = mat.m03() * x + mat.m13() * y + mat.m23() * z + mat.m33() * w;
        return result;
    }
}
