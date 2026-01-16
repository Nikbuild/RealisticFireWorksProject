package com.nick.realisticfirework.client.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Represents a dynamic light source in the world.
 * Used for client-side dynamic lighting calculations.
 */
public class DynamicLightSource {
    private Vec3 position;
    private int luminance;
    private long expirationTime;
    private final boolean isCore;  // true for sparkler core, false for sparks

    public DynamicLightSource(Vec3 position, int luminance, long lifetimeMs, boolean isCore) {
        this.position = position;
        this.luminance = luminance;
        this.expirationTime = System.currentTimeMillis() + lifetimeMs;
        this.isCore = isCore;
    }

    public Vec3 getPosition() {
        return position;
    }

    public void setPosition(Vec3 position) {
        this.position = position;
    }

    public int getLuminance() {
        return luminance;
    }

    public void setLuminance(int luminance) {
        this.luminance = luminance;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expirationTime;
    }

    public void refresh(long lifetimeMs) {
        this.expirationTime = System.currentTimeMillis() + lifetimeMs;
    }

    public boolean isCore() {
        return isCore;
    }

    /**
     * Calculate the light contribution at a given position (no shadow check).
     * Uses inverse square falloff for realistic light attenuation.
     */
    public float getLightAt(Vec3 pos) {
        return getLightAt(pos, null);
    }

    /**
     * Calculate the light contribution at a given position with optional shadow check.
     * Uses inverse square falloff for realistic light attenuation.
     *
     * @param pos The position to calculate light at
     * @param level The level for shadow ray casting (null to skip shadow check)
     */
    public float getLightAt(Vec3 pos, BlockGetter level) {
        double distance = position.distanceTo(pos);
        if (distance > 15.0) return 0;  // Beyond max light range

        // Use smooth falloff formula similar to LambDynamicLights
        // Light falls off over ~8 blocks for luminance 15
        double range = luminance * 0.5;  // Effective range based on luminance
        if (distance > range) return 0;

        // Check for shadows if level is provided
        float shadowFactor = 1.0f;
        if (level != null && distance > 0.5) {  // Skip shadow check for very close blocks
            shadowFactor = calculateShadowFactor(level, pos);
            if (shadowFactor <= 0) {
                return 0;  // Fully in shadow
            }
        }

        double multiplier = 1.0 - (distance / range);
        return (float) (multiplier * luminance * shadowFactor);
    }

    /**
     * Calculate shadow factor using a single straight-line ray.
     * Light travels in straight lines only - no wrapping around obstacles.
     * Returns 0.0 for full shadow, 1.0 for no shadow, partial for semi-transparent blocks.
     */
    private float calculateShadowFactor(BlockGetter level, Vec3 targetPos) {
        // Single ray - light travels in straight lines like a laser
        return traceRay(level, targetPos);
    }

    /**
     * Trace a single ray from light to target.
     * Uses fine-grained stepping with face-crossing detection to prevent diagonal bleed-through.
     * Returns 1.0 if unblocked, 0.0 if fully blocked, partial for semi-transparent.
     */
    private float traceRay(BlockGetter level, Vec3 targetPos) {
        Vec3 lightPos = this.position;
        Vec3 direction = targetPos.subtract(lightPos);
        double distance = direction.length();

        if (distance < 0.1) return 1.0f;  // Same position

        // Normalize
        direction = direction.normalize();

        // Very fine step size to catch all block crossings
        double stepSize = 0.1;
        int maxSteps = (int) (distance / stepSize) + 1;
        maxSteps = Math.min(maxSteps, 150);  // Cap iterations

        int lastBlockX = Integer.MIN_VALUE;
        int lastBlockY = Integer.MIN_VALUE;
        int lastBlockZ = Integer.MIN_VALUE;

        float lightRemaining = 1.0f;

        int targetBlockX = (int) Math.floor(targetPos.x);
        int targetBlockY = (int) Math.floor(targetPos.y);
        int targetBlockZ = (int) Math.floor(targetPos.z);

        for (int i = 1; i < maxSteps; i++) {
            double t = i * stepSize;
            if (t >= distance - 0.05) break;

            Vec3 checkPos = lightPos.add(direction.scale(t));
            int blockX = (int) Math.floor(checkPos.x);
            int blockY = (int) Math.floor(checkPos.y);
            int blockZ = (int) Math.floor(checkPos.z);

            // Skip target block
            if (blockX == targetBlockX && blockY == targetBlockY && blockZ == targetBlockZ) {
                continue;
            }

            // When we transition between blocks, check for diagonal bleed-through
            if (lastBlockX != Integer.MIN_VALUE) {
                int dx = blockX - lastBlockX;
                int dy = blockY - lastBlockY;
                int dz = blockZ - lastBlockZ;

                // Count how many axes changed
                int changes = (dx != 0 ? 1 : 0) + (dy != 0 ? 1 : 0) + (dz != 0 ? 1 : 0);

                // If moving diagonally (2 or 3 axes changed), check the edge/corner blocks
                if (changes >= 2) {
                    // Check all intermediate blocks that could block diagonal movement
                    // For 2D diagonal (e.g., X and Z both change), check both edge blocks
                    // For 3D diagonal, check all face-adjacent blocks

                    if (dx != 0 && dz != 0) {
                        // XZ diagonal - check both edge blocks
                        if (isBlockOpaque(level, lastBlockX + dx, lastBlockY, lastBlockZ) ||
                            isBlockOpaque(level, lastBlockX, lastBlockY, lastBlockZ + dz)) {
                            return 0;
                        }
                    }
                    if (dx != 0 && dy != 0) {
                        // XY diagonal
                        if (isBlockOpaque(level, lastBlockX + dx, lastBlockY, lastBlockZ) ||
                            isBlockOpaque(level, lastBlockX, lastBlockY + dy, lastBlockZ)) {
                            return 0;
                        }
                    }
                    if (dy != 0 && dz != 0) {
                        // YZ diagonal
                        if (isBlockOpaque(level, lastBlockX, lastBlockY + dy, lastBlockZ) ||
                            isBlockOpaque(level, lastBlockX, lastBlockY, lastBlockZ + dz)) {
                            return 0;
                        }
                    }
                    if (changes == 3) {
                        // Full 3D diagonal - check all 6 face-adjacent intermediate blocks
                        if (isBlockOpaque(level, lastBlockX + dx, lastBlockY, lastBlockZ) ||
                            isBlockOpaque(level, lastBlockX, lastBlockY + dy, lastBlockZ) ||
                            isBlockOpaque(level, lastBlockX, lastBlockY, lastBlockZ + dz) ||
                            isBlockOpaque(level, lastBlockX + dx, lastBlockY + dy, lastBlockZ) ||
                            isBlockOpaque(level, lastBlockX + dx, lastBlockY, lastBlockZ + dz) ||
                            isBlockOpaque(level, lastBlockX, lastBlockY + dy, lastBlockZ + dz)) {
                            return 0;
                        }
                    }
                }
            }

            // Check current block if different from last
            if (blockX != lastBlockX || blockY != lastBlockY || blockZ != lastBlockZ) {
                float occlusion = getBlockOcclusion(level, blockX, blockY, blockZ);
                if (occlusion >= 1.0f) {
                    return 0;  // Fully blocked
                } else if (occlusion > 0) {
                    lightRemaining *= (1.0f - occlusion * 0.5f);
                    if (lightRemaining < 0.1f) {
                        return 0;
                    }
                }

                lastBlockX = blockX;
                lastBlockY = blockY;
                lastBlockZ = blockZ;
            }
        }

        return lightRemaining;
    }

    /**
     * Check if a block is fully opaque (blocks light completely).
     */
    private boolean isBlockOpaque(BlockGetter level, int x, int y, int z) {
        try {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            return state.canOcclude() || state.getLightBlock(level, pos) >= 15;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get occlusion value for a block (0 = transparent, 1 = fully opaque).
     */
    private float getBlockOcclusion(BlockGetter level, int x, int y, int z) {
        try {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (state.canOcclude()) {
                return 1.0f;
            }
            int lightBlock = state.getLightBlock(level, pos);
            if (lightBlock >= 15) {
                return 1.0f;
            }
            return lightBlock / 15.0f;
        } catch (Exception e) {
            return 0;
        }
    }
}
