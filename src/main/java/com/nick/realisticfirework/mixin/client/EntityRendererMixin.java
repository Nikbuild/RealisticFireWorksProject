package com.nick.realisticfirework.mixin.client;

import com.nick.realisticfirework.client.lighting.DynamicLightingEngine;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to add dynamic lighting to entity rendering.
 * This ensures entities near dynamic light sources are properly lit.
 */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin<T extends Entity> {

    /**
     * Inject at the end of getBlockLightLevel to add dynamic light contribution.
     */
    @Inject(method = "getBlockLightLevel", at = @At("RETURN"), cancellable = true)
    private void onGetBlockLightLevel(T entity, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        DynamicLightingEngine engine = DynamicLightingEngine.getInstance();
        if (!engine.isEnabled() || !engine.hasActiveLights()) {
            return;
        }

        // Get dynamic light at entity position for more accurate lighting
        Vec3 entityPos = entity.position();
        float dynamicLight = engine.getDynamicLightAtPrecise(entityPos);

        if (dynamicLight <= 0) {
            return;
        }

        int currentLight = cir.getReturnValue();
        int newLight = Math.min(15, currentLight + (int) dynamicLight);
        cir.setReturnValue(newLight);
    }
}
