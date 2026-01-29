package net.fit.cobblemonmerchants.action.condition;

import net.fit.cobblemonmerchants.merchant.CustomMerchantEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Condition that checks if the player is holding a specific item in their main hand.
 */
public class HoldingItemCondition implements Condition {

    @Override
    public boolean evaluate(ConditionData data, Player player, @Nullable CustomMerchantEntity merchant) {
        ItemStack heldItem = player.getMainHandItem();
        ResourceLocation heldItemId = BuiltInRegistries.ITEM.getKey(heldItem.getItem());

        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info(
            "[HoldingItemCondition] Player {} holding: {} (empty={})",
            player.getName().getString(), heldItemId, heldItem.isEmpty());

        // If player is holding nothing (air), they can't be holding a specific item
        if (heldItem.isEmpty()) {
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info(
                "[HoldingItemCondition] Player has empty hand, returning invert={}", data.invert());
            return data.invert();
        }

        if (data.item().isPresent()) {
            ResourceLocation itemId = ResourceLocation.parse(data.item().get());

            // Check if the item exists in the registry
            if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
                net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.warn(
                    "[HoldingItemCondition] Item '{}' not found in registry", itemId);
                return data.invert();
            }

            Item item = BuiltInRegistries.ITEM.get(itemId);
            boolean result = heldItem.is(item);

            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info(
                "[HoldingItemCondition] Checking for item '{}': match={}, invert={}, finalResult={}",
                itemId, result, data.invert(), data.invert() ? !result : result);

            return data.invert() ? !result : result;
        }

        if (data.tag().isPresent()) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(data.tag().get()));
            boolean result = heldItem.is(tag);

            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info(
                "[HoldingItemCondition] Checking for tag '{}': match={}, invert={}, finalResult={}",
                data.tag().get(), result, data.invert(), data.invert() ? !result : result);

            return data.invert() ? !result : result;
        }

        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.warn(
            "[HoldingItemCondition] No item or tag specified in condition data");
        return false;
    }
}
