package com.nick.realisticfirework.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * Handles stick and coating rendering for the sparkler.
 * Includes burnt carbon residue, cylinder, melting coating, melt edge, and drip tendrils.
 */
public class StickRenderer {

    private static final int MELT_EDGE_LAYERS = 4;       // Fewer layers - hot burn is sharper
    private static final float EDGE_ROUNDNESS = 0.008f;  // Less rounded - burning edge is crisper than melting

    // Fixed sparkler geometry constants (must match SparklerSpecialRenderer)
    private static final float STICK_BASE_Y = 0.1f;
    private static final float STICK_TIP_Y = 0.9f;
    private static final float TOTAL_STICK_HEIGHT = STICK_TIP_Y - STICK_BASE_Y;

    /**
     * Render burnt residue on the wire.
     *
     * REALISTIC BEHAVIOR: The debris exists along the ENTIRE stick from the start,
     * hidden inside the white coating. As the coating burns away from top to bottom,
     * the debris is revealed - it was always there, just occluded.
     *
     * We render ALL debris blobs for the whole stick. The white coating (rendered AFTER)
     * will naturally occlude the debris that's still covered.
     */
    public static void renderBurntCarbonResidue(VertexConsumer consumer, Matrix4f pose, SparklerState state,
                                          float centerX, float centerZ, float burnY, float tipY, float wireRadius,
                                          float uMid, float vMid, int light) {
        // Render ALL debris for the entire stick - the coating will occlude what's not exposed yet
        renderChunkyResidue(consumer, pose, state, centerX, centerZ, wireRadius, uMid, vMid, light);
    }

    /**
     * Render extremely bumpy, chunky residue blobs on the wire.
     * Creates the crusty, barnacle-like appearance of real burnt sparklers.
     *
     * IMPORTANT: This renders debris for the ENTIRE stick length. The debris exists
     * "inside" the coating and is revealed as the coating burns away. The white coating
     * renders AFTER this, so it naturally occludes debris that hasn't been exposed yet.
     */
    private static void renderChunkyResidue(VertexConsumer consumer, Matrix4f pose, SparklerState state,
                                             float centerX, float centerZ,
                                             float wireRadius, float uMid, float vMid, int light) {
        // Grid-based placement for dense, even coverage along ENTIRE stick
        int heightLayers = 60;   // Layers along the stick height
        int angularSlots = 8;    // Slots around the wire circumference

        for (int h = 0; h < heightLayers; h++) {
            // Fixed Y position for this layer - covers ENTIRE stick
            float t = (float)h / (heightLayers - 1);
            float y = STICK_BASE_Y + t * TOTAL_STICK_HEIGHT;

            for (int a = 0; a < angularSlots; a++) {
                int blobIndex = h * angularSlots + a;

                // Noise for variation
                float noise1 = state.sampleNoise(blobIndex * 7.3f + 2000);
                float noise2 = state.sampleNoise(blobIndex * 13.7f + 2000);
                float noise3 = state.sampleNoise(blobIndex * 19.1f + 2000);
                float noise4 = state.sampleNoise(blobIndex * 29.3f + 2000);
                float noise5 = state.sampleNoise(blobIndex * 37.7f + 2000);

                // Base angle with random offset
                float baseAngle = (float)(a * 2 * Math.PI / angularSlots);
                float angle = baseAngle + (noise1 - 0.5f) * 0.8f;  // Random wiggle

                // Y offset for organic look (not perfectly aligned rows)
                float yOffset = (noise2 - 0.5f) * (TOTAL_STICK_HEIGHT / heightLayers) * 0.8f;
                float finalY = y + yOffset;

                // Clamp to stick bounds
                finalY = Math.max(STICK_BASE_Y, Math.min(STICK_TIP_Y, finalY));

                float cos = (float)Math.cos(angle);
                float sin = (float)Math.sin(angle);

                // Blob size - varied, some big some small
                float blobSize = wireRadius * (1.0f + noise3 * 2.0f);  // 1x to 3x wire radius

                // Position - on wire surface
                float blobX = centerX + cos * (wireRadius + blobSize * 0.4f);
                float blobZ = centerZ + sin * (wireRadius + blobSize * 0.4f);

                // Color - dark burnt orange/brown/black mix
                int baseR, baseG, baseB;
                if (noise4 < 0.3f) {
                    // Dark brown/orange (burnt)
                    baseR = 50 + (int)(noise5 * 35);
                    baseG = 28 + (int)(noise5 * 18);
                    baseB = 12 + (int)(noise5 * 12);
                } else if (noise4 < 0.6f) {
                    // Dark reddish brown
                    baseR = 60 + (int)(noise5 * 30);
                    baseG = 25 + (int)(noise5 * 18);
                    baseB = 18 + (int)(noise5 * 12);
                } else {
                    // Very dark / black
                    baseR = 25 + (int)(noise5 * 25);
                    baseG = 22 + (int)(noise5 * 18);
                    baseB = 18 + (int)(noise5 * 15);
                }

                // Render irregular blob
                renderIrregularBlob(consumer, pose, state, blobX, finalY, blobZ, blobSize,
                                   uMid, vMid, light, baseR, baseG, baseB, blobIndex);
            }
        }
    }

    /**
     * Render a single irregular, lumpy blob (not a smooth sphere).
     */
    private static void renderIrregularBlob(VertexConsumer consumer, Matrix4f pose, SparklerState state,
                                            float x, float y, float z, float size,
                                            float uMid, float vMid, int light,
                                            int r, int g, int b, int seed) {
        // Create irregular shape with multiple bumps
        int segments = 8;
        int rings = 4;

        for (int ring = 0; ring < rings; ring++) {
            float ringT1 = (float)ring / rings;
            float ringT2 = (float)(ring + 1) / rings;

            // Vertical angles (latitude)
            float phi1 = ringT1 * (float)Math.PI - (float)Math.PI / 2;  // -90 to +90
            float phi2 = ringT2 * (float)Math.PI - (float)Math.PI / 2;

            float cosPhi1 = (float)Math.cos(phi1);
            float sinPhi1 = (float)Math.sin(phi1);
            float cosPhi2 = (float)Math.cos(phi2);
            float sinPhi2 = (float)Math.sin(phi2);

            for (int seg = 0; seg < segments; seg++) {
                float theta1 = (float)(seg * 2 * Math.PI / segments);
                float theta2 = (float)((seg + 1) * 2 * Math.PI / segments);

                float cosTheta1 = (float)Math.cos(theta1);
                float sinTheta1 = (float)Math.sin(theta1);
                float cosTheta2 = (float)Math.cos(theta2);
                float sinTheta2 = (float)Math.sin(theta2);

                // Irregular radius at each vertex (bumpy surface)
                float r1 = size * (0.5f + state.sampleNoise(seed * 100 + ring * 20 + seg * 3) * 1.0f);
                float r2 = size * (0.5f + state.sampleNoise(seed * 100 + ring * 20 + seg * 3 + 1) * 1.0f);
                float r3 = size * (0.5f + state.sampleNoise(seed * 100 + (ring+1) * 20 + seg * 3) * 1.0f);
                float r4 = size * (0.5f + state.sampleNoise(seed * 100 + (ring+1) * 20 + seg * 3 + 1) * 1.0f);

                // Vertex positions (spherical to cartesian with irregular radius)
                float x1 = x + cosTheta1 * cosPhi1 * r1;
                float y1 = y + sinPhi1 * r1;
                float z1 = z + sinTheta1 * cosPhi1 * r1;

                float x2 = x + cosTheta2 * cosPhi1 * r2;
                float y2_v = y + sinPhi1 * r2;
                float z2 = z + sinTheta2 * cosPhi1 * r2;

                float x3 = x + cosTheta1 * cosPhi2 * r3;
                float y3 = y + sinPhi2 * r3;
                float z3 = z + sinTheta1 * cosPhi2 * r3;

                float x4 = x + cosTheta2 * cosPhi2 * r4;
                float y4 = y + sinPhi2 * r4;
                float z4 = z + sinTheta2 * cosPhi2 * r4;

                // Normal (approximate outward)
                float nx = (cosTheta1 + cosTheta2) * 0.5f * (cosPhi1 + cosPhi2) * 0.5f;
                float ny = (sinPhi1 + sinPhi2) * 0.5f;
                float nz = (sinTheta1 + sinTheta2) * 0.5f * (cosPhi1 + cosPhi2) * 0.5f;
                float nLen = (float)Math.sqrt(nx*nx + ny*ny + nz*nz);
                if (nLen > 0.001f) { nx /= nLen; ny /= nLen; nz /= nLen; }

                // Quad
                consumer.addVertex(pose, x1, y1, z1)
                    .setColor(r, g, b, 255).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(nx, ny, nz);
                consumer.addVertex(pose, x3, y3, z3)
                    .setColor(r, g, b, 255).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(nx, ny, nz);
                consumer.addVertex(pose, x4, y4, z4)
                    .setColor(r, g, b, 255).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(nx, ny, nz);
                consumer.addVertex(pose, x2, y2_v, z2)
                    .setColor(r, g, b, 255).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(nx, ny, nz);
            }
        }
    }

    /**
     * Render a cylinder (for the inner wire core)
     */
    public static void renderCylinder(VertexConsumer consumer, Matrix4f pose,
                                 float x1, float y1, float z1,  // base center
                                 float x2, float y2, float z2,  // tip center
                                 float baseRadius, float tipRadius,
                                 float uMid, float vMid, int light,
                                 int r, int g, int b, int a) {
        int segments = 8;

        for (int i = 0; i < segments; i++) {
            float angle1 = (float) (i * 2 * Math.PI / segments);
            float angle2 = (float) ((i + 1) * 2 * Math.PI / segments);

            float cos1 = (float) Math.cos(angle1);
            float sin1 = (float) Math.sin(angle1);
            float cos2 = (float) Math.cos(angle2);
            float sin2 = (float) Math.sin(angle2);

            // Base vertices
            float bx1 = x1 + cos1 * baseRadius;
            float bz1 = z1 + sin1 * baseRadius;
            float bx2 = x1 + cos2 * baseRadius;
            float bz2 = z1 + sin2 * baseRadius;

            // Tip vertices
            float tx1 = x2 + cos1 * tipRadius;
            float tz1 = z2 + sin1 * tipRadius;
            float tx2 = x2 + cos2 * tipRadius;
            float tz2 = z2 + sin2 * tipRadius;

            // Normals point outward
            float nx1 = cos1, nz1 = sin1;
            float nx2 = cos2, nz2 = sin2;

            // Side quad (front face)
            consumer.addVertex(pose, bx1, y1, bz1)
                  .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(nx1, 0, nz1);
            consumer.addVertex(pose, tx1, y2, tz1)
                  .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(nx1, 0, nz1);
            consumer.addVertex(pose, tx2, y2, tz2)
                  .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(nx2, 0, nz2);
            consumer.addVertex(pose, bx2, y1, bz2)
                  .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(nx2, 0, nz2);

            // Side quad (back face for double-sided)
            consumer.addVertex(pose, bx2, y1, bz2)
                  .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(-nx2, 0, -nz2);
            consumer.addVertex(pose, tx2, y2, tz2)
                  .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(-nx2, 0, -nz2);
            consumer.addVertex(pose, tx1, y2, tz1)
                  .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(-nx1, 0, -nz1);
            consumer.addVertex(pose, bx1, y1, bz1)
                  .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(-nx1, 0, -nz1);
        }

        // Top cap (at the tip)
        for (int i = 0; i < segments; i++) {
            float angle1 = (float) (i * 2 * Math.PI / segments);
            float angle2 = (float) ((i + 1) * 2 * Math.PI / segments);

            float cos1 = (float) Math.cos(angle1);
            float sin1 = (float) Math.sin(angle1);
            float cos2 = (float) Math.cos(angle2);
            float sin2 = (float) Math.sin(angle2);

            float tx1 = x2 + cos1 * tipRadius;
            float tz1 = z2 + sin1 * tipRadius;
            float tx2 = x2 + cos2 * tipRadius;
            float tz2 = z2 + sin2 * tipRadius;

            // Triangle fan for cap
            consumer.addVertex(pose, x2, y2, z2)
                  .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(0, 1, 0);
            consumer.addVertex(pose, tx1, y2, tz1)
                  .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(0, 1, 0);
            consumer.addVertex(pose, tx2, y2, tz2)
                  .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(0, 1, 0);
            consumer.addVertex(pose, x2, y2, z2)
                  .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(0, 1, 0);
        }
    }

    /**
     * Ultra-high resolution coating renderer with uniform burning
     * Features:
     * - 256 render columns for smooth curves
     * - Multi-layer rounded melt edge
     * - Drip tendrils that hang below
     * - Bezier-interpolated positions
     * - Proper 3D rounded cap at melt front
     */
    public static void renderMeltingCoating(VertexConsumer consumer, Matrix4f pose, SparklerState state,
                                       float centerX, float centerZ, float baseY, float tipY, float halfWidth,
                                       float u0, float u1, float v0, float v1, int light) {
        // Grey coating color - matches the handle
        int r = 130, g = 130, b = 135, a = 255;
        float totalHeight = tipY - baseY;

        // Ultra-high resolution rendering
        int numStrips = SparklerState.RENDER_COLUMNS;

        // Pre-calculate all melt heights with sub-pixel accuracy
        float[] meltHeights = new float[numStrips + 1];
        float[] dripExtensions = new float[numStrips + 1];
        for (int i = 0; i <= numStrips; i++) {
            float angle = (float)(i * 2 * Math.PI / numStrips);
            float rawMeltHeight = state.getMeltHeightAtAngle(angle);
            float rawDripExt = state.getDripExtensionAtAngle(angle);

            // Calculate combined melt progress (melt + drip)
            float combinedMeltProgress = rawMeltHeight + rawDripExt;

            // CRITICAL FINAL SAFETY: Enforce monotonicity at render level
            // This catches ANY remaining source of regeneration
            // maxRenderedHeight tracks the maximum height ever rendered at this column
            if (combinedMeltProgress > state.maxRenderedHeight[i]) {
                state.maxRenderedHeight[i] = combinedMeltProgress;
            } else {
                // Would be regeneration! Use the max instead
                combinedMeltProgress = state.maxRenderedHeight[i];
            }

            // Store separated values (proportionally distribute the clamped height)
            if (rawMeltHeight + rawDripExt > 0.0001f) {
                float ratio = rawMeltHeight / (rawMeltHeight + rawDripExt);
                meltHeights[i] = combinedMeltProgress * ratio;
                dripExtensions[i] = combinedMeltProgress * (1.0f - ratio);
            } else {
                meltHeights[i] = combinedMeltProgress;
                dripExtensions[i] = 0;
            }
        }

        // Render main body strips
        for (int i = 0; i < numStrips; i++) {
            float angle1 = (float)(i * 2 * Math.PI / numStrips);
            float angle2 = (float)((i + 1) * 2 * Math.PI / numStrips);

            float melt1 = meltHeights[i];
            float melt2 = meltHeights[i + 1];
            float drip1 = dripExtensions[i];
            float drip2 = dripExtensions[i + 1];

            // Top Y with drip extension
            float topY1 = tipY - (melt1 + drip1) * totalHeight;
            float topY2 = tipY - (melt2 + drip2) * totalHeight;

            if (topY1 <= baseY && topY2 <= baseY) continue;
            topY1 = Math.max(baseY, topY1);
            topY2 = Math.max(baseY, topY2);

            // Smooth circular profile (no squarify - pure smooth)
            float cos1 = (float)Math.cos(angle1);
            float sin1 = (float)Math.sin(angle1);
            float cos2 = (float)Math.cos(angle2);
            float sin2 = (float)Math.sin(angle2);

            float x1 = centerX + cos1 * halfWidth;
            float z1 = centerZ + sin1 * halfWidth;
            float x2 = centerX + cos2 * halfWidth;
            float z2 = centerZ + sin2 * halfWidth;

            // Smooth normal
            float nx = (cos1 + cos2) / 2;
            float nz = (sin1 + sin2) / 2;
            float nLen = (float)Math.sqrt(nx * nx + nz * nz);
            if (nLen > 0.001f) { nx /= nLen; nz /= nLen; }

            // Use center UV for solid color (no texture speckle)
            float uMid = (u0 + u1) / 2;
            float vMid = (v0 + v1) / 2;

            // Main face
            consumer.addVertex(pose, x1, baseY, z1)
                  .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(nx, 0, nz);
            consumer.addVertex(pose, x1, topY1, z1)
                  .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(nx, 0, nz);
            consumer.addVertex(pose, x2, topY2, z2)
                  .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(nx, 0, nz);
            consumer.addVertex(pose, x2, baseY, z2)
                  .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(nx, 0, nz);

            // Back face
            consumer.addVertex(pose, x2, baseY, z2)
                  .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(-nx, 0, -nz);
            consumer.addVertex(pose, x2, topY2, z2)
                  .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(-nx, 0, -nz);
            consumer.addVertex(pose, x1, topY1, z1)
                  .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(-nx, 0, -nz);
            consumer.addVertex(pose, x1, baseY, z1)
                  .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(-nx, 0, -nz);
        }

        // Render rounded melt edge cap
        renderRoundedMeltEdge(consumer, pose, state, centerX, centerZ, baseY, tipY, halfWidth,
                              u0, u1, v0, v1, light, meltHeights, dripExtensions);

        // Render drip tendrils
        renderDripTendrils(consumer, pose, state, centerX, centerZ, baseY, tipY, halfWidth,
                           u0, u1, v0, v1, light);
    }

    /**
     * Render a rounded 3D edge at the melt front
     * Creates smooth beveled appearance instead of sharp cutoff
     */
    public static void renderRoundedMeltEdge(VertexConsumer consumer, Matrix4f pose, SparklerState state,
                                        float centerX, float centerZ, float baseY, float tipY, float halfWidth,
                                        float u0, float u1, float v0, float v1, int light,
                                        float[] meltHeights, float[] dripExtensions) {
        // Grey coating color - matches the handle
        int r = 130, g = 130, b = 135, a = 255;
        float totalHeight = tipY - baseY;
        int numStrips = SparklerState.RENDER_COLUMNS;

        // Render multiple layers for rounded edge
        for (int layer = 0; layer < MELT_EDGE_LAYERS; layer++) {
            float layerT = (float)layer / (MELT_EDGE_LAYERS - 1);  // 0 to 1
            // Semicircle profile for rounded edge
            float layerAngle = layerT * (float)Math.PI / 2;  // 0 to 90 degrees
            float radiusScale = (float)Math.cos(layerAngle);  // 1 at edge, 0 at center
            float heightOffset = (float)Math.sin(layerAngle) * EDGE_ROUNDNESS;

            for (int i = 0; i < numStrips; i++) {
                float angle1 = (float)(i * 2 * Math.PI / numStrips);
                float angle2 = (float)((i + 1) * 2 * Math.PI / numStrips);

                float melt1 = meltHeights[i] + dripExtensions[i];
                float melt2 = meltHeights[i + 1] + dripExtensions[i + 1];

                float topY1 = tipY - melt1 * totalHeight + heightOffset * totalHeight;
                float topY2 = tipY - melt2 * totalHeight + heightOffset * totalHeight;

                if (topY1 <= baseY && topY2 <= baseY) continue;

                float cos1 = (float)Math.cos(angle1);
                float sin1 = (float)Math.sin(angle1);
                float cos2 = (float)Math.cos(angle2);
                float sin2 = (float)Math.sin(angle2);

                // Scale radius inward for inner layers
                float radius = halfWidth * radiusScale;
                float x1 = centerX + cos1 * radius;
                float z1 = centerZ + sin1 * radius;
                float x2 = centerX + cos2 * radius;
                float z2 = centerZ + sin2 * radius;

                // Normal blends between outward and upward
                float ny = (float)Math.sin(layerAngle);
                float nHoriz = (float)Math.cos(layerAngle);
                float nx = ((cos1 + cos2) / 2) * nHoriz;
                float nz = ((sin1 + sin2) / 2) * nHoriz;

                // Use center UV for solid color (no texture speckle)
                float uMid = (u0 + u1) / 2;
                float vMid = (v0 + v1) / 2;

                // Connect to next layer or center
                float nextLayerT = (float)(layer + 1) / (MELT_EDGE_LAYERS - 1);
                float nextLayerAngle = Math.min(nextLayerT * (float)Math.PI / 2, (float)Math.PI / 2);
                float nextRadiusScale = (float)Math.cos(nextLayerAngle);
                float nextHeightOffset = (float)Math.sin(nextLayerAngle) * EDGE_ROUNDNESS;

                float nextRadius = halfWidth * nextRadiusScale;
                float nextTopY1 = tipY - melt1 * totalHeight + nextHeightOffset * totalHeight;
                float nextTopY2 = tipY - melt2 * totalHeight + nextHeightOffset * totalHeight;

                float nx1 = centerX + cos1 * nextRadius;
                float nz1 = centerZ + sin1 * nextRadius;
                float nx2 = centerX + cos2 * nextRadius;
                float nz2 = centerZ + sin2 * nextRadius;

                if (layer < MELT_EDGE_LAYERS - 1) {
                    // Quad connecting this layer to next
                    consumer.addVertex(pose, x1, topY1, z1)
                          .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(nx, ny, nz);
                    consumer.addVertex(pose, nx1, nextTopY1, nz1)
                          .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(nx, ny, nz);
                    consumer.addVertex(pose, nx2, nextTopY2, nz2)
                          .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(nx, ny, nz);
                    consumer.addVertex(pose, x2, topY2, z2)
                          .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(nx, ny, nz);
                }
            }
        }

        // Final center cap (flat top)
        float avgMelt = state.getAverageMeltHeight();
        float capY = tipY - avgMelt * totalHeight + EDGE_ROUNDNESS * totalHeight;
        if (capY > baseY) {
            for (int i = 0; i < numStrips; i++) {
                float angle1 = (float)(i * 2 * Math.PI / numStrips);
                float angle2 = (float)((i + 1) * 2 * Math.PI / numStrips);

                float cos1 = (float)Math.cos(angle1);
                float sin1 = (float)Math.sin(angle1);
                float cos2 = (float)Math.cos(angle2);
                float sin2 = (float)Math.sin(angle2);

                // Tiny inner radius for center
                float innerR = halfWidth * 0.05f;
                float x1 = centerX + cos1 * innerR;
                float z1 = centerZ + sin1 * innerR;
                float x2 = centerX + cos2 * innerR;
                float z2 = centerZ + sin2 * innerR;

                float melt1 = meltHeights[i] + dripExtensions[i];
                float melt2 = meltHeights[i + 1] + dripExtensions[i + 1];
                float y1 = tipY - melt1 * totalHeight + EDGE_ROUNDNESS * totalHeight;
                float y2 = tipY - melt2 * totalHeight + EDGE_ROUNDNESS * totalHeight;

                consumer.addVertex(pose, centerX, capY, centerZ)
                      .setColor(r, g, b, a).setUv((u0+u1)/2, (v0+v1)/2).setOverlay(0).setLight(light).setNormal(0, 1, 0);
                consumer.addVertex(pose, x1, y1, z1)
                      .setColor(r, g, b, a).setUv((u0+u1)/2, (v0+v1)/2).setOverlay(0).setLight(light).setNormal(0, 1, 0);
                consumer.addVertex(pose, x2, y2, z2)
                      .setColor(r, g, b, a).setUv((u0+u1)/2, (v0+v1)/2).setOverlay(0).setLight(light).setNormal(0, 1, 0);
                consumer.addVertex(pose, centerX, capY, centerZ)
                      .setColor(r, g, b, a).setUv((u0+u1)/2, (v0+v1)/2).setOverlay(0).setLight(light).setNormal(0, 1, 0);
            }
        }
    }

    /**
     * Render organic drip tendrils hanging from the melt line
     */
    public static void renderDripTendrils(VertexConsumer consumer, Matrix4f pose, SparklerState state,
                                     float centerX, float centerZ, float baseY, float tipY, float halfWidth,
                                     float u0, float u1, float v0, float v1, int light) {
        float totalHeight = tipY - baseY;
        float uMid = (u0 + u1) / 2;
        float vMid = (v0 + v1) / 2;

        for (int i = 0; i < SparklerState.DRIP_COUNT; i++) {
            if (!state.dripActive[i] || state.dripLength[i] < 0.01f) continue;

            float angle = state.dripAngle[i];
            float width = state.dripWidth[i];
            float length = state.dripLength[i];

            // Base of drip at melt line
            float meltHeight = state.getMeltHeightAtAngle(angle);
            float dripBaseY = tipY - meltHeight * totalHeight;

            if (dripBaseY <= baseY) continue;

            // Drip tip (tapers to point)
            float dripTipY = dripBaseY - length * totalHeight;
            dripTipY = Math.max(baseY, dripTipY);

            float cos = (float)Math.cos(angle);
            float sin = (float)Math.sin(angle);

            // Drip has teardrop cross-section - wider at top, pointed at bottom
            int dripSegments = 8;
            for (int seg = 0; seg < dripSegments; seg++) {
                float t1 = (float)seg / dripSegments;
                float t2 = (float)(seg + 1) / dripSegments;

                // Teardrop width profile
                float w1 = width * (1.0f - t1 * t1);  // Quadratic taper
                float w2 = width * (1.0f - t2 * t2);

                float y1 = RenderUtils.lerp(dripBaseY, dripTipY, t1);
                float y2 = RenderUtils.lerp(dripBaseY, dripTipY, t2);

                // Render small cylinder segment for drip
                float perpCos = (float)Math.cos(angle + Math.PI/2);
                float perpSin = (float)Math.sin(angle + Math.PI/2);

                float x1a = centerX + cos * halfWidth + perpCos * w1;
                float z1a = centerZ + sin * halfWidth + perpSin * w1;
                float x1b = centerX + cos * halfWidth - perpCos * w1;
                float z1b = centerZ + sin * halfWidth - perpSin * w1;

                float x2a = centerX + cos * halfWidth + perpCos * w2;
                float z2a = centerZ + sin * halfWidth + perpSin * w2;
                float x2b = centerX + cos * halfWidth - perpCos * w2;
                float z2b = centerZ + sin * halfWidth - perpSin * w2;

                // Grey coating color - matches the handle
                int r = 130, g = 130, b = 135, a = 255;

                // Front face of drip
                consumer.addVertex(pose, x1a, y1, z1a)
                      .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(cos, 0, sin);
                consumer.addVertex(pose, x2a, y2, z2a)
                      .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(cos, 0, sin);
                consumer.addVertex(pose, x2b, y2, z2b)
                      .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(cos, 0, sin);
                consumer.addVertex(pose, x1b, y1, z1b)
                      .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(cos, 0, sin);

                // Side faces
                consumer.addVertex(pose, x1a, y1, z1a)
                      .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(perpCos, 0, perpSin);
                consumer.addVertex(pose, x1b, y1, z1b)
                      .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(-perpCos, 0, -perpSin);
                consumer.addVertex(pose, x2b, y2, z2b)
                      .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(-perpCos, 0, -perpSin);
                consumer.addVertex(pose, x2a, y2, z2a)
                      .setColor(r, g, b, a).setUv(uMid, vMid).setOverlay(0).setLight(light).setNormal(perpCos, 0, perpSin);
            }
        }
    }
}
