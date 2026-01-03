package net.fit.cobblemonmerchants.merchant.blackmarket;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Manages per-player Black Market inventories with rotating stock.
 * Each player gets a unique set of trades based on their UUID and the current rotation ID.
 * All Black Market merchants share the same inventory per player.
 */
public class BlackMarketInventory extends SavedData {
    private static final String DATA_NAME = "cobblemonmerchants_black_market";

    // Cache of player inventories: UUID -> (rotationId, offers)
    private final Map<UUID, PlayerInventory> playerInventories = new HashMap<>();

    // Per-player rotation offsets for manual refresh command
    private final Map<UUID, Long> playerRotationOffsets = new HashMap<>();

    // Track which players have been notified about lucky trades this rotation
    private final Map<UUID, Long> luckyTradeNotifications = new HashMap<>();

    public BlackMarketInventory() {
        super();
    }

    /**
     * Gets or creates the Black Market inventory manager for a world
     */
    public static BlackMarketInventory get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new Factory<>(
                BlackMarketInventory::new,
                BlackMarketInventory::load
            ),
            DATA_NAME
        );
    }

    /**
     * Gets the merchant offers for a specific player at the current rotation.
     * Generates new offers if the rotation has changed or player is new.
     *
     * @param playerUUID Player's UUID
     * @param worldTime Current world time in ticks
     * @return Merchant offers for this player
     */
    public MerchantOffers getOffersForPlayer(UUID playerUUID, long worldTime) {
        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("getOffersForPlayer called - player: {}, worldTime: {}", playerUUID, worldTime);

        long baseRotationId = BlackMarketConfig.getCurrentRotationId(worldTime);
        long rotationOffset = playerRotationOffsets.getOrDefault(playerUUID, 0L);
        long currentRotationId = baseRotationId + rotationOffset;
        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("Current rotation ID: {} (base: {} + offset: {})", currentRotationId, baseRotationId, rotationOffset);

        // Check if we have cached inventory for this player and rotation
        PlayerInventory cached = playerInventories.get(playerUUID);
        if (cached != null && cached.rotationId == currentRotationId && !cached.offers.isEmpty()) {
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("Returning cached offers ({} items)", cached.offers.size());
            return cached.offers;
        }

        // If cached offers are empty, regenerate them (data may have been corrupted)
        if (cached != null && cached.offers.isEmpty()) {
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.warn("Cached offers are EMPTY! Regenerating...");
        }

        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("No cache found or rotation changed - generating new offers");
        // Generate new inventory for this rotation
        MerchantOffers newOffers = generateOffersForPlayer(playerUUID, currentRotationId, worldTime);
        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("Generated {} offers", newOffers.size());
        playerInventories.put(playerUUID, new PlayerInventory(currentRotationId, newOffers));
        setDirty();

        return newOffers;
    }

    /**
     * Checks if the player has any lucky trades in their current rotation
     * and if they haven't been notified yet this rotation
     *
     * @param playerUUID Player's UUID
     * @param rotationId Current rotation ID
     * @return LuckyTradeInfo if player has lucky trades and hasn't been notified, null otherwise
     */
    public LuckyTradeInfo checkForLuckyTrades(UUID playerUUID, long rotationId) {
        // Check if we've already notified this player for this rotation
        Long lastNotifiedRotation = luckyTradeNotifications.get(playerUUID);
        if (lastNotifiedRotation != null && lastNotifiedRotation == rotationId) {
            return null; // Already notified
        }

        // Check the cached inventory for lucky trades
        PlayerInventory cached = playerInventories.get(playerUUID);
        if (cached == null || cached.rotationId != rotationId) {
            return null; // No cached data for this rotation yet
        }

        // Scan through the offers to find lucky trades
        List<String> selectedItems = selectItemsForRotation(
            new Random(playerUUID.getMostSignificantBits() ^ playerUUID.getLeastSignificantBits() ^ rotationId));

        int luckyPriceCount = 0;
        int luckyUsesCount = 0;

        for (String itemId : selectedItems) {
            CobblemonDropData dropData = CobblemonDropRegistry.getDropData(itemId);
            if (dropData == null) continue;

            // Use same random seed as generation
            Random luckyRandom = new Random(playerUUID.hashCode() + itemId.hashCode() + rotationId * 31L);
            if (BlackMarketConfig.LUCKY_TRADES_ENABLED && luckyRandom.nextDouble() < BlackMarketConfig.LUCKY_TRADE_CHANCE) {
                boolean luckyPriceBonus = luckyRandom.nextBoolean();
                if (luckyPriceBonus) {
                    luckyPriceCount++;
                } else {
                    luckyUsesCount++;
                }
            }
        }

        if (luckyPriceCount > 0 || luckyUsesCount > 0) {
            // Mark as notified
            luckyTradeNotifications.put(playerUUID, rotationId);
            setDirty();
            return new LuckyTradeInfo(luckyPriceCount, luckyUsesCount);
        }

        return null;
    }

    /**
     * Generates merchant offers for a player based on their UUID and rotation ID.
     * The same player always gets the same offers for the same rotation.
     *
     * @param playerUUID Player's UUID
     * @param rotationId Current rotation ID
     * @param worldTime Current world time in ticks
     * @return Generated merchant offers
     */
    private MerchantOffers generateOffersForPlayer(UUID playerUUID, long rotationId, long worldTime) {
        MerchantOffers offers = new MerchantOffers();

        // Create deterministic random for this player and rotation
        Random random = new Random(playerUUID.getMostSignificantBits() ^ playerUUID.getLeastSignificantBits() ^ rotationId);

        // Select items for this rotation
        List<String> selectedItems = selectItemsForRotation(random);

        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("Generating offers for player {} at rotation {}", playerUUID, rotationId);
        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("Selected {} items: {}", selectedItems.size(), selectedItems);

        // Create offers for each selected item
        for (String itemId : selectedItems) {
            CobblemonDropData dropData = CobblemonDropRegistry.getDropData(itemId);
            if (dropData == null) {
                net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.warn("No drop data for item: {}", itemId);
                continue; // Skip if no drop data
            }

            // Log drop data details
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("Item: {} | Avg Drop%: {} | Avg Qty: {} | Exclusive: {} | Drops from {} Pokemon",
                itemId, dropData.getAverageDropChance(), dropData.getAverageDropQuantity(),
                dropData.isCobblemonExclusive(), dropData.getPokemonDrops().size());

            // Calculate value, item count, and trade uses for this player
            double baseValue = DropValueCalculator.calculateBaseValue(dropData);
            int relicCoinCost = DropValueCalculator.calculatePlayerValue(dropData, playerUUID, rotationId);
            int itemCount = DropValueCalculator.calculatePlayerItemCount(1, playerUUID, itemId, rotationId);
            int maxTradeUses = DropValueCalculator.calculatePlayerTradeUses(dropData, playerUUID, rotationId);

            // Apply low-cost trade modifications
            Random lowCostRandom = new Random(playerUUID.hashCode() + itemId.hashCode() + rotationId * 17L);

            // For very low cost items (1 RC before rounding), add price variation
            if (baseValue <= 1.0 && relicCoinCost == 1) {
                // Vary the price between MIN_SINGLE_RC_PRICE and MAX_SINGLE_RC_PRICE
                // Weighted toward lower prices based on the pre-rounded value
                double weight = baseValue; // 0.0-1.0, lower value = more likely to stay at 1 RC
                int priceRange = BlackMarketConfig.MAX_SINGLE_RC_PRICE - BlackMarketConfig.MIN_SINGLE_RC_PRICE;

                // Use cubic weighting to favor lower prices
                double weightedRoll = Math.pow(lowCostRandom.nextDouble(), 3.0 - (2.0 * weight));
                relicCoinCost = BlackMarketConfig.MIN_SINGLE_RC_PRICE + (int)(weightedRoll * priceRange);
            }

            // For low-cost trades, require multiple items
            if (relicCoinCost <= BlackMarketConfig.LOW_COST_MULTI_ITEM_THRESHOLD) {
                // Calculate how many items to require based on the pre-rounded base value
                // Lower value = more items required
                double itemMultiplier = Math.max(1.0, BlackMarketConfig.LOW_COST_MULTI_ITEM_THRESHOLD / Math.max(0.5, baseValue));
                itemCount = (int) Math.ceil(itemCount * itemMultiplier);
                // Cap at a reasonable maximum
                itemCount = Math.min(itemCount, 64);
            }

            // Check for lucky trade
            boolean isLuckyTrade = false;
            boolean luckyPriceBonus = false; // true = price bonus, false = trade uses bonus
            if (BlackMarketConfig.LUCKY_TRADES_ENABLED) {
                // Create separate random for lucky trade check
                Random luckyRandom = new Random(playerUUID.hashCode() + itemId.hashCode() + rotationId * 31L);
                if (luckyRandom.nextDouble() < BlackMarketConfig.LUCKY_TRADE_CHANCE) {
                    isLuckyTrade = true;
                    // Randomly choose which bonus type
                    luckyPriceBonus = luckyRandom.nextBoolean();

                    if (luckyPriceBonus) {
                        // Apply price multiplier
                        relicCoinCost = (int) Math.round(relicCoinCost * BlackMarketConfig.LUCKY_TRADE_PRICE_MULTIPLIER);
                    } else {
                        // Apply trade uses multiplier
                        maxTradeUses = (int) Math.round(maxTradeUses * BlackMarketConfig.LUCKY_TRADE_USES_MULTIPLIER);
                    }
                }
            }

            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("  Base Value: {} coins | Final Cost: {} coins | Item Count: {} | Max Uses: {} | Lucky: {}{}",
                String.format("%.2f", baseValue), relicCoinCost, itemCount, maxTradeUses, isLuckyTrade,
                isLuckyTrade ? (luckyPriceBonus ? " (PRICE)" : " (USES)") : "");

            // Create the item stack
            ItemStack resultStack = createItemStack(itemId, itemCount);
            if (resultStack.isEmpty()) {
                net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.warn("Empty item stack for: {}", itemId);
                continue; // Skip if item doesn't exist
            }

            // Set custom name on the result item to show what the merchant wants
            net.minecraft.network.chat.Component itemName;
            if (isLuckyTrade) {
                // Add enchantment glint using data component
                resultStack.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);

                // Add custom name with sparkles for lucky trade
                itemName = net.minecraft.network.chat.Component.literal("§6✦ §r")
                    .append(resultStack.getHoverName())
                    .append(net.minecraft.network.chat.Component.literal(" §6✦"));

                // Add lore explaining the bonus
                java.util.List<net.minecraft.network.chat.Component> lore = new java.util.ArrayList<>();
                lore.add(net.minecraft.network.chat.Component.literal("§7Lucky Trade!"));
                lore.add(net.minecraft.network.chat.Component.literal(
                    luckyPriceBonus ? "§eExtra coins for this trade!" : "§eMore trades available!"
                ));

                resultStack.set(net.minecraft.core.component.DataComponents.LORE,
                    new net.minecraft.world.item.component.ItemLore(lore));
            } else {
                // Regular trade - use item's default name
                itemName = resultStack.getHoverName();
            }

            // Set the custom name to show what the merchant wants
            resultStack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, itemName);

            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("Created offer: {} x{} for {} relic coins", itemId, itemCount, relicCoinCost);

            // Get the relic coin item from Cobblemon
            net.minecraft.world.item.Item relicCoinItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", "relic_coin")
            );

            if (relicCoinItem == null || relicCoinItem == net.minecraft.world.item.Items.AIR) {
                // Fallback if relic coin not found (shouldn't happen if Cobblemon is loaded)
                relicCoinItem = net.minecraft.world.item.Items.GOLD_NUGGET;
            }

            // Create the merchant offer - player gives items, gets relic coins
            // Swap so player gives the item and receives relic coins
            ItemStack relicCoinStack = new ItemStack(relicCoinItem, relicCoinCost);
            MerchantOffer offer = new MerchantOffer(
                new ItemCost(resultStack.getItem(), itemCount),
                relicCoinStack,
                maxTradeUses,  // Use dynamically calculated max uses based on value
                BlackMarketConfig.VILLAGER_XP_PER_TRADE,
                0.0f // No price multiplier
            );

            offers.add(offer);
        }

        // Add countdown clock in position 26 (bottom right)
        addCountdownClock(offers, worldTime);

        return offers;
    }

    /**
     * Adds a countdown clock showing days until next rotation
     * Placed in slot 26 (bottom right corner) of the trading GUI
     */
    private void addCountdownClock(MerchantOffers offers, long worldTime) {
        // Calculate days until rotation
        long ticksUntilRotation = BlackMarketConfig.getTicksUntilRotation(worldTime);
        long totalMinutes = ticksUntilRotation / 1000;
        long days = totalMinutes / 1440;

        // Create clock item with simple name showing only days
        ItemStack clockStack = new ItemStack(net.minecraft.world.item.Items.CLOCK);

        // Set custom name with just the day count
        net.minecraft.network.chat.Component clockName = net.minecraft.network.chat.Component.literal(
            String.format("§6%d day%s", days, days == 1 ? "" : "s")
        );
        clockStack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, clockName);

        // Add lore description
        java.util.List<net.minecraft.network.chat.Component> lore = new java.util.ArrayList<>();
        lore.add(net.minecraft.network.chat.Component.literal("§7I'll only be around for"));
        lore.add(net.minecraft.network.chat.Component.literal("§7so long, get these trades"));
        lore.add(net.minecraft.network.chat.Component.literal("§7while you can!"));
        clockStack.set(net.minecraft.core.component.DataComponents.LORE,
            new net.minecraft.world.item.component.ItemLore(lore));

        // Create a fake "trade" that requires an impossible item to prevent trading
        // Use maxUses=0 so it always appears disabled and never highlighted
        net.minecraft.world.item.Item barrierItem = net.minecraft.world.item.Items.BARRIER;
        MerchantOffer clockOffer = new MerchantOffer(
            new ItemCost(barrierItem, 64), // Impossible cost
            clockStack,
            0, // Always disabled - never shows as available
            0, // No XP
            0.0f // No price multiplier
        );
        // Set uses to 0 so it appears grayed out
        clockOffer.resetUses();

        // Pad offers to ensure clock is in position 26
        // Use barrier items with 0 maxUses so they appear disabled
        while (offers.size() < 26) {
            ItemStack paddingStack = new ItemStack(net.minecraft.world.item.Items.BARRIER);
            MerchantOffer paddingOffer = new MerchantOffer(
                new ItemCost(net.minecraft.world.item.Items.BARRIER, 64),
                paddingStack,
                0, // maxUses = 0 means always disabled
                0, // No XP
                0.0f // No price multiplier
            );
            paddingOffer.resetUses(); // Ensure it's grayed out
            offers.add(paddingOffer);
        }

        offers.add(clockOffer);
    }

    /**
     * Selects items for the current rotation using deterministic randomness.
     * Selects MINECRAFT_ITEMS_COUNT regular items and COBBLEMON_ITEMS_COUNT exclusive items.
     *
     * @param random Seeded random instance
     * @return List of selected item IDs
     */
    private List<String> selectItemsForRotation(Random random) {
        List<String> selected = new ArrayList<>();

        // Select Minecraft items
        List<String> minecraftItems = new ArrayList<>(CobblemonDropRegistry.getMinecraftItems());
        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("Available Minecraft items from registry: {}", minecraftItems.size());
        Collections.shuffle(minecraftItems, random);
        int minecraftCount = Math.min(BlackMarketConfig.MINECRAFT_ITEMS_COUNT, minecraftItems.size());
        if (minecraftCount > 0) {
            selected.addAll(minecraftItems.subList(0, minecraftCount));
        }

        // Select Cobblemon-exclusive items
        List<String> cobblemonItems = new ArrayList<>(CobblemonDropRegistry.getCobblemonExclusiveItems());
        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("Available Cobblemon items from registry: {}", cobblemonItems.size());
        Collections.shuffle(cobblemonItems, random);
        int cobblemonCount = Math.min(BlackMarketConfig.COBBLEMON_ITEMS_COUNT, cobblemonItems.size());
        if (cobblemonCount > 0) {
            selected.addAll(cobblemonItems.subList(0, cobblemonCount));
        }

        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("Total selected items: {}", selected.size());
        return selected;
    }

    /**
     * Creates an ItemStack from an item ID string
     * @param itemId Item ID (e.g., "minecraft:diamond")
     * @param count Stack count
     * @return ItemStack, or empty if item doesn't exist
     */
    private ItemStack createItemStack(String itemId, int count) {
        try {
            // Parse the resource location
            net.minecraft.resources.ResourceLocation resourceLocation = net.minecraft.resources.ResourceLocation.parse(itemId);

            // Get the item from the registry
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(resourceLocation);

            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                return ItemStack.EMPTY;
            }

            return new ItemStack(item, count);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * Marks offers as changed (so trade usage gets saved)
     */
    public void markOffersDirty() {
        setDirty();
    }

    /**
     * Forces all player inventories to refresh by incrementing their rotation offsets.
     * Next time a player accesses the Black Market, they will get newly generated offers.
     */
    public void forceRefreshAll() {
        // Increment rotation offset for all players who have accessed the Black Market
        for (UUID playerUUID : playerInventories.keySet()) {
            long currentOffset = playerRotationOffsets.getOrDefault(playerUUID, 0L);
            playerRotationOffsets.put(playerUUID, currentOffset + 1);
        }

        // Clear the cached inventories so they regenerate with new rotation ID
        playerInventories.clear();
        setDirty();
        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("Incremented rotation offsets for {} players - will regenerate on next access", playerRotationOffsets.size());
    }

    /**
     * Forces a specific player's inventory to refresh by incrementing their rotation offset.
     * @param playerUUID The player to refresh
     */
    public void forceRefreshPlayer(UUID playerUUID) {
        long currentOffset = playerRotationOffsets.getOrDefault(playerUUID, 0L);
        playerRotationOffsets.put(playerUUID, currentOffset + 1);
        playerInventories.remove(playerUUID);
        setDirty();
        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("Incremented rotation offset for player {} to {} - will regenerate on next access", playerUUID, currentOffset + 1);
    }

    /**
     * Saves the inventory data to NBT
     * Saves both rotation IDs and offers with their usage data
     */
    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        ListTag playerList = new ListTag();

        for (Map.Entry<UUID, PlayerInventory> entry : playerInventories.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("UUID", entry.getKey());
            playerTag.putLong("RotationId", entry.getValue().rotationId);

            // Save the offers with their usage data using CODEC
            ListTag offersListTag = new ListTag();
            for (MerchantOffer offer : entry.getValue().offers) {
                MerchantOffer.CODEC.encodeStart(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), offer)
                    .resultOrPartial(error -> net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.error("Failed to save offer: {}", error))
                    .ifPresent(offersListTag::add);
            }
            playerTag.put("Offers", offersListTag);

            playerList.add(playerTag);
        }

        tag.put("PlayerInventories", playerList);

        // Save rotation offsets
        ListTag offsetList = new ListTag();
        for (Map.Entry<UUID, Long> entry : playerRotationOffsets.entrySet()) {
            CompoundTag offsetTag = new CompoundTag();
            offsetTag.putUUID("UUID", entry.getKey());
            offsetTag.putLong("Offset", entry.getValue());
            offsetList.add(offsetTag);
        }
        tag.put("RotationOffsets", offsetList);

        return tag;
    }

    /**
     * Loads the inventory data from NBT
     * Loads offers with their usage data preserved
     */
    public static BlackMarketInventory load(CompoundTag tag, HolderLookup.Provider registries) {
        BlackMarketInventory inventory = new BlackMarketInventory();

        ListTag playerList = tag.getList("PlayerInventories", Tag.TAG_COMPOUND);
        for (int i = 0; i < playerList.size(); i++) {
            CompoundTag playerTag = playerList.getCompound(i);

            UUID playerUUID = playerTag.getUUID("UUID");
            long rotationId = playerTag.getLong("RotationId");

            // Load saved offers if they exist
            MerchantOffers offers = new MerchantOffers();
            if (playerTag.contains("Offers")) {
                ListTag offersList = playerTag.getList("Offers", Tag.TAG_COMPOUND);
                for (int j = 0; j < offersList.size(); j++) {
                    CompoundTag offerTag = offersList.getCompound(j);
                    MerchantOffer.CODEC.parse(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), offerTag)
                        .resultOrPartial(error -> net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.error("Failed to load offer: {}", error))
                        .ifPresent(offers::add);
                }
            }

            inventory.playerInventories.put(playerUUID, new PlayerInventory(rotationId, offers));
        }

        // Load rotation offsets
        if (tag.contains("RotationOffsets")) {
            ListTag offsetList = tag.getList("RotationOffsets", Tag.TAG_COMPOUND);
            for (int i = 0; i < offsetList.size(); i++) {
                CompoundTag offsetTag = offsetList.getCompound(i);
                UUID playerUUID = offsetTag.getUUID("UUID");
                long offset = offsetTag.getLong("Offset");
                inventory.playerRotationOffsets.put(playerUUID, offset);
            }
        }

        return inventory;
    }

    /**
     * Stores a player's inventory for a specific rotation
     */
    private record PlayerInventory(long rotationId, MerchantOffers offers) {}

    /**
     * Information about lucky trades for a player
     */
    public record LuckyTradeInfo(int priceCount, int usesCount) {
        public int totalCount() {
            return priceCount + usesCount;
        }
    }
}