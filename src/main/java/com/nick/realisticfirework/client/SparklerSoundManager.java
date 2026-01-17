package com.nick.realisticfirework.client;

import com.nick.realisticfirework.RealisticFireworkMod;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Manages sparkler sound instances on the client side.
 * Ensures only one sound per entity and cleans up stopped sounds.
 */
public class SparklerSoundManager {

    private static final Map<Integer, SparklerSoundInstance> activeSounds = new HashMap<>();

    /**
     * Start playing a sparkler sound for an entity if not already playing.
     */
    public static void startSound(Entity entity) {
        if (entity == null || entity.level().isClientSide() == false) return;

        int entityId = entity.getId();

        // Already playing?
        if (activeSounds.containsKey(entityId)) {
            SparklerSoundInstance existing = activeSounds.get(entityId);
            if (!existing.isStopped()) {
                return; // Already playing, don't start another
            }
            // Old one stopped, remove it
            RealisticFireworkMod.LOGGER.info("MANAGER: removing stopped sound for entity id={}", entityId);
            activeSounds.remove(entityId);
        }

        // Create and play new sound
        RealisticFireworkMod.LOGGER.info("MANAGER: starting new sound for entity={}, id={}", entity.getClass().getSimpleName(), entityId);
        SparklerSoundInstance sound = new SparklerSoundInstance(entity);
        activeSounds.put(entityId, sound);
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    /**
     * Stop the sparkler sound for an entity.
     */
    public static void stopSound(Entity entity) {
        if (entity == null) return;

        int entityId = entity.getId();
        SparklerSoundInstance sound = activeSounds.remove(entityId);
        if (sound != null) {
            RealisticFireworkMod.LOGGER.info("MANAGER: explicitly stopping sound for entity id={}", entityId);
            Minecraft.getInstance().getSoundManager().stop(sound);
        }
    }

    /**
     * Clean up any stopped sounds from the map.
     * Call this periodically (e.g., on client tick).
     */
    public static void cleanup() {
        Iterator<Map.Entry<Integer, SparklerSoundInstance>> it = activeSounds.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, SparklerSoundInstance> entry = it.next();
            if (entry.getValue().isStopped()) {
                RealisticFireworkMod.LOGGER.info("MANAGER: cleanup removing stopped sound for id={}", entry.getKey());
                it.remove();
            }
        }
    }

    /**
     * Check if a sound is playing for an entity.
     */
    public static boolean isPlaying(Entity entity) {
        if (entity == null) return false;
        SparklerSoundInstance sound = activeSounds.get(entity.getId());
        return sound != null && !sound.isStopped();
    }
}
