package com.nick.realisticfirework.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import com.nick.realisticfirework.data.SparklerData;

/**
 * Handles all glow and lighting effect rendering for the sparkler.
 * Separates glow rendering logic from the main renderer.
 */
public class GlowRenderer {

    /**
     * Render the emissive glow at the burn point using additive blending (lightning render type)
     * Creates a SMOOTH SUN-LIKE glow using gradient quads (bright center, transparent edges)
     * @param fadeFactor 0-1 value to scale down the glow as sparkler finishes burning
     */
    public static void renderEmissiveGlow(PoseStack.Pose pose, VertexConsumer consumer,
                                     ItemDisplayContext displayContext,
                                     SparklerState state,
                                     float burnY,
                                     float fadeFactor) {
        if (displayContext == ItemDisplayContext.GUI) {
            return;
        }

        // Apply the same transformation as main rendering
        Matrix4f poseMat = new Matrix4f(pose.pose());
        poseMat.translate(0.5f, 0.5f, 0.5f);
        poseMat.rotateZ((float)Math.toRadians(-45.0));
        poseMat.translate(-0.5f, -0.5f, -0.5f);

        float stickX = 0.5f;
        float stickZ = 0.5f;

        // Skip rendering if fade is too low
        if (fadeFactor < 0.01f) {
            return;
        }

        // Add subtle flicker/pulsing based on time
        float time = (System.nanoTime() / 1_000_000_000.0f);
        float flicker = 0.90f + 0.10f * (float)(
            Math.sin(time * 25) * 0.4 +
            Math.sin(time * 41) * 0.3 +
            Math.sin(time * 67) * 0.3
        );
        flicker = Math.max(0.8f, Math.min(1.0f, flicker));

        float intensity = fadeFactor * flicker;

        // ========== SMOOTH SUN GLOW - Many overlapping gradient discs ==========
        // Each disc has bright center, transparent edges for smooth blending
        // Use LOW alpha values - additive blending will accumulate brightness
        // This prevents the "forcefield" effect that hides the rod

        // Maximum glow radius
        float maxRadius = 0.12f * intensity;

        // Render 20+ concentric gradient layers for ultra-smooth falloff
        int numLayers = 24;
        for (int i = numLayers - 1; i >= 0; i--) {
            float t = (float) i / (numLayers - 1);  // 0 = innermost, 1 = outermost

            // Radius increases with layer
            float layerRadius = maxRadius * (0.08f + t * 0.92f);

            // Color gradient: white core -> yellow -> orange -> red edge
            float r, g, b;
            if (t < 0.15f) {
                // Core: pure white
                r = 1.0f; g = 1.0f; b = 1.0f;
            } else if (t < 0.35f) {
                // Inner: white to yellow
                float lt = (t - 0.15f) / 0.20f;
                r = 1.0f;
                g = 1.0f - lt * 0.1f;
                b = 1.0f - lt * 0.5f;
            } else if (t < 0.6f) {
                // Middle: yellow to orange
                float lt = (t - 0.35f) / 0.25f;
                r = 1.0f;
                g = 0.9f - lt * 0.35f;
                b = 0.5f - lt * 0.35f;
            } else {
                // Outer: orange to deep red
                float lt = (t - 0.6f) / 0.4f;
                r = 1.0f - lt * 0.15f;
                g = 0.55f - lt * 0.4f;
                b = 0.15f - lt * 0.1f;
            }

            // Alpha: MUCH LOWER values - additive blending stacks them
            // This prevents the glow from obscuring the rod underneath
            float baseAlpha = (1.0f - t * t) * 0.12f;  // Reduced from 0.35f
            float alpha = baseAlpha * intensity;

            // Skip nearly invisible layers
            if (alpha < 0.003f) continue;

            // Render this layer as gradient discs facing multiple directions
            renderSmoothGlowLayer(consumer, poseMat, stickX, burnY, stickZ,
                                  layerRadius, r, g, b, alpha);
        }

        // Core point - still bright but not opaque
        renderSmoothGlowLayer(consumer, poseMat, stickX, burnY, stickZ,
                              0.008f * intensity, 1.0f, 1.0f, 1.0f, 0.4f * intensity);
    }

    /**
     * Render a smooth glow layer as gradient discs (bright center, transparent edge)
     * Uses triangular fan pattern for proper gradient interpolation
     */
    public static void renderSmoothGlowLayer(VertexConsumer consumer, Matrix4f pose,
                                       float x, float y, float z, float radius,
                                       float r, float g, float b, float centerAlpha) {
        if (radius < 0.001f || centerAlpha < 0.005f) return;

        // Render gradient discs facing multiple directions for volumetric look
        // Each disc: bright center, transparent edge
        renderGradientDisc(consumer, pose, x, y, z, radius, r, g, b, centerAlpha, 0, 0, 1);
        renderGradientDisc(consumer, pose, x, y, z, radius, r, g, b, centerAlpha, 0, 0, -1);
        renderGradientDisc(consumer, pose, x, y, z, radius, r, g, b, centerAlpha, 0, 1, 0);
        renderGradientDisc(consumer, pose, x, y, z, radius, r, g, b, centerAlpha, 0, -1, 0);
        renderGradientDisc(consumer, pose, x, y, z, radius, r, g, b, centerAlpha, 1, 0, 0);
        renderGradientDisc(consumer, pose, x, y, z, radius, r, g, b, centerAlpha, -1, 0, 0);

        // Diagonal directions for smoother appearance
        float d = 0.707f;
        renderGradientDisc(consumer, pose, x, y, z, radius, r, g, b, centerAlpha, d, d, 0);
        renderGradientDisc(consumer, pose, x, y, z, radius, r, g, b, centerAlpha, d, -d, 0);
        renderGradientDisc(consumer, pose, x, y, z, radius, r, g, b, centerAlpha, -d, d, 0);
        renderGradientDisc(consumer, pose, x, y, z, radius, r, g, b, centerAlpha, -d, -d, 0);
        renderGradientDisc(consumer, pose, x, y, z, radius, r, g, b, centerAlpha, d, 0, d);
        renderGradientDisc(consumer, pose, x, y, z, radius, r, g, b, centerAlpha, d, 0, -d);
        renderGradientDisc(consumer, pose, x, y, z, radius, r, g, b, centerAlpha, -d, 0, d);
        renderGradientDisc(consumer, pose, x, y, z, radius, r, g, b, centerAlpha, -d, 0, -d);
        renderGradientDisc(consumer, pose, x, y, z, radius, r, g, b, centerAlpha, 0, d, d);
        renderGradientDisc(consumer, pose, x, y, z, radius, r, g, b, centerAlpha, 0, d, -d);
        renderGradientDisc(consumer, pose, x, y, z, radius, r, g, b, centerAlpha, 0, -d, d);
        renderGradientDisc(consumer, pose, x, y, z, radius, r, g, b, centerAlpha, 0, -d, -d);
    }

    /**
     * Render a gradient disc with bright center and transparent edges
     * Uses quad strips radiating from center for smooth gradient
     */
    public static void renderGradientDisc(VertexConsumer consumer, Matrix4f pose,
                                    float x, float y, float z, float radius,
                                    float r, float g, float b, float centerAlpha,
                                    float nx, float ny, float nz) {
        // Create basis vectors for the disc plane
        float upX, upY, upZ;
        if (Math.abs(ny) > 0.9f) {
            upX = 1; upY = 0; upZ = 0;
        } else {
            upX = 0; upY = 1; upZ = 0;
        }

        // right = up × normal
        float rightX = upY * nz - upZ * ny;
        float rightY = upZ * nx - upX * nz;
        float rightZ = upX * ny - upY * nx;
        float rightLen = (float)Math.sqrt(rightX * rightX + rightY * rightY + rightZ * rightZ);
        if (rightLen > 0.001f) {
            rightX /= rightLen; rightY /= rightLen; rightZ /= rightLen;
        }

        // actualUp = normal × right
        float actualUpX = ny * rightZ - nz * rightY;
        float actualUpY = nz * rightX - nx * rightZ;
        float actualUpZ = nx * rightY - ny * rightX;

        // High resolution disc - 16 segments for smooth circle
        int segments = 16;
        // 3 rings for gradient: center, mid, edge
        int rings = 3;
        float[] ringRadii = {0.0f, 0.5f, 1.0f};  // As fraction of radius
        float[] ringAlphas = {centerAlpha, centerAlpha * 0.4f, 0.0f};  // Gradient falloff

        // Render ring by ring
        for (int ring = 0; ring < rings - 1; ring++) {
            float innerR = ringRadii[ring] * radius;
            float outerR = ringRadii[ring + 1] * radius;
            float innerA = ringAlphas[ring];
            float outerA = ringAlphas[ring + 1];

            for (int seg = 0; seg < segments; seg++) {
                float angle1 = (float)(seg * 2 * Math.PI / segments);
                float angle2 = (float)((seg + 1) * 2 * Math.PI / segments);

                float cos1 = (float)Math.cos(angle1);
                float sin1 = (float)Math.sin(angle1);
                float cos2 = (float)Math.cos(angle2);
                float sin2 = (float)Math.sin(angle2);

                // Inner ring vertices
                float ix1 = x + (rightX * cos1 + actualUpX * sin1) * innerR;
                float iy1 = y + (rightY * cos1 + actualUpY * sin1) * innerR;
                float iz1 = z + (rightZ * cos1 + actualUpZ * sin1) * innerR;

                float ix2 = x + (rightX * cos2 + actualUpX * sin2) * innerR;
                float iy2 = y + (rightY * cos2 + actualUpY * sin2) * innerR;
                float iz2 = z + (rightZ * cos2 + actualUpZ * sin2) * innerR;

                // Outer ring vertices
                float ox1 = x + (rightX * cos1 + actualUpX * sin1) * outerR;
                float oy1 = y + (rightY * cos1 + actualUpY * sin1) * outerR;
                float oz1 = z + (rightZ * cos1 + actualUpZ * sin1) * outerR;

                float ox2 = x + (rightX * cos2 + actualUpX * sin2) * outerR;
                float oy2 = y + (rightY * cos2 + actualUpY * sin2) * outerR;
                float oz2 = z + (rightZ * cos2 + actualUpZ * sin2) * outerR;

                // Front face quad (inner1, inner2, outer2, outer1)
                consumer.addVertex(pose, ix1, iy1, iz1).setColor(r, g, b, innerA);
                consumer.addVertex(pose, ix2, iy2, iz2).setColor(r, g, b, innerA);
                consumer.addVertex(pose, ox2, oy2, oz2).setColor(r, g, b, outerA);
                consumer.addVertex(pose, ox1, oy1, oz1).setColor(r, g, b, outerA);

                // Back face (reversed)
                consumer.addVertex(pose, ox1, oy1, oz1).setColor(r, g, b, outerA);
                consumer.addVertex(pose, ox2, oy2, oz2).setColor(r, g, b, outerA);
                consumer.addVertex(pose, ix2, iy2, iz2).setColor(r, g, b, innerA);
                consumer.addVertex(pose, ix1, iy1, iz1).setColor(r, g, b, innerA);
            }
        }
    }

    /**
     * Render light rays emanating from the glowing core.
     * Creates visible light beams radiating outward from the combustion point.
     * @param fadeFactor overall fade factor (0-1) for end-of-burn fadeout
     */
    public static void renderLightRays(VertexConsumer consumer, Matrix4f pose,
                                 float centerX, float centerZ, float burnY,
                                 SparklerState state, float fadeFactor) {
        // Number of light rays - reduced as fade increases
        int numRays = (int)(12 * fadeFactor);
        if (numRays < 3) numRays = 3;  // Minimum rays

        // Ray properties - shrink with fade
        float rayLength = 0.08f * fadeFactor;  // How far rays extend
        float rayWidth = 0.003f * fadeFactor;  // Width at base
        float rayOriginY = burnY + 0.003f;  // Slightly above burn point

        for (int i = 0; i < numRays; i++) {
            // Use noise for varied ray angles and lengths
            float noise1 = state.sampleNoise(i * 17.3f + state.burnProgress * 100);
            float noise2 = state.sampleNoise(i * 29.7f + state.burnProgress * 80);
            float noise3 = state.sampleNoise(i * 41.1f);

            // Base angle with some randomness
            float baseAngle = (float)(i * 2 * Math.PI / numRays);
            float angle = baseAngle + (noise1 - 0.5f) * 0.4f;

            // Vertical angle - mostly horizontal with some variation
            float vertAngle = (noise2 - 0.5f) * 0.6f;  // -0.3 to +0.3 radians

            // Ray length varies
            float thisRayLength = rayLength * (0.5f + noise3 * 0.7f);

            // Calculate ray direction
            float cosH = (float)Math.cos(angle);
            float sinH = (float)Math.sin(angle);
            float cosV = (float)Math.cos(vertAngle);
            float sinV = (float)Math.sin(vertAngle);

            // Ray end point
            float endX = centerX + cosH * thisRayLength * cosV;
            float endY = rayOriginY + sinV * thisRayLength;
            float endZ = centerZ + sinH * thisRayLength * cosV;

            // Ray width perpendicular to direction (billboard-ish)
            float perpX = -sinH * rayWidth;
            float perpZ = cosH * rayWidth;

            // Colors - bright white/yellow at center, fading to transparent
            // Core: intense white - alpha scaled by fade factor
            float coreR = 1.0f, coreG = 1.0f, coreB = 0.9f, coreA = 0.9f * fadeFactor;
            // Tip: transparent
            float tipR = 1.0f, tipG = 0.9f, tipB = 0.6f, tipA = 0.0f;

            // Render ray as a quad that tapers to a point
            // Two triangles forming a tapered beam

            // Triangle 1: left side of ray
            consumer.addVertex(pose, centerX - perpX * 0.5f, rayOriginY, centerZ - perpZ * 0.5f)
                .setColor(coreR, coreG, coreB, coreA);
            consumer.addVertex(pose, centerX + perpX * 0.5f, rayOriginY, centerZ + perpZ * 0.5f)
                .setColor(coreR, coreG, coreB, coreA);
            consumer.addVertex(pose, endX, endY, endZ)
                .setColor(tipR, tipG, tipB, tipA);
            consumer.addVertex(pose, endX, endY, endZ)
                .setColor(tipR, tipG, tipB, tipA);  // Degenerate to make quad

            // Backface
            consumer.addVertex(pose, endX, endY, endZ)
                .setColor(tipR, tipG, tipB, tipA);
            consumer.addVertex(pose, centerX + perpX * 0.5f, rayOriginY, centerZ + perpZ * 0.5f)
                .setColor(coreR, coreG, coreB, coreA);
            consumer.addVertex(pose, centerX - perpX * 0.5f, rayOriginY, centerZ - perpZ * 0.5f)
                .setColor(coreR, coreG, coreB, coreA);
            consumer.addVertex(pose, endX, endY, endZ)
                .setColor(tipR, tipG, tipB, tipA);  // Degenerate
        }
    }

    /**
     * Render large atmospheric glow at the burn point.
     * Creates brilliant white-hot core with large diffuse halo.
     * Mimics the look of an overexposed light source.
     */
    public static void renderSparkGlows(PoseStack.Pose pose, VertexConsumer consumer,
                                   ItemDisplayContext displayContext,
                                   SparklerState state,
                                   Matrix4f worldToModel,
                                   float fadeFactor) {
        if (displayContext == ItemDisplayContext.GUI) {
            return;
        }

        Matrix4f poseMat = new Matrix4f(pose.pose());
        poseMat.translate(0.5f, 0.5f, 0.5f);
        poseMat.rotateZ((float)Math.toRadians(-45.0));
        poseMat.translate(-0.5f, -0.5f, -0.5f);

        float stickX = 0.5f;
        float stickZ = 0.5f;
        float stickTipY = 0.9f;
        float stickBaseY = 0.1f;
        float totalHeight = stickTipY - stickBaseY;
        float meltProgress = state.getAverageMeltHeight();
        float burnY = stickTipY - meltProgress * totalHeight;

        if (fadeFactor > 0.01f && state.burnProgress > 0.01f && state.burnProgress < SparklerData.MAX_BURN_PROGRESS) {

            float time = (System.nanoTime() / 1_000_000_000.0f);
            // Subtle flicker - less dramatic than before
            float flicker = 0.95f + 0.05f * (float)(
                Math.sin(time * 31) * 0.5 +
                Math.sin(time * 47) * 0.3 +
                Math.sin(time * 67) * 0.2
            );

            float intensity = fadeFactor * flicker;

            // Render the large atmospheric glow
            renderAtmosphericGlow(consumer, poseMat, stickX, burnY, stickZ, intensity, time);
        }
    }

    /**
     * Render large atmospheric glow effect.
     * Creates the brilliant white-hot core with large diffuse halo like the reference image.
     * Uses billboard technique for perfect spherical appearance.
     */
    public static void renderAtmosphericGlow(VertexConsumer consumer, Matrix4f pose,
                                              float cx, float cy, float cz,
                                              float intensity, float time) {
        // Get camera direction for billboard
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        float yaw = camera.getYRot();
        float pitch = camera.getXRot();
        float yawRad = (float)Math.toRadians(yaw);
        float pitchRad = (float)Math.toRadians(pitch);

        // Direction toward camera
        float dirWorldX = (float)(Math.sin(yawRad) * Math.cos(pitchRad));
        float dirWorldY = (float)(Math.sin(pitchRad));
        float dirWorldZ = (float)(-Math.cos(yawRad) * Math.cos(pitchRad));

        // Transform to model space
        Matrix4f invPose = new Matrix4f(pose).invert();
        Vector4f dirModel = new Vector4f(dirWorldX, dirWorldY, dirWorldZ, 0.0f);
        dirModel.mul(invPose);

        float dirX = dirModel.x;
        float dirY = dirModel.y;
        float dirZ = dirModel.z;
        float dirLen = (float)Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (dirLen > 0.0001f) {
            dirX /= dirLen; dirY /= dirLen; dirZ /= dirLen;
        }

        // Create billboard basis vectors
        float upX = 0, upY = 1, upZ = 0;
        if (Math.abs(dirY) > 0.99f) {
            upX = 0; upY = 0; upZ = 1;
        }

        float rightX = upY * dirZ - upZ * dirY;
        float rightY = upZ * dirX - upX * dirZ;
        float rightZ = upX * dirY - upY * dirX;
        float rightLen = (float)Math.sqrt(rightX * rightX + rightY * rightY + rightZ * rightZ);
        if (rightLen > 0.0001f) {
            rightX /= rightLen; rightY /= rightLen; rightZ /= rightLen;
        }

        float actualUpX = dirY * rightZ - dirZ * rightY;
        float actualUpY = dirZ * rightX - dirX * rightZ;
        float actualUpZ = dirX * rightY - dirY * rightX;

        int segments = 32;

        // ========== LAYER DEFINITIONS ==========
        // Reference image: COMPACT intense white-hot core with rapid falloff
        // The glow should be concentrated, not spread out

        // Layer structure: {radiusMultiplier, R, G, B, alpha}
        // Colors: brilliant white core -> warm yellow -> soft amber falloff
        float[][] layers = {
            // Layer 0: INTENSE white-hot core (tiny, maximum brightness)
            {0.1f,   1.0f, 1.0f, 1.0f, 0.99f},
            // Layer 1: Bright white
            {0.2f,   1.0f, 1.0f, 0.98f, 0.90f},
            // Layer 2: Warm white
            {0.35f,  1.0f, 0.98f, 0.92f, 0.70f},
            // Layer 3: Cream yellow
            {0.55f,  1.0f, 0.95f, 0.80f, 0.50f},
            // Layer 4: Warm yellow
            {0.8f,   1.0f, 0.90f, 0.65f, 0.32f},
            // Layer 5: Golden
            {1.1f,   1.0f, 0.85f, 0.50f, 0.18f},
            // Layer 6: Soft amber (faint)
            {1.5f,   1.0f, 0.78f, 0.40f, 0.08f},
            // Layer 7: Outer haze (very faint)
            {2.0f,   1.0f, 0.70f, 0.35f, 0.03f},
        };

        // Base radius - SMALLER for concentrated glow (was 0.06f, now 0.035f)
        float baseRadius = 0.035f;

        // Apply intensity to alphas
        for (int l = 0; l < layers.length; l++) {
            layers[l][4] *= intensity;
        }

        // Render from outermost to innermost (proper depth for additive)
        for (int l = layers.length - 1; l >= 0; l--) {
            float layerRadius = baseRadius * layers[l][0];
            float cR = layers[l][1];
            float cG = layers[l][2];
            float cB = layers[l][3];
            float cA = layers[l][4];

            if (cA < 0.001f) continue;

            // Render billboard disc with gradient (center bright, edge transparent)
            for (int i = 0; i < segments; i++) {
                float angle1 = (float)(i * 2 * Math.PI / segments);
                float angle2 = (float)((i + 1) * 2 * Math.PI / segments);

                float cos1 = (float)Math.cos(angle1), sin1 = (float)Math.sin(angle1);
                float cos2 = (float)Math.cos(angle2), sin2 = (float)Math.sin(angle2);

                float edge1X = cx + (cos1 * rightX + sin1 * actualUpX) * layerRadius;
                float edge1Y = cy + (cos1 * rightY + sin1 * actualUpY) * layerRadius;
                float edge1Z = cz + (cos1 * rightZ + sin1 * actualUpZ) * layerRadius;

                float edge2X = cx + (cos2 * rightX + sin2 * actualUpX) * layerRadius;
                float edge2Y = cy + (cos2 * rightY + sin2 * actualUpY) * layerRadius;
                float edge2Z = cz + (cos2 * rightZ + sin2 * actualUpZ) * layerRadius;

                // Quad from center to edge - gradient from center color to transparent
                // RenderType.lightning() expects quads, so we duplicate the last vertex
                consumer.addVertex(pose, cx, cy, cz).setColor(cR, cG, cB, cA);
                consumer.addVertex(pose, edge1X, edge1Y, edge1Z).setColor(cR, cG, cB, 0f);
                consumer.addVertex(pose, edge2X, edge2Y, edge2Z).setColor(cR, cG, cB, 0f);
                consumer.addVertex(pose, edge2X, edge2Y, edge2Z).setColor(cR, cG, cB, 0f); // Degenerate quad
            }
        }

        // ========== EXTRA BRIGHT CORE PASS ==========
        // Stack additional very small, very bright layers at center for "overexposed" effect
        float coreRadius = baseRadius * 0.08f;
        float coreAlpha = intensity * 0.98f;

        // Multiple stacked core layers for intense white-hot center
        for (int pass = 0; pass < 4; pass++) {
            float passRadius = coreRadius * (1.0f + pass * 0.25f);
            float passAlpha = coreAlpha * (1.0f - pass * 0.15f);

            for (int i = 0; i < segments; i++) {
                float angle1 = (float)(i * 2 * Math.PI / segments);
                float angle2 = (float)((i + 1) * 2 * Math.PI / segments);

                float cos1 = (float)Math.cos(angle1), sin1 = (float)Math.sin(angle1);
                float cos2 = (float)Math.cos(angle2), sin2 = (float)Math.sin(angle2);

                float edge1X = cx + (cos1 * rightX + sin1 * actualUpX) * passRadius;
                float edge1Y = cy + (cos1 * rightY + sin1 * actualUpY) * passRadius;
                float edge1Z = cz + (cos1 * rightZ + sin1 * actualUpZ) * passRadius;

                float edge2X = cx + (cos2 * rightX + sin2 * actualUpX) * passRadius;
                float edge2Y = cy + (cos2 * rightY + sin2 * actualUpY) * passRadius;
                float edge2Z = cz + (cos2 * rightZ + sin2 * actualUpZ) * passRadius;

                // Pure white core - quads for RenderType.lightning()
                consumer.addVertex(pose, cx, cy, cz).setColor(1f, 1f, 1f, passAlpha);
                consumer.addVertex(pose, edge1X, edge1Y, edge1Z).setColor(1f, 1f, 0.95f, 0f);
                consumer.addVertex(pose, edge2X, edge2Y, edge2Z).setColor(1f, 1f, 0.95f, 0f);
                consumer.addVertex(pose, edge2X, edge2Y, edge2Z).setColor(1f, 1f, 0.95f, 0f); // Degenerate quad
            }
        }
    }

    /**
     * Render ALIVE animated flame - dancing, flickering, breathing fire!
     * With darker reds/oranges and circular ambient light glow.
     * @deprecated Use renderAtmosphericGlow instead for reference-matching glow
     */
    public static void renderSmoothCircularGlow(VertexConsumer consumer, Matrix4f pose,
                                           float cx, float cy, float cz,
                                           float maxRadius, float intensity) {
        // Time for animation - different frequencies for organic movement
        float time = (System.nanoTime() / 1_000_000_000.0f);

        // === CIRCULAR AMBIENT GLOW - light radiating outward ===
        renderAmbientLightGlow(consumer, pose, cx, cy, cz, maxRadius, intensity, time);

        // === FLAME TONGUES ===
        int numTongues = 5;

        for (int t = 0; t < numTongues; t++) {
            // Each tongue has its own phase offset
            float tonguePhase = t * 1.2566f; // 2PI/5

            // Animated height - each tongue pulses independently
            float heightPulse = 0.7f + 0.4f * (float)Math.sin(time * 15.0 + tonguePhase);
            float flameHeight = maxRadius * 2.5f * heightPulse;

            // Base width with breathing animation
            float breathe = 0.8f + 0.3f * (float)Math.sin(time * 12.0 + tonguePhase * 0.7);
            float baseWidth = maxRadius * 0.5f * breathe;

            // Tongue sways side to side as it rises
            float swayFreq = 18.0f + t * 2.0f;
            float swayAmount = maxRadius * 0.3f;

            // Rotation around center - tongues spread out and spin slowly
            float baseAngle = tonguePhase + time * 0.5f;
            float baseCosR = (float)Math.cos(baseAngle);
            float baseSinR = (float)Math.sin(baseAngle);

            // Offset this tongue from center
            float tongueOffsetDist = maxRadius * 0.15f;
            float tcx = cx + baseCosR * tongueOffsetDist;
            float tcz = cz + baseSinR * tongueOffsetDist;

            // Render this flame tongue as vertical segments
            int segments = 6;
            for (int seg = 0; seg < segments; seg++) {
                float bottomT = (float)seg / segments;
                float topT = (float)(seg + 1) / segments;

                // Y positions with upward motion animation
                float riseWave = (float)Math.sin(time * 20.0 + tonguePhase - bottomT * 3.0f) * 0.1f;
                float y1 = cy + bottomT * flameHeight + riseWave * flameHeight * bottomT;
                float y2 = cy + topT * flameHeight + riseWave * flameHeight * topT;

                // Width tapers sharply at top
                float taperPower = 2.0f;
                float bottomWidth = baseWidth * (float)Math.pow(1.0f - bottomT, 1.0/taperPower);
                float topWidth = baseWidth * (float)Math.pow(1.0f - topT, 1.0/taperPower);

                // Sway increases with height
                float sway1 = (float)Math.sin(time * swayFreq + bottomT * 2.0f) * swayAmount * bottomT;
                float sway2 = (float)Math.sin(time * swayFreq + topT * 2.0f) * swayAmount * topT;
                float swayZ1 = (float)Math.cos(time * swayFreq * 0.7f + bottomT * 2.0f) * swayAmount * bottomT * 0.5f;
                float swayZ2 = (float)Math.cos(time * swayFreq * 0.7f + topT * 2.0f) * swayAmount * topT * 0.5f;

                // DARKER color palette - orange core to deep red tips
                float r1, g1, b1, a1;
                float r2, g2, b2, a2;

                float flicker1 = 0.85f + 0.15f * (float)Math.sin(time * 25.0 + tonguePhase + bottomT * 5.0);
                float flicker2 = 0.85f + 0.15f * (float)Math.sin(time * 25.0 + tonguePhase + topT * 5.0);

                if (bottomT < 0.2f) {
                    r1 = 1.0f; g1 = 0.7f; b1 = 0.2f; a1 = 0.95f * flicker1;
                } else if (bottomT < 0.5f) {
                    float ft = (bottomT - 0.2f) / 0.3f;
                    r1 = 1.0f; g1 = 0.7f - ft * 0.3f; b1 = 0.2f - ft * 0.15f; a1 = (0.9f - ft * 0.1f) * flicker1;
                } else if (bottomT < 0.75f) {
                    float ft = (bottomT - 0.5f) / 0.25f;
                    r1 = 1.0f - ft * 0.1f; g1 = 0.4f - ft * 0.2f; b1 = 0.05f; a1 = (0.8f - ft * 0.15f) * flicker1;
                } else {
                    float ft = (bottomT - 0.75f) / 0.25f;
                    r1 = 0.9f - ft * 0.2f; g1 = 0.2f - ft * 0.15f; b1 = 0.02f; a1 = (0.65f - ft * 0.5f) * flicker1;
                }

                if (topT < 0.2f) {
                    r2 = 1.0f; g2 = 0.7f; b2 = 0.2f; a2 = 0.95f * flicker2;
                } else if (topT < 0.5f) {
                    float ft = (topT - 0.2f) / 0.3f;
                    r2 = 1.0f; g2 = 0.7f - ft * 0.3f; b2 = 0.2f - ft * 0.15f; a2 = (0.9f - ft * 0.1f) * flicker2;
                } else if (topT < 0.75f) {
                    float ft = (topT - 0.5f) / 0.25f;
                    r2 = 1.0f - ft * 0.1f; g2 = 0.4f - ft * 0.2f; b2 = 0.05f; a2 = (0.8f - ft * 0.15f) * flicker2;
                } else {
                    float ft = (topT - 0.75f) / 0.25f;
                    r2 = 0.9f - ft * 0.2f; g2 = 0.2f - ft * 0.15f; b2 = 0.02f; a2 = (0.65f - ft * 0.5f) * flicker2;
                }

                a1 *= intensity * 0.6f;
                a2 *= intensity * 0.6f;

                if (a1 < 0.01f && a2 < 0.01f) continue;

                // Render on two perpendicular planes for volume
                for (int plane = 0; plane < 2; plane++) {
                    float planeAngle = baseAngle + plane * 1.5708f;
                    float pCos = (float)Math.cos(planeAngle);
                    float pSin = (float)Math.sin(planeAngle);

                    float x1L = tcx + sway1 - bottomWidth * pCos;
                    float z1L = tcz + swayZ1 - bottomWidth * pSin;
                    float x1R = tcx + sway1 + bottomWidth * pCos;
                    float z1R = tcz + swayZ1 + bottomWidth * pSin;

                    float x2L = tcx + sway2 - topWidth * pCos;
                    float z2L = tcz + swayZ2 - topWidth * pSin;
                    float x2R = tcx + sway2 + topWidth * pCos;
                    float z2R = tcz + swayZ2 + topWidth * pSin;

                    consumer.addVertex(pose, x1L, y1, z1L).setColor(r1, g1, b1, a1);
                    consumer.addVertex(pose, x1R, y1, z1R).setColor(r1, g1, b1, a1);
                    consumer.addVertex(pose, x2R, y2, z2R).setColor(r2, g2, b2, a2);
                    consumer.addVertex(pose, x2L, y2, z2L).setColor(r2, g2, b2, a2);

                    consumer.addVertex(pose, x1R, y1, z1R).setColor(r1, g1, b1, a1);
                    consumer.addVertex(pose, x1L, y1, z1L).setColor(r1, g1, b1, a1);
                    consumer.addVertex(pose, x2L, y2, z2L).setColor(r2, g2, b2, a2);
                    consumer.addVertex(pose, x2R, y2, z2R).setColor(r2, g2, b2, a2);
                }
            }
        }

        // Pulsing bright orange core at base
        float corePulse = 0.8f + 0.2f * (float)Math.sin(time * 30.0);
        float coreSize = maxRadius * 0.2f * corePulse;
        float coreA = intensity * 0.85f * corePulse;

        consumer.addVertex(pose, cx - coreSize, cy, cz).setColor(1f, 0.8f, 0.3f, coreA);
        consumer.addVertex(pose, cx + coreSize, cy, cz).setColor(1f, 0.8f, 0.3f, coreA);
        consumer.addVertex(pose, cx + coreSize * 0.5f, cy + coreSize * 2, cz).setColor(1f, 0.5f, 0.1f, coreA * 0.4f);
        consumer.addVertex(pose, cx - coreSize * 0.5f, cy + coreSize * 2, cz).setColor(1f, 0.5f, 0.1f, coreA * 0.4f);

        consumer.addVertex(pose, cx, cy, cz - coreSize).setColor(1f, 0.8f, 0.3f, coreA);
        consumer.addVertex(pose, cx, cy, cz + coreSize).setColor(1f, 0.8f, 0.3f, coreA);
        consumer.addVertex(pose, cx, cy + coreSize * 2, cz + coreSize * 0.5f).setColor(1f, 0.5f, 0.1f, coreA * 0.4f);
        consumer.addVertex(pose, cx, cy + coreSize * 2, cz - coreSize * 0.5f).setColor(1f, 0.5f, 0.1f, coreA * 0.4f);
    }

    /**
     * Render gradient glow as a BILLBOARD - a single disc that always faces the camera.
     * This creates a perfect "sphere" illusion because the player can never see the edge.
     *
     * The "good tech" - center vertex is bright, edge vertices are transparent (alpha=0),
     * GPU interpolates smoothly creating a soft glow effect.
     */
    public static void renderAmbientLightGlow(VertexConsumer consumer, Matrix4f pose,
                                         float cx, float cy, float cz,
                                         float maxRadius, float intensity, float time) {
        float glowCy = cy;

        // === MULTI-LAYER SMOOTH GRADIENT GLOW ===
        // Multiple concentric rings using the "good tech" (center bright → edge transparent)
        // Each layer's edge color matches the next layer's center color for seamless blending
        // Colors transition: white-hot → yellow → orange → deep orange → faint red

        int segments = 32;  // Smooth circle

        // Define layers: {radiusMult, cR, cG, cB, cAlpha}
        // Edge of each layer fades to transparent, next layer picks up the color
        float[][] layers = {
            // Layer 0: Tiny intense white-hot core
            {0.4f,   1.0f, 1.0f, 1.0f, 0.95f},
            // Layer 1: Warm white transitioning to yellow
            {1.0f,   1.0f, 0.97f, 0.9f, 0.7f},
            // Layer 2: Yellow-orange
            {2.0f,   1.0f, 0.9f, 0.6f, 0.45f},
            // Layer 3: Orange
            {3.5f,   1.0f, 0.75f, 0.35f, 0.25f},
            // Layer 4: Deep orange
            {5.5f,   1.0f, 0.55f, 0.2f, 0.12f},
            // Layer 5: Faint outer glow
            {8.0f,   1.0f, 0.4f, 0.1f, 0.04f},
        };

        // Apply intensity to all alpha values
        for (int l = 0; l < layers.length; l++) {
            layers[l][4] *= intensity;
        }

        // Use outermost radius for billboard calculation
        float radius = maxRadius * layers[layers.length - 1][0];

        // === BILLBOARD: Make disc always face the camera ===
        //
        // The pose matrix is LOCAL (item space with -45° rotation), not world space.
        // So we can't use camera.position() to calculate direction.
        //
        // Instead, use camera's LOOK VECTOR directly - this tells us which way the camera
        // is facing in world space. The billboard should face OPPOSITE to this direction.
        // Then transform that world-space direction into model space.

        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();

        // Calculate the camera's look direction from yaw/pitch angles
        // The billboard should face TOWARD the camera (opposite of look direction)
        // In 1.21.1, Camera uses standard getters: getYRot() and getXRot()
        float yaw = camera.getYRot();
        float pitch = camera.getXRot();
        float yawRad = (float)Math.toRadians(yaw);
        float pitchRad = (float)Math.toRadians(pitch);

        // Camera look direction (standard Minecraft convention)
        // lookX = -sin(yaw) * cos(pitch), lookY = -sin(pitch), lookZ = cos(yaw) * cos(pitch)
        // We want OPPOSITE direction (toward camera), so negate:
        float dirWorldX = (float)(Math.sin(yawRad) * Math.cos(pitchRad));
        float dirWorldY = (float)(Math.sin(pitchRad));
        float dirWorldZ = (float)(-Math.cos(yawRad) * Math.cos(pitchRad));

        // Transform this world-space direction into model space
        // We need to "undo" the pose transformations to get the direction in local coords
        Matrix4f invPose = new Matrix4f(pose).invert();

        // Transform direction vector (w=0 for direction, not point)
        Vector4f dirModel = new Vector4f(dirWorldX, dirWorldY, dirWorldZ, 0.0f);
        dirModel.mul(invPose);

        float dirX = dirModel.x;
        float dirY = dirModel.y;
        float dirZ = dirModel.z;

        // Normalize (transformation might change length)
        float dirLen = (float)Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (dirLen > 0.0001f) {
            dirX /= dirLen;
            dirY /= dirLen;
            dirZ /= dirLen;
        }

        // Create two perpendicular vectors to form the billboard plane in model space
        // "up" vector - try to use model-space up, but handle edge case
        float upX = 0, upY = 1, upZ = 0;

        // If looking straight up or down in model space, use a different up vector
        if (Math.abs(dirY) > 0.99f) {
            upX = 0; upY = 0; upZ = 1;
        }

        // right = normalize(up × dir)
        float rightX = upY * dirZ - upZ * dirY;
        float rightY = upZ * dirX - upX * dirZ;
        float rightZ = upX * dirY - upY * dirX;
        float rightLen = (float)Math.sqrt(rightX * rightX + rightY * rightY + rightZ * rightZ);
        if (rightLen > 0.0001f) {
            rightX /= rightLen;
            rightY /= rightLen;
            rightZ /= rightLen;
        }

        // actualUp = dir × right
        float actualUpX = dirY * rightZ - dirZ * rightY;
        float actualUpY = dirZ * rightX - dirX * rightZ;
        float actualUpZ = dirX * rightY - dirY * rightX;

        // === Render all layers from outermost to innermost ===
        // Each layer: center is bright with color, edge fades to transparent
        // Layers stack additively creating smooth color transitions
        for (int l = layers.length - 1; l >= 0; l--) {
            float layerRadius = maxRadius * layers[l][0];
            float cR = layers[l][1];
            float cG = layers[l][2];
            float cB = layers[l][3];
            float cA = layers[l][4];

            // Edge is always transparent (the "good tech")
            float eR = cR, eG = cG, eB = cB;
            float eA = 0.0f;

            for (int i = 0; i < segments; i++) {
                float angle1 = (float)(i * 2 * Math.PI / segments);
                float angle2 = (float)((i + 1) * 2 * Math.PI / segments);

                float cos1 = (float)Math.cos(angle1), sin1 = (float)Math.sin(angle1);
                float cos2 = (float)Math.cos(angle2), sin2 = (float)Math.sin(angle2);

                float edge1X = cx + (cos1 * rightX + sin1 * actualUpX) * layerRadius;
                float edge1Y = glowCy + (cos1 * rightY + sin1 * actualUpY) * layerRadius;
                float edge1Z = cz + (cos1 * rightZ + sin1 * actualUpZ) * layerRadius;

                float edge2X = cx + (cos2 * rightX + sin2 * actualUpX) * layerRadius;
                float edge2Y = glowCy + (cos2 * rightY + sin2 * actualUpY) * layerRadius;
                float edge2Z = cz + (cos2 * rightZ + sin2 * actualUpZ) * layerRadius;

                // Quad for RenderType.lightning() - duplicate last vertex for degenerate quad
                consumer.addVertex(pose, cx, glowCy, cz).setColor(cR, cG, cB, cA);
                consumer.addVertex(pose, edge1X, edge1Y, edge1Z).setColor(eR, eG, eB, eA);
                consumer.addVertex(pose, edge2X, edge2Y, edge2Z).setColor(eR, eG, eB, eA);
                consumer.addVertex(pose, edge2X, edge2Y, edge2Z).setColor(eR, eG, eB, eA); // Degenerate quad
            }
        }
    }

    /**
     * Render tiny glow point for spark heads
     */
    public static void renderTinyGlowPoint(VertexConsumer consumer, Matrix4f pose,
                                      float x, float y, float z, float size,
                                      float r, float g, float b, float a) {
        consumer.addVertex(pose, x - size, y - size, z).setColor(r, g, b, a);
        consumer.addVertex(pose, x + size, y - size, z).setColor(r, g, b, a);
        consumer.addVertex(pose, x + size, y + size, z).setColor(r, g, b, a);
        consumer.addVertex(pose, x - size, y + size, z).setColor(r, g, b, a);

        consumer.addVertex(pose, x, y - size, z - size).setColor(r, g, b, a);
        consumer.addVertex(pose, x, y - size, z + size).setColor(r, g, b, a);
        consumer.addVertex(pose, x, y + size, z + size).setColor(r, g, b, a);
        consumer.addVertex(pose, x, y + size, z - size).setColor(r, g, b, a);
    }

    /**
     * Render a tiny bright hotspot - simple billboard quad
     */
    public static void renderTinyHotspot(VertexConsumer consumer, Matrix4f pose,
                                    float x, float y, float z, float size,
                                    float r, float g, float b, float a) {
        // Simple billboard quad facing camera (approximated as facing +Z)
        consumer.addVertex(pose, x - size, y - size, z).setColor(r, g, b, a);
        consumer.addVertex(pose, x + size, y - size, z).setColor(r, g, b, a);
        consumer.addVertex(pose, x + size, y + size, z).setColor(r, g, b, a * 0.5f);
        consumer.addVertex(pose, x - size, y + size, z).setColor(r, g, b, a * 0.5f);

        // Second plane rotated 90 degrees
        consumer.addVertex(pose, x, y - size, z - size).setColor(r, g, b, a);
        consumer.addVertex(pose, x, y - size, z + size).setColor(r, g, b, a);
        consumer.addVertex(pose, x, y + size, z + size).setColor(r, g, b, a * 0.5f);
        consumer.addVertex(pose, x, y + size, z - size).setColor(r, g, b, a * 0.5f);
    }

    /**
     * Render a small glowing burst for a single spark.
     * TRUE 3D laser-like rays shooting in all directions - keeps all visual properties.
     */
    public static void renderSparkGlowDisc(VertexConsumer consumer, Matrix4f pose,
                                      float x, float y, float z, float radius,
                                      float r, float g, float b, float a) {
        // Render thin laser lines radiating outward in TRUE 3D directions
        // Uses golden ratio spiral for even distribution on sphere

        int numLines = 10;  // Number of radiating laser lines
        float lineWidth = radius * 0.1f;  // Very thin lines
        float lineLength = radius * 3.0f;  // Length of each laser

        // Golden ratio for even sphere distribution
        float goldenRatio = (1.0f + (float)Math.sqrt(5.0)) / 2.0f;
        float angleIncrement = (float)Math.PI * 2.0f * goldenRatio;

        for (int i = 0; i < numLines; i++) {
            // Fibonacci sphere distribution for even 3D coverage
            float t = (float)i / (float)(numLines - 1);
            float inclination = (float)Math.acos(1 - 2 * t);  // 0 to PI
            float azimuth = angleIncrement * i;  // Spiral around

            // Direction vector pointing outward from center
            float dirX = (float)(Math.sin(inclination) * Math.cos(azimuth));
            float dirY = (float)(Math.sin(inclination) * Math.sin(azimuth));
            float dirZ = (float)(Math.cos(inclination));

            // Line endpoint
            float endX = x + dirX * lineLength;
            float endY = y + dirY * lineLength;
            float endZ = z + dirZ * lineLength;

            // Create perpendicular vector for quad width
            float perpX, perpY, perpZ;
            if (Math.abs(dirZ) < 0.9f) {
                perpX = -dirY; perpY = dirX; perpZ = 0;
            } else {
                perpX = 0; perpY = -dirZ; perpZ = dirY;
            }
            float perpLen = (float)Math.sqrt(perpX*perpX + perpY*perpY + perpZ*perpZ);
            if (perpLen > 0.001f) { perpX /= perpLen; perpY /= perpLen; perpZ /= perpLen; }
            perpX *= lineWidth; perpY *= lineWidth; perpZ *= lineWidth;

            // Render thin tapered laser quad (bright at center, fades to point at tip)
            consumer.addVertex(pose, x - perpX, y - perpY, z - perpZ).setColor(r, g, b, a);
            consumer.addVertex(pose, x + perpX, y + perpY, z + perpZ).setColor(r, g, b, a);
            consumer.addVertex(pose, endX + perpX * 0.1f, endY + perpY * 0.1f, endZ + perpZ * 0.1f).setColor(r, g, b, a * 0.05f);
            consumer.addVertex(pose, endX - perpX * 0.1f, endY - perpY * 0.1f, endZ - perpZ * 0.1f).setColor(r, g, b, a * 0.05f);
        }
    }

    /**
     * Render an organic, soft glow at the burn point.
     * Uses dense volumetric point cloud with smooth falloff.
     * No geometric shapes - just pure radial gradient with noise modulation.
     */
    public static void renderCentralGlow(VertexConsumer consumer, Matrix4f pose,
                                    float x, float y, float z, float radius,
                                    float r, float g, float b, float a) {
        // Time for animation
        float time = (System.nanoTime() / 1_000_000_000.0f);

        // === SIMPLE VOLUMETRIC GLOW ===
        // Render as a dense cloud of tiny overlapping quads
        // Each quad is very small and low alpha - they blend together smoothly

        // Density layers from center outward
        int layers = 8;
        float maxAlpha = a * 0.6f;

        for (int layer = 0; layer < layers; layer++) {
            float layerT = (float)layer / (float)(layers - 1);
            float layerRadius = radius * (0.1f + layerT * 0.9f);

            // Alpha decreases with distance (Gaussian-like falloff)
            float layerAlpha = maxAlpha * (float)Math.exp(-layerT * layerT * 3.0f);

            // Color gets more saturated (less white) further from center
            float colorT = layerT * 0.7f;
            float lr = r + (1.0f - r) * (1.0f - colorT);
            float lg = g + (1.0f - g) * (1.0f - colorT);
            float lb = b + (1.0f - b) * (1.0f - colorT) * 0.5f;

            // Points per layer - more points further out to maintain density
            int pointsInLayer = 6 + layer * 4;

            for (int i = 0; i < pointsInLayer; i++) {
                // Distribute points on sphere surface with noise offset
                float golden = (float)(Math.PI * (3.0 - Math.sqrt(5.0)));
                float theta = golden * (layer * 20 + i);
                float phi = (float)Math.acos(1.0f - 2.0f * ((float)i + 0.5f) / pointsInLayer);

                // Add organic noise to position
                float noiseScale = 0.3f + layerT * 0.4f;
                float nx = noise3D(theta + time * 2, phi, layer * 0.5f + time) * noiseScale;
                float ny = noise3D(theta + 100, phi + time * 1.5f, layer * 0.5f) * noiseScale;
                float nz = noise3D(theta + 200, phi + 50, layer * 0.5f + time * 1.2f) * noiseScale;

                // Position on noisy sphere
                float px = x + layerRadius * ((float)Math.sin(phi) * (float)Math.cos(theta) + nx);
                float py = y + layerRadius * ((float)Math.cos(phi) + ny);
                float pz = z + layerRadius * ((float)Math.sin(phi) * (float)Math.sin(theta) + nz);

                // Flicker - subtle brightness variation
                float flicker = 0.85f + 0.15f * noise3D(i * 0.5f + time * 10, layer, 0);

                // Render tiny quad at this point
                float pointSize = radius * 0.08f * (1.0f + layerT * 0.5f);
                float pointAlpha = layerAlpha * flicker;

                // Single tiny quad - will blend with others via additive blending
                renderTinyGlowPoint(consumer, pose, px, py, pz, pointSize, lr, lg, lb, pointAlpha);
            }
        }

        // === BRIGHT CORE - overlapping center points ===
        float coreAlpha = a * 0.9f;
        int corePoints = 12;
        for (int i = 0; i < corePoints; i++) {
            float angle = (float)(i * Math.PI * 2 / corePoints) + time * 3;
            float coreOffset = radius * 0.05f * (0.5f + 0.5f * noise3D(angle, time * 5, i));

            float cx = x + coreOffset * (float)Math.cos(angle);
            float cy = y + coreOffset * (float)Math.sin(angle * 0.7f) * 0.3f;
            float cz = z + coreOffset * (float)Math.sin(angle);

            float coreSize = radius * 0.12f;
            renderTinyGlowPoint(consumer, pose, cx, cy, cz, coreSize, 1f, 1f, 0.95f, coreAlpha * 0.5f);
        }

        // Central brightest point
        renderTinyGlowPoint(consumer, pose, x, y, z, radius * 0.15f, 1f, 1f, 1f, coreAlpha * 0.7f);
    }

    /**
     * Simple 3D noise function using sine waves.
     */
    public static float noise3D(float x, float y, float z) {
        float n = (float)Math.sin(x * 1.1f + y * 2.3f + z * 0.7f);
        n += (float)Math.sin(x * 2.7f - y * 1.4f + z * 3.1f) * 0.5f;
        n += (float)Math.sin(x * 4.3f + y * 0.8f - z * 2.2f) * 0.25f;
        return n / 1.75f;
    }

    /**
     * Render a glowing cylinder (matching the inner wire rod shape)
     * Uses additive blending via lightning render type for emissive glow effect
     */
    public static void renderGlowCylinder(VertexConsumer consumer, Matrix4f pose,
                                     float centerX, float centerZ,
                                     float yBottom, float yTop,
                                     float radius, int segments,
                                     float r, float g, float b, float a) {
        for (int i = 0; i < segments; i++) {
            float angle1 = (float) (i * 2 * Math.PI / segments);
            float angle2 = (float) ((i + 1) * 2 * Math.PI / segments);

            float cos1 = (float) Math.cos(angle1);
            float sin1 = (float) Math.sin(angle1);
            float cos2 = (float) Math.cos(angle2);
            float sin2 = (float) Math.sin(angle2);

            // Cylinder wall vertices
            float x1 = centerX + cos1 * radius;
            float z1 = centerZ + sin1 * radius;
            float x2 = centerX + cos2 * radius;
            float z2 = centerZ + sin2 * radius;

            // Cylinder side wall (quad facing outward)
            consumer.addVertex(pose, x1, yBottom, z1).setColor(r, g, b, a);
            consumer.addVertex(pose, x1, yTop, z1).setColor(r, g, b, a);
            consumer.addVertex(pose, x2, yTop, z2).setColor(r, g, b, a);
            consumer.addVertex(pose, x2, yBottom, z2).setColor(r, g, b, a);

            // Also render inward-facing for visibility from inside
            consumer.addVertex(pose, x2, yBottom, z2).setColor(r, g, b, a);
            consumer.addVertex(pose, x2, yTop, z2).setColor(r, g, b, a);
            consumer.addVertex(pose, x1, yTop, z1).setColor(r, g, b, a);
            consumer.addVertex(pose, x1, yBottom, z1).setColor(r, g, b, a);
        }

        // Top cap (disc)
        for (int i = 0; i < segments; i++) {
            float angle1 = (float) (i * 2 * Math.PI / segments);
            float angle2 = (float) ((i + 1) * 2 * Math.PI / segments);

            float cos1 = (float) Math.cos(angle1);
            float sin1 = (float) Math.sin(angle1);
            float cos2 = (float) Math.cos(angle2);
            float sin2 = (float) Math.sin(angle2);

            float x1 = centerX + cos1 * radius;
            float z1 = centerZ + sin1 * radius;
            float x2 = centerX + cos2 * radius;
            float z2 = centerZ + sin2 * radius;

            // Top cap triangle (as part of quad with center)
            consumer.addVertex(pose, centerX, yTop, centerZ).setColor(r, g, b, a);
            consumer.addVertex(pose, x1, yTop, z1).setColor(r, g, b, a);
            consumer.addVertex(pose, x2, yTop, z2).setColor(r, g, b, a);
            consumer.addVertex(pose, x2, yTop, z2).setColor(r, g, b, a);  // Degenerate to make quad

            // Bottom cap triangle
            consumer.addVertex(pose, centerX, yBottom, centerZ).setColor(r, g, b, a);
            consumer.addVertex(pose, x2, yBottom, z2).setColor(r, g, b, a);
            consumer.addVertex(pose, x1, yBottom, z1).setColor(r, g, b, a);
            consumer.addVertex(pose, x1, yBottom, z1).setColor(r, g, b, a);  // Degenerate to make quad
        }
    }

    /**
     * Render a red glowing cylinder that fades out aggressively at the top.
     * Uses high segment count and renders all faces for consistent visibility.
     * @param fadeFactor overall fade factor (0-1) for end-of-burn fadeout
     */
    public static void renderGlowCylinderFadeTop(VertexConsumer consumer, Matrix4f pose,
                                           float centerX, float centerZ,
                                           float yBottom, float yTop,
                                           float radius, int segments, float fadeFactor) {
        // Solid red color
        float r = 0.9f, g = 0.15f, b = 0.05f;
        // Bottom alpha (fully visible) - scaled by fade factor
        float bottomA = 0.9f * fadeFactor;

        // Use more segments for smoother cylinder visible from all angles
        int cylSegments = 16;

        // Render multiple vertical segments for smooth alpha fade
        int heightSegments = 6;
        float heightStep = (yTop - yBottom) / heightSegments;

        for (int h = 0; h < heightSegments; h++) {
            float y1 = yBottom + h * heightStep;
            float y2 = yBottom + (h + 1) * heightStep;

            // Calculate alpha fade - aggressive cubic falloff
            float t1 = (float) h / heightSegments;
            float t2 = (float) (h + 1) / heightSegments;
            t1 = t1 * t1 * t1;
            t2 = t2 * t2 * t2;

            float a1 = bottomA * (1.0f - t1);
            float a2 = bottomA * (1.0f - t2);

            for (int i = 0; i < cylSegments; i++) {
                float angle1 = (float) (i * 2 * Math.PI / cylSegments);
                float angle2 = (float) ((i + 1) * 2 * Math.PI / cylSegments);

                float cos1 = (float) Math.cos(angle1);
                float sin1 = (float) Math.sin(angle1);
                float cos2 = (float) Math.cos(angle2);
                float sin2 = (float) Math.sin(angle2);

                float x1 = centerX + cos1 * radius;
                float z1 = centerZ + sin1 * radius;
                float x2 = centerX + cos2 * radius;
                float z2 = centerZ + sin2 * radius;

                // Outward facing quad
                consumer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a1);
                consumer.addVertex(pose, x1, y2, z1).setColor(r, g, b, a2);
                consumer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a2);
                consumer.addVertex(pose, x2, y1, z2).setColor(r, g, b, a1);

                // Inward facing quad (reverse winding)
                consumer.addVertex(pose, x2, y1, z2).setColor(r, g, b, a1);
                consumer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a2);
                consumer.addVertex(pose, x1, y2, z1).setColor(r, g, b, a2);
                consumer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a1);
            }
        }

        // Bottom cap - renders both directions for visibility
        for (int i = 0; i < cylSegments; i++) {
            float angle1 = (float) (i * 2 * Math.PI / cylSegments);
            float angle2 = (float) ((i + 1) * 2 * Math.PI / cylSegments);

            float x1 = centerX + (float) Math.cos(angle1) * radius;
            float z1 = centerZ + (float) Math.sin(angle1) * radius;
            float x2 = centerX + (float) Math.cos(angle2) * radius;
            float z2 = centerZ + (float) Math.sin(angle2) * radius;

            // Bottom cap facing down
            consumer.addVertex(pose, centerX, yBottom, centerZ).setColor(r, g, b, bottomA);
            consumer.addVertex(pose, x2, yBottom, z2).setColor(r, g, b, bottomA);
            consumer.addVertex(pose, x1, yBottom, z1).setColor(r, g, b, bottomA);
            consumer.addVertex(pose, x1, yBottom, z1).setColor(r, g, b, bottomA);

            // Bottom cap facing up
            consumer.addVertex(pose, centerX, yBottom, centerZ).setColor(r, g, b, bottomA);
            consumer.addVertex(pose, x1, yBottom, z1).setColor(r, g, b, bottomA);
            consumer.addVertex(pose, x2, yBottom, z2).setColor(r, g, b, bottomA);
            consumer.addVertex(pose, x2, yBottom, z2).setColor(r, g, b, bottomA);
        }
    }
}
