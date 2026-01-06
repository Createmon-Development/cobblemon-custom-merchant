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
    Optional<BlackMarketConfigData> blackMarketConfig,
    Optional<DailyRewardConfig> dailyRewardConfig
) {
    public static final Codec<MerchantConfig> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("display_name").forGetter(MerchantConfig::displayName),
            Codec.STRING.optionalFieldOf("player_skin_name").forGetter(MerchantConfig::playerSkinName),
            Codec.STRING.optionalFieldOf("villager_biome").forGetter(MerchantConfig::villagerBiome),
            Codec.STRING.optionalFieldOf("villager_profession").forGetter(MerchantConfig::villagerProfession),
            TradeEntry.CODEC.listOf().fieldOf("trades").forGetter(MerchantConfig::trades),
            BlackMarketConfigData.CODEC.optionalFieldOf("black_market_config").forGetter(MerchantConfig::blackMarketConfig),
            DailyRewardConfig.CODEC.optionalFieldOf("daily_reward").forGetter(MerchantConfig::dailyRewardConfig)
        ).apply(instance, MerchantConfig::new)
    );

    /**
     * Converts this config into MerchantOffers (all trades, no variant filtering)
     */
    public MerchantOffers toMerchantOffers() {
        MerchantOffers offers = new MerchantOffers();
        for (int i = 0; i < trades.size(); i++) {
            TradeEntry trade = trades.get(i);
            try {
                MerchantOffer offer = trade.toMerchantOffer();
                if (offer != null) {
                    offers.add(offer);
                }
            } catch (Exception e) {
                net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.warn(
                    "Failed to create trade {} for merchant '{}': {}",
                    i, displayName, e.getMessage());
                // Add a broken trade placeholder
                offers.add(TradeEntry.createBrokenTradeOffer());
            }
        }
        return offers;
    }

    /**
     * Converts this config into MerchantOffers, applying variant overrides and filtering.
     *
     * @param variant The merchant variant (e.g., "default", "housed")
     * @return MerchantOffers with variant-specific values applied, only including trades for this variant
     */
    public MerchantOffers toMerchantOffersForVariant(String variant) {
        MerchantOffers offers = new MerchantOffers();
        for (int i = 0; i < trades.size(); i++) {
            TradeEntry trade = trades.get(i);
            if (trade.appliesToVariant(variant)) {
                try {
                    MerchantOffer offer = trade.toMerchantOfferForVariant(variant);
                    if (offer != null) {
                        offers.add(offer);
                    }
                } catch (Exception e) {
                    net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.warn(
                        "Failed to create trade {} for merchant '{}' (variant: {}): {}",
                        i, displayName, variant, e.getMessage());
                    // Add a broken trade placeholder
                    offers.add(TradeEntry.createBrokenTradeOffer());
                }
            }
        }
        return offers;
    }

    /**
     * Gets trade entries filtered by variant.
     *
     * @param variant The merchant variant (e.g., "default", "housed")
     * @return List of trade entries applicable to this variant
     */
    public List<TradeEntry> getTradesForVariant(String variant) {
        return trades.stream()
            .filter(trade -> trade.appliesToVariant(variant))
            .toList();
    }

    /**
     * Represents overrides for a specific variant's trade values.
     * Any field not specified uses the base trade value.
     */
    public record TradeVariantOverride(
        Optional<Integer> inputCount,
        Optional<Integer> secondInputCount,
        Optional<Integer> outputCount,
        Optional<Integer> maxUses
    ) {
        public static final Codec<TradeVariantOverride> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.INT.optionalFieldOf("input_count").forGetter(TradeVariantOverride::inputCount),
                Codec.INT.optionalFieldOf("second_input_count").forGetter(TradeVariantOverride::secondInputCount),
                Codec.INT.optionalFieldOf("output_count").forGetter(TradeVariantOverride::outputCount),
                Codec.INT.optionalFieldOf("max_uses").forGetter(TradeVariantOverride::maxUses)
            ).apply(instance, TradeVariantOverride::new)
        );
    }

    /**
     * Represents a single trade entry in the config
     *
     * @param variantOverrides Optional map of variant name -> overrides for that variant.
     *                         Allows different variants to have different prices/counts.
     * @param dailyReset If true, the trade's max_uses resets at midnight each real-world day.
     *                   If false (default), max_uses is permanent until server restart.
     * @param variants Optional list of variant names this trade applies to.
     *                 If empty/not specified, trade applies to all variants.
     *                 If specified, trade only shows for merchants with matching variant.
     * @param outputCount The raw output count from JSON (not capped by ItemStack limits).
     *                    This allows trades with output counts > 64.
     */
    public record TradeEntry(
        ItemRequirement input,
        Optional<ItemRequirement> secondInput,
        ItemStack output,
        int outputCount,
        int maxUses,
        int villagerXp,
        float priceMultiplier,
        Optional<String> tradeDisplayName,
        Optional<Integer> position,
        Optional<Map<String, TradeVariantOverride>> variantOverrides,
        boolean dailyReset,
        Optional<List<String>> variants
    ) {
        /**
         * A lenient ItemStack codec that falls back to a barrier item if parsing fails.
         * This allows merchants to load even if some items are from missing mods.
         * Also parses count separately to allow counts > 64.
         */
        private static final Codec<OutputWithCount> LENIENT_ITEMSTACK_WITH_COUNT_CODEC = Codec.PASSTHROUGH.comapFlatMap(
            dynamic -> {
                // Parse the raw count from JSON before ItemStack parsing caps it
                int rawCount = dynamic.get("count").flatMap(d -> Codec.INT.parse(d)).result().orElse(1);

                var result = ItemStack.CODEC.parse(dynamic);
                if (result.error().isPresent()) {
                    // Log the error but return a barrier as fallback
                    String errorMsg = result.error().get().message();
                    net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.warn(
                        "Failed to parse output ItemStack, using barrier as fallback: {}", errorMsg);
                    return com.mojang.serialization.DataResult.success(
                        new OutputWithCount(new ItemStack(net.minecraft.world.item.Items.BARRIER, 1), rawCount));
                }
                return result.map(stack -> new OutputWithCount(stack, rawCount));
            },
            outputWithCount -> ItemStack.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, outputWithCount.stack())
                .map(json -> new com.mojang.serialization.Dynamic<>(com.mojang.serialization.JsonOps.INSTANCE, json))
                .result()
                .orElseGet(() -> new com.mojang.serialization.Dynamic<>(
                    com.mojang.serialization.JsonOps.INSTANCE,
                    com.mojang.serialization.JsonOps.INSTANCE.empty()))
        );

        /**
         * Helper record to hold both ItemStack and uncapped count during parsing.
         */
        private record OutputWithCount(ItemStack stack, int count) {}

        public static final Codec<TradeEntry> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                ItemRequirement.CODEC.fieldOf("input").forGetter(TradeEntry::input),
                ItemRequirement.CODEC.optionalFieldOf("second_input").forGetter(TradeEntry::secondInput),
                LENIENT_ITEMSTACK_WITH_COUNT_CODEC.fieldOf("output").forGetter(e -> new OutputWithCount(e.output(), e.outputCount())),
                Codec.INT.optionalFieldOf("max_uses", Integer.MAX_VALUE).forGetter(TradeEntry::maxUses),
                Codec.INT.optionalFieldOf("villager_xp", 0).forGetter(TradeEntry::villagerXp),
                Codec.FLOAT.optionalFieldOf("price_multiplier", 0.0f).forGetter(TradeEntry::priceMultiplier),
                Codec.STRING.optionalFieldOf("trade_display_name").forGetter(TradeEntry::tradeDisplayName),
                Codec.INT.optionalFieldOf("position").forGetter(TradeEntry::position),
                Codec.unboundedMap(Codec.STRING, TradeVariantOverride.CODEC)
                    .optionalFieldOf("variant_overrides").forGetter(TradeEntry::variantOverrides),
                Codec.BOOL.optionalFieldOf("daily_reset", false).forGetter(TradeEntry::dailyReset),
                Codec.STRING.listOf().optionalFieldOf("variants").forGetter(TradeEntry::variants)
            ).apply(instance, (input, secondInput, outputWithCount, maxUses, villagerXp, priceMultiplier,
                               tradeDisplayName, position, variantOverrides, dailyReset, variants) ->
                new TradeEntry(input, secondInput, outputWithCount.stack(), outputWithCount.count(),
                    maxUses, villagerXp, priceMultiplier, tradeDisplayName, position,
                    variantOverrides, dailyReset, variants))
        );

        /**
         * Checks if this trade applies to the given variant.
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
         * Gets the override for a specific variant, or null if none exists.
         */
        public TradeVariantOverride getOverrideForVariant(String variant) {
            if (variantOverrides.isEmpty()) {
                return null;
            }
            return variantOverrides.get().get(variant != null ? variant : "default");
        }

        /**
         * Gets the effective input count for a variant (applies override if present).
         */
        public int getInputCountForVariant(String variant) {
            TradeVariantOverride override = getOverrideForVariant(variant);
            if (override != null && override.inputCount().isPresent()) {
                return override.inputCount().get();
            }
            return input.count();
        }

        /**
         * Gets the effective second input count for a variant (applies override if present).
         */
        public int getSecondInputCountForVariant(String variant) {
            if (secondInput.isEmpty()) {
                return 0;
            }
            TradeVariantOverride override = getOverrideForVariant(variant);
            if (override != null && override.secondInputCount().isPresent()) {
                return override.secondInputCount().get();
            }
            return secondInput.get().count();
        }

        /**
         * Gets the effective output count for a variant (applies override if present).
         * Uses the uncapped outputCount field to support counts > 64.
         */
        public int getOutputCountForVariant(String variant) {
            TradeVariantOverride override = getOverrideForVariant(variant);
            if (override != null && override.outputCount().isPresent()) {
                return override.outputCount().get();
            }
            return outputCount;
        }

        /**
         * Gets the effective max uses for a variant (applies override if present).
         */
        public int getMaxUsesForVariant(String variant) {
            TradeVariantOverride override = getOverrideForVariant(variant);
            if (override != null && override.maxUses().isPresent()) {
                return override.maxUses().get();
            }
            return maxUses;
        }

        /**
         * Converts this trade entry to a MerchantOffer for vanilla merchant GUI.
         * Note: For tag-based requirements, displays the first item from the tag.
         */
        public MerchantOffer toMerchantOffer() {
            return toMerchantOfferForVariant("default");
        }

        /**
         * Converts this trade entry to a MerchantOffer with variant-specific values applied.
         *
         * @param variant The merchant variant (e.g., "default", "housed")
         * @return MerchantOffer with variant overrides applied, or null if the trade is invalid
         */
        public MerchantOffer toMerchantOfferForVariant(String variant) {
            // Get variant-specific values
            int effectiveInputCount = getInputCountForVariant(variant);
            int effectiveOutputCount = getOutputCountForVariant(variant);
            int effectiveMaxUses = getMaxUsesForVariant(variant);

            // Check if output is valid (not empty/air)
            if (output.isEmpty()) {
                net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.warn(
                    "Trade has empty output item - trade will be shown as broken");
                return createBrokenTradeOffer();
            }

            // Create input cost with potentially modified count
            ItemCost inputCost = input.toItemCostWithCount(effectiveInputCount);

            // Check if input cost is a barrier (indicates broken input)
            if (inputCost.itemStack().getItem() == net.minecraft.world.item.Items.BARRIER) {
                net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.warn(
                    "Trade has invalid input item (missing mod or empty tag) - trade will be shown as broken");
                return createBrokenTradeOffer();
            }

            // Create output with potentially modified count
            ItemStack outputStack = output.copy();
            outputStack.setCount(effectiveOutputCount);

            if (secondInput.isPresent()) {
                // Two-item trade
                int effectiveSecondInputCount = getSecondInputCountForVariant(variant);
                ItemCost secondInputCost = secondInput.get().toItemCostWithCount(effectiveSecondInputCount);

                // Check if second input is a barrier (indicates broken input)
                if (secondInputCost.itemStack().getItem() == net.minecraft.world.item.Items.BARRIER) {
                    net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.warn(
                        "Trade has invalid second input item - trade will be shown as broken");
                    return createBrokenTradeOffer();
                }

                return new MerchantOffer(
                    inputCost,
                    Optional.of(secondInputCost),
                    outputStack,
                    effectiveMaxUses,
                    villagerXp,
                    priceMultiplier
                );
            } else {
                // Single-item trade
                return new MerchantOffer(
                    inputCost,
                    outputStack,
                    effectiveMaxUses,
                    villagerXp,
                    priceMultiplier
                );
            }
        }

        /**
         * Creates a placeholder offer for broken trades that displays as a barrier block
         */
        public static MerchantOffer createBrokenTradeOffer() {
            return new MerchantOffer(
                new net.minecraft.world.item.trading.ItemCost(net.minecraft.world.item.Items.BARRIER, 1),
                new ItemStack(net.minecraft.world.item.Items.BARRIER, 1),
                0, // maxUses = 0 means always out of stock
                0, // no XP
                0.0f // no price multiplier
            );
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

    /**
     * Daily reward configuration for merchants that give free items once per day.
     * Supports variants where different spawned versions of the same merchant
     * can give different rewards.
     *
     * @param sharedCooldown If true (default), all merchants of this type share a single cooldown -
     *                       claiming from any merchant puts ALL merchants of this type on cooldown.
     *                       If false, each individual merchant entity has its own cooldown -
     *                       you can claim from multiple different merchant entities of the same type.
     *                       Note: All variants (default, housed, etc.) always share the same cooldown.
     */
    public record DailyRewardConfig(
        Map<String, DailyRewardVariant> variants,
        boolean sharedCooldown
    ) {
        public static final Codec<DailyRewardConfig> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.unboundedMap(Codec.STRING, DailyRewardVariant.CODEC)
                    .fieldOf("variants")
                    .forGetter(DailyRewardConfig::variants),
                Codec.BOOL.optionalFieldOf("shared_cooldown", true)
                    .forGetter(DailyRewardConfig::sharedCooldown)
            ).apply(instance, DailyRewardConfig::new)
        );

        /**
         * Get the reward variant config, or null if not found
         */
        public DailyRewardVariant getVariant(String variantName) {
            return variants.get(variantName != null ? variantName : "default");
        }
    }

    /**
     * A specific variant of daily reward (e.g., "default" vs "housed")
     */
    public record DailyRewardVariant(
        ItemStack item,
        int minCount,
        int maxCount,
        Optional<String> message,
        Optional<Integer> displayPosition
    ) {
        public static final Codec<DailyRewardVariant> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                ItemStack.CODEC.fieldOf("item").forGetter(DailyRewardVariant::item),
                Codec.INT.optionalFieldOf("min_count", 1).forGetter(DailyRewardVariant::minCount),
                Codec.INT.optionalFieldOf("max_count", 1).forGetter(DailyRewardVariant::maxCount),
                Codec.STRING.optionalFieldOf("message").forGetter(DailyRewardVariant::message),
                Codec.INT.optionalFieldOf("display_position").forGetter(DailyRewardVariant::displayPosition)
            ).apply(instance, DailyRewardVariant::new)
        );

        /**
         * Get a random count between min and max (inclusive)
         */
        public int getRandomCount(java.util.Random random) {
            if (minCount >= maxCount) {
                return minCount;
            }
            return minCount + random.nextInt(maxCount - minCount + 1);
        }
    }
}
