package net.fit.cobblemonmerchants.merchant.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

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
 */
public record DailyRotatingTradeConfig(
    String poolId,
    Optional<Integer> position,
    String slotId
) {
    public static final Codec<DailyRotatingTradeConfig> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("pool").forGetter(DailyRotatingTradeConfig::poolId),
            Codec.INT.optionalFieldOf("position").forGetter(DailyRotatingTradeConfig::position),
            Codec.STRING.fieldOf("slot_id").forGetter(DailyRotatingTradeConfig::slotId)
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
}
