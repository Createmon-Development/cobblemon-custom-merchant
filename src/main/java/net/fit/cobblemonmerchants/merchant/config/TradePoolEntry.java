package net.fit.cobblemonmerchants.merchant.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * Represents a single entry in a trade pool.
 * Each entry defines an item that can be randomly selected for a daily rotating trade slot.
 *
 * @param itemId The item ID (e.g., "minecraft:diamond", "cobblemon:rare_candy")
 * @param inputAmount The base cost in the input currency (e.g., relic coins)
 * @param outputAmount The base output count
 * @param weight The selection weight (higher = more likely to be selected)
 * @param inputVariance Optional variance for input cost (e.g., 0.2 means ±20%)
 * @param outputVariance Optional variance for output count (e.g., 0.3 means ±30%)
 * @param maxUses Optional max uses per day (default unlimited)
 * @param displayName Optional custom display name for the trade
 */
public record TradePoolEntry(
    String itemId,
    int inputAmount,
    int outputAmount,
    int weight,
    Optional<Double> inputVariance,
    Optional<Double> outputVariance,
    Optional<Integer> maxUses,
    Optional<String> displayName
) {
    public static final Codec<TradePoolEntry> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("item_id").forGetter(TradePoolEntry::itemId),
            Codec.INT.fieldOf("input_amount").forGetter(TradePoolEntry::inputAmount),
            Codec.INT.fieldOf("output_amount").forGetter(TradePoolEntry::outputAmount),
            Codec.INT.optionalFieldOf("weight", 1).forGetter(TradePoolEntry::weight),
            Codec.DOUBLE.optionalFieldOf("input_variance").forGetter(TradePoolEntry::inputVariance),
            Codec.DOUBLE.optionalFieldOf("output_variance").forGetter(TradePoolEntry::outputVariance),
            Codec.INT.optionalFieldOf("max_uses").forGetter(TradePoolEntry::maxUses),
            Codec.STRING.optionalFieldOf("display_name").forGetter(TradePoolEntry::displayName)
        ).apply(instance, TradePoolEntry::new)
    );

    /**
     * Calculates the effective input amount with variance applied.
     *
     * @param random The random source for variance calculation
     * @return The input amount with variance applied
     */
    public int getEffectiveInputAmount(java.util.Random random) {
        if (inputVariance.isEmpty() || inputVariance.get() <= 0) {
            return inputAmount;
        }
        double variance = inputVariance.get();
        double multiplier = 1.0 + (random.nextDouble() * 2 * variance - variance);
        return Math.max(1, (int) Math.round(inputAmount * multiplier));
    }

    /**
     * Calculates the effective output amount with variance applied.
     *
     * @param random The random source for variance calculation
     * @return The output amount with variance applied
     */
    public int getEffectiveOutputAmount(java.util.Random random) {
        if (outputVariance.isEmpty() || outputVariance.get() <= 0) {
            return outputAmount;
        }
        double variance = outputVariance.get();
        double multiplier = 1.0 + (random.nextDouble() * 2 * variance - variance);
        return Math.max(1, (int) Math.round(outputAmount * multiplier));
    }

    /**
     * Gets the max uses for this trade entry.
     *
     * @return The max uses, or Integer.MAX_VALUE if not specified
     */
    public int getMaxUsesOrDefault() {
        return maxUses.orElse(Integer.MAX_VALUE);
    }
}
