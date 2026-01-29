package net.fit.cobblemonmerchants.merchant.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Represents a pool of trade entries that can be randomly selected for daily rotating trades.
 * Pools are loaded from data/<namespace>/pools/<pool_name>.json
 *
 * @param entries The list of possible trade entries in this pool
 * @param inputItem The input item ID for all trades in this pool (e.g., "cobblemon:relic_coin")
 * @param description Optional description of this pool for documentation
 */
public record TradePool(
    List<TradePoolEntry> entries,
    String inputItem,
    Optional<String> description
) {
    public static final Codec<TradePool> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            TradePoolEntry.CODEC.listOf().fieldOf("entries").forGetter(TradePool::entries),
            Codec.STRING.optionalFieldOf("input_item", "cobblemon:relic_coin").forGetter(TradePool::inputItem),
            Codec.STRING.optionalFieldOf("description").forGetter(TradePool::description)
        ).apply(instance, TradePool::new)
    );

    /**
     * Calculates the total weight of all entries in the pool.
     *
     * @return The sum of all entry weights
     */
    public int getTotalWeight() {
        return entries.stream().mapToInt(TradePoolEntry::weight).sum();
    }

    /**
     * Selects a random entry from the pool based on weights.
     *
     * @param random The random source for selection
     * @return The selected entry, or null if the pool is empty
     */
    public TradePoolEntry selectRandomEntry(Random random) {
        if (entries.isEmpty()) {
            return null;
        }

        int totalWeight = getTotalWeight();
        if (totalWeight <= 0) {
            // If all weights are 0 or negative, select uniformly
            return entries.get(random.nextInt(entries.size()));
        }

        int roll = random.nextInt(totalWeight);
        int currentWeight = 0;

        for (TradePoolEntry entry : entries) {
            currentWeight += entry.weight();
            if (roll < currentWeight) {
                return entry;
            }
        }

        // Fallback (shouldn't happen)
        return entries.get(entries.size() - 1);
    }

    /**
     * Selects a random entry using a seed for deterministic selection.
     * This is useful for daily rotation where the same seed should produce the same result.
     *
     * @param seed The seed for deterministic selection
     * @return The selected entry, or null if the pool is empty
     */
    public TradePoolEntry selectEntryWithSeed(long seed) {
        return selectRandomEntry(new Random(seed));
    }
}
