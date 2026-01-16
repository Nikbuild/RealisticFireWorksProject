#version 330 compatibility

// ============================================
// FINAL - Simple passthrough, no global tonemapping
// ============================================

in vec2 texcoord;

uniform sampler2D colortex0;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 color;

void main() {
    color = texture(colortex0, texcoord);
    // No tonemapping - keep vanilla look
}
