package net.fit.cobblemonmerchants.merchant.blackmarket;

import java.util.List;

/**
 * Stores drop data for a single item from Cobblemon Pokemon
 */
public class CobblemonDropData {
    private final String itemId;
    private final List<PokemonDropInfo> pokemonDrops;
    private final boolean isCobblemonExclusive;
    private final boolean isCraftable;
    private final boolean isGrowable;

    public CobblemonDropData(String itemId, List<PokemonDropInfo> pokemonDrops,
                            boolean isCobblemonExclusive, boolean isCraftable, boolean isGrowable) {
        this.itemId = itemId;
        this.pokemonDrops = pokemonDrops;
        this.isCobblemonExclusive = isCobblemonExclusive;
        this.isCraftable = isCraftable;
        this.isGrowable = isGrowable;
    }

    public String getItemId() {
        return itemId;
    }

    public List<PokemonDropInfo> getPokemonDrops() {
        return pokemonDrops;
    }

    public boolean isCobblemonExclusive() {
        return isCobblemonExclusive;
    }

    public boolean isCraftable() {
        return isCraftable;
    }

    public boolean isGrowable() {
        return isGrowable;
    }

    /**
     * Calculates the average drop chance across all Pokemon that drop this item
     * @return Average drop chance as a percentage (0-100)
     */
    public double getAverageDropChance() {
        if (pokemonDrops.isEmpty()) {
            return 0;
        }

        double sum = 0;
        for (PokemonDropInfo drop : pokemonDrops) {
            sum += drop.dropChance();
        }
        return sum / pokemonDrops.size();
    }

    /**
     * Calculates the average drop quantity for 100% drop chance items
     * @return Average quantity, minimum 1
     */
    public double getAverageDropQuantity() {
        if (pokemonDrops.isEmpty()) {
            return 1;
        }

        double sum = 0;
        for (PokemonDropInfo drop : pokemonDrops) {
            sum += drop.medianQuantity();
        }
        double average = sum / pokemonDrops.size();
        return Math.max(1.0, average);
    }

    /**
     * Information about a specific Pokemon's drop
     * @param pokemonName Name of the Pokemon
     * @param dropChance Chance to drop (0-100)
     * @param medianQuantity Median quantity when dropped
     */
    public record PokemonDropInfo(String pokemonName, double dropChance, double medianQuantity) {}
}