// ============================================
// COLORED LIGHTS LIBRARY
// Defines light colors for blocks and items
// ============================================

// Structure for colored light sources
struct ColoredLight {
    int blockId;
    float intensity;
    vec3 color;
};

// ============================================
// VANILLA LIGHT COLORS
// ============================================
// Torches - warm orange flame
const vec3 TORCH_COLOR = vec3(1.0, 0.6, 0.3);

// Soul torches - cyan/blue
const vec3 SOUL_TORCH_COLOR = vec3(0.3, 0.8, 0.9);

// Redstone - red glow
const vec3 REDSTONE_COLOR = vec3(1.0, 0.1, 0.1);

// Lanterns
const vec3 LANTERN_COLOR = vec3(1.0, 0.7, 0.4);
const vec3 SOUL_LANTERN_COLOR = vec3(0.3, 0.7, 0.9);

// Fire
const vec3 FIRE_COLOR = vec3(1.0, 0.5, 0.2);
const vec3 SOUL_FIRE_COLOR = vec3(0.2, 0.7, 0.9);

// Lava - deep orange/red
const vec3 LAVA_COLOR = vec3(1.0, 0.4, 0.1);

// Glowstone - warm yellow
const vec3 GLOWSTONE_COLOR = vec3(1.0, 0.8, 0.5);

// Sea lantern - aqua blue
const vec3 SEA_LANTERN_COLOR = vec3(0.5, 0.8, 1.0);

// Shroomlight - orange
const vec3 SHROOMLIGHT_COLOR = vec3(1.0, 0.6, 0.3);

// ============================================
// FIREWORK MOD LIGHT COLORS
// Based on real pyrotechnic chemistry!
// ============================================

// Sparkler colors (match the customer's specifications)
// Standard sparkler - titanium/magnesium white-gold
const vec3 SPARKLER_COLOR = vec3(1.0, 0.9, 0.7);

// Neon sparklers (as specified in requirements)
// Red - Strontium salts
const vec3 SPARKLER_RED = vec3(1.0, 0.2, 0.15);

// Green - Barium salts
const vec3 SPARKLER_GREEN = vec3(0.3, 1.0, 0.3);

// Blue - Copper salts
const vec3 SPARKLER_BLUE = vec3(0.3, 0.5, 1.0);

// Morning glory - typically multi-color, use warm white
const vec3 MORNING_GLORY_COLOR = vec3(1.0, 0.85, 0.6);

// ============================================
// SHELL COLORS (from pyrotechnic chemistry)
// ============================================

// Red (Strontium Salts)
const vec3 SHELL_RED = vec3(1.0, 0.15, 0.1);

// Orange (Calcium Salts)
const vec3 SHELL_ORANGE = vec3(1.0, 0.5, 0.1);

// Yellow (Sodium Salts)
const vec3 SHELL_YELLOW = vec3(1.0, 0.9, 0.3);

// Green (Barium Salts)
const vec3 SHELL_GREEN = vec3(0.2, 1.0, 0.2);

// Blue (Copper Salts)
const vec3 SHELL_BLUE = vec3(0.2, 0.4, 1.0);

// Purple (Copper + Strontium)
const vec3 SHELL_PURPLE = vec3(0.7, 0.2, 1.0);

// Silver (Magnesium + Aluminum)
const vec3 SHELL_SILVER = vec3(0.9, 0.9, 1.0);

// White (Magnesium + Aluminum + Titanium)
const vec3 SHELL_WHITE = vec3(1.0, 1.0, 1.0);

// ============================================
// HELPER FUNCTIONS
// ============================================

// Get light color for a block ID
vec3 getBlockLightColor(int blockId) {
    // Vanilla blocks
    if (blockId == 10001) return TORCH_COLOR;
    if (blockId == 10002) return SOUL_TORCH_COLOR;
    if (blockId == 10003) return REDSTONE_COLOR;
    if (blockId == 10010) return LANTERN_COLOR;
    if (blockId == 10011) return SOUL_LANTERN_COLOR;
    if (blockId == 10020) return FIRE_COLOR;
    if (blockId == 10021) return SOUL_FIRE_COLOR;
    if (blockId == 10030) return LAVA_COLOR;
    if (blockId == 10040) return GLOWSTONE_COLOR;
    if (blockId == 10041) return SEA_LANTERN_COLOR;
    if (blockId == 10042) return SHROOMLIGHT_COLOR;

    // Firework mod items
    if (blockId == 20001) return SPARKLER_COLOR;
    // if (blockId == 20002) return SPARKLER_RED;
    // if (blockId == 20003) return SPARKLER_GREEN;
    // if (blockId == 20004) return SPARKLER_BLUE;
    // if (blockId == 20005) return MORNING_GLORY_COLOR;

    // Default warm white for unknown light sources
    return vec3(1.0, 0.8, 0.6);
}

// Calculate colored light contribution at a position
vec3 calculateColoredBlockLight(float blockLightLevel, int blockId) {
    vec3 lightColor = getBlockLightColor(blockId);
    float intensity = pow(blockLightLevel, 2.0); // Quadratic falloff
    return lightColor * intensity;
}
