package com.nick.realisticfirework.registry;

import com.nick.realisticfirework.RealisticFireworkMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry for mod sound events.
 */
public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
        DeferredRegister.create(Registries.SOUND_EVENT, RealisticFireworkMod.MODID);

    // Sparkler burning/sizzling sound - loops while sparkler is lit
    // Uses fixed range with large value so OpenAL attenuation is nearly flat
    // Our SoundHelper controls actual volume via packet volume parameter
    public static final DeferredHolder<SoundEvent, SoundEvent> SPARKLER_BURNING = SOUND_EVENTS.register(
        "sparkler_burning",
        () -> SoundEvent.createFixedRangeEvent(
            ResourceLocation.fromNamespaceAndPath(RealisticFireworkMod.MODID, "sparkler_burning"),
            256.0f  // Very large range so OpenAL won't attenuate within normal play distances
        )
    );

    // Sparkler ignition sound - plays once when lit
    public static final DeferredHolder<SoundEvent, SoundEvent> SPARKLER_IGNITE = SOUND_EVENTS.register(
        "sparkler_ignite",
        () -> SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(RealisticFireworkMod.MODID, "sparkler_ignite")
        )
    );
}
