package com.nick.realisticfirework.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Custom particle for sparkler sparks.
 * Uses Minecraft's particle system for proper small-point rendering.
 */
@OnlyIn(Dist.CLIENT)
public class SparkParticle extends TextureSheetParticle {

    private final float startAlpha;
    private final SpriteSet sprites;

    protected SparkParticle(ClientLevel level, double x, double y, double z,
                            double xSpeed, double ySpeed, double zSpeed,
                            float r, float g, float b, float alpha,
                            float size, int lifetime, SpriteSet sprites) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);

        this.sprites = sprites;

        // Set velocity
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;

        // Color - hot spark colors
        this.rCol = r;
        this.gCol = g;
        this.bCol = b;
        this.startAlpha = alpha;
        this.alpha = alpha;

        // Size - small for spark effect
        this.quadSize = size;

        // Lifetime
        this.lifetime = lifetime;
        this.age = 0;

        // Physics
        this.gravity = 0.3f;  // Sparks fall due to gravity
        this.friction = 0.96f;  // Air resistance

        // Use first sprite
        this.setSpriteFromAge(sprites);

        // Full brightness (emissive)
        // this.setLightColor() not available, using hasPhysics instead
        this.hasPhysics = false;  // Don't collide with blocks
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        // Apply gravity
        this.yd -= 0.04 * this.gravity;

        // Move
        this.move(this.xd, this.yd, this.zd);

        // Apply friction
        this.xd *= this.friction;
        this.yd *= this.friction;
        this.zd *= this.friction;

        // Fade out over time
        float progress = (float) this.age / (float) this.lifetime;
        this.alpha = this.startAlpha * (1.0f - progress);

        // Update sprite for animation
        this.setSpriteFromAge(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        // Use PARTICLE_SHEET_TRANSLUCENT for proper blending
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        // Full brightness - emissive particle
        return 0xF000F0;  // Max light level (15, 15 in packed format)
    }

    /**
     * Factory for creating spark particles
     */
    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                        double x, double y, double z,
                                        double xSpeed, double ySpeed, double zSpeed) {
            // Default spark appearance
            return new SparkParticle(level, x, y, z, xSpeed, ySpeed, zSpeed,
                    1.0f, 0.9f, 0.6f, 1.0f,  // Warm yellow-orange color
                    0.02f, 20, sprites);  // Small size, short lifetime
        }
    }

    /**
     * Create a spark particle with custom parameters
     */
    public static SparkParticle createCustom(ClientLevel level, double x, double y, double z,
                                              double xSpeed, double ySpeed, double zSpeed,
                                              float r, float g, float b, float alpha,
                                              float size, int lifetime, SpriteSet sprites) {
        return new SparkParticle(level, x, y, z, xSpeed, ySpeed, zSpeed,
                r, g, b, alpha, size, lifetime, sprites);
    }
}
