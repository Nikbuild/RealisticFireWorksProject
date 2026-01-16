package com.nick.realisticfirework.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * Custom RenderTypes for sparkler effects.
 * Includes a GL_LINES based render type for thin spark lines.
 */
public class SparkRenderTypes extends RenderType {

    // Private constructor - we only use static methods
    private SparkRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }

    /**
     * GL_LINES render type for thin spark streaks.
     * Uses additive blending for glow effect.
     */
    public static final RenderType SPARK_LINES = create(
        "spark_lines",
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.LINES,
        1536,
        false,
        true,
        CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
            .setLineState(new RenderStateShard.LineStateShard(java.util.OptionalDouble.of(2.0))) // 2 pixel wide lines
            .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
            .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY) // Additive blending
            .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
            .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
            .setCullState(RenderStateShard.NO_CULL)
            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
            .createCompositeState(false)
    );

    /**
     * Thinner 1-pixel lines for finer sparks
     */
    public static final RenderType SPARK_LINES_THIN = create(
        "spark_lines_thin",
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.LINES,
        1536,
        false,
        true,
        CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
            .setLineState(new RenderStateShard.LineStateShard(java.util.OptionalDouble.of(1.0))) // 1 pixel wide
            .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
            .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
            .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
            .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
            .setCullState(RenderStateShard.NO_CULL)
            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
            .createCompositeState(false)
    );
}
