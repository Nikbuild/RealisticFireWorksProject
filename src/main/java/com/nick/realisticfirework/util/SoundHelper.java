package com.nick.realisticfirework.util;

import com.nick.realisticfirework.RealisticFireworkMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * Helper for playing sounds with smooth distance-based volume falloff.
 * Uses exponential decay for natural-sounding attenuation.
 */
public class SoundHelper {

    // Maximum distance at which the sound is still audible
    private static final float MAX_DISTANCE = 16.0f;

    // Distance at which volume is at 50% (controls curve steepness)
    // At 1.5, volume drops to 50% at 1.5 blocks - starts fading early but stays audible longer
    private static final float HALF_VOLUME_DISTANCE = 1.5f;

    /**
     * Calculate volume based on distance using smooth exponential decay.
     * Returns 1.0 at distance 0, smoothly decreasing to ~0 at MAX_DISTANCE.
     *
     * Uses the formula: volume = e^(-distance * k) where k controls decay rate
     * This gives a natural-sounding falloff similar to real-world sound.
     */
    public static float calculateVolume(float distance, float baseVolume) {
        if (distance <= 0) {
            return baseVolume;
        }
        if (distance >= MAX_DISTANCE) {
            return 0.0f;
        }

        // Calculate decay constant from half-volume distance
        // At HALF_VOLUME_DISTANCE, we want volume = 0.5
        // 0.5 = e^(-HALF_VOLUME_DISTANCE * k)
        // ln(0.5) = -HALF_VOLUME_DISTANCE * k
        // k = -ln(0.5) / HALF_VOLUME_DISTANCE = ln(2) / HALF_VOLUME_DISTANCE
        float k = (float)(Math.log(2) / HALF_VOLUME_DISTANCE);

        // Exponential decay
        float volume = (float)Math.exp(-distance * k) * baseVolume;

        // Ensure minimum threshold (below this is inaudible anyway)
        // Using very low threshold so sound plays even at quiet levels
        if (volume < 0.001f) {
            return 0.0f;
        }

        return volume;
    }

    /**
     * Play a sound to all nearby players with smooth distance-based volume.
     * Each player hears the sound at a volume based on their distance from the source.
     */
    public static void playSoundWithSmoothFalloff(Level level, double x, double y, double z,
                                                   SoundEvent sound, SoundSource category,
                                                   float baseVolume, float pitch) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        // Play to each player with distance-based volume
        for (ServerPlayer player : serverLevel.players()) {
            double dx = player.getX() - x;
            double dy = player.getY() - y;
            double dz = player.getZ() - z;
            float distance = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);

            float volume = calculateVolume(distance, baseVolume);

            if (volume > 0) {
                // Play sound at the source position - Minecraft handles stereo panning
                // But we override the volume based on our smooth curve
                serverLevel.playSound(null, x, y, z, sound, category, volume, pitch);
                // Note: This plays to ALL players, but we only need it once
                // Break after first iteration since playSound broadcasts to all
                break;
            }
        }
    }

    /**
     * Play a sound to each player individually with their own volume level.
     * This gives true per-player volume control for hyper-smooth falloff.
     *
     * Sound is played at the ACTUAL source position for proper stereo panning.
     * Since we use a 256-block fixed range sound, OpenAL's linear attenuation
     * is negligible (at 16 blocks = ~94% of original).
     * Our volume parameter does all the real attenuation work.
     */
    public static void playSoundToEachPlayer(Level level, double x, double y, double z,
                                              SoundEvent sound, SoundSource category,
                                              float baseVolume, float pitch) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        // Play to each player individually with their distance-based volume
        for (ServerPlayer player : serverLevel.players()) {
            double dx = x - player.getX();
            double dy = y - player.getY();
            double dz = z - player.getZ();
            float distance = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);

            float volume = calculateVolume(distance, baseVolume);

            if (volume > 0) {
                // Debug logging (only every ~1 second to avoid spam)
                if (serverLevel.getGameTime() % 20 == 0) {
                    RealisticFireworkMod.LOGGER.info("SOUND DEBUG: player={}, distance={}, volume={}",
                        player.getName().getString(), String.format("%.2f", distance), String.format("%.4f", volume));
                }

                // Send sound packet with our calculated volume at actual source position
                // Stereo panning works naturally, and our 256-block range means
                // OpenAL applies almost no distance attenuation
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                    net.minecraft.core.Holder.direct(sound),
                    category,
                    x, y, z,  // Actual source position for stereo panning
                    volume,
                    pitch,
                    serverLevel.random.nextLong()
                ));
            }
        }
    }
}
