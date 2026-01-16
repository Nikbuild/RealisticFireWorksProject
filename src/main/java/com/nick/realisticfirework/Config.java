package com.nick.realisticfirework;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for RealisticFireworkMod.
 * Add your mod's configuration options here.
 */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // Sparkler settings
    public static final ModConfigSpec.IntValue SPARKLER_BURN_TIME = BUILDER
            .comment("How long a lit sparkler burns before going out (in ticks, 20 ticks = 1 second)")
            .defineInRange("sparklerBurnTime", 600, 100, 6000);

    public static final ModConfigSpec.IntValue SPARKLER_PARTICLE_COUNT = BUILDER
            .comment("Number of spark particles spawned per tick")
            .defineInRange("sparklerParticleCount", 3, 1, 10);

    static final ModConfigSpec SPEC = BUILDER.build();
}
