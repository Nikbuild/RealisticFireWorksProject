package com.nick.realisticfirework.client.lighting;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.nick.realisticfirework.RealisticFireworkMod;

/**
 * Client-side dynamic lighting engine.
 * Tracks all dynamic light sources and calculates light levels for rendering.
 *
 * This is the core of the custom dynamic lighting system - it intercepts
 * Minecraft's lighting calculations and adds light from dynamic sources.
 */
public class DynamicLightingEngine {

    private static final DynamicLightingEngine INSTANCE = new DynamicLightingEngine();

    // All tracked light sources - keyed by unique ID
    private final Map<UUID, DynamicLightSource> lightSources = new ConcurrentHashMap<>();

    // Spark light sources - separate tracking for performance
    private final Map<Long, DynamicLightSource> sparkLights = new ConcurrentHashMap<>();
    private long sparkIdCounter = 0;

    // Cache for recently calculated positions to avoid recalculating every frame
    private final Map<BlockPos, CachedLight> lightCache = new ConcurrentHashMap<>();
    private static final long CACHE_LIFETIME_MS = 50;  // Cache for 50ms (about 1 tick)

    // Configuration
    private boolean enabled = true;
    private boolean shadowsEnabled = true;
    private float globalBrightness = 1.0f;

    private DynamicLightingEngine() {}

    public static DynamicLightingEngine getInstance() {
        return INSTANCE;
    }

    // Debug: Track last log time to avoid spam
    private static long lastLogTime = 0;

    /**
     * Register or update a sparkler core light source.
     */
    public void updateSparklerLight(UUID sparklerId, Vec3 position, int luminance) {
        if (!enabled) return;

        // Debug: Log occasionally
        long now = System.currentTimeMillis();
        if (now - lastLogTime > 1000) {
            RealisticFireworkMod.LOGGER.info("CLIENT LIGHT: updateSparklerLight at {} luminance {}", position, luminance);
            lastLogTime = now;
        }

        DynamicLightSource source = lightSources.get(sparklerId);
        if (source == null) {
            // Long lifetime - will be refreshed each render frame
            source = new DynamicLightSource(position, luminance, 2000, true);
            lightSources.put(sparklerId, source);
        } else {
            source.setPosition(position);
            source.setLuminance(luminance);
            source.refresh(2000);  // Refresh for another 2 seconds
        }

        // Invalidate cache near this position
        invalidateCacheNear(position);
    }

    /**
     * Remove a sparkler light source.
     */
    public void removeSparklerLight(UUID sparklerId) {
        lightSources.remove(sparklerId);
    }

    /**
     * Add a spark light source (short-lived).
     * Returns the spark ID for tracking.
     */
    public long addSparkLight(Vec3 position, int luminance, long lifetimeMs) {
        if (!enabled) return -1;

        long id = sparkIdCounter++;
        DynamicLightSource source = new DynamicLightSource(position, luminance, lifetimeMs, false);
        sparkLights.put(id, source);
        return id;
    }

    /**
     * Update a spark's position (for moving sparks).
     */
    public void updateSparkPosition(long sparkId, Vec3 position) {
        DynamicLightSource source = sparkLights.get(sparkId);
        if (source != null) {
            source.setPosition(position);
        }
    }

    // Debug: Track last getDynamicLightAt log time
    private static long lastLightLogTime = 0;
    private static int callCount = 0;

    /**
     * Calculate the dynamic light level at a given position.
     * This is called by the mixin to add to vanilla lighting.
     */
    public int getDynamicLightAt(BlockPos pos) {
        if (!enabled) return 0;

        // Get the level for shadow ray casting
        BlockGetter level = null;
        if (shadowsEnabled) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                level = mc.level;
            }
        }

        Vec3 posVec = Vec3.atCenterOf(pos);
        float totalLight = 0;

        // Calculate light from sparkler cores (with shadow check)
        for (DynamicLightSource source : lightSources.values()) {
            if (!source.isExpired()) {
                totalLight += source.getLightAt(posVec, level);
            }
        }

        // Calculate light from sparks (capped for performance, no shadow check for sparks)
        int sparkCount = 0;
        for (DynamicLightSource source : sparkLights.values()) {
            if (!source.isExpired()) {
                totalLight += source.getLightAt(posVec);  // No shadow for sparks (performance)
                sparkCount++;
                if (sparkCount > 50) break;  // Performance cap
            }
        }

        // Apply global brightness and clamp
        int lightLevel = (int) (totalLight * globalBrightness);
        lightLevel = Math.min(15, Math.max(0, lightLevel));

        // Debug: Log when light is calculated and > 0
        callCount++;
        if (lightLevel > 0) {
            long now = System.currentTimeMillis();
            if (now - lastLightLogTime > 2000) {
                RealisticFireworkMod.LOGGER.info("CLIENT LIGHT: getDynamicLightAt {} = {} (sources: {}, sparks: {})",
                    pos, lightLevel, lightSources.size(), sparkLights.size());
                lastLightLogTime = now;
            }
        }

        return lightLevel;
    }

    /**
     * Get the dynamic light at a precise position (for entity rendering).
     */
    public float getDynamicLightAtPrecise(Vec3 pos) {
        if (!enabled) return 0;

        // Get the level for shadow ray casting
        BlockGetter level = null;
        if (shadowsEnabled) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                level = mc.level;
            }
        }

        float totalLight = 0;

        // Calculate light from sparkler cores (with shadow check)
        for (DynamicLightSource source : lightSources.values()) {
            if (!source.isExpired()) {
                totalLight += source.getLightAt(pos, level);
            }
        }

        // Calculate light from sparks (no shadow check for performance)
        int sparkCount = 0;
        for (DynamicLightSource source : sparkLights.values()) {
            if (!source.isExpired()) {
                totalLight += source.getLightAt(pos);
                sparkCount++;
                if (sparkCount > 50) break;
            }
        }

        return Math.min(15f, totalLight * globalBrightness);
    }

    /**
     * Clean up expired light sources.
     * Should be called periodically (e.g., every tick).
     */
    public void tick() {
        // Remove expired sparkler lights
        lightSources.entrySet().removeIf(entry -> entry.getValue().isExpired());

        // Remove expired spark lights
        sparkLights.entrySet().removeIf(entry -> entry.getValue().isExpired());

        // Clean old cache entries
        long now = System.currentTimeMillis();
        lightCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * Invalidate cache entries near a position.
     */
    private void invalidateCacheNear(Vec3 position) {
        BlockPos center = BlockPos.containing(position);
        int range = 10;  // Invalidate within 10 blocks

        lightCache.entrySet().removeIf(entry -> {
            BlockPos pos = entry.getKey();
            return Math.abs(pos.getX() - center.getX()) <= range &&
                   Math.abs(pos.getY() - center.getY()) <= range &&
                   Math.abs(pos.getZ() - center.getZ()) <= range;
        });
    }

    /**
     * Check if there are any active light sources.
     */
    public boolean hasActiveLights() {
        return !lightSources.isEmpty() || !sparkLights.isEmpty();
    }

    /**
     * Get all active light source positions for chunk rebuild triggering.
     */
    public Set<BlockPos> getAffectedChunks() {
        Set<BlockPos> chunks = new HashSet<>();

        for (DynamicLightSource source : lightSources.values()) {
            if (!source.isExpired()) {
                BlockPos pos = BlockPos.containing(source.getPosition());
                // Add this chunk and adjacent chunks
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        chunks.add(new BlockPos(
                            (pos.getX() >> 4) + dx,
                            pos.getY() >> 4,
                            (pos.getZ() >> 4) + dz
                        ));
                    }
                }
            }
        }

        return chunks;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setShadowsEnabled(boolean shadowsEnabled) {
        this.shadowsEnabled = shadowsEnabled;
    }

    public boolean areShadowsEnabled() {
        return shadowsEnabled;
    }

    public void setGlobalBrightness(float brightness) {
        this.globalBrightness = Math.max(0, Math.min(2.0f, brightness));
    }

    /**
     * Clear all light sources (e.g., when leaving world).
     */
    public void clear() {
        lightSources.clear();
        sparkLights.clear();
        lightCache.clear();
    }

    /**
     * Cache entry for light calculations.
     */
    private static class CachedLight {
        final int lightLevel;
        final long timestamp;

        CachedLight(int lightLevel) {
            this.lightLevel = lightLevel;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_LIFETIME_MS;
        }
    }
}
