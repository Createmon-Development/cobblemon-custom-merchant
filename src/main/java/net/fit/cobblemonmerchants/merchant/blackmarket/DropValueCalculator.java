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
     */
    private static final Map<String, Double> GAMEPLAY_MODIFIERS = new HashMap<>();

    static {
        // Items that are less valuable than their rarity suggests
        GAMEPLAY_MODIFIERS.put("minecraft:carrot", 0.3);
        GAMEPLAY_MODIFIERS.put("minecraft:potato", 0.3);
        GAMEPLAY_MODIFIERS.put("minecraft:poisonous_potato", 0.1);

        // Items that are more valuable than their rarity suggests
        GAMEPLAY_MODIFIERS.put("minecraft:ender_pearl", 1.4);
        GAMEPLAY_MODIFIERS.put("minecraft:gold_ingot", 1.2);
        GAMEPLAY_MODIFIERS.put("cobblemon:rare_candy", 2.0);
        GAMEPLAY_MODIFIERS.put("cobblemon:exp_share", 1.5);

        // Add more gameplay modifiers as needed
    }

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
     * Calculates the base value for an item before per-player variability
     *
     * Formula:
     * 1. Rarity Score = 100 / Average Drop Chance
     * 2. If 100% drop: Apply quantity penalty = 1 / max(1, Average Drop Quantity)
     * 3. Exclusivity = Cobblemon-exclusive ? config multiplier : 1.0
     * 4. Craftability = (!craftable && !growable) ? config multiplier : 1.0
     * 5. Availability = max(config min, Number of Pokemon that drop it)
     * 6. Base Value = (Rarity × Exclusivity × Craftability) / Availability
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

        // Step 3: Calculate exclusivity multiplier
        double exclusivity = dropData.isCobblemonExclusive() ?
            BlackMarketConfig.COBBLEMON_EXCLUSIVE_MULTIPLIER : 1.0;

        // Step 4: Calculate craftability multiplier
        double craftability = (!dropData.isCraftable() && !dropData.isGrowable()) ?
            BlackMarketConfig.NON_CRAFTABLE_MULTIPLIER : 1.0;

        // Step 5: Calculate availability (number of Pokemon that drop this item)
        double availability = Math.max(
            BlackMarketConfig.MIN_AVAILABILITY,
            dropData.getPokemonDrops().size()
        );

        // Step 6: Calculate base value
        double baseValue = (rarityScore * exclusivity * craftability) / availability;

        // Step 7: Apply gameplay modifier
        double gameplayModifier = getGameplayModifier(dropData.getItemId());
        baseValue *= gameplayModifier;

        // Step 8: Apply global multiplier
        baseValue *= BlackMarketConfig.GLOBAL_PRICE_MULTIPLIER;

        return baseValue;
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