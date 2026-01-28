package net.fit.cobblemonmerchants.merchant.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;

/**
 * Configuration for a daily rotating trade slot in a merchant.
 * This defines a trade slot that will randomly select an item from a pool each day.
 *
 * @param poolId The resource location of the trade pool to select from
 *               (e.g., "cobblemoncustommerchants:rare_items")
 * @param position Optional fixed position in the trade GUI (0-26 for chest GUI)
 * @param slotId Unique identifier for this rotating slot within the merchant.
 *               Used to track which item was selected for today.
 *               Different merchants can share the same slotId to offer the same daily item.
 * @param variants Optional list of variant names this trade applies to.
 *                 If empty/not specified, trade applies to all variants.
 *                 If specified, trade only shows for merchants with matching variant.
 * @param tradeType The type of trade: "buy" (default - pool item is output, player pays),
 *                  "sell" (pool item is input, player receives payment), or
 *                  "free" (pool item is given for free)
 * @param customInput Optional custom input item ID (overrides pool's default input)
 * @param customOutput Optional custom output item ID (overrides pool item as output)
 * @param inputCount Optional custom input count (overrides pool entry's input_amount)
 * @param outputCount Optional custom output count (overrides pool entry's output_amount)
 */
public record DailyRotatingTradeConfig(
    String poolId,
    Optional<Integer> position,
    String slotId,
    Optional<List<String>> variants,
    Optional<String> tradeType,
    Optional<String> customInput,
    Optional<String> customOutput,
    Optional<Integer> inputCount,
    Optional<Integer> outputCount
) {
    /**
     * Trade type constants
     */
    public static final String TYPE_BUY = "buy";    // Pool item is output, player pays input
    public static final String TYPE_SELL = "sell";  // Pool item is input, player receives output
    public static final String TYPE_FREE = "free";  // Pool item is given for free

    public static final Codec<DailyRotatingTradeConfig> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("pool").forGetter(DailyRotatingTradeConfig::poolId),
            Codec.INT.optionalFieldOf("position").forGetter(DailyRotatingTradeConfig::position),
            Codec.STRING.fieldOf("slot_id").forGetter(DailyRotatingTradeConfig::slotId),
            Codec.STRING.listOf().optionalFieldOf("variants").forGetter(DailyRotatingTradeConfig::variants),
            Codec.STRING.optionalFieldOf("trade_type").forGetter(DailyRotatingTradeConfig::tradeType),
            Codec.STRING.optionalFieldOf("custom_input").forGetter(DailyRotatingTradeConfig::customInput),
            Codec.STRING.optionalFieldOf("custom_output").forGetter(DailyRotatingTradeConfig::customOutput),
            Codec.INT.optionalFieldOf("input_count").forGetter(DailyRotatingTradeConfig::inputCount),
            Codec.INT.optionalFieldOf("output_count").forGetter(DailyRotatingTradeConfig::outputCount)
        ).apply(instance, DailyRotatingTradeConfig::new)
    );

    /**
     * Gets the trade pool for this rotating trade.
     *
     * @return The trade pool, or null if not found
     */
    public TradePool getPool() {
        return TradePoolRegistry.getPool(poolId);
    }

    /**
     * Checks if this daily rotating trade applies to the given variant.
     * If no variants are specified, the trade applies to all variants.
     *
     * @param merchantVariant The variant of the merchant (e.g., "default", "housed")
     * @return true if this trade should be shown for the given variant
     */
    public boolean appliesToVariant(String merchantVariant) {
        if (variants.isEmpty() || variants.get().isEmpty()) {
            return true; // No variant restriction - applies to all
        }
        String variantToCheck = merchantVariant != null ? merchantVariant : "default";
        return variants.get().contains(variantToCheck);
    }

    /**
     * Gets the effective trade type, defaulting to "buy" if not specified.
     *
     * @return The trade type: "buy", "sell", or "free"
     */
    public String getEffectiveTradeType() {
        return tradeType.orElse(TYPE_BUY);
    }

    /**
     * Checks if this is a free trade (no input required).
     *
     * @return true if trade_type is "free"
     */
    public boolean isFree() {
        return TYPE_FREE.equals(getEffectiveTradeType());
    }

    /**
     * Checks if the pool item should be the output (player buys it).
     * This is the default behavior.
     *
     * @return true if trade_type is "buy" or not specified
     */
    public boolean isPoolItemOutput() {
        String type = getEffectiveTradeType();
        return TYPE_BUY.equals(type) || TYPE_FREE.equals(type);
    }

    /**
     * Checks if the pool item should be the input (player sells it).
     *
     * @return true if trade_type is "sell"
     */
    public boolean isPoolItemInput() {
        return TYPE_SELL.equals(getEffectiveTradeType());
    }
}
