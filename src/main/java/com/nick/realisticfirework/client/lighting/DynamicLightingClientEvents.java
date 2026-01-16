package com.nick.realisticfirework.client.lighting;

import com.nick.realisticfirework.RealisticFireworkMod;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Client-side event handler for dynamic lighting.
 * Manages the lifecycle of the dynamic lighting engine.
 */
@EventBusSubscriber(modid = RealisticFireworkMod.MODID, value = Dist.CLIENT)
public class DynamicLightingClientEvents {

    /**
     * Tick the lighting engine to clean up expired sources.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && !mc.isPaused()) {
            DynamicLightingEngine.getInstance().tick();
        }
    }

    /**
     * Clear all light sources when leaving a world.
     */
    @SubscribeEvent
    public static void onWorldUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            DynamicLightingEngine.getInstance().clear();
        }
    }
}
