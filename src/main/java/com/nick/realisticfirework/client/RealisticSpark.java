package com.nick.realisticfirework.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Realistic spark with two-stage lifecycle:
 * 1. Primary spark shoots straight from burn point, travels short distance
 * 2. Primary explodes into secondary sparks that travel even shorter
 *
 * Key behaviors:
 * - Straight line travel (no curves)
 * - Upper hemisphere only (never below burn point)
 * - Tail-first dissipation (trail shrinks from back)
 * - White-hot color with slight fade to yellow at tips
 */
public class RealisticSpark {

    private static final Random random = new Random();

    // Spark type
    public static final int TYPE_PRIMARY = 0;
    public static final int TYPE_SECONDARY = 1;

    // Position (world space)
    public float x, y, z;

    // Fixed direction (normalized) - spark travels in straight line
    public float dirX, dirY, dirZ;

    // Speed (slows down over time due to air resistance)
    public float speed;
    public float initialSpeed;

    // Lifecycle
    public float age;           // Current age in seconds
    public float maxAge;        // When spark dies
    public float explosionAge;  // When primary explodes (only for TYPE_PRIMARY)
    public boolean hasExploded; // Has this primary already spawned secondaries

    // Visual properties
    public int type;            // PRIMARY or SECONDARY
    public float thickness;     // Base thickness of the spark line
    public float trailLength;   // Full trail length at birth
    public float currentTrailLength; // Current trail length (shrinks from tail)
    public long seed;           // For consistent randomness

    // Origin point (for reference - where spark was born)
    public float originX, originY, originZ;

    // Secondary sparks spawned from this one
    public List<RealisticSpark> children;

    /**
     * Create a primary spark
     */
    public static RealisticSpark createPrimary(float x, float y, float z,
                                                float dirX, float dirY, float dirZ,
                                                float speed) {
        RealisticSpark spark = new RealisticSpark();
        spark.x = x;
        spark.y = y;
        spark.z = z;
        spark.originX = x;
        spark.originY = y;
        spark.originZ = z;

        // Normalize direction
        float len = (float)Math.sqrt(dirX*dirX + dirY*dirY + dirZ*dirZ);
        spark.dirX = dirX / len;
        spark.dirY = dirY / len;
        spark.dirZ = dirZ / len;

        spark.speed = speed;
        spark.initialSpeed = speed;
        spark.age = 0;
        spark.maxAge = 0.3f + random.nextFloat() * 0.2f;  // 0.3-0.5 seconds
        spark.explosionAge = spark.maxAge * (0.95f + random.nextFloat() * 0.05f); // Explode at end of life (95-100%)
        spark.hasExploded = false;

        spark.type = TYPE_PRIMARY;
        spark.thickness = 0.003f + random.nextFloat() * 0.002f;  // Thick primary sparks
        spark.trailLength = 0.04f + random.nextFloat() * 0.03f;
        spark.currentTrailLength = spark.trailLength;
        spark.seed = random.nextLong();

        spark.children = new ArrayList<>();

        return spark;
    }

    /**
     * Create a secondary spark (from explosion)
     */
    public static RealisticSpark createSecondary(float x, float y, float z,
                                                  float dirX, float dirY, float dirZ,
                                                  float speed) {
        RealisticSpark spark = new RealisticSpark();
        spark.x = x;
        spark.y = y;
        spark.z = z;
        spark.originX = x;
        spark.originY = y;
        spark.originZ = z;

        // Normalize direction
        float len = (float)Math.sqrt(dirX*dirX + dirY*dirY + dirZ*dirZ);
        spark.dirX = dirX / len;
        spark.dirY = dirY / len;
        spark.dirZ = dirZ / len;

        spark.speed = speed;
        spark.initialSpeed = speed;
        spark.age = 0;
        spark.maxAge = 0.15f + random.nextFloat() * 0.15f;  // 0.15-0.3 seconds (shorter than primary)
        spark.explosionAge = Float.MAX_VALUE;  // Secondaries don't explode
        spark.hasExploded = true;  // Mark as already exploded so it won't spawn children

        spark.type = TYPE_SECONDARY;
        spark.thickness = 0.0015f + random.nextFloat() * 0.001f;  // Thinner secondary sparks
        spark.trailLength = 0.02f + random.nextFloat() * 0.015f;  // Shorter trail
        spark.currentTrailLength = spark.trailLength;
        spark.seed = random.nextLong();

        spark.children = new ArrayList<>();

        return spark;
    }

    /**
     * Update the spark. Returns false when spark should be removed.
     */
    public boolean update(float deltaTime) {
        age += deltaTime;

        // Update children first
        children.removeIf(child -> !child.update(deltaTime));

        // Primary sparks stay alive until all children are gone
        if (type == TYPE_PRIMARY && hasExploded) {
            // Once exploded, primary is invisible but keeps children alive
            return !children.isEmpty();
        }

        // Normal death check for non-exploded primaries and secondaries
        if (age >= maxAge) {
            return false;
        }

        // Move in straight line
        float movement = speed * deltaTime;
        x += dirX * movement;
        y += dirY * movement;
        z += dirZ * movement;

        // Slow down due to air resistance (heated metal particles experience drag)
        float drag = type == TYPE_PRIMARY ? 0.92f : 0.88f;  // Secondaries slow faster
        speed *= (float)Math.pow(drag, deltaTime * 60f);  // Frame-rate independent drag

        // Tail-first dissipation: trail shrinks from back as spark ages
        // Trail stays full length until halfway through life, then shrinks
        float lifeProgress = age / maxAge;
        if (lifeProgress > 0.4f) {
            float shrinkProgress = (lifeProgress - 0.4f) / 0.6f;  // 0 to 1 over last 60% of life
            currentTrailLength = trailLength * (1.0f - shrinkProgress * shrinkProgress);
        }

        // Check if primary should explode
        if (type == TYPE_PRIMARY && !hasExploded && age >= explosionAge) {
            explode();
        }

        return true;
    }

    /**
     * Primary spark explodes into secondary sparks
     */
    private void explode() {
        hasExploded = true;

        // Spawn 3-5 secondary sparks
        int numSecondaries = 3 + random.nextInt(3);

        for (int i = 0; i < numSecondaries; i++) {
            // Secondary direction: spread out from primary direction
            // Use golden angle distribution for even spread
            float goldenAngle = (float)(Math.PI * (3.0 - Math.sqrt(5.0)));
            float theta = goldenAngle * i + random.nextFloat() * 0.5f;
            float spreadAngle = 0.3f + random.nextFloat() * 0.5f;  // 17-46 degrees from primary

            // Create perpendicular vectors to primary direction
            float perpX, perpY, perpZ;
            if (Math.abs(dirY) < 0.9f) {
                perpX = -dirZ;
                perpY = 0;
                perpZ = dirX;
            } else {
                perpX = 1;
                perpY = 0;
                perpZ = 0;
            }
            float perpLen = (float)Math.sqrt(perpX*perpX + perpY*perpY + perpZ*perpZ);
            perpX /= perpLen; perpY /= perpLen; perpZ /= perpLen;

            float perp2X = dirY * perpZ - dirZ * perpY;
            float perp2Y = dirZ * perpX - dirX * perpZ;
            float perp2Z = dirX * perpY - dirY * perpX;

            // Rotate around primary direction
            float cosTheta = (float)Math.cos(theta);
            float sinTheta = (float)Math.sin(theta);
            float cosSpread = (float)Math.cos(spreadAngle);
            float sinSpread = (float)Math.sin(spreadAngle);

            // Secondary direction
            float secDirX = dirX * cosSpread + (perpX * cosTheta + perp2X * sinTheta) * sinSpread;
            float secDirY = dirY * cosSpread + (perpY * cosTheta + perp2Y * sinTheta) * sinSpread;
            float secDirZ = dirZ * cosSpread + (perpZ * cosTheta + perp2Z * sinTheta) * sinSpread;

            // Ensure secondary doesn't go too far down (keep mostly in upper hemisphere relative to origin)
            // We allow some downward angle but not straight down
            if (secDirY < -0.3f) {
                secDirY = -0.3f + random.nextFloat() * 0.2f;
                // Renormalize
                float len = (float)Math.sqrt(secDirX*secDirX + secDirY*secDirY + secDirZ*secDirZ);
                secDirX /= len; secDirY /= len; secDirZ /= len;
            }

            // Secondary speed: less energy than primary had
            float secSpeed = speed * (0.4f + random.nextFloat() * 0.4f);

            RealisticSpark secondary = createSecondary(x, y, z, secDirX, secDirY, secDirZ, secSpeed);
            children.add(secondary);
        }
    }

    /**
     * Get the head position (current position)
     */
    public float getHeadX() { return x; }
    public float getHeadY() { return y; }
    public float getHeadZ() { return z; }

    /**
     * Get the tail position (behind the head along direction)
     */
    public float getTailX() { return x - dirX * currentTrailLength; }
    public float getTailY() { return y - dirY * currentTrailLength; }
    public float getTailZ() { return z - dirZ * currentTrailLength; }

    /**
     * Get color based on life progress.
     * White-hot at birth, fading to yellow then amber at death.
     */
    public float[] getColor() {
        float lifeProgress = age / maxAge;

        float r, g, b, a;

        if (lifeProgress < 0.3f) {
            // Pure white-hot
            r = 1.0f;
            g = 1.0f;
            b = 1.0f;
        } else if (lifeProgress < 0.6f) {
            // White to pale yellow
            float t = (lifeProgress - 0.3f) / 0.3f;
            r = 1.0f;
            g = 1.0f - t * 0.05f;
            b = 1.0f - t * 0.3f;
        } else if (lifeProgress < 0.85f) {
            // Pale yellow to yellow
            float t = (lifeProgress - 0.6f) / 0.25f;
            r = 1.0f;
            g = 0.95f - t * 0.1f;
            b = 0.7f - t * 0.3f;
        } else {
            // Yellow to amber (final fade)
            float t = (lifeProgress - 0.85f) / 0.15f;
            r = 1.0f;
            g = 0.85f - t * 0.25f;
            b = 0.4f - t * 0.3f;
        }

        // Alpha: full brightness until late in life, then fade
        if (lifeProgress < 0.7f) {
            a = 1.0f;
        } else {
            a = 1.0f - (lifeProgress - 0.7f) / 0.3f;
        }

        // Secondary sparks are slightly dimmer
        if (type == TYPE_SECONDARY) {
            a *= 0.85f;
        }

        return new float[]{r, g, b, a};
    }

    /**
     * Get brightness for glow effect (brighter at head, dimmer at tail)
     */
    public float getBrightness() {
        float lifeProgress = age / maxAge;
        float baseBrightness = type == TYPE_PRIMARY ? 1.0f : 0.7f;

        // Fade brightness as spark dies
        if (lifeProgress > 0.5f) {
            baseBrightness *= 1.0f - (lifeProgress - 0.5f);
        }

        return baseBrightness;
    }

    /**
     * Get all sparks (this one and all children recursively) as a flat list
     */
    public void collectAllSparks(List<RealisticSpark> list) {
        // Don't render exploded primaries (they're invisible, only their children show)
        if (!(type == TYPE_PRIMARY && hasExploded)) {
            list.add(this);
        }
        for (RealisticSpark child : children) {
            child.collectAllSparks(list);
        }
    }
}
