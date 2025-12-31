package net.fit.cobblemonmerchants.merchant.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.List;
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
    List<TradeEntry> trades
) {
    public static final Codec<MerchantConfig> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("display_name").forGetter(MerchantConfig::displayName),
            Codec.STRING.optionalFieldOf("player_skin_name").forGetter(MerchantConfig::playerSkinName),
            Codec.STRING.optionalFieldOf("villager_biome").forGetter(MerchantConfig::villagerBiome),
            Codec.STRING.optionalFieldOf("villager_profession").forGetter(MerchantConfig::villagerProfession),
            TradeEntry.CODEC.listOf().fieldOf("trades").forGetter(MerchantConfig::trades)
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
}
