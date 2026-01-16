package com.nick.realisticfirework.client;

import com.nick.realisticfirework.data.SparklerData;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Ultra-high resolution state class for organic melting
 * Features:
 * - 128 erosion columns with cubic bezier interpolation
 * - Multiple harmonic wobble frequencies for organic edges
 * - Drip tendrils that extend below the main melt line
 * - Smooth gradient transitions at melt front
 * - NO floating pieces - physics-based melting only
 * - World-space spark tracking for realistic detachment when swinging
 */
public class SparklerState {
    private static final Random random = new Random();

    // Constants
    public static final float BURN_DURATION = 60.0f;  // Slower burn - 60 seconds
    public static final int NUM_EMISSION_POINTS = 60;
    public static final int EROSION_COLUMNS = 128;
    public static final int RENDER_COLUMNS = 256;
    public static final int DRIP_COUNT = 8;

    public long lastUpdateTime = 0;
    public float burnProgress = 0;
    public long fullyBurnedTime = 0;  // Timestamp when sparkler became fully burned (for post-burn light fade)
    public final List<Spark> sparks = new ArrayList<>();
    public final List<RealisticSpark> realisticSparks = new ArrayList<>();  // New realistic spark system
    public final boolean[] emissionPointsFired = new boolean[NUM_EMISSION_POINTS];
    public long lastAccessTime = System.currentTimeMillis();

    // World-space tracking for spark detachment
    // We store the inverse of the last frame's pose matrix to convert model->world
    // and the current pose to convert world->model for rendering
    public Matrix4f lastInversePose = null;
    public boolean hasLastPose = false;

    // Primary melt height per column (0 = intact at top, 1 = fully melted)
    public final float[] columnMeltHeight = new float[EROSION_COLUMNS];

    // Burn characteristics per column
    public final float[] columnBurnRate = new float[EROSION_COLUMNS];
    public final float[] columnDrip = new float[EROSION_COLUMNS];

    // Multiple wobble frequencies for organic detail (harmonics)
    public final float[] wobblePhase1 = new float[EROSION_COLUMNS];  // Primary wobble
    public final float[] wobblePhase2 = new float[EROSION_COLUMNS];  // Secondary harmonic
    public final float[] wobblePhase3 = new float[EROSION_COLUMNS];  // Tertiary micro-detail
    public final float[] wobbleSpeed1 = new float[EROSION_COLUMNS];
    public final float[] wobbleSpeed2 = new float[EROSION_COLUMNS];
    public final float[] wobbleSpeed3 = new float[EROSION_COLUMNS];
    public final float[] wobbleAmp1 = new float[EROSION_COLUMNS];
    public final float[] wobbleAmp2 = new float[EROSION_COLUMNS];
    public final float[] wobbleAmp3 = new float[EROSION_COLUMNS];

    // Drip tendrils - extend below main melt line
    public final float[] dripLength = new float[DRIP_COUNT];      // How far each drip extends
    public final float[] dripAngle = new float[DRIP_COUNT];       // Angular position
    public final float[] dripWidth = new float[DRIP_COUNT];       // Width of drip
    public final float[] dripSpeed = new float[DRIP_COUNT];       // Growth speed
    public final boolean[] dripActive = new boolean[DRIP_COUNT];  // Is this drip currently active
    public final float[] dripFade = new float[DRIP_COUNT];        // Fade factor for smooth death (1=full, 0=gone)

    // Perlin-like noise table for ultra-smooth variations
    public final float[] noiseTable = new float[256];

    // CRITICAL: Track maximum RENDERED height per render column to enforce monotonicity
    // This catches ALL sources of regeneration at the final render stage
    public final float[] maxRenderedHeight = new float[RENDER_COLUMNS + 1];

    public boolean erosionInitialized = false;

    public void reset() {
        burnProgress = 0;
        sparks.clear();
        realisticSparks.clear();
        Arrays.fill(emissionPointsFired, false);
        erosionInitialized = false;
        Arrays.fill(columnMeltHeight, 0);
        Arrays.fill(columnDrip, 0);
        Arrays.fill(dripLength, 0);
        Arrays.fill(dripActive, false);
        Arrays.fill(dripFade, 0);
        Arrays.fill(maxRenderedHeight, 0);
        lastInversePose = null;
        hasLastPose = false;
    }

    public void initializeErosion() {
        if (!erosionInitialized) {
            // Initialize noise table
            for (int i = 0; i < 256; i++) {
                noiseTable[i] = random.nextFloat();
            }

            // Initialize columns with varied characteristics
            for (int i = 0; i < EROSION_COLUMNS; i++) {
                // Burn rate with spatial coherence (nearby columns similar)
                float spatialNoise = sampleNoise(i * 2.0f);
                columnBurnRate[i] = 0.6f + spatialNoise * 0.8f;
                columnMeltHeight[i] = 0;
                columnDrip[i] = 0;

                // Multiple wobble harmonics for organic feel
                wobblePhase1[i] = random.nextFloat() * (float)Math.PI * 2;
                wobblePhase2[i] = random.nextFloat() * (float)Math.PI * 2;
                wobblePhase3[i] = random.nextFloat() * (float)Math.PI * 2;
                wobbleSpeed1[i] = 0.8f + random.nextFloat() * 1.5f;
                wobbleSpeed2[i] = 2.0f + random.nextFloat() * 3.0f;
                wobbleSpeed3[i] = 5.0f + random.nextFloat() * 8.0f;
                wobbleAmp1[i] = 0.012f + random.nextFloat() * 0.008f;
                wobbleAmp2[i] = 0.005f + random.nextFloat() * 0.004f;
                wobbleAmp3[i] = 0.002f + random.nextFloat() * 0.002f;
            }

            // Initialize drips at random positions
            for (int i = 0; i < DRIP_COUNT; i++) {
                dripAngle[i] = random.nextFloat() * (float)Math.PI * 2;
                dripWidth[i] = 0.008f + random.nextFloat() * 0.012f;
                dripSpeed[i] = 0.3f + random.nextFloat() * 0.5f;
                dripLength[i] = 0;
                dripActive[i] = false;
                dripFade[i] = 0;
            }

            // Initialize max rendered heights to 0
            Arrays.fill(maxRenderedHeight, 0);

            erosionInitialized = true;
        }
    }

    // Simple noise sampling for spatial coherence
    public float sampleNoise(float x) {
        int xi = ((int)x) & 255;
        int xi1 = (xi + 1) & 255;
        float t = x - (int)x;
        t = t * t * (3 - 2 * t); // Smoothstep
        return noiseTable[xi] + (noiseTable[xi1] - noiseTable[xi]) * t;
    }

    /**
     * HOT UNIFORM BURNING - like a real sparkler
     * The coating burns evenly because the wire heats the entire circumference uniformly.
     * Only tiny micro-variations (±3mm equivalent, ~0.004 in normalized units) are realistic.
     * NO ice cream melting, NO harsh columns, NO catch-up dynamics.
     * CRITICAL: Melt height can ONLY increase, NEVER decrease (regenerate)
     */
    public void updateErosion(float deltaTime) {
        float baseSpeed = 1.0f / BURN_DURATION;

        // Store previous heights - we will NEVER go below these
        float[] previousHeight = new float[EROSION_COLUMNS];
        System.arraycopy(columnMeltHeight, 0, previousHeight, 0, EROSION_COLUMNS);

        // Calculate current average height for uniform base progression
        float avgHeight = 0;
        for (int i = 0; i < EROSION_COLUMNS; i++) {
            avgHeight += columnMeltHeight[i];
        }
        avgHeight /= EROSION_COLUMNS;

        // All columns burn together at nearly the same rate
        // The wire is uniformly hot, so the burn front stays flat
        for (int i = 0; i < EROSION_COLUMNS; i++) {
            if (columnMeltHeight[i] >= SparklerData.MAX_BURN_PROGRESS) {
                continue; // Already fully melted (coating consumed, handle remains)
            }

            float myHeight = columnMeltHeight[i];

            // Base burn rate - nearly uniform for all columns
            float burnRate = baseSpeed;

            // TINY micro-variation for realism (±5% max, creates subtle texture)
            // This represents microscopic density variations in the sparkler coating
            float microNoise = sampleNoise(i * 0.3f + burnProgress * 5.0f);
            burnRate *= 0.97f + microNoise * 0.06f;  // 0.97 to 1.03 range

            // STRONG cohesion - columns stay together (±0.004 max deviation = ~3mm on a sparkler)
            // If a column gets ahead or behind, it strongly corrects toward the average
            float deviation = myHeight - avgHeight;
            float maxDeviation = 0.004f;  // Maximum allowed deviation (~3mm)

            if (Math.abs(deviation) > maxDeviation * 0.5f) {
                // Column is deviating - apply correction force
                if (deviation > 0) {
                    // This column is ahead - slow down slightly (but keep burning!)
                    float correction = Math.min(1.0f, deviation / maxDeviation);
                    burnRate *= 1.0f - correction * 0.3f;  // Slow to 70% at max deviation
                } else {
                    // This column is behind - speed up to catch up
                    float correction = Math.min(1.0f, Math.abs(deviation) / maxDeviation);
                    burnRate *= 1.0f + correction * 0.5f;  // Speed up to 150% at max deviation
                }
            }

            // Ensure burn rate stays positive
            burnRate = Math.max(0.01f * baseSpeed, burnRate);

            // Apply burn - STRICTLY MONOTONIC
            // Cap at 0.75 (MAX_BURN_PROGRESS) to preserve the handle (bottom 25%)
            float newHeight = myHeight + burnRate * deltaTime;
            columnMeltHeight[i] = Math.max(previousHeight[i], Math.min(SparklerData.MAX_BURN_PROGRESS, newHeight));
        }

        // Gentle smoothing pass - keeps the burn front cohesive
        // Uses wide kernel for smooth transitions, strong pull toward neighbors
        for (int pass = 0; pass < 2; pass++) {
            float[] beforeSmooth = new float[EROSION_COLUMNS];
            System.arraycopy(columnMeltHeight, 0, beforeSmooth, 0, EROSION_COLUMNS);

            for (int i = 0; i < EROSION_COLUMNS; i++) {
                // Wide neighborhood average (5 columns each side)
                float neighborSum = 0;
                float weightSum = 0;
                for (int offset = -5; offset <= 5; offset++) {
                    int idx = (i + offset + EROSION_COLUMNS) % EROSION_COLUMNS;
                    float weight = 1.0f / (1 + Math.abs(offset) * 0.3f);
                    neighborSum += beforeSmooth[idx] * weight;
                    weightSum += weight;
                }
                float neighborAvg = neighborSum / weightSum;

                // Strong blend toward neighbors (70% weight to neighborhood)
                float blended = beforeSmooth[i] * 0.3f + neighborAvg * 0.7f;

                // CRITICAL: Only allow increase, never decrease
                columnMeltHeight[i] = Math.max(previousHeight[i], blended);
            }
        }

        // Occasional tiny drip for visual interest (much rarer than before)
        if (random.nextFloat() < 0.02f * deltaTime) {
            int col = random.nextInt(EROSION_COLUMNS);
            if (columnMeltHeight[col] > 0.1f && columnMeltHeight[col] < 0.9f) {
                startDrip(col);
            }
        }

        // Final safety check - ensure monotonic increase from previous frame
        for (int i = 0; i < EROSION_COLUMNS; i++) {
            columnMeltHeight[i] = Math.max(previousHeight[i], columnMeltHeight[i]);
        }

        // Update drip tendrils
        updateDrips(deltaTime);
    }

    public void startDrip(int column) {
        // Find an inactive drip slot (fade must be 0 to reuse)
        for (int i = 0; i < DRIP_COUNT; i++) {
            if (!dripActive[i] && dripFade[i] <= 0) {
                dripActive[i] = true;
                dripAngle[i] = (float)(column * Math.PI * 2 / EROSION_COLUMNS);
                dripLength[i] = 0;
                dripWidth[i] = 0.003f + random.nextFloat() * 0.004f;  // Smaller drips
                dripSpeed[i] = 0.1f + random.nextFloat() * 0.15f;     // Slower, subtle
                dripFade[i] = 1.0f;  // Full visibility
                break;
            }
        }
    }

    public void updateDrips(float deltaTime) {
        for (int i = 0; i < DRIP_COUNT; i++) {
            if (dripActive[i]) {
                // Grow the drip - ONLY GROWS, NEVER SHRINKS
                dripLength[i] += dripSpeed[i] * deltaTime;

                // Get melt height at drip position (use base melt, not with drip)
                float meltHeight = getBaseMeltHeightAtAngle(dripAngle[i]);

                // Start fading if drip is too long or main melt catches up
                // Shorter max length for realistic sparkler (they don't drip much)
                if (dripLength[i] > 0.05f || meltHeight > 0.9f) {
                    // Begin fading instead of instant death
                    dripActive[i] = false;
                    // Keep dripLength as-is - it will fade out visually
                }

                // Drip thins as it extends
                dripWidth[i] *= (1.0f - deltaTime * 0.8f);  // Thins faster
                if (dripWidth[i] < 0.001f) {
                    dripActive[i] = false;
                }
            }

            // Fade out inactive drips smoothly (prevents height jump)
            if (!dripActive[i] && dripFade[i] > 0) {
                dripFade[i] -= deltaTime * 3.0f;  // Faster fade (~0.33 seconds)
                if (dripFade[i] <= 0) {
                    dripFade[i] = 0;
                    dripLength[i] = 0;  // Only reset length after fully faded
                }
            }
        }
    }

    /**
     * Get base melt height (without drip extension) for drip logic
     * Uses clamped Catmull-Rom to prevent overshoot
     */
    public float getBaseMeltHeightAtAngle(float angle) {
        // Normalize angle
        while (angle < 0) angle += (float)(Math.PI * 2);
        while (angle >= Math.PI * 2) angle -= (float)(Math.PI * 2);

        // Find column and interpolation factor
        float columnF = angle / (float)(Math.PI * 2) * EROSION_COLUMNS;
        int col0 = (int)columnF % EROSION_COLUMNS;
        float t = columnF - (int)columnF;

        // Get 4 columns for cubic interpolation
        int colM1 = (col0 - 1 + EROSION_COLUMNS) % EROSION_COLUMNS;
        int col1 = (col0 + 1) % EROSION_COLUMNS;
        int col2 = (col0 + 2) % EROSION_COLUMNS;

        float hM1 = columnMeltHeight[colM1];
        float h0 = columnMeltHeight[col0];
        float h1 = columnMeltHeight[col1];
        float h2 = columnMeltHeight[col2];

        // Catmull-Rom spline with CLAMPING to prevent overshoot below minimum
        float baseHeight = catmullRomClamped(hM1, h0, h1, h2, t);

        return Math.max(0, Math.min(1.0f, baseHeight));
    }

    /**
     * Get melt height at angle - STRICTLY MONOTONIC
     * NO wobble (wobble causes oscillation = regeneration)
     * Uses clamped interpolation to prevent overshoot
     */
    public float getMeltHeightAtAngle(float angle) {
        // Get base height without wobble - wobble was causing regeneration
        // The "organic" look now comes purely from the varied burn rates
        return getBaseMeltHeightAtAngle(angle);
    }

    /**
     * Catmull-Rom spline with CLAMPING
     * Prevents the spline from going BELOW the minimum of the two center points
     * This eliminates mathematical overshoot that could cause regeneration
     */
    public float catmullRomClamped(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        float result = 0.5f * (
            (2 * p1) +
            (-p0 + p2) * t +
            (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 +
            (-p0 + 3 * p1 - 3 * p2 + p3) * t3
        );

        // CRITICAL: Clamp result to be at least the minimum of the interpolated segment
        // This prevents Catmull-Rom overshoot from causing regeneration
        float minHeight = Math.min(p1, p2);
        return Math.max(minHeight, result);
    }

    /**
     * Get drip extension at a specific angle (how much drip extends below melt line)
     * Uses fade factor to prevent instant height jumps when drips die
     */
    public float getDripExtensionAtAngle(float angle) {
        float maxExtension = 0;
        for (int i = 0; i < DRIP_COUNT; i++) {
            // Check both active drips AND fading drips
            if (dripActive[i] || dripFade[i] > 0) {
                float angleDiff = Math.abs(angle - dripAngle[i]);
                if (angleDiff > Math.PI) angleDiff = (float)(Math.PI * 2) - angleDiff;

                // Gaussian falloff from drip center
                float width = dripWidth[i] * 10; // Convert to angular width
                if (angleDiff < width * 3) {
                    float falloff = (float)Math.exp(-(angleDiff * angleDiff) / (2 * width * width));
                    // Apply fade factor - drip smoothly fades out instead of instant death
                    float fadedLength = dripLength[i] * dripFade[i];
                    maxExtension = Math.max(maxExtension, fadedLength * falloff);
                }
            }
        }
        return maxExtension;
    }

    public float getAverageMeltHeight() {
        float sum = 0;
        for (int i = 0; i < EROSION_COLUMNS; i++) {
            sum += columnMeltHeight[i];
        }
        return sum / EROSION_COLUMNS;
    }
}
