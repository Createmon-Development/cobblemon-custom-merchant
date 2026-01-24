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

        if (data.item().isPresent()) {
            ResourceLocation itemId = ResourceLocation.parse(data.item().get());
            Item item = BuiltInRegistries.ITEM.get(itemId);
            boolean result = heldItem.is(item);
            return data.invert() ? !result : result;
        }

        if (data.tag().isPresent()) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(data.tag().get()));
            boolean result = heldItem.is(tag);
            return data.invert() ? !result : result;
        }

        return false;
    }
}
