package com.nick.realisticfirework.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nick.realisticfirework.RealisticFireworkMod;
import com.nick.realisticfirework.data.SparklerData;
import com.nick.realisticfirework.registry.ModDataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom BEWLR (BlockEntityWithoutLevelRenderer) for the lit sparkler.
 * This is the 1.21.1 equivalent of SparklerSpecialRenderer used in 1.21.4+.
 * Features a burning stick that shrinks over time with predetermined spark emission points.
 * Each sparkler instance maintains its own independent state keyed by UUID.
 */
public class SparklerBEWLR extends BlockEntityWithoutLevelRenderer {

    // Per-sparkler state, keyed by UUID from the data component
    private static final Map<UUID, SparklerState> sparklerStates = new ConcurrentHashMap<>();
    private static final Random random = new Random();

    // Custom spark texture
    private static final ResourceLocation SPARK_TEXTURE = ResourceLocation.fromNamespaceAndPath(
        RealisticFireworkMod.MODID, "particle/sparkler_spark");

    // Sparkler stick texture (for the actual item appearance)
    private static final ResourceLocation SPARKLER_TEXTURE = ResourceLocation.fromNamespaceAndPath(
        RealisticFireworkMod.MODID, "particle/sparkler_stick");

    public SparklerBEWLR() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
              Minecraft.getInstance().getEntityModels());
    }

    /**
     * Clean up old sparkler states that haven't been accessed recently
     */
    private static void cleanupOldStates() {
        long now = System.currentTimeMillis();
        sparklerStates.entrySet().removeIf(entry ->
            now - entry.getValue().lastAccessTime > 10000); // Remove after 10 seconds of no access
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                              MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        // Get sparkler data from the item stack
        SparklerData data = stack.get(ModDataComponents.SPARKLER.get());

        // If no data, use a fallback UUID (shouldn't happen normally)
        UUID sparklerUUID = (data != null) ? data.uniqueId() : UUID.randomUUID();

        // Check if sparkler is lit
        boolean isLit = (data != null) && data.isLit();

        // Get or create state for this sparkler using its unique ID
        SparklerState state = sparklerStates.computeIfAbsent(sparklerUUID, k -> new SparklerState());
        state.lastAccessTime = System.currentTimeMillis();
        state.initializeErosion();

        // Periodically cleanup old states
        if (random.nextInt(100) == 0) {
            cleanupOldStates();
        }

        long currentTime = System.currentTimeMillis();
        float deltaTime = Math.min((currentTime - state.lastUpdateTime) / 1000.0f, 0.05f);
        if (state.lastUpdateTime == 0) deltaTime = 0.016f;
        state.lastUpdateTime = currentTime;

        // WORLD-SPACE SPARK TRACKING
        // Get the current pose matrix (model->world transformation)
        // We need this to convert model-space spawn points to world-space
        // Apply the same rotation we use in rendering (-45° around Z)
        Matrix4f currentPose = new Matrix4f(poseStack.last().pose());
        currentPose.translate(0.5f, 0.5f, 0.5f);
        currentPose.rotateZ((float)Math.toRadians(-45.0));
        currentPose.translate(-0.5f, -0.5f, -0.5f);
        Matrix4f currentInverse = new Matrix4f(currentPose).invert();

        // Only progress burn and spawn sparks if lit
        if (isLit) {
            // Get burn progress from SparklerData using game ticks (synced with server)
            // This ensures the visual fade matches the server-side light block fade
            Minecraft mc = Minecraft.getInstance();
            long currentTick = (mc.level != null) ? mc.level.getGameTime() : 0L;

            // Use the centralized burn progress calculation from SparklerData
            float burnProgress = (data != null) ? data.getBurnProgress(currentTick) : 0.0f;
            float fadeFactor = (data != null) ? data.getFadeFactor(currentTick) : 1.0f;

            // Sync internal state with data-driven progress for erosion system
            state.burnProgress = burnProgress;

            // Check if sparkler is fully burned (coating consumed, only handle left)
            boolean fullyBurned = (data != null) ? data.isFullyBurned(currentTick) : false;
            if (fullyBurned) {
                state.burnProgress = SparklerData.MAX_BURN_PROGRESS;

                // Track time since fully burned
                if (state.fullyBurnedTime == 0) {
                    state.fullyBurnedTime = System.currentTimeMillis();
                }

                long timeSinceBurned = System.currentTimeMillis() - state.fullyBurnedTime;

                // Update lighting for fully burned state (fade out sequence)
                SparklerLightingManager.updateFullyBurnedLighting(sparklerUUID, state, currentPose, timeSinceBurned);
            } else {
                // Still burning - update erosion pattern
                state.updateErosion(deltaTime);

                // Track the sparkler tip position for dynamic lighting
                // Only track for in-world rendering contexts (not GUI)
                if (displayContext != ItemDisplayContext.GUI &&
                    displayContext != ItemDisplayContext.FIXED &&
                    displayContext != ItemDisplayContext.GROUND) {
                    SparklerTipPositionTracker.updateTipPosition(
                        sparklerUUID,
                        state.getAverageMeltHeight(),
                        currentPose
                    );
                }

                // Update dynamic lighting for the sparkler core
                SparklerLightingManager.updateBurningLighting(sparklerUUID, state, currentPose);

                // ========== NEW REALISTIC SPARK SYSTEM ==========
                // Calculate burn Y for spawn point
                float stickTipYSpawn = 0.9f;
                float stickBaseYSpawn = 0.1f;
                float burnYSpawn = stickTipYSpawn - state.getAverageMeltHeight() * (stickTipYSpawn - stickBaseYSpawn);

                // Spawn new realistic sparks
                RealisticSparkManager.spawnSparks(state.realisticSparks, burnYSpawn, currentPose, deltaTime, fadeFactor);

                // Update realistic sparks
                RealisticSparkManager.updateSparks(state.realisticSparks, deltaTime);
            }
        } else {
            // Unlit - keep burn progress at 0, no sparks
            state.burnProgress = 0.0f;
            SparkLifecycleManager.clearSparks(state);
            RealisticSparkManager.clearSparks(state.realisticSparks);
            SparklerLightingManager.removeLighting(sparklerUUID);
        }

        // Store the current inverse pose for next frame (and for rendering)
        state.lastInversePose = currentInverse;
        state.hasLastPose = true;

        // Get texture atlas sprites via the texture atlas lookup function
        TextureAtlasSprite sparkSprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(SPARK_TEXTURE);
        TextureAtlasSprite stickSprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(SPARKLER_TEXTURE);

        // Calculate fade factor for glow using SparklerData constants (synced with server)
        float glowFade = 1.0f;
        if (state.burnProgress > SparklerData.FADE_START) {
            glowFade = 1.0f - (state.burnProgress - SparklerData.FADE_START) / (SparklerData.MAX_BURN_PROGRESS - SparklerData.FADE_START);
            glowFade = Math.max(0.0f, Math.min(1.0f, glowFade));
        }

        // First pass: Render solid geometry with itemEntityTranslucentCull
        VertexConsumer solidConsumer = bufferSource.getBuffer(RenderType.itemEntityTranslucentCull(TextureAtlas.LOCATION_BLOCKS));
        renderAllEffects(poseStack.last(), solidConsumer, sparkSprite, stickSprite, displayContext, state, currentInverse);

        // Second pass: Render spark GLOWS using lightning render type (additive blending)
        if (isLit && state.burnProgress > 0.01f && state.burnProgress < SparklerData.MAX_BURN_PROGRESS) {
            VertexConsumer glowConsumer = bufferSource.getBuffer(RenderType.lightning());
            GlowRenderer.renderSparkGlows(poseStack.last(), glowConsumer, displayContext, state, currentInverse, glowFade);
        }

        // Third pass: ATTEMPT 21 - Camera-facing billboard tubes with magenta marker
        // Each spark is ONE thin quad that faces the camera - creates thin tube appearance
        if (isLit && !state.realisticSparks.isEmpty()) {
            VertexConsumer sparkConsumer = bufferSource.getBuffer(RenderType.lightning());
            Matrix4f poseMat = new Matrix4f(poseStack.last().pose());
            poseMat.translate(0.5f, 0.5f, 0.5f);
            poseMat.rotateZ((float)Math.toRadians(-45.0));
            poseMat.translate(-0.5f, -0.5f, -0.5f);

            // Get camera position for billboard calculation
            net.minecraft.world.phys.Vec3 cameraPos = BillboardSparkRenderer.getCameraPosition();

            // MARKER COLOR: Pure magenta (RGB 255, 0, 255)
            final int MARKER_R = 255;
            final int MARKER_G = 0;
            final int MARKER_B = 255;

            java.util.List<RealisticSpark> allSparks = RealisticSparkManager.getAllSparks(state.realisticSparks);
            for (RealisticSpark spark : allSparks) {
                float[] color = spark.getColor();
                float a = color[3];
                int headAlpha = (int)(a * 255);
                int tailAlpha = (int)(a * 60); // Fade at tail

                // ULTRA-THIN width - creates thin tube appearance
                float width = spark.type == RealisticSpark.TYPE_PRIMARY ? 0.0015f : 0.0008f;

                // Use BillboardSparkRenderer for proper camera-facing quad
                // This creates ONE thin quad that always faces the camera = thin tube look
                BillboardSparkRenderer.renderBillboardSpark(
                    sparkConsumer, poseMat, currentInverse, cameraPos,
                    spark.getHeadX(), spark.getHeadY(), spark.getHeadZ(),
                    spark.getTailX(), spark.getTailY(), spark.getTailZ(),
                    width,
                    MARKER_R, MARKER_G, MARKER_B, headAlpha, tailAlpha
                );
            }
        }
    }

    /**
     * Render the burning stick and all sparks
     * Uses worldToModel matrix to transform world-space sparks back to model-space for rendering
     */
    private void renderAllEffects(PoseStack.Pose pose, VertexConsumer consumer,
                                   TextureAtlasSprite sparkSprite,
                                   TextureAtlasSprite stickSprite,
                                   ItemDisplayContext displayContext,
                                   SparklerState state,
                                   Matrix4f worldToModel) {
        float stickX = 0.5f;
        float stickZ = 0.5f;
        float stickBaseY = 0.1f;
        float stickTipY = 0.9f;

        if (displayContext == ItemDisplayContext.GUI) {
            stickX = 0.5f;
            stickZ = 0.5f;
            stickBaseY = 0.15f;
            stickTipY = 0.85f;
        }

        // Apply transformation to make our Y-axis geometry work with handheld transforms
        // The handheld transforms expect diagonal geometry (like sword texture: bottom-left to top-right)
        // Our geometry is a vertical cylinder along Y-axis (0.1 to 0.9)
        // We rotate -45° around Z to make it diagonal, matching sword orientation
        Matrix4f poseMat = new Matrix4f(pose.pose());
        poseMat.translate(0.5f, 0.5f, 0.5f);
        poseMat.rotateZ((float)Math.toRadians(-45.0));
        poseMat.translate(-0.5f, -0.5f, -0.5f);

        float u0 = sparkSprite.getU0();
        float u1 = sparkSprite.getU1();
        float v0 = sparkSprite.getV0();
        float v1 = sparkSprite.getV1();
        float uMid = (u0 + u1) / 2;
        float vMid = (v0 + v1) / 2;
        int light = 0xF000F0;

        // Dimensions
        float totalHeight = stickTipY - stickBaseY;
        float halfWidth = 0.006f;  // Thin combustible coating - realistic sparkler thickness
        float wireRadius = halfWidth * 0.25f; // Handle/wire is 25% the width of the coating

        // UV coordinates from the stick texture
        float stickU0 = stickSprite.getU0();
        float stickU1 = stickSprite.getU1();
        float stickV0 = stickSprite.getV0();
        float stickV1 = stickSprite.getV1();

        // ========== RENDER THE INNER WIRE CORE ==========
        float meltProgress = state.getAverageMeltHeight();
        float burnY = stickTipY - meltProgress * totalHeight;

        // Render the base wire as dark metal
        StickRenderer.renderCylinder(consumer, poseMat,
            stickX, stickBaseY, stickZ,
            stickX, stickTipY, stickZ,
            wireRadius, wireRadius,
            uMid, vMid, light,
            45, 45, 50, 255);  // Dark gray/black wire

        // ========== RENDER BURNT CARBON RESIDUE ==========
        // Debris exists along the ENTIRE stick, hidden inside the coating.
        // As the coating burns away, the debris is revealed (was always there).
        // Render ALL debris - the coating (rendered after) will occlude what's not exposed.
        StickRenderer.renderBurntCarbonResidue(consumer, poseMat, state,
            stickX, stickZ, burnY, stickTipY, wireRadius,
            uMid, vMid, light);

        // Only add the hot spot if actively burning
        if (meltProgress > 0.01f && meltProgress < 0.98f) {
            // Bright spot at the burn point
            float hotSpotSize = 0.008f;
            float hotSpotBottom = burnY - 0.002f;
            float hotSpotTop = Math.min(stickTipY, burnY + hotSpotSize);

            // Render hot spot - bright white/yellow core
            StickRenderer.renderCylinder(consumer, poseMat,
                stickX, hotSpotBottom, stickZ,
                stickX, hotSpotTop, stickZ,
                wireRadius * 1.8f, wireRadius * 1.8f,
                uMid, vMid, light,
                255, 255, 230, 255);  // Bright white-yellow
        }

        // ========== RENDER THE OUTER COATING WITH UNIFORM BURNING ==========
        // Uses column-based rendering - each column around the perimeter has its own melt height
        // This guarantees NO floating pieces - material only exists below the melt line
        // Use spark sprite UVs (white texture) so vertex color shows through as grey
        StickRenderer.renderMeltingCoating(consumer, poseMat, state,
            stickX, stickZ, stickBaseY, stickTipY, halfWidth,
            u0, u1, v0, v1, light);

        // ========== BOTTOM CAP (stays at base) ==========
        // Use spark sprite UVs (white texture) so vertex color shows through
        RenderUtils.renderCapDoubleSided(consumer, poseMat,
            stickX - halfWidth, stickBaseY, stickZ + halfWidth,
            stickX + halfWidth, stickBaseY, stickZ + halfWidth,
            stickX + halfWidth, stickBaseY, stickZ - halfWidth,
            stickX - halfWidth, stickBaseY, stickZ - halfWidth,
            u0, u1, v0, v1,
            light, 0, -1, 0);
    }
}
