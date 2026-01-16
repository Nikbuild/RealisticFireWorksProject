package com.nick.realisticfirework.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Spark {
    private static final Random random = new Random();

    public static final int SPARK_CORE = 0;      // Brilliant white, very short, creates central brightness
    public static final int SPARK_INNER = 1;     // Bright white/silver, medium range wire-like
    public static final int SPARK_OUTER = 2;     // White to yellow tips, long radiating wires
    public static final int SPARK_HAIR = 3;      // Ultra-thin hair-like sparks for density

    // Spark phases - 8 phases mapped to 25 frames
    public static final int PHASE_EJECTION = 0;        // Frames 1-3: Violent ejection, molten blob
    public static final int PHASE_STRETCH = 1;         // Frames 4-7: Blob stretches into tadpole
    public static final int PHASE_FLIGHT = 2;          // Frames 8-12: Comet flight with corona
    public static final int PHASE_INSTABILITY = 3;     // Frames 13-16: Wobbling, cracking, calving
    public static final int PHASE_FRAGMENT_PRIMARY = 4;// Frames 17-19: Main body ruptures
    public static final int PHASE_FRAGMENT_SECONDARY = 5;// Frames 20-22: Chunks split again
    public static final int PHASE_BLOOM = 6;           // Frames 23-24: Terminal bloom explosion
    public static final int PHASE_EMBER = 7;           // Frame 25: Ember rain fadeout

    // World-space position and velocity - these don't rotate with the item
    public float wx, wy, wz;    // World-space position
    public float vx, vy, vz;    // World-space velocity
    public float life;          // 0 to 1, dies at 1
    public float maxLife;       // Total lifetime
    public float length;        // Trail length
    public float brightness;    // How bright this spark is (for center glow effect)
    public int sparkType;       // SPARK_CORE, SPARK_INNER, or SPARK_OUTER
    public float wobblePhase;   // For chaotic movement
    public float wobbleSpeed;   // How fast the wobble changes
    public long seed;           // Random seed for consistent jagged shape

    // === NEW: Phase-based lifecycle ===
    public int phase;                          // Current phase (0-7)
    public float phaseTime;                    // Time spent in current phase
    public float rotation;                     // Spin rotation angle
    public float rotationSpeed;                // How fast the spark spins
    public float blobStretch;                  // How stretched the blob is (0=sphere, 1=full tadpole)
    public float pulsePhase;                   // For pulsing/flickering effect
    public float crackProgress;                // How cracked the surface is (0-1)
    public boolean hasFragmented;              // Has primary fragmentation occurred
    public boolean hasSecondaryFragmented;     // Has secondary fragmentation occurred
    public boolean hasBloomed;                 // Has terminal bloom occurred

    // Fragment children - created during fragmentation phases
    public List<SparkFragment> fragments;      // Child fragments
    public List<MicroSpark> microSparks;       // Tiny particles shed during flight

    // Trail history for rendering smooth curves
    public float[] trailHistoryX;
    public float[] trailHistoryY;
    public float[] trailHistoryZ;
    public int trailHistoryIndex;
    public static final int TRAIL_HISTORY_SIZE = 12;

    // Bloom rays - created during terminal bloom
    public float[] bloomRayDirX;
    public float[] bloomRayDirY;
    public float[] bloomRayDirZ;
    public float[] bloomRayLength;
    public float[] bloomRayFade;
    public int numBloomRays;

    // Create spark with world-space coordinates (after transformation)
    public Spark(float wx, float wy, float wz, float vx, float vy, float vz, float maxLife, float length, float brightness, int sparkType) {
        this.wx = wx;
        this.wy = wy;
        this.wz = wz;
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
        this.life = 0;
        this.maxLife = maxLife;
        this.length = length;
        this.brightness = brightness;
        this.sparkType = sparkType;
        this.wobblePhase = random.nextFloat() * (float)Math.PI * 2;
        this.wobbleSpeed = 10f + random.nextFloat() * 20f;
        this.seed = random.nextLong();

        // Initialize phase system
        this.phase = PHASE_EJECTION;
        this.phaseTime = 0;
        this.rotation = random.nextFloat() * (float)Math.PI * 2;
        this.rotationSpeed = 15f + random.nextFloat() * 25f;
        this.blobStretch = 0;
        this.pulsePhase = random.nextFloat() * (float)Math.PI * 2;
        this.crackProgress = 0;
        this.hasFragmented = false;
        this.hasSecondaryFragmented = false;
        this.hasBloomed = false;

        this.fragments = new ArrayList<>();
        this.microSparks = new ArrayList<>();

        // Initialize trail history
        this.trailHistoryX = new float[TRAIL_HISTORY_SIZE];
        this.trailHistoryY = new float[TRAIL_HISTORY_SIZE];
        this.trailHistoryZ = new float[TRAIL_HISTORY_SIZE];
        this.trailHistoryIndex = 0;
        for (int i = 0; i < TRAIL_HISTORY_SIZE; i++) {
            trailHistoryX[i] = wx;
            trailHistoryY[i] = wy;
            trailHistoryZ[i] = wz;
        }

        // Bloom rays initialized when bloom phase starts
        this.numBloomRays = 0;
    }

    // Backwards compatible constructor
    public Spark(float wx, float wy, float wz, float vx, float vy, float vz, float maxLife, float length, float brightness) {
        this(wx, wy, wz, vx, vy, vz, maxLife, length, brightness, SPARK_OUTER);
    }

    // Get current phase based on life progress (25 frames mapped to 0-1 life)
    public int calculatePhase() {
        float frame = life * 25f;
        if (frame < 3) return PHASE_EJECTION;
        if (frame < 7) return PHASE_STRETCH;
        if (frame < 12) return PHASE_FLIGHT;
        if (frame < 16) return PHASE_INSTABILITY;
        if (frame < 19) return PHASE_FRAGMENT_PRIMARY;
        if (frame < 22) return PHASE_FRAGMENT_SECONDARY;
        if (frame < 24) return PHASE_BLOOM;
        return PHASE_EMBER;
    }

    public boolean update(float deltaTime) {
        life += deltaTime / maxLife;
        if (life >= 1.0f) return false;

        // Update phase
        int newPhase = calculatePhase();
        if (newPhase != phase) {
            phase = newPhase;
            phaseTime = 0;
            onPhaseChange(newPhase);
        } else {
            phaseTime += deltaTime;
        }

        // Update rotation and pulse
        rotation += rotationSpeed * deltaTime;
        pulsePhase += 20f * deltaTime;
        wobblePhase += wobbleSpeed * deltaTime;

        // Phase-specific updates
        switch (phase) {
            case PHASE_EJECTION:
                updateEjectionPhase(deltaTime);
                break;
            case PHASE_STRETCH:
                updateStretchPhase(deltaTime);
                break;
            case PHASE_FLIGHT:
                updateFlightPhase(deltaTime);
                break;
            case PHASE_INSTABILITY:
                updateInstabilityPhase(deltaTime);
                break;
            case PHASE_FRAGMENT_PRIMARY:
                updatePrimaryFragmentPhase(deltaTime);
                break;
            case PHASE_FRAGMENT_SECONDARY:
                updateSecondaryFragmentPhase(deltaTime);
                break;
            case PHASE_BLOOM:
                updateBloomPhase(deltaTime);
                break;
            case PHASE_EMBER:
                updateEmberPhase(deltaTime);
                break;
        }

        // Update trail history
        trailHistoryIndex = (trailHistoryIndex + 1) % TRAIL_HISTORY_SIZE;
        trailHistoryX[trailHistoryIndex] = wx;
        trailHistoryY[trailHistoryIndex] = wy;
        trailHistoryZ[trailHistoryIndex] = wz;

        // Update fragments
        fragments.removeIf(f -> !f.update(deltaTime));

        // Update micro sparks
        microSparks.removeIf(m -> !m.update(deltaTime));

        // Update bloom rays
        if (numBloomRays > 0) {
            for (int i = 0; i < numBloomRays; i++) {
                bloomRayFade[i] -= deltaTime * 4f;
                bloomRayLength[i] += deltaTime * 2f;
            }
        }

        return true;
    }

    public void onPhaseChange(int newPhase) {
        java.util.Random phaseRandom = new java.util.Random(seed + newPhase);

        switch (newPhase) {
            case PHASE_FRAGMENT_PRIMARY:
                if (!hasFragmented) {
                    hasFragmented = true;
                    // Create 3-5 primary fragments
                    int numFrags = 3 + phaseRandom.nextInt(3);
                    for (int i = 0; i < numFrags; i++) {
                        float angle = (float)(i * Math.PI * 2 / numFrags) + phaseRandom.nextFloat() * 0.5f;
                        float upAngle = -0.3f + phaseRandom.nextFloat() * 0.6f;
                        float speed = 0.5f + phaseRandom.nextFloat() * 1.0f;
                        float fragVx = vx + (float)(Math.cos(angle) * Math.cos(upAngle)) * speed;
                        float fragVy = vy + (float)(Math.sin(upAngle)) * speed;
                        float fragVz = vz + (float)(Math.sin(angle) * Math.cos(upAngle)) * speed;
                        fragments.add(new SparkFragment(wx, wy, wz, fragVx, fragVy, fragVz,
                                0.4f + phaseRandom.nextFloat() * 0.3f, brightness * 0.7f, seed + i * 1000));
                    }
                }
                break;

            case PHASE_FRAGMENT_SECONDARY:
                if (!hasSecondaryFragmented) {
                    hasSecondaryFragmented = true;
                    // Each fragment splits into 2-3 more
                    List<SparkFragment> newFrags = new ArrayList<>();
                    for (SparkFragment frag : fragments) {
                        int numSplits = 2 + phaseRandom.nextInt(2);
                        for (int i = 0; i < numSplits; i++) {
                            float angle = phaseRandom.nextFloat() * (float)Math.PI * 2;
                            float speed = 0.3f + phaseRandom.nextFloat() * 0.5f;
                            newFrags.add(new SparkFragment(
                                    frag.x, frag.y, frag.z,
                                    frag.vx + (float)Math.cos(angle) * speed,
                                    frag.vy + (phaseRandom.nextFloat() - 0.5f) * speed,
                                    frag.vz + (float)Math.sin(angle) * speed,
                                    0.2f + phaseRandom.nextFloat() * 0.2f,
                                    frag.brightness * 0.6f,
                                    frag.seed + i * 100));
                        }
                    }
                    fragments.addAll(newFrags);
                }
                break;

            case PHASE_BLOOM:
                if (!hasBloomed) {
                    hasBloomed = true;
                    // Create bloom rays for main spark and each fragment
                    int totalRays = 6 + phaseRandom.nextInt(4);
                    for (SparkFragment frag : fragments) {
                        totalRays += 4 + phaseRandom.nextInt(3);
                    }
                    numBloomRays = Math.min(totalRays, 50);
                    bloomRayDirX = new float[numBloomRays];
                    bloomRayDirY = new float[numBloomRays];
                    bloomRayDirZ = new float[numBloomRays];
                    bloomRayLength = new float[numBloomRays];
                    bloomRayFade = new float[numBloomRays];

                    for (int i = 0; i < numBloomRays; i++) {
                        // Random direction on sphere
                        float theta = phaseRandom.nextFloat() * (float)Math.PI * 2;
                        float phi = (float)Math.acos(2 * phaseRandom.nextFloat() - 1);
                        bloomRayDirX[i] = (float)(Math.sin(phi) * Math.cos(theta));
                        bloomRayDirY[i] = (float)(Math.sin(phi) * Math.sin(theta));
                        bloomRayDirZ[i] = (float)(Math.cos(phi));
                        bloomRayLength[i] = 0.01f;
                        bloomRayFade[i] = 1.0f;
                    }
                }
                break;
        }
    }

    public void updateEjectionPhase(float deltaTime) {
        // Violent initial acceleration, blob shape
        float ejectionBoost = 1.5f * (1.0f - phaseTime * 3f);
        if (ejectionBoost > 0) {
            float speed = getSpeed();
            if (speed > 0.01f) {
                vx += (vx / speed) * ejectionBoost * deltaTime;
                vy += (vy / speed) * ejectionBoost * deltaTime;
                vz += (vz / speed) * ejectionBoost * deltaTime;
            }
        }

        // Spawn recoil micro-sparks
        if (random.nextFloat() < 0.3f) {
            microSparks.add(new MicroSpark(wx, wy, wz,
                    -vx * 0.3f + (random.nextFloat() - 0.5f) * 0.5f,
                    -vy * 0.3f + (random.nextFloat() - 0.5f) * 0.5f,
                    -vz * 0.3f + (random.nextFloat() - 0.5f) * 0.5f,
                    0.1f + random.nextFloat() * 0.1f));
        }

        updateBasicPhysics(deltaTime);
    }

    public void updateStretchPhase(float deltaTime) {
        // Blob stretches into tadpole shape
        blobStretch = Math.min(1.0f, blobStretch + deltaTime * 3f);

        // Spawn micro-droplets from spinning
        if (random.nextFloat() < 0.2f) {
            float angle = random.nextFloat() * (float)Math.PI * 2;
            microSparks.add(new MicroSpark(wx, wy, wz,
                    (float)Math.cos(angle) * 0.3f,
                    (random.nextFloat() - 0.5f) * 0.2f,
                    (float)Math.sin(angle) * 0.3f,
                    0.08f + random.nextFloat() * 0.08f));
        }

        updateBasicPhysics(deltaTime);
    }

    public void updateFlightPhase(float deltaTime) {
        // Corkscrew motion
        float corkscrewStrength = 0.15f;
        float corkscrewSpeed = wobblePhase * 2f;
        vx += (float)Math.sin(corkscrewSpeed) * corkscrewStrength * deltaTime;
        vz += (float)Math.cos(corkscrewSpeed) * corkscrewStrength * deltaTime;

        // Shed continuous stream of micro-sparks
        if (random.nextFloat() < 0.4f) {
            microSparks.add(new MicroSpark(
                    wx - vx * 0.02f, wy - vy * 0.02f, wz - vz * 0.02f,
                    (random.nextFloat() - 0.5f) * 0.1f,
                    (random.nextFloat() - 0.5f) * 0.1f,
                    (random.nextFloat() - 0.5f) * 0.1f,
                    0.05f + random.nextFloat() * 0.1f));
        }

        updateBasicPhysics(deltaTime);
    }

    public void updateInstabilityPhase(float deltaTime) {
        // Increasing wobble and cracking
        crackProgress = Math.min(1.0f, crackProgress + deltaTime * 2f);

        // Erratic flight path
        float wobbleStrength = 0.5f + crackProgress * 0.5f;
        vx += (float)Math.sin(wobblePhase * 3) * wobbleStrength * deltaTime;
        vy += (float)Math.cos(wobblePhase * 2.7f) * wobbleStrength * 0.5f * deltaTime;
        vz += (float)Math.sin(wobblePhase * 2.3f) * wobbleStrength * deltaTime;

        // Calve off small pieces
        if (random.nextFloat() < 0.15f * crackProgress) {
            float angle = random.nextFloat() * (float)Math.PI * 2;
            fragments.add(new SparkFragment(wx, wy, wz,
                    vx + (float)Math.cos(angle) * 0.4f,
                    vy + (random.nextFloat() - 0.3f) * 0.3f,
                    vz + (float)Math.sin(angle) * 0.4f,
                    0.15f + random.nextFloat() * 0.15f,
                    brightness * 0.4f, seed + fragments.size() * 77));
        }

        updateBasicPhysics(deltaTime);
    }

    public void updatePrimaryFragmentPhase(float deltaTime) {
        // Main body slowing down, fragments taking over
        vx *= 0.95f;
        vy *= 0.95f;
        vz *= 0.95f;
        updateBasicPhysics(deltaTime);
    }

    public void updateSecondaryFragmentPhase(float deltaTime) {
        // Main body almost stopped
        vx *= 0.9f;
        vy *= 0.9f;
        vz *= 0.9f;
        updateBasicPhysics(deltaTime);
    }

    public void updateBloomPhase(float deltaTime) {
        // Main body frozen, bloom rays expanding
        updateBasicPhysics(deltaTime * 0.2f);
    }

    public void updateEmberPhase(float deltaTime) {
        // Slow ember fall
        vy -= 1.0f * deltaTime;
        wx += vx * deltaTime * 0.3f;
        wy += vy * deltaTime * 0.3f;
        wz += vz * deltaTime * 0.3f;
    }

    public void updateBasicPhysics(float deltaTime) {
        wx += vx * deltaTime;
        wy += vy * deltaTime;
        wz += vz * deltaTime;

        // REALISTIC SPARKLER PHYSICS:
        // Real sparkler sparks are tiny burning metal pieces - they experience
        // significant air drag and gravity, but initial momentum carries them far
        // The key is LOW drag so they maintain their radiating pattern

        // Gravity - sparks do fall but not as dramatically as heavy objects
        float gravityMult;
        if (sparkType == SPARK_CORE) {
            gravityMult = 0.4f;  // Core: very light, mostly horizontal
        } else if (sparkType == SPARK_INNER) {
            gravityMult = 0.5f;  // Inner: light
        } else if (sparkType == SPARK_HAIR) {
            gravityMult = 0.3f;  // Hair: very light, almost no gravity
        } else {
            gravityMult = 0.6f;  // Outer: slightly heavier for arc effect
        }
        vy -= 2.5f * gravityMult * deltaTime;

        // VERY LOW DRAG - sparks maintain their velocity to fly far
        // This is key to the radiating wire pattern
        float drag;
        if (sparkType == SPARK_CORE) {
            drag = 0.08f;  // Core: very low drag
        } else if (sparkType == SPARK_INNER) {
            drag = 0.06f;  // Inner: low drag
        } else if (sparkType == SPARK_HAIR) {
            drag = 0.05f;  // Hair: minimal drag
        } else {
            drag = 0.04f;  // Outer: minimal drag for longest travel
        }
        float dragMult = 1.0f - drag * deltaTime;
        vx *= dragMult;
        vy *= dragMult;
        vz *= dragMult;
    }

    // Color depends on spark type - REALISTIC SPARKLER: Brilliant white with only tips going yellow/orange
    public int[] getColor() {
        float progress = life;
        int r, g, b, a;

        // REALISTIC SPARKLER COLORS:
        // Real sparklers burn iron/steel which produces BRILLIANT WHITE sparks
        // Only the very tips (cooling embers) show yellow/orange
        // The key is: 90%+ of the spark should be WHITE

        if (sparkType == SPARK_CORE) {
            // Core sparks: Pure brilliant white the entire time, slight warmth at very end
            if (progress < 0.85f) {
                r = 255; g = 255; b = 255;  // Pure white
            } else {
                float p = (progress - 0.85f) / 0.15f;
                r = 255;
                g = (int)(255 - 15 * p);  // Barely warm
                b = (int)(255 - 25 * p);
            }
            a = progress < 0.7f ? 255 : (int)(255 * (1.0f - (progress - 0.7f) / 0.3f));

        } else if (sparkType == SPARK_INNER) {
            // Inner sparks: Brilliant white, warm tips
            if (progress < 0.75f) {
                r = 255; g = 255; b = 255;  // Pure white
            } else {
                float p = (progress - 0.75f) / 0.25f;
                r = 255;
                g = (int)(255 - 40 * p);   // Slight yellow
                b = (int)(255 - 80 * p);   // Warmer tips
            }
            a = progress < 0.6f ? 255 : (int)(255 * (1.0f - (progress - 0.6f) / 0.4f));

        } else if (sparkType == SPARK_HAIR) {
            // Hair sparks: White with rapid fade
            if (progress < 0.6f) {
                r = 255; g = 255; b = 250;
            } else {
                float p = (progress - 0.6f) / 0.4f;
                r = 255;
                g = (int)(255 - 50 * p);
                b = (int)(250 - 120 * p);
            }
            a = progress < 0.4f ? 255 : (int)(255 * (1.0f - (progress - 0.4f) / 0.6f));

        } else {
            // Outer sparks: White body, yellow-orange tips (last 15% only)
            if (progress < 0.65f) {
                r = 255; g = 255; b = 255;  // Pure white
            } else if (progress < 0.85f) {
                float p = (progress - 0.65f) / 0.2f;
                r = 255;
                g = (int)(255 - 30 * p);   // Slight warm
                b = (int)(255 - 100 * p);  // Yellow tint
            } else {
                // Final cooling ember tips
                float p = (progress - 0.85f) / 0.15f;
                r = 255;
                g = (int)(225 - 75 * p);   // Orange
                b = (int)(155 - 105 * p);  // Deep orange tips
            }
            a = progress < 0.5f ? 255 : (int)(255 * (1.0f - (progress - 0.5f) / 0.5f));
        }

        // Subtle flickering for realism - sparks twinkle
        float flicker = 0.92f + 0.08f * (float)Math.sin(pulsePhase * 15 + seed * 0.001f);
        r = (int)(r * flicker);
        g = (int)(g * flicker);
        b = (int)(b * flicker);

        return new int[]{Math.min(255, Math.max(0, r)), Math.min(255, Math.max(0, g)), Math.min(255, Math.max(0, b)), Math.max(0, a)};
    }

    public float getSpeed() {
        return (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
    }
}
