package com.nick.realisticfirework.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * Data component for sparkler items.
 * Stores whether the sparkler is lit, when it was lit, and a unique ID for animation tracking.
 */
public record SparklerData(
        boolean isLit,      // Whether the sparkler is currently lit
        UUID uniqueId,      // Unique identifier for this specific sparkler instance
        long litAtTick      // Game tick when the sparkler was lit (for calculating burn progress)
) {
    // Maximum burn progress (coating covers top 75%, handle is bottom 25%)
    public static final float MAX_BURN_PROGRESS = 0.75f;
    // Fade starts at this progress (last 10% of coating)
    public static final float FADE_START = 0.65f;
    // Burn duration in ticks (30 seconds * 20 ticks/second = 600 ticks)
    public static final int BURN_DURATION_TICKS = 600;

    // Codec for saving/loading from NBT/JSON
    public static final Codec<SparklerData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.BOOL.fieldOf("lit").forGetter(SparklerData::isLit),
                    UUIDUtil.CODEC.fieldOf("id").forGetter(SparklerData::uniqueId),
                    Codec.LONG.fieldOf("litAt").forGetter(SparklerData::litAtTick)
            ).apply(instance, SparklerData::new)
    );

    // Stream codec for network sync
    public static final StreamCodec<ByteBuf, SparklerData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SparklerData::isLit,
            UUIDUtil.STREAM_CODEC, SparklerData::uniqueId,
            ByteBufCodecs.VAR_LONG, SparklerData::litAtTick,
            SparklerData::new
    );

    /**
     * Create a new unlit sparkler data with a random ID.
     */
    public static SparklerData unlit() {
        return new SparklerData(false, UUID.randomUUID(), 0L);
    }

    /**
     * Create a newly lit sparkler data with a NEW random ID.
     * The new ID ensures this sparkler has its own animation state.
     * @param currentTick The current game tick when lighting
     */
    public static SparklerData newlyLit(long currentTick) {
        return new SparklerData(true, UUID.randomUUID(), currentTick);
    }

    /**
     * Calculate burn progress based on current game tick.
     * @param currentTick The current game tick
     * @return Burn progress from 0.0 to MAX_BURN_PROGRESS
     */
    public float getBurnProgress(long currentTick) {
        if (!isLit || litAtTick <= 0) {
            return 0.0f;
        }
        long ticksElapsed = currentTick - litAtTick;
        float progress = (float) ticksElapsed / BURN_DURATION_TICKS;
        return Math.min(progress, MAX_BURN_PROGRESS);
    }

    /**
     * Check if the sparkler is fully burned (coating consumed).
     * @param currentTick The current game tick
     */
    public boolean isFullyBurned(long currentTick) {
        return getBurnProgress(currentTick) >= MAX_BURN_PROGRESS;
    }

    /**
     * Get the fade factor (1.0 = full intensity, 0.0 = fully faded).
     * @param currentTick The current game tick
     */
    public float getFadeFactor(long currentTick) {
        float burnProgress = getBurnProgress(currentTick);
        if (burnProgress <= FADE_START) {
            return 1.0f;
        }
        float fade = 1.0f - (burnProgress - FADE_START) / (MAX_BURN_PROGRESS - FADE_START);
        return Math.max(0.0f, Math.min(1.0f, fade));
    }

    /**
     * Get the light fade factor - delayed by 3 seconds after visual fade.
     * This keeps world light blocks on longer so light doesn't disappear before visuals.
     * @param currentTick The current game tick
     */
    public float getLightFadeFactor(long currentTick) {
        // Delay by 60 ticks (3 seconds)
        long delayedTick = currentTick - 24;
        float burnProgress = getBurnProgress(delayedTick);
        if (burnProgress <= FADE_START) {
            return 1.0f;
        }
        float fade = 1.0f - (burnProgress - FADE_START) / (MAX_BURN_PROGRESS - FADE_START);
        return Math.max(0.0f, Math.min(1.0f, fade));
    }

    /**
     * Check if light should be fully removed (delayed 3 seconds after visual burn completes).
     * @param currentTick The current game tick
     */
    public boolean isLightFullyBurned(long currentTick) {
        // Light stays on 60 ticks (3 seconds) longer than visuals
        long delayedTick = currentTick - 60;
        return getBurnProgress(delayedTick) >= MAX_BURN_PROGRESS;
    }

    /**
     * Get the state name for model selection.
     */
    public String getStateName() {
        return isLit ? "lit" : "unlit";
    }
}
