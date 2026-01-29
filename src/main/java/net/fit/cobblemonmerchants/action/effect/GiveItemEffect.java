package net.fit.cobblemonmerchants.action.effect;

import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.fit.cobblemonmerchants.merchant.CustomMerchantEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Effect that gives an item to the player.
 */
public class GiveItemEffect implements ActionEffect {

    @Override
    public void execute(ActionEffectData data, ServerPlayer player, @Nullable CustomMerchantEntity merchant) {
        if (data.item().isEmpty()) {
            CobblemonMerchants.LOGGER.warn("[GiveItemEffect] Missing item parameter");
            return;
        }

        String itemId = data.item().get();
        int count = data.getCount(1);

        CobblemonMerchants.LOGGER.info("[GiveItemEffect] Attempting to give item '{}' x{} to player {}",
            itemId, count, player.getName().getString());

        try {
            ResourceLocation itemLoc = ResourceLocation.parse(itemId);

            // Check if the item exists in the registry
            if (!BuiltInRegistries.ITEM.containsKey(itemLoc)) {
                CobblemonMerchants.LOGGER.warn("[GiveItemEffect] Item not found in registry: {}. " +
                    "Make sure the mod providing this item is loaded.", itemId);
                return;
            }

            Item item = BuiltInRegistries.ITEM.get(itemLoc);

            // BuiltInRegistries.ITEM.get() returns Items.AIR for unknown items, not null
            if (item == null || item == Items.AIR) {
                CobblemonMerchants.LOGGER.warn("[GiveItemEffect] Item resolved to AIR (invalid): {}", itemId);
                return;
            }

            ItemStack stack = new ItemStack(item, count);

            CobblemonMerchants.LOGGER.info("[GiveItemEffect] Created ItemStack: {} x{}",
                BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount());

            // Try to add to inventory, drop if full
            if (!player.getInventory().add(stack)) {
                CobblemonMerchants.LOGGER.info("[GiveItemEffect] Inventory full, dropping item");
                player.drop(stack, false);
            }

            CobblemonMerchants.LOGGER.info("[GiveItemEffect] Successfully gave {} x{} to player {}",
                itemId, count, player.getName().getString());

        } catch (Exception e) {
            CobblemonMerchants.LOGGER.error("[GiveItemEffect] Error giving item {}: {}", itemId, e.getMessage());
            e.printStackTrace();
        }
    }
}
