package net.fit.cobblemonmerchants.merchant.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration for a merchant loaded from datapacks.
 * Defines the merchant's display name, appearance, and trades.
 */
public record MerchantConfig(
    String displayName,
    Optional<String> playerSkinName,
    Optional<String> villagerBiome,
    Optional<String> villagerProfession,
    List<TradeEntry> trades,
    Optional<BlackMarketConfigData> blackMarketConfig
) {
    public static final Codec<MerchantConfig> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("display_name").forGetter(MerchantConfig::displayName),
            Codec.STRING.optionalFieldOf("player_skin_name").forGetter(MerchantConfig::playerSkinName),
            Codec.STRING.optionalFieldOf("villager_biome").forGetter(MerchantConfig::villagerBiome),
            Codec.STRING.optionalFieldOf("villager_profession").forGetter(MerchantConfig::villagerProfession),
            TradeEntry.CODEC.listOf().fieldOf("trades").forGetter(MerchantConfig::trades),
            BlackMarketConfigData.CODEC.optionalFieldOf("black_market_config").forGetter(MerchantConfig::blackMarketConfig)
        ).apply(instance, MerchantConfig::new)
    );

    /**
     * Converts this config into MerchantOffers
     */
    public MerchantOffers toMerchantOffers() {
        MerchantOffers offers = new MerchantOffers();
        for (TradeEntry trade : trades) {
            offers.add(trade.toMerchantOffer());
        }
        return offers;
    }

    /**
     * Represents a single trade entry in the config
     */
    public record TradeEntry(
        ItemRequirement input,
        Optional<ItemRequirement> secondInput,
        ItemStack output,
        int maxUses,
        int villagerXp,
        float priceMultiplier,
        Optional<String> tradeDisplayName,
        Optional<Integer> position
    ) {
        public static final Codec<TradeEntry> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                ItemRequirement.CODEC.fieldOf("input").forGetter(TradeEntry::input),
                ItemRequirement.CODEC.optionalFieldOf("second_input").forGetter(TradeEntry::secondInput),
                ItemStack.CODEC.fieldOf("output").forGetter(TradeEntry::output),
                Codec.INT.optionalFieldOf("max_uses", Integer.MAX_VALUE).forGetter(TradeEntry::maxUses),
                Codec.INT.optionalFieldOf("villager_xp", 0).forGetter(TradeEntry::villagerXp),
                Codec.FLOAT.optionalFieldOf("price_multiplier", 0.0f).forGetter(TradeEntry::priceMultiplier),
                Codec.STRING.optionalFieldOf("trade_display_name").forGetter(TradeEntry::tradeDisplayName),
                Codec.INT.optionalFieldOf("position").forGetter(TradeEntry::position)
            ).apply(instance, TradeEntry::new)
        );

        /**
         * Converts this trade entry to a MerchantOffer for vanilla merchant GUI.
         * Note: For tag-based requirements, displays the first item from the tag.
         */
        public MerchantOffer toMerchantOffer() {
            ItemCost inputCost = input.toItemCost();

            if (secondInput.isPresent()) {
                // Two-item trade
                ItemCost secondInputCost = secondInput.get().toItemCost();
                return new MerchantOffer(
                    inputCost,
                    Optional.of(secondInputCost),
                    output,
                    maxUses,
                    villagerXp,
                    priceMultiplier
                );
            } else {
                // Single-item trade
                return new MerchantOffer(
                    inputCost,
                    output,
                    maxUses,
                    villagerXp,
                    priceMultiplier
                );
            }
        }
    }

    /**
     * Black Market configuration data loaded from JSON
     * Simplified to stay within codec parameter limits
     */
    public record BlackMarketConfigData(
        int rotationDays,
        int minecraftItemsCount,
        int cobblemonItemsCount,
        double globalPriceMultiplier,
        double cobblemonExclusiveMultiplier,
        double heldItemMultiplier,
        TradeUsesConfig tradeUsesConfig,
        VariabilityConfig variabilityConfig,
        Map<String, Double> gameplayModifiers,
        LuckyTradeConfig luckyTradeConfig,
        LowCostTradeConfig lowCostTradeConfig,
        ValueCappingConfig valueCappingConfig,
        List<String> excludedMods
    ) {
        public static final Codec<BlackMarketConfigData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.INT.optionalFieldOf("rotation_days", 14).forGetter(BlackMarketConfigData::rotationDays),
                Codec.INT.optionalFieldOf("minecraft_items_count", 3).forGetter(BlackMarketConfigData::minecraftItemsCount),
                Codec.INT.optionalFieldOf("cobblemon_items_count", 1).forGetter(BlackMarketConfigData::cobblemonItemsCount),
                Codec.DOUBLE.optionalFieldOf("global_price_multiplier", 1.0).forGetter(BlackMarketConfigData::globalPriceMultiplier),
                Codec.DOUBLE.optionalFieldOf("cobblemon_exclusive_multiplier", 1.5).forGetter(BlackMarketConfigData::cobblemonExclusiveMultiplier),
                Codec.DOUBLE.optionalFieldOf("held_item_multiplier", 1.3).forGetter(BlackMarketConfigData::heldItemMultiplier),
                TradeUsesConfig.CODEC.optionalFieldOf("trade_uses_config", new TradeUsesConfig(1, 3, 10, 10.0)).forGetter(BlackMarketConfigData::tradeUsesConfig),
                VariabilityConfig.CODEC.optionalFieldOf("variability_config", new VariabilityConfig(0.6, 1.2, 0.7, 1.3, 0.7, 1.3)).forGetter(BlackMarketConfigData::variabilityConfig),
                Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("gameplay_modifiers", Map.of()).forGetter(BlackMarketConfigData::gameplayModifiers),
                LuckyTradeConfig.CODEC.optionalFieldOf("lucky_trade_config", new LuckyTradeConfig(true, 0.01, 1.5, 2.0)).forGetter(BlackMarketConfigData::luckyTradeConfig),
                LowCostTradeConfig.CODEC.optionalFieldOf("low_cost_trade_config", new LowCostTradeConfig(3, 1, 5)).forGetter(BlackMarketConfigData::lowCostTradeConfig),
                ValueCappingConfig.CODEC.optionalFieldOf("value_capping_config", new ValueCappingConfig(true, 0.5, true, 10)).forGetter(BlackMarketConfigData::valueCappingConfig),
                Codec.STRING.listOf().optionalFieldOf("excluded_mods", List.of()).forGetter(BlackMarketConfigData::excludedMods)
            ).apply(instance, BlackMarketConfigData::new)
        );

        // Helper methods for backward compatibility
        public int minTradeUses() { return tradeUsesConfig.min(); }
        public int baseTradeUses() { return tradeUsesConfig.base(); }
        public int maxTradeUses() { return tradeUsesConfig.max(); }
        public double tradeUseThreshold() { return tradeUsesConfig.threshold(); }
        public double minPriceVariability() { return variabilityConfig.minPrice(); }
        public double maxPriceVariability() { return variabilityConfig.maxPrice(); }
        public double minCountVariability() { return variabilityConfig.minCount(); }
        public double maxCountVariability() { return variabilityConfig.maxCount(); }
        public double minTradeUseVariability() { return variabilityConfig.minTradeUse(); }
        public double maxTradeUseVariability() { return variabilityConfig.maxTradeUse(); }
        public boolean luckyTradesEnabled() { return luckyTradeConfig.enabled(); }
        public double luckyTradeChance() { return luckyTradeConfig.chance(); }
        public double luckyTradePriceMultiplier() { return luckyTradeConfig.priceMultiplier(); }
        public double luckyTradeUsesMultiplier() { return luckyTradeConfig.tradeUsesMultiplier(); }
        public int lowCostMultiItemThreshold() { return lowCostTradeConfig.multiItemThreshold(); }
        public int minSingleRcPrice() { return lowCostTradeConfig.minPrice(); }
        public int maxSingleRcPrice() { return lowCostTradeConfig.maxPrice(); }
        public boolean outlierDetectionEnabled() { return valueCappingConfig.outlierDetectionEnabled(); }
        public double outlierPenaltyMultiplier() { return valueCappingConfig.outlierPenalty(); }
        public boolean craftingRecipeCheckEnabled() { return valueCappingConfig.craftingCheckEnabled(); }
        public int craftableItemMaxValue() { return valueCappingConfig.craftableMaxValue(); }
    }

    /**
     * Trade uses configuration sub-group
     */
    public record TradeUsesConfig(int min, int base, int max, double threshold) {
        public static final Codec<TradeUsesConfig> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.INT.fieldOf("min").forGetter(TradeUsesConfig::min),
                Codec.INT.fieldOf("base").forGetter(TradeUsesConfig::base),
                Codec.INT.fieldOf("max").forGetter(TradeUsesConfig::max),
                Codec.DOUBLE.fieldOf("threshold").forGetter(TradeUsesConfig::threshold)
            ).apply(instance, TradeUsesConfig::new)
        );
    }

    /**
     * Variability configuration sub-group
     */
    public record VariabilityConfig(
        double minPrice,
        double maxPrice,
        double minCount,
        double maxCount,
        double minTradeUse,
        double maxTradeUse
    ) {
        public static final Codec<VariabilityConfig> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.DOUBLE.fieldOf("min_price").forGetter(VariabilityConfig::minPrice),
                Codec.DOUBLE.fieldOf("max_price").forGetter(VariabilityConfig::maxPrice),
                Codec.DOUBLE.fieldOf("min_count").forGetter(VariabilityConfig::minCount),
                Codec.DOUBLE.fieldOf("max_count").forGetter(VariabilityConfig::maxCount),
                Codec.DOUBLE.fieldOf("min_trade_use").forGetter(VariabilityConfig::minTradeUse),
                Codec.DOUBLE.fieldOf("max_trade_use").forGetter(VariabilityConfig::maxTradeUse)
            ).apply(instance, VariabilityConfig::new)
        );
    }

    /**
     * Lucky trade configuration sub-group
     */
    public record LuckyTradeConfig(
        boolean enabled,
        double chance,
        double priceMultiplier,
        double tradeUsesMultiplier
    ) {
        public static final Codec<LuckyTradeConfig> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.BOOL.fieldOf("enabled").forGetter(LuckyTradeConfig::enabled),
                Codec.DOUBLE.fieldOf("chance").forGetter(LuckyTradeConfig::chance),
                Codec.DOUBLE.fieldOf("price_multiplier").forGetter(LuckyTradeConfig::priceMultiplier),
                Codec.DOUBLE.fieldOf("trade_uses_multiplier").forGetter(LuckyTradeConfig::tradeUsesMultiplier)
            ).apply(instance, LuckyTradeConfig::new)
        );
    }

    /**
     * Low cost trade configuration sub-group
     */
    public record LowCostTradeConfig(
        int multiItemThreshold,
        int minPrice,
        int maxPrice
    ) {
        public static final Codec<LowCostTradeConfig> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.INT.fieldOf("multi_item_threshold").forGetter(LowCostTradeConfig::multiItemThreshold),
                Codec.INT.fieldOf("min_price").forGetter(LowCostTradeConfig::minPrice),
                Codec.INT.fieldOf("max_price").forGetter(LowCostTradeConfig::maxPrice)
            ).apply(instance, LowCostTradeConfig::new)
        );
    }

    /**
     * Value capping configuration sub-group
     */
    public record ValueCappingConfig(
        boolean outlierDetectionEnabled,
        double outlierPenalty,
        boolean craftingCheckEnabled,
        int craftableMaxValue
    ) {
        public static final Codec<ValueCappingConfig> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.BOOL.fieldOf("outlier_detection_enabled").forGetter(ValueCappingConfig::outlierDetectionEnabled),
                Codec.DOUBLE.fieldOf("outlier_penalty_multiplier").forGetter(ValueCappingConfig::outlierPenalty),
                Codec.BOOL.fieldOf("crafting_check_enabled").forGetter(ValueCappingConfig::craftingCheckEnabled),
                Codec.INT.fieldOf("craftable_max_value").forGetter(ValueCappingConfig::craftableMaxValue)
            ).apply(instance, ValueCappingConfig::new)
        );
    }
}
