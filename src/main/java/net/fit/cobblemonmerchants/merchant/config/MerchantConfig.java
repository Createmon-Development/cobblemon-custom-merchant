package net.fit.cobblemonmerchants.merchant.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    Optional<DailyRewardConfig> dailyRewardConfig,
    Optional<List<DailyRotatingTradeConfig>> dailyRotatingTrades,
    Optional<String> actionId,
    boolean actionBeforeTrade,
    Optional<Map<String, VariantBonusConfig>> variantBonuses,
    int resetTimerPosition, // Position for the reset timer clock display (default: 26 = bottom right)
    boolean syncRotatingTrades, // If true (default), all merchants of this type share the same rotating trade selections and lucky rolls.
                                // If false, each merchant entity has unique rotating trade picks and lucky rolls.
    boolean syncTrades // Per-player stock sync. If true (default), a player's trade usage is shared across all entities of this type.
                       // If false, each entity tracks usage independently per player.
) {
    public static final int DEFAULT_RESET_TIMER_POSITION = 26;

    public static final Codec<MerchantConfig> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("display_name").forGetter(MerchantConfig::displayName),
            Codec.STRING.optionalFieldOf("player_skin_name").forGetter(MerchantConfig::playerSkinName),
            Codec.STRING.optionalFieldOf("villager_biome").forGetter(MerchantConfig::villagerBiome),
            Codec.STRING.optionalFieldOf("villager_profession").forGetter(MerchantConfig::villagerProfession),
            TradeEntry.CODEC.listOf().optionalFieldOf("trades", List.of()).forGetter(MerchantConfig::trades),
            DailyRewardConfig.CODEC.optionalFieldOf("daily_reward").forGetter(MerchantConfig::dailyRewardConfig),
            DailyRotatingTradeConfig.CODEC.listOf().optionalFieldOf("daily_rotating_trades").forGetter(MerchantConfig::dailyRotatingTrades),
            Codec.STRING.optionalFieldOf("action_id").forGetter(MerchantConfig::actionId),
            Codec.BOOL.optionalFieldOf("action_before_trade", false).forGetter(MerchantConfig::actionBeforeTrade),
            Codec.unboundedMap(Codec.STRING, VariantBonusConfig.CODEC).optionalFieldOf("variant_bonuses").forGetter(MerchantConfig::variantBonuses),
            Codec.INT.optionalFieldOf("reset_timer_position", DEFAULT_RESET_TIMER_POSITION).forGetter(MerchantConfig::resetTimerPosition),
            Codec.BOOL.optionalFieldOf("sync_rotating_trades", true).forGetter(MerchantConfig::syncRotatingTrades),
            Codec.BOOL.optionalFieldOf("sync_trades", true).forGetter(MerchantConfig::syncTrades)
        ).apply(instance, MerchantConfig::new)
    );

    /**
     * Gets the variant bonus config for a specific variant.
     *
     * @param variant The variant name (e.g., "housed")
     * @return The bonus config, or null if none configured
     */
    public VariantBonusConfig getVariantBonus(String variant) {
        if (variantBonuses.isEmpty()) {
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info(
                "getVariantBonus: variantBonuses is empty for merchant '{}'", displayName);
            return null;
        }
        if (variant == null) {
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info(
                "getVariantBonus: variant is null for merchant '{}'", displayName);
            return null;
        }
        VariantBonusConfig result = variantBonuses.get().get(variant);
        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info(
            "getVariantBonus: merchant='{}', variant='{}', availableKeys={}, found={}",
            displayName, variant, variantBonuses.get().keySet(), result != null);
        return result;
    }

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
            .map(trade -> trade.withVariantApplied(variant))
            .toList();
    }

    /**
     * Gets all available variant names for this merchant.
     * Collects variants from trades (variant_overrides, variants),
     * daily rewards, and daily rotating trades.
     * Always includes "default".
     */
    public Set<String> getAvailableVariants() {
        Set<String> available = new HashSet<>();
        available.add("default");

        // Collect from trade variant_overrides and variants
        for (TradeEntry trade : trades) {
            if (trade.variantOverrides().isPresent()) {
                available.addAll(trade.variantOverrides().get().keySet());
            }
            if (trade.variants().isPresent()) {
                available.addAll(trade.variants().get());
            }
        }

        // Collect from daily reward variants
        if (dailyRewardConfig.isPresent()) {
            available.addAll(dailyRewardConfig.get().variants().keySet());
        }

        // Collect from daily rotating trades
        if (dailyRotatingTrades.isPresent()) {
            for (DailyRotatingTradeConfig rotatingTrade : dailyRotatingTrades.get()) {
                if (rotatingTrade.variants().isPresent()) {
                    available.addAll(rotatingTrade.variants().get());
                }
            }
        }

        return available;
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
     * @param slotId Optional slot ID for daily rotating trades. Used to track usage independently
     *               of trade index so that refreshing daily rotating trades resets usage properly.
     * @param isLucky Whether this trade is a "lucky" trade with enhanced rewards (client display only).
     * @param luckyOutputMultiplier The output multiplier applied to lucky trades (for tooltip display).
     * @param luckyMaxUsesMultiplier The max uses multiplier applied to lucky trades (for tooltip display).
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
        Optional<List<String>> variants,
        Optional<String> slotId,
        boolean isLucky,
        double luckyOutputMultiplier,
        double luckyMaxUsesMultiplier
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
                Codec.STRING.listOf().optionalFieldOf("variants").forGetter(TradeEntry::variants),
                Codec.STRING.optionalFieldOf("slot_id").forGetter(TradeEntry::slotId),
                // Lucky trade fields are not parsed from JSON - they're set programmatically for daily rotating trades
                Codec.BOOL.optionalFieldOf("is_lucky", false).forGetter(TradeEntry::isLucky),
                Codec.DOUBLE.optionalFieldOf("lucky_output_multiplier", 1.0).forGetter(TradeEntry::luckyOutputMultiplier),
                Codec.DOUBLE.optionalFieldOf("lucky_max_uses_multiplier", 1.0).forGetter(TradeEntry::luckyMaxUsesMultiplier)
            ).apply(instance, (input, secondInput, outputWithCount, maxUses, villagerXp, priceMultiplier,
                               tradeDisplayName, position, variantOverrides, dailyReset, variants, slotId,
                               isLucky, luckyOutputMultiplier, luckyMaxUsesMultiplier) ->
                new TradeEntry(input, secondInput, outputWithCount.stack(), outputWithCount.count(),
                    maxUses, villagerXp, priceMultiplier, tradeDisplayName, position,
                    variantOverrides, dailyReset, variants, slotId, isLucky, luckyOutputMultiplier, luckyMaxUsesMultiplier))
        );

        /**
         * Returns a new TradeEntry with variant overrides baked into the base values.
         * This ensures that input counts, output counts, and max uses reflect the
         * variant-specific values. Used so that trade entries sent to the client and
         * used for server-side validation have the correct effective values.
         *
         * @param variant The merchant variant (e.g., "default", "housed")
         * @return A new TradeEntry with overrides applied, or this entry if no overrides exist
         */
        public TradeEntry withVariantApplied(String variant) {
            TradeVariantOverride override = getOverrideForVariant(variant);
            if (override == null) {
                return this;
            }

            int effectiveOutputCount = override.outputCount().orElse(this.outputCount);
            int effectiveMaxUses = override.maxUses().orElse(this.maxUses);

            ItemRequirement effectiveInput = override.inputCount().isPresent()
                ? input.withCount(override.inputCount().get()) : input;
            Optional<ItemRequirement> effectiveSecondInput = override.secondInputCount().isPresent() && secondInput.isPresent()
                ? Optional.of(secondInput.get().withCount(override.secondInputCount().get())) : secondInput;

            // Create output stack with effective count
            ItemStack effectiveOutput = output.copy();
            effectiveOutput.setCount(Math.min(effectiveOutputCount, effectiveOutput.getMaxStackSize()));

            return new TradeEntry(effectiveInput, effectiveSecondInput, effectiveOutput, effectiveOutputCount,
                effectiveMaxUses, villagerXp, priceMultiplier, tradeDisplayName, position,
                variantOverrides, dailyReset, variants, slotId, isLucky, luckyOutputMultiplier, luckyMaxUsesMultiplier);
        }

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

    /**
     * Configuration for variant-specific bonuses that affect daily rotating trades.
     * Applied when a merchant has a specific variant (e.g., "housed").
     *
     * @param extraTrades Map of pool ID (or table reference) -> extra trades to add from that pool
     * @param outputMultiplier Multiplier for output amounts (1.0 = no change, 1.5 = 50% more)
     * @param luckyTradeConfig Configuration for "lucky" trades with enhanced rewards
     */
    public record VariantBonusConfig(
        Optional<Map<String, Integer>> extraTrades,
        double outputMultiplier,
        VariantLuckyTradeConfig luckyTradeConfig
    ) {
        public static final Codec<VariantBonusConfig> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("extra_trades").forGetter(VariantBonusConfig::extraTrades),
                Codec.DOUBLE.optionalFieldOf("output_multiplier", 1.0).forGetter(VariantBonusConfig::outputMultiplier),
                VariantLuckyTradeConfig.CODEC.optionalFieldOf("lucky_trades", VariantLuckyTradeConfig.DISABLED).forGetter(VariantBonusConfig::luckyTradeConfig)
            ).apply(instance, VariantBonusConfig::new)
        );

        /**
         * Gets the extra trade count for a specific pool.
         *
         * @param poolId The pool ID (with optional table reference, e.g., "mod:pool.table")
         * @return The number of extra trades, or 0 if none configured
         */
        public int getExtraTradesForPool(String poolId) {
            if (extraTrades.isEmpty()) {
                return 0;
            }
            return extraTrades.get().getOrDefault(poolId, 0);
        }

        /**
         * Applies the output multiplier to a base amount.
         *
         * @param baseAmount The base output amount
         * @return The multiplied amount (minimum 1)
         */
        public int applyOutputMultiplier(int baseAmount) {
            return Math.max(1, (int) Math.round(baseAmount * outputMultiplier));
        }
    }

    /**
     * Configuration for lucky trades within a variant bonus.
     * Lucky trades have a chance to spawn with enhanced rewards.
     *
     * @param chance Probability (0.0 to 1.0) that a trade will be "lucky"
     * @param maxUsesMultiplier Multiplier for max uses on lucky trades (1.0 = no change)
     * @param outputMultiplier Additional multiplier for output on lucky trades (stacks with variant bonus)
     */
    public record VariantLuckyTradeConfig(
        double chance,
        double maxUsesMultiplier,
        double outputMultiplier
    ) {
        public static final VariantLuckyTradeConfig DISABLED = new VariantLuckyTradeConfig(0.0, 1.0, 1.0);

        public static final Codec<VariantLuckyTradeConfig> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.DOUBLE.optionalFieldOf("chance", 0.0).forGetter(VariantLuckyTradeConfig::chance),
                Codec.DOUBLE.optionalFieldOf("max_uses_multiplier", 1.0).forGetter(VariantLuckyTradeConfig::maxUsesMultiplier),
                Codec.DOUBLE.optionalFieldOf("output_multiplier", 1.0).forGetter(VariantLuckyTradeConfig::outputMultiplier)
            ).apply(instance, VariantLuckyTradeConfig::new)
        );

        /**
         * Checks if lucky trades are enabled (chance > 0).
         */
        public boolean isEnabled() {
            return chance > 0.0;
        }

        /**
         * Rolls to determine if a trade should be lucky.
         *
         * @param random The random source
         * @return true if this trade should be lucky
         */
        public boolean rollLucky(java.util.Random random) {
            return random.nextDouble() < chance;
        }

        /**
         * Applies the max uses multiplier.
         *
         * @param baseMaxUses The base max uses
         * @return The multiplied max uses (minimum 1)
         */
        public int applyMaxUsesMultiplier(int baseMaxUses) {
            if (baseMaxUses == Integer.MAX_VALUE) {
                return baseMaxUses; // Don't modify unlimited trades
            }
            return Math.max(1, (int) Math.round(baseMaxUses * maxUsesMultiplier));
        }

        /**
         * Applies the output multiplier.
         *
         * @param baseAmount The base output amount
         * @return The multiplied amount (minimum 1)
         */
        public int applyOutputMultiplier(int baseAmount) {
            return Math.max(1, (int) Math.round(baseAmount * outputMultiplier));
        }
    }
}
