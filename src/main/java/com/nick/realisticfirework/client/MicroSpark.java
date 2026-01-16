package com.nick.realisticfirework.client;

// MicroSpark - tiny particles shed during flight
public class MicroSpark {
    public float x, y, z;
    public float vx, vy, vz;
    public float life;
    public float maxLife;

    public MicroSpark(float x, float y, float z, float vx, float vy, float vz, float maxLife) {
        this.x = x; this.y = y; this.z = z;
        this.vx = vx; this.vy = vy; this.vz = vz;
        this.life = 0;
        this.maxLife = maxLife;
    }

    public boolean update(float deltaTime) {
        life += deltaTime / maxLife;
        if (life >= 1.0f) return false;

        x += vx * deltaTime;
        y += vy * deltaTime;
        z += vz * deltaTime;

        vy -= 5f * deltaTime;
        vx *= 0.9f;
        vy *= 0.95f;
        vz *= 0.9f;
        return true;
    }

    public int[] getColor() {
        float progress = life;
        int r = 255;
        int g = (int)(200 - 150 * progress);
        int b = (int)(50 * (1.0f - progress));
        int a = (int)(200 * (1.0f - progress));
        return new int[]{r, g, b, Math.max(0, a)};
    }
}
