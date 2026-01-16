package com.nick.realisticfirework.mixin.client;

import com.nick.realisticfirework.client.lighting.DynamicLightingEngine;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nick.realisticfirework.RealisticFireworkMod;

/**
 * Mixin to inject dynamic lighting into Minecraft's light level calculations.
 * This intercepts the getLightColor method to add light from dynamic sources.
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    // Debug: track last log time
    private static long lastMixinLogTime = 0;
    private static boolean firstCall = true;

    /**
     * Inject at the end of getLightColor to add dynamic light contribution.
     * The lightmap is packed as: (skyLight << 20) | (blockLight << 4)
     */
    @Inject(method = "getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I", at = @At("RETURN"), cancellable = true)
    private static void onGetLightColor(BlockAndTintGetter level, BlockPos pos,
                                        CallbackInfoReturnable<Integer> cir) {
        // Log first call to prove mixin is working
        if (firstCall) {
            RealisticFireworkMod.LOGGER.info("MIXIN: LevelRendererMixin.onGetLightColor FIRST CALL - mixin is active!");
            firstCall = false;
        }

        DynamicLightingEngine engine = DynamicLightingEngine.getInstance();

        // Debug: Log mixin is being called
        long now = System.currentTimeMillis();
        if (now - lastMixinLogTime > 5000) {
            RealisticFireworkMod.LOGGER.info("MIXIN: getLightColor called, enabled={}, hasLights={}",
                engine.isEnabled(), engine.hasActiveLights());
            lastMixinLogTime = now;
        }

        if (!engine.isEnabled() || !engine.hasActiveLights()) {
            return;
        }

        int dynamicLight = engine.getDynamicLightAt(pos);
        if (dynamicLight <= 0) {
            return;
        }

        // Unpack the current lightmap value
        int currentValue = cir.getReturnValue();
        int skyLight = (currentValue >> 20) & 0xF;
        int blockLight = (currentValue >> 4) & 0xF;

        // Add dynamic light to block light, capping at 15
        int newBlockLight = Math.min(15, blockLight + dynamicLight);

        // Repack and return
        int newValue = (skyLight << 20) | (newBlockLight << 4);
        cir.setReturnValue(newValue);
    }
}
