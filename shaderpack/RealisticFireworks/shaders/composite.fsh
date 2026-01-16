#version 330 compatibility

// ============================================
// COMPOSITE - Minimal Processing
// Java renders thin camera-facing quads
// Shader just converts magenta marker to bright color
// NO extra glow effects that create star shapes
// ============================================

in vec2 texcoord;

uniform sampler2D colortex0;
uniform float viewWidth;
uniform float viewHeight;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 color;

bool isSparkMarker(vec3 col) {
    return col.r > 0.7 && col.g < 0.4 && col.b > 0.7;
}

void main() {
    color = texture(colortex0, texcoord);

    // Simply convert magenta markers to bright white-gold
    // NO radial sampling, NO star-shaped glow
    if (isSparkMarker(color.rgb)) {
        float intensity = color.a;
        // Bright white with slight warmth
        color.rgb = vec3(1.0, 0.95, 0.85) * intensity * 3.0;
    }

    color.rgb = min(color.rgb, vec3(3.0));
}
