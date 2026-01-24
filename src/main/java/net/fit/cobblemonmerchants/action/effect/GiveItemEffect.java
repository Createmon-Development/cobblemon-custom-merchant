package net.fit.cobblemonmerchants.action.effect;

import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.fit.cobblemonmerchants.merchant.CustomMerchantEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Effect that gives an item to the player.
 */
public class GiveItemEffect implements ActionEffect {

    @Override
    public void execute(ActionEffectData data, ServerPlayer player, @Nullable CustomMerchantEntity merchant) {
        if (data.item().isEmpty()) {
            CobblemonMerchants.LOGGER.warn("GiveItemEffect missing item parameter");
            return;
        }

        String itemId = data.item().get();
        int count = data.getCount(1);

        try {
            ResourceLocation itemLoc = ResourceLocation.parse(itemId);
            Item item = BuiltInRegistries.ITEM.get(itemLoc);

            if (item == null) {
                CobblemonMerchants.LOGGER.warn("Unknown item in GiveItemEffect: {}", itemId);
                return;
            }

            ItemStack stack = new ItemStack(item, count);

            // Try to add to inventory, drop if full
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }

            CobblemonMerchants.LOGGER.debug("Gave {} x{} to player {}", itemId, count, player.getName().getString());

        } catch (Exception e) {
            CobblemonMerchants.LOGGER.error("Error in GiveItemEffect for item {}: {}", itemId, e.getMessage());
        }
    }
}
