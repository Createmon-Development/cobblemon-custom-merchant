package net.fit.cobblemonmerchants.merchant.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Represents a pool of trade entries that can be randomly selected for daily rotating trades.
 * Pools are loaded from data/<namespace>/pools/<pool_name>.json
 *
 * Supports two formats:
 * 1. Simple format with root-level entries:
 *    {"entries": [...], "input_item": "..."}
 *
 * 2. Named sub-pools format with tables:
 *    {"tables": {"bakery": [...], "drinks": [...]}, "input_item": "..."}
 *    Reference as "namespace:pool_name.table_name" (e.g., "cobblemoncustommerchants:baker.bakery")
 *
 * Trade direction:
 * - Buy pools (default): Player pays input_item, receives pool entry items
 * - Sell pools: Player gives pool entry items, receives output_item
 *   Use "output_item" field to create a sell pool
 *
 * @param entries The list of possible trade entries in this pool (for simple format)
 * @param tables Optional map of named sub-pools (for named tables format)
 * @param inputItem The input item ID for "buy" trades (what player pays, e.g., "cobblemon:relic_coin")
 * @param outputItem Optional output item ID for "sell" trades (what player receives when selling pool items)
 * @param defaultWeight Default weight for entries that don't specify their own weight (default 1)
 * @param description Optional description of this pool for documentation
 */
public record TradePool(
    List<TradePoolEntry> entries,
    Optional<Map<String, List<TradePoolEntry>>> tables,
    String inputItem,
    Optional<String> outputItem,
    int defaultWeight,
    Optional<String> description
) {
    public static final Codec<TradePool> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            TradePoolEntry.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(TradePool::entries),
            Codec.unboundedMap(Codec.STRING, TradePoolEntry.CODEC.listOf()).optionalFieldOf("tables").forGetter(TradePool::tables),
            Codec.STRING.optionalFieldOf("input_item", "cobblemon:relic_coin").forGetter(TradePool::inputItem),
            Codec.STRING.optionalFieldOf("output_item").forGetter(TradePool::outputItem),
            Codec.INT.optionalFieldOf("default_weight", 1).forGetter(TradePool::defaultWeight),
            Codec.STRING.optionalFieldOf("description").forGetter(TradePool::description)
        ).apply(instance, TradePool::new)
    );

    /**
     * Creates a simple TradePool without tables (for backwards compatibility).
     */
    public TradePool(List<TradePoolEntry> entries, String inputItem, Optional<String> description) {
        this(entries, Optional.empty(), inputItem, Optional.empty(), 1, description);
    }

    /**
     * Checks if this pool is a "sell" pool (player sells items for output_item).
     * A pool is considered a sell pool if it has an output_item defined.
     *
     * @return true if this is a sell pool
     */
    public boolean isSellPool() {
        return outputItem.isPresent();
    }

    /**
     * Gets a sub-pool by table name.
     * Returns a new TradePool containing only the entries from the specified table.
     * The sub-pool inherits inputItem, outputItem, defaultWeight, and description from the parent.
     *
     * @param tableName The name of the table to retrieve
     * @return A new TradePool with the table's entries, or null if table not found
     */
    public TradePool getSubPool(String tableName) {
        if (tables.isEmpty()) {
            return null;
        }
        List<TradePoolEntry> tableEntries = tables.get().get(tableName);
        if (tableEntries == null) {
            return null;
        }
        return new TradePool(tableEntries, Optional.empty(), inputItem, outputItem, defaultWeight, description);
    }

    /**
     * Checks if this pool has named tables.
     */
    public boolean hasTables() {
        return tables.isPresent() && !tables.get().isEmpty();
    }

    /**
     * Gets all table names in this pool.
     *
     * @return Set of table names, or empty set if no tables
     */
    public java.util.Set<String> getTableNames() {
        if (tables.isEmpty()) {
            return java.util.Set.of();
        }
        return tables.get().keySet();
    }

    /**
     * Calculates the total weight of all entries in the pool.
     * Uses each entry's weight if specified, otherwise uses the pool's default_weight.
     *
     * @return The sum of all entry weights
     */
    public int getTotalWeight() {
        return entries.stream().mapToInt(e -> e.getEffectiveWeight(defaultWeight)).sum();
    }

    /**
     * Selects a random entry from the pool based on weights.
     * Uses each entry's weight if specified, otherwise uses the pool's default_weight.
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
            currentWeight += entry.getEffectiveWeight(defaultWeight);
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
