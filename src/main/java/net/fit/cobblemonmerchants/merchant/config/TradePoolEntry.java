package net.fit.cobblemonmerchants.merchant.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Represents a single entry in a trade pool.
 * Each entry defines an item that can be randomly selected for a daily rotating trade slot.
 *
 * Supports two modes:
 * - Specific item: Use "item_id" field (e.g., "minecraft:diamond")
 * - Random from tag: Use "tag" field (e.g., "c:fishes") - picks a random item from the tag each day
 *
 * Amount configuration:
 * - For fixed amounts: use input_amount and output_amount
 * - For random ranges: use input_min_amount/input_max_amount and output_min_amount/output_max_amount
 *   (min/max take priority over fixed amounts if specified)
 *
 * @param itemId Optional item ID (e.g., "minecraft:diamond", "cobblemon:rare_candy")
 * @param tag Optional item tag (e.g., "c:fishes", "minecraft:flowers") - mutually exclusive with itemId
 * @param inputAmount The base cost in the input currency (used if min/max not specified)
 * @param outputAmount The base output count (used if min/max not specified)
 * @param weight The selection weight (higher = more likely to be selected)
 * @param inputVariance Optional variance for input cost (e.g., 0.2 means ±20%) - legacy, prefer min/max
 * @param outputVariance Optional variance for output count (e.g., 0.3 means ±30%) - legacy, prefer min/max
 * @param inputMinAmount Optional minimum input amount (inclusive)
 * @param inputMaxAmount Optional maximum input amount (inclusive)
 * @param outputMinAmount Optional minimum output amount (inclusive)
 * @param outputMaxAmount Optional maximum output amount (inclusive)
 * @param maxUses Optional max uses per day (default unlimited)
 * @param displayName Optional custom display name for the trade
 */
public record TradePoolEntry(
    Optional<String> itemId,
    Optional<String> tag,
    int inputAmount,
    int outputAmount,
    int weight,
    Optional<Double> inputVariance,
    Optional<Double> outputVariance,
    Optional<Integer> inputMinAmount,
    Optional<Integer> inputMaxAmount,
    Optional<Integer> outputMinAmount,
    Optional<Integer> outputMaxAmount,
    Optional<Integer> maxUses,
    Optional<String> displayName
) {
    public static final Codec<TradePoolEntry> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.optionalFieldOf("item_id").forGetter(TradePoolEntry::itemId),
            Codec.STRING.optionalFieldOf("tag").forGetter(TradePoolEntry::tag),
            Codec.INT.optionalFieldOf("input_amount", 1).forGetter(TradePoolEntry::inputAmount),
            Codec.INT.optionalFieldOf("output_amount", 1).forGetter(TradePoolEntry::outputAmount),
            Codec.INT.optionalFieldOf("weight", 1).forGetter(TradePoolEntry::weight),
            Codec.DOUBLE.optionalFieldOf("input_variance").forGetter(TradePoolEntry::inputVariance),
            Codec.DOUBLE.optionalFieldOf("output_variance").forGetter(TradePoolEntry::outputVariance),
            Codec.INT.optionalFieldOf("input_min_amount").forGetter(TradePoolEntry::inputMinAmount),
            Codec.INT.optionalFieldOf("input_max_amount").forGetter(TradePoolEntry::inputMaxAmount),
            Codec.INT.optionalFieldOf("output_min_amount").forGetter(TradePoolEntry::outputMinAmount),
            Codec.INT.optionalFieldOf("output_max_amount").forGetter(TradePoolEntry::outputMaxAmount),
            Codec.INT.optionalFieldOf("max_uses").forGetter(TradePoolEntry::maxUses),
            Codec.STRING.optionalFieldOf("display_name").forGetter(TradePoolEntry::displayName)
        ).apply(instance, TradePoolEntry::new)
    );

    /**
     * Checks if this entry uses a tag instead of a specific item ID.
     */
    public boolean isTag() {
        return tag.isPresent();
    }

    /**
     * Gets the resolved item ID for this entry.
     * For specific items, returns the item ID directly.
     * For tags, returns a random item from the tag using the provided Random.
     *
     * @param random The random source for tag selection
     * @return The resolved item ID, or null if invalid/empty
     */
    public String getResolvedItemId(Random random) {
        if (itemId.isPresent()) {
            return itemId.get();
        }

        if (tag.isPresent()) {
            TagKey<Item> tagKey = TagKey.create(Registries.ITEM, ResourceLocation.parse(tag.get()));
            List<Item> tagItems = BuiltInRegistries.ITEM.stream()
                .filter(item -> item.builtInRegistryHolder().is(tagKey))
                .toList();

            if (tagItems.isEmpty()) {
                net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.warn(
                    "Tag '{}' is empty or doesn't exist, cannot resolve item", tag.get());
                return null;
            }

            Item selectedItem = tagItems.get(random.nextInt(tagItems.size()));
            return BuiltInRegistries.ITEM.getKey(selectedItem).toString();
        }

        return null;
    }

    /**
     * Gets a display identifier for logging purposes.
     * Returns the item ID or tag string.
     */
    public String getDisplayIdentifier() {
        if (itemId.isPresent()) {
            return itemId.get();
        }
        if (tag.isPresent()) {
            return "#" + tag.get();
        }
        return "unknown";
    }

    /**
     * Calculates the effective input amount.
     * If min/max are specified, picks a random value in that range.
     * Otherwise, applies variance if specified, or returns the base amount.
     *
     * @param random The random source for calculation
     * @return The effective input amount
     */
    public int getEffectiveInputAmount(Random random) {
        // Min/max range takes priority
        if (inputMinAmount.isPresent() && inputMaxAmount.isPresent()) {
            int min = inputMinAmount.get();
            int max = inputMaxAmount.get();
            if (min >= max) {
                return min;
            }
            return min + random.nextInt(max - min + 1);
        }

        // Fall back to variance-based calculation
        if (inputVariance.isEmpty() || inputVariance.get() <= 0) {
            return inputAmount;
        }
        double variance = inputVariance.get();
        double multiplier = 1.0 + (random.nextDouble() * 2 * variance - variance);
        return Math.max(1, (int) Math.round(inputAmount * multiplier));
    }

    /**
     * Calculates the effective output amount.
     * If min/max are specified, picks a random value in that range.
     * Otherwise, applies variance if specified, or returns the base amount.
     *
     * @param random The random source for calculation
     * @return The effective output amount
     */
    public int getEffectiveOutputAmount(Random random) {
        // Min/max range takes priority
        if (outputMinAmount.isPresent() && outputMaxAmount.isPresent()) {
            int min = outputMinAmount.get();
            int max = outputMaxAmount.get();
            if (min >= max) {
                return min;
            }
            return min + random.nextInt(max - min + 1);
        }

        // Fall back to variance-based calculation
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
