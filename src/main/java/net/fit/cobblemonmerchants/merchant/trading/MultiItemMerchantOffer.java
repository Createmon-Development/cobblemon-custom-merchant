package net.fit.cobblemonmerchants.merchant.trading;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A MerchantOffer that accepts multiple different items for the input
 */
public class MultiItemMerchantOffer extends MerchantOffer {
    private final Set<Item> acceptedItems;
    private final List<String> acceptedItemIds;
    private final int requiredCount;
    private final String customDisplayName;
    private ItemStack cachedDisplayStack;

    public MultiItemMerchantOffer(
        ItemCost displayCost,
        Optional<ItemCost> costB,
        ItemStack result,
        int maxUses,
        int xp,
        float priceMultiplier,
        List<String> acceptedItemIds,
        String customDisplayName
    ) {
        super(displayCost, costB, result, maxUses, xp, priceMultiplier);

        this.acceptedItems = new HashSet<>();
        this.acceptedItemIds = new ArrayList<>(acceptedItemIds);
        this.requiredCount = displayCost.count();
        this.customDisplayName = customDisplayName;
        this.cachedDisplayStack = null;

        System.out.println("MultiItemMerchantOffer created with customDisplayName: " + customDisplayName);

        // Parse all accepted item IDs
        for (String itemId : acceptedItemIds) {
            try {
                ResourceLocation location = ResourceLocation.parse(itemId);
                Item item = BuiltInRegistries.ITEM.get(location);
                if (item != null && !item.equals(BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace("air")))) {
                    acceptedItems.add(item);
                }
            } catch (Exception e) {
                // Skip invalid item IDs
                System.err.println("Warning: Invalid item ID in merchant trade: " + itemId);
            }
        }
    }

    public ItemStack getCustomDisplayStack() {
        // Create the display stack on-demand and cache it
        if (cachedDisplayStack == null) {
            cachedDisplayStack = this.getItemCostA().itemStack();
            System.out.println("getCustomDisplayStack: customDisplayName = " + customDisplayName);
            if (customDisplayName != null && !customDisplayName.isEmpty()) {
                cachedDisplayStack.set(DataComponents.CUSTOM_NAME, Component.literal(customDisplayName));
                System.out.println("Applied custom name to display stack");
            }
        }
        return cachedDisplayStack.copy();
    }

    @Override
    public boolean satisfiedBy(ItemStack firstItem, ItemStack secondItem) {
        // Check if the first item is in our accepted list
        boolean firstMatches = acceptedItems.contains(firstItem.getItem()) &&
                              firstItem.getCount() >= requiredCount;

        if (!firstMatches) {
            return false;
        }

        // Check second input if present
        ItemStack costB = this.getCostB();
        if (!costB.isEmpty()) {
            return costB.getItem() == secondItem.getItem() &&
                   secondItem.getCount() >= costB.getCount();
        }

        return true;
    }

    public List<String> getAcceptedItemIds() {
        return acceptedItemIds;
    }

    public String getCustomDisplayName() {
        return customDisplayName;
    }
}
