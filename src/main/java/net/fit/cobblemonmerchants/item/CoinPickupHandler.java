package net.fit.cobblemonmerchants.item;

import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.fit.cobblemonmerchants.item.component.ModDataComponents;
import net.fit.cobblemonmerchants.item.custom.RelicCoinBagItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

/**
 * Handles auto pickup mode for relic coins
 * When auto pickup mode is enabled (green pane - default), coins go directly to bag
 * When disabled (red pane), coins go to inventory normally
 */
@EventBusSubscriber(modid = CobblemonMerchants.MODID)
public class CoinPickupHandler {

    /**
     * Handle coin pickup to redirect to bag if auto pickup mode is enabled
     * Runs before pickup to prevent coins from entering inventory
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        Player player = event.getPlayer();
        ItemEntity itemEntity = event.getItemEntity();
        ItemStack itemStack = itemEntity.getItem();

        // Check if the picked up item is a relic coin
        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        if (itemId == null || !itemId.toString().equals("cobblemon:relic_coin")) {
            return; // Not a relic coin, allow normal pickup
        }

        // Find the player's coin bag
        ItemStack bagStack = null;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof RelicCoinBagItem) {
                bagStack = stack;
                break;
            }
        }

        // If player doesn't have a bag, allow normal pickup to inventory
        if (bagStack == null) {
            return;
        }

        // Check auto pickup mode setting (default is true - enabled)
        boolean autoPickupEnabled = bagStack.getOrDefault(ModDataComponents.AUTO_PICKUP_ENABLED.get(), true);

        if (!autoPickupEnabled) {
            // Auto pickup disabled: coins go to inventory (allow normal pickup)
            return;
        }

        // Auto pickup enabled: prevent pickup and add directly to bag
        int coinCount = itemStack.getCount();

        // Add coins to bag
        int currentCoins = bagStack.getOrDefault(ModDataComponents.RELIC_COIN_COUNT.get(), 0);
        bagStack.set(ModDataComponents.RELIC_COIN_COUNT.get(), currentCoins + coinCount);

        // Play pickup sound and animation
        player.take(itemEntity, coinCount);

        // Play pickup sound explicitly for better feedback
        player.playSound(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 0.2F,
            ((player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F);

        // Remove the item entity from the world
        itemEntity.discard();

        // Prevent normal pickup to inventory (prevents duplication)
        event.setCanPickup(TriState.FALSE);
    }
}
