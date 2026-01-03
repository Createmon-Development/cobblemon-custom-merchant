package net.fit.cobblemonmerchants.merchant.blackmarket;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Calculates relic coin values for Cobblemon drops based on rarity, exclusivity, and other factors.
 * Implements the approved formula system.
 */
public class DropValueCalculator {

    /**
     * Gameplay balance modifiers for specific items
     * These adjust the calculated value to reflect in-game usefulness/desirability
     * Loaded from black_market.json configuration
     */
    private static final Map<String, Double> GAMEPLAY_MODIFIERS = new HashMap<>();

    /**
     * Gets the gameplay modifier for an item
     * @param itemId Item ID
     * @return Gameplay modifier (default 1.0)
     */
    public static double getGameplayModifier(String itemId) {
        return GAMEPLAY_MODIFIERS.getOrDefault(itemId, 1.0);
    }

    /**
     * Sets a gameplay modifier for an item
     * @param itemId Item ID
     * @param modifier Modifier value
     */
    public static void setGameplayModifier(String itemId, double modifier) {
        GAMEPLAY_MODIFIERS.put(itemId, modifier);
    }

    /**
     * Clears all gameplay modifiers (used when reloading configuration)
     */
    public static void clearGameplayModifiers() {
        GAMEPLAY_MODIFIERS.clear();
    }

    /**
     * Calculates the base value for an item before per-player variability
     *
     * Formula:
     * 1. Rarity Score = 100 / Average Drop Chance
     * 2. If 100% drop: Apply quantity penalty = 1 / max(1, Average Drop Quantity)
     * 3. Exclusivity = Cobblemon-exclusive ? config multiplier : 1.0
     * 4. Battle Usefulness = isHeldItem ? config multiplier : 1.0
     *    (Items in cobblemon:held/is_held_item tag are battle-useful and get a value bonus)
     * 5. Availability = max(config min, Number of Pokemon that drop it)
     * 6. Base Value = (Rarity × Exclusivity × Battle Usefulness) / Availability
     * 7. Apply Gameplay Modifier
     * 8. Apply Global Multiplier
     *
     * @param dropData The drop data for the item
     * @return Base value in relic coins (before per-player variability)
     */
    public static double calculateBaseValue(CobblemonDropData dropData) {
        // Step 1: Calculate rarity score
        double averageDropChance = dropData.getAverageDropChance();
        if (averageDropChance <= 0) {
            return 0; // Invalid data
        }

        double rarityScore = 100.0 / averageDropChance;

        // Step 2: Apply quantity penalty for 100% drops
        if (averageDropChance >= 100.0) {
            double averageQuantity = dropData.getAverageDropQuantity();
            double quantityPenalty = 1.0 / averageQuantity;
            rarityScore *= quantityPenalty;
        }

        // Step 2.5: Apply outlier detection penalty
        // If only 1-2 Pokemon drop this item at 100%, it's likely an outlier (e.g., nether star from rare boss)
        if (BlackMarketConfig.OUTLIER_DETECTION_ENABLED && averageDropChance >= 100.0) {
            int pokemonCount = dropData.getPokemonDrops().size();
            if (pokemonCount <= 2) {
                // This is likely an outlier - rare boss Pokemon with 100% drop
                rarityScore *= BlackMarketConfig.OUTLIER_PENALTY_MULTIPLIER;
            }
        }

        // Step 3: Calculate exclusivity multiplier
        double exclusivity = dropData.isCobblemonExclusive() ?
            BlackMarketConfig.COBBLEMON_EXCLUSIVE_MULTIPLIER : 1.0;

        // Step 4: Calculate battle usefulness multiplier
        // Held items are battle-useful and get a value bonus
        double battleUsefulness = dropData.isHeldItem() ?
            BlackMarketConfig.HELD_ITEM_MULTIPLIER : 1.0;

        // Step 5: Calculate availability (number of Pokemon that drop this item)
        double availability = Math.max(
            BlackMarketConfig.MIN_AVAILABILITY,
            dropData.getPokemonDrops().size()
        );

        // Step 6: Calculate base value
        double baseValue = (rarityScore * exclusivity * battleUsefulness) / availability;

        // Step 7: Apply gameplay modifier
        double gameplayModifier = getGameplayModifier(dropData.getItemId());
        baseValue *= gameplayModifier;

        // Step 8: Apply crafting recipe cap
        if (BlackMarketConfig.CRAFTING_RECIPE_CHECK_ENABLED && isCraftable(dropData.getItemId())) {
            // Cap the value for craftable items
            baseValue = Math.min(baseValue, BlackMarketConfig.CRAFTABLE_ITEM_MAX_VALUE);
        }

        // Step 9: Apply global multiplier
        baseValue *= BlackMarketConfig.GLOBAL_PRICE_MULTIPLIER;

        return baseValue;
    }

    /**
     * Checks if an item has a crafting recipe
     * @param itemId The item ID to check
     * @return true if the item is craftable
     */
    private static boolean isCraftable(String itemId) {
        try {
            // Parse the item ID
            net.minecraft.resources.ResourceLocation itemLocation = net.minecraft.resources.ResourceLocation.parse(itemId);

            // Get the item from registry
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemLocation);
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                return false;
            }

            // Check if there's a crafting recipe for this item
            // We need access to RecipeManager, which we can't get statically
            // For now, we'll use a simple heuristic - check common craftable items
            // This can be improved by passing ServerLevel to this method
            return isLikelyCraftable(itemId);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Simple heuristic to check if an item is likely craftable
     * This is a fallback when we don't have access to RecipeManager
     */
    private static boolean isLikelyCraftable(String itemId) {
        // Common patterns for craftable items
        String[] craftablePatterns = {
            "minecraft:.*_planks",
            "minecraft:.*_stairs",
            "minecraft:.*_slab",
            "minecraft:.*_fence",
            "minecraft:.*_door",
            "minecraft:.*_trapdoor",
            "minecraft:stick",
            "minecraft:crafting_table",
            "minecraft:chest",
            "minecraft:furnace",
            "minecraft:.*_pickaxe",
            "minecraft:.*_axe",
            "minecraft:.*_shovel",
            "minecraft:.*_hoe",
            "minecraft:.*_sword",
            "minecraft:.*_helmet",
            "minecraft:.*_chestplate",
            "minecraft:.*_leggings",
            "minecraft:.*_boots"
        };

        for (String pattern : craftablePatterns) {
            if (itemId.matches(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Calculates the final relic coin cost for a specific player
     * Applies per-player variability based on UUID and rotation ID
     *
     * @param dropData The drop data for the item
     * @param playerUUID Player's UUID for deterministic randomness
     * @param rotationId Current rotation ID
     * @return Final relic coin cost for this player
     */
    public static int calculatePlayerValue(CobblemonDropData dropData, UUID playerUUID, long rotationId) {
        double baseValue = calculateBaseValue(dropData);

        // Create deterministic random based on player UUID, item, and rotation
        Random random = createDeterministicRandom(playerUUID, dropData.getItemId(), rotationId);

        // Apply price variability (-40% to +20%)
        double priceVariability = BlackMarketConfig.MIN_PRICE_VARIABILITY +
            random.nextDouble() * (BlackMarketConfig.MAX_PRICE_VARIABILITY - BlackMarketConfig.MIN_PRICE_VARIABILITY);

        double finalValue = baseValue * priceVariability;

        // Round to nearest integer, minimum 1
        return Math.max(1, (int) Math.round(finalValue));
    }

    /**
     * Calculates the number of items to offer in the trade
     * Applies per-player variability (0.7x to 1.3x)
     *
     * @param baseCount Base number of items to offer
     * @param playerUUID Player's UUID for deterministic randomness
     * @param itemId Item ID for seed
     * @param rotationId Current rotation ID
     * @return Final item count for this player
     */
    public static int calculatePlayerItemCount(int baseCount, UUID playerUUID, String itemId, long rotationId) {
        // Create deterministic random based on player UUID, item, and rotation
        Random random = createDeterministicRandom(playerUUID, itemId, rotationId);

        // Apply count variability (0.7x to 1.3x)
        double countVariability = BlackMarketConfig.MIN_COUNT_VARIABILITY +
            random.nextDouble() * (BlackMarketConfig.MAX_COUNT_VARIABILITY - BlackMarketConfig.MIN_COUNT_VARIABILITY);

        double finalCount = baseCount * countVariability;

        // Round to nearest integer, minimum 1
        return Math.max(1, (int) Math.round(finalCount));
    }

    /**
     * Calculates the maximum number of trades for a specific player based on item value
     * Formula:
     * - Low value items (< threshold): More trades (up to MAX_TRADE_USES)
     * - High value items (> threshold): Fewer trades (down to MIN_TRADE_USES)
     * - Variance scales with base trade count (more trades = more variance possible)
     *
     * @param dropData The drop data for the item
     * @param playerUUID Player's UUID for deterministic randomness
     * @param rotationId Current rotation ID
     * @return Maximum trade uses for this player
     */
    public static int calculatePlayerTradeUses(CobblemonDropData dropData, UUID playerUUID, long rotationId) {
        double baseValue = calculateBaseValue(dropData);

        // Calculate base trade uses using inverse relationship with value
        // Items worth less than threshold get more trades
        double baseTradeUses;

        if (baseValue <= BlackMarketConfig.TRADE_USE_THRESHOLD) {
            // Low value items: scale from MAX down to BASE as value increases
            // Formula: MAX - ((value / threshold) * (MAX - BASE))
            double valueRatio = baseValue / BlackMarketConfig.TRADE_USE_THRESHOLD;
            baseTradeUses = BlackMarketConfig.MAX_TRADE_USES -
                (valueRatio * (BlackMarketConfig.MAX_TRADE_USES - BlackMarketConfig.BASE_MAX_TRADE_USES));
        } else {
            // High value items: scale from BASE down to MIN as value increases
            // Formula: BASE - ((log(value/threshold) / log(4)) * (BASE - MIN))
            // Using log scale so extremely expensive items don't hit MIN too quickly
            double valueRatio = Math.log(baseValue / BlackMarketConfig.TRADE_USE_THRESHOLD) / Math.log(4.0);
            valueRatio = Math.min(valueRatio, 1.0); // Cap at 1.0
            baseTradeUses = BlackMarketConfig.BASE_MAX_TRADE_USES -
                (valueRatio * (BlackMarketConfig.BASE_MAX_TRADE_USES - BlackMarketConfig.MIN_TRADE_USES));
        }

        // Create deterministic random for this player, item, and rotation
        Random random = createDeterministicRandom(playerUUID, dropData.getItemId(), rotationId);

        // Calculate variance range that scales with base trade uses
        // More base trades = wider variance range
        double varianceScale = baseTradeUses / BlackMarketConfig.MAX_TRADE_USES;

        // Scale variance: high trade counts get full variance, low counts get less
        double minVariance = 1.0 - ((1.0 - BlackMarketConfig.MIN_TRADE_USE_VARIABILITY) * varianceScale);
        double maxVariance = 1.0 + ((BlackMarketConfig.MAX_TRADE_USE_VARIABILITY - 1.0) * varianceScale);

        // Apply variance
        double variance = minVariance + random.nextDouble() * (maxVariance - minVariance);
        double finalTradeUses = baseTradeUses * variance;

        // Round and clamp to valid range
        int tradeUses = (int) Math.round(finalTradeUses);
        return Math.max(BlackMarketConfig.MIN_TRADE_USES,
                       Math.min(BlackMarketConfig.MAX_TRADE_USES, tradeUses));
    }

    /**
     * Creates a deterministic Random instance based on player UUID, item ID, and rotation
     * This ensures the same player always gets the same prices/counts for the same item in the same rotation
     *
     * @param playerUUID Player's UUID
     * @param itemId Item ID
     * @param rotationId Current rotation ID
     * @return Seeded Random instance
     */
    private static Random createDeterministicRandom(UUID playerUUID, String itemId, long rotationId) {
        long seed = playerUUID.getMostSignificantBits() ^
                   playerUUID.getLeastSignificantBits() ^
                   itemId.hashCode() ^
                   rotationId;
        return new Random(seed);
    }
}