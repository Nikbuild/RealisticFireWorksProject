package com.nick.realisticfirework.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Renders sparks as camera-facing billboard quads.
 * This ensures sparks are always visible from all angles, unlike GL_LINES which have view-angle issues.
 *
 * The key insight: a thin quad that always faces the camera looks like a bright line from ANY angle.
 * This is how most games render sparks, laser beams, and particle trails.
 */
public class BillboardSparkRenderer {

    /**
     * Render a spark as a camera-facing billboard quad.
     * The quad is thin (like a line) but always oriented perpendicular to the view direction.
     *
     * @param consumer The vertex consumer (should use additive blending like RenderType.lightning())
     * @param pose The pose matrix for transformations
     * @param worldToModel Matrix to convert world positions to model space
     * @param cameraPos Camera position in world space
     * @param headX Head position X in world space
     * @param headY Head position Y in world space
     * @param headZ Head position Z in world space
     * @param tailX Tail position X in world space
     * @param tailY Tail position Y in world space
     * @param tailZ Tail position Z in world space
     * @param width Width of the spark line (thickness)
     * @param r Red color component (0-255)
     * @param g Green color component (0-255)
     * @param b Blue color component (0-255)
     * @param headAlpha Alpha at head (0-255)
     * @param tailAlpha Alpha at tail (0-255)
     */
    public static void renderBillboardSpark(VertexConsumer consumer, Matrix4f pose, Matrix4f worldToModel,
                                            Vec3 cameraPos,
                                            float headX, float headY, float headZ,
                                            float tailX, float tailY, float tailZ,
                                            float width,
                                            int r, int g, int b, int headAlpha, int tailAlpha) {

        // Transform world positions to model space for rendering
        Vector3f headModel = new Vector3f(headX, headY, headZ);
        Vector3f tailModel = new Vector3f(tailX, tailY, tailZ);
        worldToModel.transformPosition(headModel);
        worldToModel.transformPosition(tailModel);

        // Direction from tail to head (spark line direction)
        float lineX = headModel.x - tailModel.x;
        float lineY = headModel.y - tailModel.y;
        float lineZ = headModel.z - tailModel.z;
        float lineLen = (float) Math.sqrt(lineX * lineX + lineY * lineY + lineZ * lineZ);
        if (lineLen < 0.0001f) return;

        // Normalize line direction
        lineX /= lineLen;
        lineY /= lineLen;
        lineZ /= lineLen;

        // Get camera position in model space
        Vector3f camModel = new Vector3f((float) cameraPos.x, (float) cameraPos.y, (float) cameraPos.z);
        worldToModel.transformPosition(camModel);

        // Midpoint of the spark line (in model space)
        float midX = (headModel.x + tailModel.x) * 0.5f;
        float midY = (headModel.y + tailModel.y) * 0.5f;
        float midZ = (headModel.z + tailModel.z) * 0.5f;

        // View direction from camera to spark midpoint
        float viewX = midX - camModel.x;
        float viewY = midY - camModel.y;
        float viewZ = midZ - camModel.z;
        float viewLen = (float) Math.sqrt(viewX * viewX + viewY * viewY + viewZ * viewZ);
        if (viewLen < 0.0001f) return;
        viewX /= viewLen;
        viewY /= viewLen;
        viewZ /= viewLen;

        // Calculate perpendicular direction (cross product of line direction and view direction)
        // This gives us the direction to offset vertices to create width
        float perpX = lineY * viewZ - lineZ * viewY;
        float perpY = lineZ * viewX - lineX * viewZ;
        float perpZ = lineX * viewY - lineY * viewX;
        float perpLen = (float) Math.sqrt(perpX * perpX + perpY * perpY + perpZ * perpZ);

        // If line is parallel to view, use an arbitrary perpendicular
        if (perpLen < 0.0001f) {
            // Line is pointing at camera, use X-axis perpendicular
            if (Math.abs(lineY) < 0.9f) {
                perpX = 0;
                perpY = 1;
                perpZ = 0;
            } else {
                perpX = 1;
                perpY = 0;
                perpZ = 0;
            }
            // Cross again to get proper perpendicular
            float newPerpX = lineY * perpZ - lineZ * perpY;
            float newPerpY = lineZ * perpX - lineX * perpZ;
            float newPerpZ = lineX * perpY - lineY * perpX;
            perpX = newPerpX;
            perpY = newPerpY;
            perpZ = newPerpZ;
            perpLen = (float) Math.sqrt(perpX * perpX + perpY * perpY + perpZ * perpZ);
        }

        if (perpLen > 0.0001f) {
            perpX /= perpLen;
            perpY /= perpLen;
            perpZ /= perpLen;
        }

        // Half-width offset
        float hw = width * 0.5f;
        float twHead = hw;  // Width at head
        float twTail = hw * 0.3f;  // Thinner at tail (taper effect)

        // Calculate 4 corners of the quad
        // Head left, Head right, Tail right, Tail left
        float hlX = headModel.x - perpX * twHead;
        float hlY = headModel.y - perpY * twHead;
        float hlZ = headModel.z - perpZ * twHead;

        float hrX = headModel.x + perpX * twHead;
        float hrY = headModel.y + perpY * twHead;
        float hrZ = headModel.z + perpZ * twHead;

        float trX = tailModel.x + perpX * twTail;
        float trY = tailModel.y + perpY * twTail;
        float trZ = tailModel.z + perpZ * twTail;

        float tlX = tailModel.x - perpX * twTail;
        float tlY = tailModel.y - perpY * twTail;
        float tlZ = tailModel.z - perpZ * twTail;

        // Render as a quad (4 vertices)
        // For lightning render type, we only need position and color
        consumer.addVertex(pose, hlX, hlY, hlZ).setColor(r, g, b, headAlpha);
        consumer.addVertex(pose, hrX, hrY, hrZ).setColor(r, g, b, headAlpha);
        consumer.addVertex(pose, trX, trY, trZ).setColor(r, g, b, tailAlpha);
        consumer.addVertex(pose, tlX, tlY, tlZ).setColor(r, g, b, tailAlpha);
    }

    /**
     * Render spark with glow effect - multiple overlapping quads at different widths
     * Creates the characteristic sparkler glow with bright core and soft falloff
     */
    public static void renderBillboardSparkWithGlow(VertexConsumer consumer, Matrix4f pose, Matrix4f worldToModel,
                                                     Vec3 cameraPos,
                                                     float headX, float headY, float headZ,
                                                     float tailX, float tailY, float tailZ,
                                                     float baseWidth, float[] color) {
        int r = (int)(color[0] * 255);
        int g = (int)(color[1] * 255);
        int b = (int)(color[2] * 255);
        int baseAlpha = (int)(color[3] * 255);

        // Core layer - bright and thin
        int coreAlpha = Math.min(255, (int)(baseAlpha * 1.2f));
        renderBillboardSpark(consumer, pose, worldToModel, cameraPos,
                headX, headY, headZ, tailX, tailY, tailZ,
                baseWidth * 0.5f, r, g, b, coreAlpha, (int)(coreAlpha * 0.1f));

        // Middle layer - medium brightness, medium width
        int midAlpha = (int)(baseAlpha * 0.7f);
        renderBillboardSpark(consumer, pose, worldToModel, cameraPos,
                headX, headY, headZ, tailX, tailY, tailZ,
                baseWidth * 1.0f, r, g, b, midAlpha, (int)(midAlpha * 0.05f));

        // Outer glow layer - dim and wide
        int glowAlpha = (int)(baseAlpha * 0.3f);
        renderBillboardSpark(consumer, pose, worldToModel, cameraPos,
                headX, headY, headZ, tailX, tailY, tailZ,
                baseWidth * 2.0f, r, g, b, glowAlpha, 0);
    }

    /**
     * Get camera position in world space
     */
    public static Vec3 getCameraPosition() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer != null && mc.gameRenderer.getMainCamera() != null) {
            return mc.gameRenderer.getMainCamera().getPosition();
        }
        // Fallback to player position
        if (mc.player != null) {
            return mc.player.getEyePosition();
        }
        return Vec3.ZERO;
    }
}
