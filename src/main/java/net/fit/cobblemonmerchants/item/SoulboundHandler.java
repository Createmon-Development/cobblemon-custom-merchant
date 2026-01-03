package net.fit.cobblemonmerchants.item;

import net.fit.cobblemonmerchants.item.component.ModDataComponents;
import net.fit.cobblemonmerchants.item.custom.RelicCoinBagItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Handles soulbound behavior - keeps bags in inventory through death
 *
 * Strategy: Remove bags from inventory BEFORE death processing (corpse creation),
 * then immediately restore them after the player entity is cloned.
 * This prevents corpse mods from ever seeing or storing the bags.
 */
@EventBusSubscriber(modid = net.fit.cobblemonmerchants.CobblemonMerchants.MODID)
public class SoulboundHandler {

    /**
     * Temporarily stores bags removed from inventory during death
     * Key: Player UUID, Value: The bag ItemStack
     */
    private static final java.util.Map<java.util.UUID, ItemStack> TEMP_STORAGE = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Remove bags from inventory BEFORE death processing
     * Runs at HIGHEST priority to execute before corpse mods
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Search inventory for relic coin bags and remove them
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof RelicCoinBagItem) {
                // Remove from inventory and store temporarily
                ItemStack removed = player.getInventory().removeItemNoUpdate(i);
                TEMP_STORAGE.put(player.getUUID(), removed);

                int coinCount = removed.getOrDefault(ModDataComponents.RELIC_COIN_COUNT.get(), 0);
                net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info(
                    "Soulbound: Removed bag from {} before death (slot {}, {} coins)",
                    player.getName().getString(), i, coinCount);
                break; // Only handle first bag
            }
        }
    }

    /**
     * Restore bags immediately after player respawns
     * Runs at LOWEST priority to execute after all other mods
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || !(event.getEntity() instanceof ServerPlayer newPlayer)) {
            return;
        }

        // Check if we have a bag in temporary storage
        ItemStack storedBag = TEMP_STORAGE.remove(newPlayer.getUUID());
        if (storedBag == null || storedBag.isEmpty()) {
            return;
        }

        int coinCount = storedBag.getOrDefault(ModDataComponents.RELIC_COIN_COUNT.get(), 0);
        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info(
            "Soulbound: Restoring bag to {} after respawn ({} coins)",
            newPlayer.getName().getString(), coinCount);

        // Try to add to first empty hotbar slot
        boolean added = false;
        for (int i = 0; i < 9; i++) {
            if (newPlayer.getInventory().getItem(i).isEmpty()) {
                newPlayer.getInventory().setItem(i, storedBag);
                added = true;
                net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info(
                    "Soulbound: Restored bag to hotbar slot {}", i);
                break;
            }
        }

        // If hotbar is full, try any slot
        if (!added) {
            added = newPlayer.getInventory().add(storedBag);
            if (added) {
                net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info(
                    "Soulbound: Restored bag to inventory");
            } else {
                // If inventory completely full, drop at player's feet
                newPlayer.drop(storedBag, false);
                net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.warn(
                    "Soulbound: Inventory full, dropped bag at player location");
            }
        }
    }
}
