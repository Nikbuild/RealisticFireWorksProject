package com.nick.realisticfirework.registry;

import com.nick.realisticfirework.RealisticFireworkMod;
import com.nick.realisticfirework.data.SparklerData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry for mod data components.
 * Data components are the 1.21+ replacement for NBT tags on items.
 */
public class ModDataComponents {

    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, RealisticFireworkMod.MODID);

    /**
     * Sparkler data component - stores lit state.
     * Items only stack if this component matches exactly.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SparklerData>> SPARKLER =
            DATA_COMPONENTS.registerComponentType(
                    "sparkler",
                    builder -> builder
                            .persistent(SparklerData.CODEC)
                            .networkSynchronized(SparklerData.STREAM_CODEC)
            );
}
