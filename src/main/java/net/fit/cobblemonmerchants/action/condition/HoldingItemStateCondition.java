package net.fit.cobblemonmerchants.action.condition;

import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.fit.cobblemonmerchants.merchant.CustomMerchantEntity;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Condition that checks if the player is holding an item with a specific component value.
 * Used for checking if the held tablet is glowing, etc.
 */
public class HoldingItemStateCondition implements Condition {

    @Override
    public boolean evaluate(ConditionData data, Player player, @Nullable CustomMerchantEntity merchant) {
        if (data.item().isEmpty() || data.component().isEmpty()) {
            CobblemonMerchants.LOGGER.info("[HoldingItemState] Missing item or component in condition data");
            return false;
        }

        ItemStack heldItem = player.getMainHandItem();

        // If player is holding nothing (air), they can't be holding a specific item
        if (heldItem.isEmpty()) {
            CobblemonMerchants.LOGGER.info("[HoldingItemState] Player has empty hand, returning invert={}", data.invert());
            return data.invert();
        }

        ResourceLocation itemId = ResourceLocation.parse(data.item().get());

        // Check if the item exists in the registry
        if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
            CobblemonMerchants.LOGGER.warn("[HoldingItemState] Item '{}' not found in registry", itemId);
            return data.invert();
        }

        Item targetItem = BuiltInRegistries.ITEM.get(itemId);

        CobblemonMerchants.LOGGER.info("[HoldingItemState] Checking: target={}, held={}, isEmpty={}",
            itemId, BuiltInRegistries.ITEM.getKey(heldItem.getItem()), heldItem.isEmpty());

        if (!heldItem.is(targetItem)) {
            CobblemonMerchants.LOGGER.info("[HoldingItemState] Item mismatch - not holding target item");
            return data.invert();
        }

        String componentName = data.component().get();
        Object expectedValue = data.getValue();
        Object actualValue = getComponentValue(heldItem, componentName);

        CobblemonMerchants.LOGGER.info("[HoldingItemState] Component '{}': expected={}, actual={}",
            componentName, expectedValue, actualValue);

        boolean result = actualValue != null && valuesMatch(actualValue, expectedValue);
        CobblemonMerchants.LOGGER.info("[HoldingItemState] Final result: {}", result);
        return data.invert() ? !result : result;
    }

    @SuppressWarnings("unchecked")
    private Object getComponentValue(ItemStack stack, String componentName) {
        // Try to find the component by name in the registry
        for (var entry : BuiltInRegistries.DATA_COMPONENT_TYPE.entrySet()) {
            if (entry.getKey().location().getPath().equals(componentName) ||
                entry.getKey().location().toString().equals(componentName)) {
                DataComponentType<?> componentType = entry.getValue();
                return stack.get(componentType);
            }
        }

        // Try common patterns for mod components
        try {
            ResourceLocation componentLoc = ResourceLocation.parse("skyscobblemonitems:" + componentName);
            for (var entry : BuiltInRegistries.DATA_COMPONENT_TYPE.entrySet()) {
                if (entry.getKey().location().equals(componentLoc)) {
                    DataComponentType<?> componentType = entry.getValue();
                    return stack.get(componentType);
                }
            }
        } catch (Exception e) {
            CobblemonMerchants.LOGGER.debug("Failed to find component {}: {}", componentName, e.getMessage());
        }

        return null;
    }

    private boolean valuesMatch(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return actual == expected;
        }

        if (actual instanceof Integer actualInt) {
            if (expected instanceof Integer expectedInt) {
                return actualInt.equals(expectedInt);
            }
            try {
                return actualInt.equals(Integer.parseInt(expected.toString()));
            } catch (NumberFormatException e) {
                return false;
            }
        }

        if (actual instanceof Boolean actualBool) {
            if (expected instanceof Boolean expectedBool) {
                return actualBool.equals(expectedBool);
            }
            return actualBool.equals(Boolean.parseBoolean(expected.toString()));
        }

        return actual.toString().equals(expected.toString());
    }
}
