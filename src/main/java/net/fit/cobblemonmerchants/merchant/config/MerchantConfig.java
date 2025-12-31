package net.fit.cobblemonmerchants.merchant.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fit.cobblemonmerchants.merchant.trading.MultiItemMerchantOffer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
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
            // If the trade has multiple accepted items, create multiple offers
            List<MerchantOffer> tradeOffers = trade.toMerchantOffers();
            offers.addAll(tradeOffers);
        }
        return offers;
    }

    /**
     * Represents a single trade entry in the config
     */
    public record TradeEntry(
        Optional<ItemStack> input,
        Optional<List<String>> acceptedInputs,
        Optional<ItemStack> displayInput,
        Optional<String> displayName,
        Optional<ItemStack> secondInput,
        Optional<List<String>> acceptedSecondInputs,
        Optional<ItemStack> displaySecondInput,
        ItemStack output,
        int maxUses,
        int villagerXp,
        float priceMultiplier
    ) {
        public static final Codec<TradeEntry> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                ItemStack.CODEC.optionalFieldOf("input").forGetter(TradeEntry::input),
                Codec.STRING.listOf().optionalFieldOf("accepted_inputs").forGetter(TradeEntry::acceptedInputs),
                ItemStack.CODEC.optionalFieldOf("display_input").forGetter(TradeEntry::displayInput),
                Codec.STRING.optionalFieldOf("display_name").forGetter(TradeEntry::displayName),
                ItemStack.CODEC.optionalFieldOf("second_input").forGetter(TradeEntry::secondInput),
                Codec.STRING.listOf().optionalFieldOf("accepted_second_inputs").forGetter(TradeEntry::acceptedSecondInputs),
                ItemStack.CODEC.optionalFieldOf("display_second_input").forGetter(TradeEntry::displaySecondInput),
                ItemStack.CODEC.fieldOf("output").forGetter(TradeEntry::output),
                Codec.INT.optionalFieldOf("max_uses", 999).forGetter(TradeEntry::maxUses),
                Codec.INT.optionalFieldOf("villager_xp", 0).forGetter(TradeEntry::villagerXp),
                Codec.FLOAT.optionalFieldOf("price_multiplier", 0.0f).forGetter(TradeEntry::priceMultiplier)
            ).apply(instance, TradeEntry::new)
        );

        /**
         * Converts this trade entry to one or more MerchantOffers
         * If accepted_inputs is specified, creates ONE trade that accepts multiple items
         */
        public List<MerchantOffer> toMerchantOffers() {
            List<MerchantOffer> offers = new ArrayList<>();

            try {
                // Determine if we're using the list-based approach
                if (acceptedInputs.isPresent() && !acceptedInputs.get().isEmpty()) {
                    // Get the display item (or use first accepted item as fallback)
                    ItemStack displayItem = displayInput.orElseGet(() -> {
                        try {
                            return createItemStack(acceptedInputs.get().get(0));
                        } catch (Exception e) {
                            System.err.println("Warning: Could not create display item, using barrier");
                            return new ItemStack(Items.BARRIER, 1);
                        }
                    });

                    // Create ItemCost using the item and count
                    ItemCost displayCost = new ItemCost(displayItem.getItem(), displayItem.getCount());
                    Optional<ItemCost> secondCost = getSecondInputCost();

                    // Create ONE MultiItemMerchantOffer that accepts all items in the list
                    // Pass the custom display name as a String (will be applied on both client and server)
                    offers.add(new MultiItemMerchantOffer(
                        displayCost,
                        secondCost,
                        output,
                        maxUses,
                        villagerXp,
                        priceMultiplier,
                        acceptedInputs.get(),
                        displayName.orElse(null)
                    ));
                } else if (input.isPresent()) {
                    // Standard single-item trade
                    ItemCost inputCost = new ItemCost(input.get().getItem(), input.get().getCount());
                    Optional<ItemCost> secondCost = getSecondInputCost();

                    offers.add(new MerchantOffer(
                        inputCost,
                        secondCost,
                        output,
                        maxUses,
                        villagerXp,
                        priceMultiplier
                    ));
                } else {
                    System.err.println("Warning: Trade entry must have either 'input' or 'accepted_inputs'");
                }
            } catch (Exception e) {
                System.err.println("Error creating merchant offer: " + e.getMessage());
                e.printStackTrace();
            }

            return offers;
        }

        private Optional<ItemCost> getSecondInputCost() {
            if (secondInput.isPresent() && !secondInput.get().isEmpty()) {
                return Optional.of(new ItemCost(secondInput.get().getItem(), secondInput.get().getCount()));
            } else if (acceptedSecondInputs.isPresent() && !acceptedSecondInputs.get().isEmpty()) {
                // For now, use the first accepted second input
                // TODO: Support multiple second inputs if needed
                ItemStack stack = createItemStack(acceptedSecondInputs.get().get(0));
                return Optional.of(new ItemCost(stack.getItem(), stack.getCount()));
            }
            return Optional.empty();
        }

        private ItemStack createItemStack(String itemId) {
            try {
                ResourceLocation location = ResourceLocation.parse(itemId);
                Item item = BuiltInRegistries.ITEM.get(location);
                if (item != null && !item.equals(Items.AIR)) {
                    return new ItemStack(item, 1);
                }
            } catch (Exception e) {
                System.err.println("Warning: Invalid item ID: " + itemId);
            }
            return new ItemStack(Items.BARRIER, 1);
        }
    }
}
