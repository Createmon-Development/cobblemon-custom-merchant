package net.fit.cobblemonmerchants.action.condition;

import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.fit.cobblemonmerchants.merchant.CustomMerchantEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for condition implementations.
 * Maps condition type strings to their implementations.
 */
public class ConditionRegistry {
    private static final Map<String, Condition> CONDITIONS = new HashMap<>();
    private static boolean initialized = false;

    /**
     * Registers a condition implementation.
     */
    public static void register(String type, Condition condition) {
        CONDITIONS.put(type, condition);
    }

    /**
     * Evaluates a condition using the registered implementation.
     */
    public static boolean evaluate(ConditionData data, Player player, @Nullable CustomMerchantEntity merchant) {
        ensureInitialized();

        Condition condition = CONDITIONS.get(data.type());
        if (condition == null) {
            CobblemonMerchants.LOGGER.warn("Unknown condition type: {}", data.type());
            return false;
        }
        return condition.evaluate(data, player, merchant);
    }

    /**
     * Initializes all built-in conditions.
     */
    public static void ensureInitialized() {
        if (initialized) return;
        initialized = true;

        register("has_item", new HasItemCondition());
        register("has_item_state", new HasItemStateCondition());
        register("holding_item", new HoldingItemCondition());
        register("holding_item_state", new HoldingItemStateCondition());
        register("has_pokemon_move", new HasPokemonMoveCondition());
        register("merchant_variant", new MerchantVariantCondition());

        CobblemonMerchants.LOGGER.info("Registered {} condition types", CONDITIONS.size());
    }
}
