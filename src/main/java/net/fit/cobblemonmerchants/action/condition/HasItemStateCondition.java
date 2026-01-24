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
 * Condition that checks if the player has an item with a specific component value.
 * Used for checking orb_state, tablet_glowing, etc.
 */
public class HasItemStateCondition implements Condition {

    @Override
    public boolean evaluate(ConditionData data, Player player, @Nullable CustomMerchantEntity merchant) {
        if (data.item().isEmpty() || data.component().isEmpty()) {
            return false;
        }

        ResourceLocation itemId = ResourceLocation.parse(data.item().get());
        Item targetItem = BuiltInRegistries.ITEM.get(itemId);
        String componentName = data.component().get();
        Object expectedValue = data.getValue();

        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(targetItem)) {
                Object actualValue = getComponentValue(stack, componentName);
                if (actualValue != null && valuesMatch(actualValue, expectedValue)) {
                    return !data.invert();
                }
            }
        }
        return data.invert();
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
        // Look for skyscobblemonitems components
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

        // Handle integer comparison
        if (actual instanceof Integer actualInt) {
            if (expected instanceof Integer expectedInt) {
                return actualInt.equals(expectedInt);
            }
            // Try parsing expected as int
            try {
                return actualInt.equals(Integer.parseInt(expected.toString()));
            } catch (NumberFormatException e) {
                return false;
            }
        }

        // Handle boolean comparison
        if (actual instanceof Boolean actualBool) {
            if (expected instanceof Boolean expectedBool) {
                return actualBool.equals(expectedBool);
            }
            return actualBool.equals(Boolean.parseBoolean(expected.toString()));
        }

        // String comparison
        return actual.toString().equals(expected.toString());
    }
}
