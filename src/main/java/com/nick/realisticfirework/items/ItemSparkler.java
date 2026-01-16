package com.nick.realisticfirework.items;

import com.nick.realisticfirework.data.SparklerData;
import com.nick.realisticfirework.registry.ModDataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Sparkler item that can be lit and produces realistic sparking effects.
 * Right-click to light. When lit, produces particle effects from the tip.
 * Uses data components to track lit state. Visual burn progress is handled by the renderer.
 *
 * Dropping a lit sparkler (Q key) will keep it lit and it will emit light on the ground.
 */
public class ItemSparkler extends Item {

    public ItemSparkler(Properties properties) {
        super(properties);
    }

    /**
     * Check if this stack is lit.
     */
    public static boolean isLit(ItemStack stack) {
        SparklerData data = stack.get(ModDataComponents.SPARKLER.get());
        return data != null && data.isLit();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        SparklerData data = stack.get(ModDataComponents.SPARKLER.get());

        if (data != null && !data.isLit()) {
            if (!level.isClientSide()) {
                // Play ignition sound
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 1.0F, 1.0F);

                // Get current game tick for burn progress tracking
                long currentTick = level.getGameTime();

                if (stack.getCount() > 1) {
                    // Stack has multiple items - split off one and light it
                    stack.shrink(1); // Remove one from the stack

                    // Create a new single lit sparkler
                    ItemStack litSparkler = new ItemStack(this, 1);
                    litSparkler.set(ModDataComponents.SPARKLER.get(), SparklerData.newlyLit(currentTick));

                    // Put the remaining unlit stack in inventory, put lit one in hand
                    ItemStack unlitStack = stack.copy();
                    player.setItemInHand(hand, litSparkler);

                    // Add the remaining unlit sparklers to inventory
                    if (!unlitStack.isEmpty() && !player.getInventory().add(unlitStack)) {
                        // Inventory full, drop them
                        player.drop(unlitStack, false);
                    }

                    return InteractionResultHolder.success(litSparkler);
                } else {
                    // Only one item - light it directly
                    stack.set(ModDataComponents.SPARKLER.get(), SparklerData.newlyLit(currentTick));
                    return InteractionResultHolder.success(stack);
                }
            }
            return InteractionResultHolder.success(stack);
        }

        return InteractionResultHolder.pass(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        // Lit sparklers don't stack
        SparklerData data = stack.get(ModDataComponents.SPARKLER.get());
        if (data != null && data.isLit()) {
            return 1;
        }
        return super.getMaxStackSize(stack);
    }
}
