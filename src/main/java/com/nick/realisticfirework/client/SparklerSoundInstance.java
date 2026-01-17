package com.nick.realisticfirework.client;

import com.nick.realisticfirework.RealisticFireworkMod;
import com.nick.realisticfirework.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Client-side tickable sound for sparklers.
 * This runs entirely on the client and smoothly adjusts volume every frame
 * based on distance - no server packets needed for volume changes.
 */
public class SparklerSoundInstance extends AbstractTickableSoundInstance {

    private final Entity trackedEntity;
    private boolean done = false;

    // Volume curve parameters
    private static final float MAX_DISTANCE = 20.0f;
    private static final float HALF_VOLUME_DISTANCE = 6.0f;  // 50% volume at 6 blocks - much more gradual
    private static final float BASE_VOLUME = 1.0f;

    public SparklerSoundInstance(Entity entity) {
        super(ModSounds.SPARKLER_BURNING.get(), SoundSource.PLAYERS, entity.level().getRandom());
        this.trackedEntity = entity;

        RealisticFireworkMod.LOGGER.info("SOUND CREATE: entity={}, id={}", entity.getClass().getSimpleName(), entity.getId());

        // Start at entity position
        this.x = entity.getX();
        this.y = entity.getY();
        this.z = entity.getZ();

        // Loop forever until we stop it
        this.looping = true;

        // Start with volume based on current distance
        this.volume = calculateVolume();

        // No delay
        this.delay = 0;

        // Let our code handle attenuation, not OpenAL
        this.attenuation = Attenuation.NONE;
    }

    private int tickCount = 0;

    @Override
    public void tick() {
        tickCount++;

        // Check if entity is still valid
        if (trackedEntity == null || trackedEntity.isRemoved()) {
            RealisticFireworkMod.LOGGER.info("SOUND STOP: entity null or removed, id={}", trackedEntity != null ? trackedEntity.getId() : "null");
            this.done = true;
            return;
        }

        // Check if sparkler is still lit (for ItemEntity)
        if (trackedEntity instanceof ItemEntity itemEntity) {
            var stack = itemEntity.getItem();
            if (stack.isEmpty()) {
                RealisticFireworkMod.LOGGER.info("SOUND STOP: ItemEntity stack empty, id={}", trackedEntity.getId());
                this.done = true;
                return;
            }
            if (!com.nick.realisticfirework.items.ItemSparkler.isLit(stack)) {
                RealisticFireworkMod.LOGGER.info("SOUND STOP: sparkler not lit, id={}", trackedEntity.getId());
                this.done = true;
                return;
            }
        }

        // Check if Player is still holding a lit sparkler
        if (trackedEntity instanceof Player player) {
            boolean hasLitSparkler = false;
            var mainHand = player.getMainHandItem();
            var offHand = player.getOffhandItem();
            if (mainHand.getItem() instanceof com.nick.realisticfirework.items.ItemSparkler &&
                com.nick.realisticfirework.items.ItemSparkler.isLit(mainHand)) {
                hasLitSparkler = true;
            }
            if (offHand.getItem() instanceof com.nick.realisticfirework.items.ItemSparkler &&
                com.nick.realisticfirework.items.ItemSparkler.isLit(offHand)) {
                hasLitSparkler = true;
            }
            if (!hasLitSparkler) {
                RealisticFireworkMod.LOGGER.info("SOUND STOP: player no longer holding lit sparkler, id={}", trackedEntity.getId());
                this.done = true;
                return;
            }
        }

        // Update position to follow entity
        this.x = trackedEntity.getX();
        this.y = trackedEntity.getY();
        this.z = trackedEntity.getZ();

        // Smoothly update volume based on distance - THIS HAPPENS EVERY FRAME
        this.volume = calculateVolume();

        // Log every second (20 ticks)
        if (tickCount % 20 == 0) {
            RealisticFireworkMod.LOGGER.info("SOUND TICK: entity={}, id={}, done={}, volume={}",
                trackedEntity.getClass().getSimpleName(), trackedEntity.getId(), done, String.format("%.3f", volume));
        }
    }

    private float calculateVolume() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;

        double dx = this.x - mc.player.getX();
        double dy = this.y - mc.player.getY();
        double dz = this.z - mc.player.getZ();
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (distance >= MAX_DISTANCE) {
            return 0.0f;
        }
        if (distance <= 0) {
            return BASE_VOLUME;
        }

        // Exponential decay for natural falloff
        float k = (float) (Math.log(2) / HALF_VOLUME_DISTANCE);
        float vol = (float) Math.exp(-distance * k) * BASE_VOLUME;

        return Math.max(vol, 0.001f);
    }

    @Override
    public boolean isStopped() {
        return done;
    }

    public Entity getTrackedEntity() {
        return trackedEntity;
    }
}
