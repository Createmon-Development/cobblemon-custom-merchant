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
 * Defines the merchant's display name, player skin, and trades.
 */
public record MerchantConfig(
    String displayName,
    Optional<String> playerSkinName,
    List<TradeEntry> trades
) {
    public static final Codec<MerchantConfig> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("display_name").forGetter(MerchantConfig::displayName),
            Codec.STRING.optionalFieldOf("player_skin_name").forGetter(MerchantConfig::playerSkinName),
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
        ItemStack input,
        Optional<ItemStack> secondInput,
        ItemStack output,
        int maxUses,
        int villagerXp,
        float priceMultiplier
    ) {
        public static final Codec<TradeEntry> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                ItemStack.CODEC.fieldOf("input").forGetter(TradeEntry::input),
                ItemStack.CODEC.optionalFieldOf("second_input").forGetter(TradeEntry::secondInput),
                ItemStack.CODEC.fieldOf("output").forGetter(TradeEntry::output),
                Codec.INT.optionalFieldOf("max_uses", 999).forGetter(TradeEntry::maxUses),
                Codec.INT.optionalFieldOf("villager_xp", 0).forGetter(TradeEntry::villagerXp),
                Codec.FLOAT.optionalFieldOf("price_multiplier", 0.0f).forGetter(TradeEntry::priceMultiplier)
            ).apply(instance, TradeEntry::new)
        );

        /**
         * Converts this trade entry to a MerchantOffer
         */
        public MerchantOffer toMerchantOffer() {
            ItemCost inputCost = new ItemCost(input.getItem(), input.getCount());

            if (secondInput.isPresent() && !secondInput.get().isEmpty()) {
                // Two-item trade
                ItemCost secondInputCost = new ItemCost(secondInput.get().getItem(), secondInput.get().getCount());
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
