package com.nick.realisticfirework.client;

import java.util.Random;

// Fragment - smaller piece that breaks off during fragmentation
public class SparkFragment {
    private static final Random random = new Random();

    public float x, y, z;
    public float vx, vy, vz;
    public float life;
    public float maxLife;
    public float brightness;
    public long seed;
    public float rotation;

    public SparkFragment(float x, float y, float z, float vx, float vy, float vz, float maxLife, float brightness, long seed) {
        this.x = x; this.y = y; this.z = z;
        this.vx = vx; this.vy = vy; this.vz = vz;
        this.life = 0;
        this.maxLife = maxLife;
        this.brightness = brightness;
        this.seed = seed;
        this.rotation = random.nextFloat() * (float)Math.PI * 2;
    }

    public boolean update(float deltaTime) {
        life += deltaTime / maxLife;
        if (life >= 1.0f) return false;

        x += vx * deltaTime;
        y += vy * deltaTime;
        z += vz * deltaTime;

        vy -= 3.5f * deltaTime;

        float drag = 0.12f;
        vx *= (1.0f - drag * deltaTime);
        vy *= (1.0f - drag * deltaTime);
        vz *= (1.0f - drag * deltaTime);

        rotation += 10f * deltaTime;
        return true;
    }

    public int[] getColor() {
        float progress = life;
        int r = 255;
        int g = (int)(255 - 100 * progress);
        int b = (int)(100 * (1.0f - progress));
        int a = progress > 0.7f ? (int)(255 * (1.0f - (progress - 0.7f) / 0.3f)) : 255;
        return new int[]{r, g, b, Math.max(0, a)};
    }
}
