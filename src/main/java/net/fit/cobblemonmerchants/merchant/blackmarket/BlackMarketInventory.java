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

        long currentRotationId = BlackMarketConfig.getCurrentRotationId(worldTime);
        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("Current rotation ID: {}", currentRotationId);

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
        MerchantOffers newOffers = generateOffersForPlayer(playerUUID, currentRotationId);
        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("Generated {} offers", newOffers.size());
        playerInventories.put(playerUUID, new PlayerInventory(currentRotationId, newOffers));
        setDirty();

        return newOffers;
    }

    /**
     * Generates merchant offers for a player based on their UUID and rotation ID.
     * The same player always gets the same offers for the same rotation.
     *
     * @param playerUUID Player's UUID
     * @param rotationId Current rotation ID
     * @return Generated merchant offers
     */
    private MerchantOffers generateOffersForPlayer(UUID playerUUID, long rotationId) {
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

            // Calculate value and item count for this player
            double baseValue = DropValueCalculator.calculateBaseValue(dropData);
            int relicCoinCost = DropValueCalculator.calculatePlayerValue(dropData, playerUUID, rotationId);
            int itemCount = DropValueCalculator.calculatePlayerItemCount(1, playerUUID, itemId, rotationId);

            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("  Base Value: {} coins | Final Cost: {} coins | Item Count: {}",
                String.format("%.2f", baseValue), relicCoinCost, itemCount);

            // Create the item stack
            ItemStack resultStack = createItemStack(itemId, itemCount);
            if (resultStack.isEmpty()) {
                net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.warn("Empty item stack for: {}", itemId);
                continue; // Skip if item doesn't exist
            }

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
                BlackMarketConfig.MAX_TRADE_USES,
                BlackMarketConfig.VILLAGER_XP_PER_TRADE,
                0.0f // No price multiplier
            );

            offers.add(offer);
        }

        return offers;
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
     * Forces all player inventories to refresh by clearing the cache.
     * Next time a player accesses the Black Market, they will get newly generated offers.
     */
    public void forceRefreshAll() {
        playerInventories.clear();
        setDirty();
        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("Cleared all Black Market inventories - will regenerate on next access");
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

        return inventory;
    }

    /**
     * Stores a player's inventory for a specific rotation
     */
    private record PlayerInventory(long rotationId, MerchantOffers offers) {}
}