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
 * Condition that checks if the player has an item in their inventory.
 * Supports both exact item matching and tag-based matching.
 */
public class HasItemCondition implements Condition {

    @Override
    public boolean evaluate(ConditionData data, Player player, @Nullable CustomMerchantEntity merchant) {
        int requiredCount = data.count();

        if (data.item().isPresent()) {
            ResourceLocation itemId = ResourceLocation.parse(data.item().get());
            Item item = BuiltInRegistries.ITEM.get(itemId);
            int count = countItemInInventory(player, item);
            boolean result = count >= requiredCount;
            return data.invert() ? !result : result;
        }

        if (data.tag().isPresent()) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(data.tag().get()));
            int count = countTagInInventory(player, tag);
            boolean result = count >= requiredCount;
            return data.invert() ? !result : result;
        }

        return false;
    }

    private int countItemInInventory(Player player, Item item) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private int countTagInInventory(Player player, TagKey<Item> tag) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(tag)) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
