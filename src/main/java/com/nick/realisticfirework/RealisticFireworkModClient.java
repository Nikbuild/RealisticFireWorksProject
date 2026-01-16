package com.nick.realisticfirework;

import com.nick.realisticfirework.client.SparklerBEWLR;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import com.nick.realisticfirework.client.SparkParticle;
import com.nick.realisticfirework.registry.ModParticles;

/**
 * Client-only event subscriber for 1.21.1.
 * Uses BEWLR (BlockEntityWithoutLevelRenderer) for custom item rendering.
 * Note: In 1.21.1, we can't use @Mod with dist=Dist.CLIENT, so we use only @EventBusSubscriber.
 */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = RealisticFireworkMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class RealisticFireworkModClient {

    // No constructor needed - this class just holds static event handlers

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        RealisticFireworkMod.LOGGER.info("RealisticFireworkMod client setup complete!");
        RealisticFireworkMod.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());

        // Register item properties on main thread
        event.enqueueWork(() -> {
            registerItemProperties();
        });
    }

    /**
     * Register item model properties for predicate-based model selection.
     * In 1.21.1, we use ItemProperties instead of SelectItemModelProperty.
     */
    private static void registerItemProperties() {
        // Register sparkler state property for model selection (unlit/lit/burnt)
        ItemProperties.register(
            ModItems.SPARKLER.get(),
            ResourceLocation.fromNamespaceAndPath(RealisticFireworkMod.MODID, "sparkler_state"),
            (stack, level, entity, seed) -> {
                var data = stack.get(com.nick.realisticfirework.registry.ModDataComponents.SPARKLER.get());
                if (data == null) return 0.0f;

                if (!data.isLit()) return 0.0f; // unlit

                // Check if fully burned
                long currentTick = (level != null) ? level.getGameTime() : 0L;
                if (data.isFullyBurned(currentTick)) return 1.0f; // burnt

                return 0.5f; // lit (burning)
            }
        );
        RealisticFireworkMod.LOGGER.info("Registered sparkler_state item property");
    }

    /**
     * Register client extensions including the custom BEWLR for sparkler rendering.
     */
    @SubscribeEvent
    static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        // Register BEWLR for sparkler item
        event.registerItem(new IClientItemExtensions() {
            private SparklerBEWLR renderer;

            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new SparklerBEWLR();
                }
                return renderer;
            }
        }, ModItems.SPARKLER.get());

        RealisticFireworkMod.LOGGER.info("Registered SparklerBEWLR for sparkler rendering");
    }

    /**
     * Register particle providers for custom particle types.
     */
    @SubscribeEvent
    static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SPARK.get(), SparkParticle.Provider::new);
        RealisticFireworkMod.LOGGER.info("Registered spark particle provider");
    }
}
