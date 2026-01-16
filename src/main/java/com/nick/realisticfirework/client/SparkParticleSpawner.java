package com.nick.realisticfirework.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * ATTEMPT 18: Spawns native Minecraft particles to represent sparks.
 *
 * Instead of rendering custom geometry (which always looks geometric),
 * this uses Minecraft's built-in particle system which:
 * - Handles billboarding automatically
 * - Uses proper sprite rendering
 * - Can render very small points without disappearing
 *
 * We spawn particles every frame at spark positions - they die after 1 tick
 * so we don't accumulate particles, but we get the visual effect.
 */
@OnlyIn(Dist.CLIENT)
public class SparkParticleSpawner {

    /**
     * Spawn an instant particle that shows for 1 frame and dies.
     * This gives us the visual without building up particle count.
     */
    public static void spawnInstantParticle(ClientLevel level,
                                             double x, double y, double z,
                                             float r, float g, float b, float alpha,
                                             float size) {
        if (level == null) return;

        Minecraft mc = Minecraft.getInstance();
        ParticleEngine engine = mc.particleEngine;

        // Create a custom instant particle
        // Using CRIT particle as base (small, bright) and customize it
        Particle particle = engine.createParticle(
            ParticleTypes.CRIT, // Small point particle
            x, y, z,
            0, 0, 0  // No velocity - we control position from spark system
        );

        if (particle != null) {
            // Customize the particle
            particle.setColor(r, g, b);
            particle.setLifetime(1); // Dies after 1 tick (instant)
            particle.scale(size * 10f); // Scale to match our size

            // Note: alpha is handled by setColor's inherent brightness
            // Brighter colors = more visible
        }
    }

    /**
     * Spawn a glowing instant particle with larger size for glow effect
     */
    public static void spawnGlowParticle(ClientLevel level,
                                          double x, double y, double z,
                                          float r, float g, float b, float alpha,
                                          float size) {
        if (level == null) return;

        Minecraft mc = Minecraft.getInstance();
        ParticleEngine engine = mc.particleEngine;

        // Use END_ROD for glow - it's larger and has bloom-like appearance
        Particle particle = engine.createParticle(
            ParticleTypes.END_ROD,
            x, y, z,
            0, 0, 0
        );

        if (particle != null) {
            particle.setColor(r, g, b);
            particle.setLifetime(1);
            particle.scale(size * 5f);
        }
    }
}
