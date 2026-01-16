package com.nick.realisticfirework;

import com.nick.realisticfirework.data.SparklerData;
import com.nick.realisticfirework.items.ItemSparkler;
import com.nick.realisticfirework.registry.ModDataComponents;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.item.Item;

/**
 * Holds all mod item registrations using NeoForge's DeferredRegister system.
 */
public class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RealisticFireworkMod.MODID);

    // Single sparkler item that uses data components to track state
    public static final DeferredItem<Item> SPARKLER = ITEMS.register("sparkler",
        () -> new ItemSparkler(new Item.Properties()
            .stacksTo(16)
            .component(ModDataComponents.SPARKLER.get(), SparklerData.unlit())));
}
