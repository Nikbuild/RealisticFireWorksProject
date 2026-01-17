package com.nick.realisticfirework;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import com.nick.realisticfirework.items.ItemSparkler;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.nick.realisticfirework.client.DynamicLightManager;
import com.nick.realisticfirework.client.SparklerSoundManager;
import com.nick.realisticfirework.registry.ModDataComponents;
import com.nick.realisticfirework.registry.ModParticles;
import com.nick.realisticfirework.registry.ModSounds;
import com.nick.realisticfirework.data.SparklerData;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(RealisticFireworkMod.MODID)
public class RealisticFireworkMod {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "realisticfireworkmod";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "realisticfireworkmod" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Creates a creative tab with the id "realisticfireworkmod:fireworks_tab" for the firework items
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FIREWORKS_TAB = CREATIVE_MODE_TABS.register("fireworks_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.realisticfireworkmod")) //The language key for the title of your CreativeModeTab
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> ModItems.SPARKLER.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.SPARKLER.get());
            }).build());

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public RealisticFireworkMod(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus so items get registered
        ModItems.ITEMS.register(modEventBus);
        // Register data components
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        // Register particles
        ModParticles.PARTICLE_TYPES.register(modEventBus);
        // Register sounds
        ModSounds.SOUND_EVENTS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);
        // All sparkler effects are now rendered in SparklerSpecialRenderer (model-space)

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (RealisticFireworkMod) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("RealisticFireworkMod common setup complete!");
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("RealisticFireworkMod server starting!");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        // Clean up all dynamic lights when server stops
        LOGGER.info("RealisticFireworkMod cleaning up dynamic lights...");
        DynamicLightManager.cleanupAllLights(null);
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        // Clean up dynamic lights for the player who logged out
        if (event.getEntity() != null) {
            DynamicLightManager.removeLightsForEntity(event.getEntity().level(), event.getEntity());
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        // Clean up dynamic lights when player changes dimension
        if (event.getEntity() != null) {
            DynamicLightManager.removeLightsForEntity(event.getEntity().level(), event.getEntity());
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        // Clean up dynamic lights when player respawns
        if (event.getEntity() != null) {
            DynamicLightManager.removeLightsForEntity(event.getEntity().level(), event.getEntity());
        }
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        // Handle dynamic lighting for lit sparklers held by players
        var player = event.getEntity();
        var level = player.level();

        if (!level.isClientSide()) {
            // Check main hand
            var mainHand = player.getMainHandItem();
            // Check offhand
            var offHand = player.getOffhandItem();

            boolean hasLitSparklerInHand =
                (mainHand.getItem() instanceof ItemSparkler && ItemSparkler.isLit(mainHand)) ||
                (offHand.getItem() instanceof ItemSparkler && ItemSparkler.isLit(offHand));

            if (hasLitSparklerInHand) {
                // Update dynamic light at player's position
                DynamicLightManager.updateLightForEntity(level, player);
            } else if (DynamicLightManager.entityHasActiveLights(player)) {
                // No lit sparkler in hand - remove lights
                DynamicLightManager.removeLightsForEntity(level, player);
            }
        }
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        // Handle dynamic lighting for dropped sparklers (server-side)
        // and sound management (client-side)
        var level = event.getLevel();
        long currentTick = level.getGameTime();

        if (!level.isClientSide() && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            // Server-side: handle dynamic lighting
            for (var entity : serverLevel.getAllEntities()) {
                if (entity instanceof ItemEntity itemEntity) {
                    var stack = itemEntity.getItem();
                    if (stack.getItem() instanceof ItemSparkler && ItemSparkler.isLit(stack)) {
                        // Update light at item entity position
                        DynamicLightManager.updateLightForItemEntity(level, itemEntity);
                    }
                }
            }
        } else if (level.isClientSide()) {
            // Client-side: handle sound for dropped sparklers
            // Get all entities near the player
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null) {
                // Search for nearby dropped sparklers
                var nearbyEntities = level.getEntitiesOfClass(
                    ItemEntity.class,
                    player.getBoundingBox().inflate(20.0), // Search within 20 blocks
                    itemEntity -> {
                        var stack = itemEntity.getItem();
                        return stack.getItem() instanceof ItemSparkler && ItemSparkler.isLit(stack);
                    }
                );

                for (ItemEntity itemEntity : nearbyEntities) {
                    var stack = itemEntity.getItem();
                    SparklerData data = stack.get(ModDataComponents.SPARKLER.get());
                    if (data != null) {
                        float burnProgress = data.getBurnProgress(currentTick);
                        if (burnProgress < SparklerData.MAX_BURN_PROGRESS) {
                            // Start looping sound for this dropped sparkler
                            SparklerSoundManager.startSound(itemEntity);
                        } else {
                            SparklerSoundManager.stopSound(itemEntity);
                        }
                    }
                }

                // Cleanup stopped sounds
                SparklerSoundManager.cleanup();
            }
        }
    }
}
