package net.fit.cobblemonmerchants.merchant.menu;

import net.fit.cobblemonmerchants.merchant.CustomMerchantEntity;
import net.fit.cobblemonmerchants.network.SyncMerchantOffersPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

/**
 * Menu for displaying merchant trades in a chest-style GUI.
 * Clicking on items executes the trade if the player has the required items.
 */
public class MerchantTradeMenu extends AbstractContainerMenu {
    private final CustomMerchantEntity merchant;
    private MerchantOffers offers;
    private java.util.List<net.fit.cobblemonmerchants.merchant.config.MerchantConfig.TradeEntry> tradeEntries;
    private final int merchantId;
    private int lastOfferCount = -1; // Start at -1 to trigger initial sync
    private boolean needsSync = false; // Flag to force sync after trade

    // Constructor for client side
    public MerchantTradeMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        super(ModMenuTypes.MERCHANT_TRADE_MENU.get(), containerId);
        this.merchantId = extraData.readInt();

        // Read synced trade entries from packet
        net.minecraft.network.RegistryFriendlyByteBuf registryBuf = (net.minecraft.network.RegistryFriendlyByteBuf) extraData;
        int tradeEntryCount = extraData.readInt();
        this.tradeEntries = new java.util.ArrayList<>();
        for (int i = 0; i < tradeEntryCount; i++) {
            net.fit.cobblemonmerchants.merchant.config.ItemRequirement input =
                extraData.readJsonWithCodec(net.fit.cobblemonmerchants.merchant.config.ItemRequirement.CODEC);
            java.util.Optional<net.fit.cobblemonmerchants.merchant.config.ItemRequirement> secondInput =
                extraData.readBoolean() ? java.util.Optional.of(extraData.readJsonWithCodec(net.fit.cobblemonmerchants.merchant.config.ItemRequirement.CODEC)) : java.util.Optional.empty();
            ItemStack output = ItemStack.STREAM_CODEC.decode(registryBuf);
            int maxUses = extraData.readInt();
            int villagerXp = extraData.readInt();
            float priceMultiplier = extraData.readFloat();
            java.util.Optional<String> tradeDisplayName = extraData.readBoolean() ? java.util.Optional.of(extraData.readUtf()) : java.util.Optional.empty();
            java.util.Optional<Integer> position = extraData.readBoolean() ? java.util.Optional.of(extraData.readInt()) : java.util.Optional.empty();

            this.tradeEntries.add(new net.fit.cobblemonmerchants.merchant.config.MerchantConfig.TradeEntry(
                input, secondInput, output, maxUses, villagerXp, priceMultiplier, tradeDisplayName, position
            ));
        }

        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("CLIENT: Received {} trade entries from server", this.tradeEntries.size());

        // Try to get the merchant from the world
        if (playerInventory.player.level().getEntity(merchantId) instanceof CustomMerchantEntity entity) {
            this.merchant = entity;
            this.offers = entity.getOffers();
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("CLIENT: Found merchant, offers size: {}", this.offers.size());
        } else {
            this.merchant = null;
            this.offers = new MerchantOffers();
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.warn("CLIENT: Could not find merchant entity!");
        }

        addPlayerInventorySlots(playerInventory);
    }

    // Constructor for server side
    public MerchantTradeMenu(int containerId, Inventory playerInventory, CustomMerchantEntity merchant) {
        super(ModMenuTypes.MERCHANT_TRADE_MENU.get(), containerId);
        this.merchant = merchant;
        this.merchantId = merchant != null ? merchant.getId() : -1;
        this.offers = merchant != null ? merchant.getOffers() : new MerchantOffers();
        this.tradeEntries = merchant != null ? merchant.getTradeEntries() : new java.util.ArrayList<>();
        // Keep lastOfferCount at -1 to trigger sync in broadcastChanges
        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("SERVER: Creating menu for merchant ID: {}, offers size: {}",
            merchantId, this.offers.size());

        addPlayerInventorySlots(playerInventory);
    }

    private void addPlayerInventorySlots(Inventory playerInventory) {
        // Player inventory slots
        // Y = 18 (top margin) + 54 (3 chest rows) + 14 (gap) - 1 (adjustment) = 85
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 85 + row * 18));
            }
        }

        // Player hotbar
        // Y = 85 + 58 (3 rows + gap) = 143
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 143));
        }
    }

    public int getMerchantId() {
        return merchantId;
    }

    public MerchantOffers getOffers() {
        return offers;
    }

    public void setOffers(MerchantOffers offers) {
        this.offers = offers;
    }

    public CustomMerchantEntity getMerchant() {
        return merchant;
    }

    public java.util.List<net.fit.cobblemonmerchants.merchant.config.MerchantConfig.TradeEntry> getTradeEntries() {
        return tradeEntries;
    }

    /**
     * Attempt to execute a trade at the given index
     * Returns true if the trade was successful
     */
    public boolean executeTrade(int tradeIndex, Player player) {
        if (merchant == null || tradeIndex < 0 || tradeIndex >= offers.size()) {
            return false;
        }

        MerchantOffer offer = offers.get(tradeIndex);
        if (offer.isOutOfStock()) {
            return false;
        }

        // Get the original trade entry for tag-based validation
        net.fit.cobblemonmerchants.merchant.config.MerchantConfig.TradeEntry tradeEntry =
            tradeIndex < tradeEntries.size() ? tradeEntries.get(tradeIndex) : null;

        if (tradeEntry == null) {
            // Fallback to vanilla validation for trades without TradeEntry (e.g., Black Market)
            return executeTradeLegacy(tradeIndex, player, offer);
        }

        // Count how many of each required item the player has
        net.fit.cobblemonmerchants.merchant.config.ItemRequirement inputReq = tradeEntry.input();
        int countA = 0;

        // Special handling for relic coins - check bag too
        if (isRelicCoin(inputReq)) {
            countA = countRelicCoins(player);
        } else {
            for (ItemStack stack : player.getInventory().items) {
                if (inputReq.matches(stack)) {
                    countA += stack.getCount();
                }
            }
        }

        int countB = 0;
        if (tradeEntry.secondInput().isPresent()) {
            net.fit.cobblemonmerchants.merchant.config.ItemRequirement secondInputReq = tradeEntry.secondInput().get();

            // Special handling for relic coins - check bag too
            if (isRelicCoin(secondInputReq)) {
                countB = countRelicCoins(player);
            } else {
                for (ItemStack stack : player.getInventory().items) {
                    if (secondInputReq.matches(stack)) {
                        countB += stack.getCount();
                    }
                }
            }
        }

        // Check if player has enough items
        if (countA < inputReq.getCount()) {
            return false;
        }
        if (tradeEntry.secondInput().isPresent() && countB < tradeEntry.secondInput().get().getCount()) {
            return false;
        }

        // Remove the cost items from player inventory
        if (isRelicCoin(inputReq)) {
            removeRelicCoins(player, inputReq.getCount());
        } else {
            removeItemsMatching(player.getInventory(), inputReq, inputReq.getCount());
        }

        if (tradeEntry.secondInput().isPresent()) {
            net.fit.cobblemonmerchants.merchant.config.ItemRequirement secondInputReq = tradeEntry.secondInput().get();
            if (isRelicCoin(secondInputReq)) {
                removeRelicCoins(player, secondInputReq.getCount());
            } else {
                removeItemsMatching(player.getInventory(), secondInputReq, secondInputReq.getCount());
            }
        }

        // Give the player the result item
        ItemStack result = offer.getResult().copy();
        if (!player.getInventory().add(result)) {
            // If inventory is full, drop the item
            player.drop(result, false);
        }

        // Update the offer usage
        offer.increaseUses();

        // Mark for sync on next broadcastChanges
        needsSync = true;

        return true;
    }

    // Legacy validation for trades without TradeEntry (Black Market, etc.)
    private boolean executeTradeLegacy(int tradeIndex, Player player, MerchantOffer offer) {
        ItemStack costA = offer.getItemCostA().itemStack();
        ItemStack costB = offer.getCostB();

        int countA = 0;
        int countB = 0;

        // Special handling for relic coins - check bag too
        if (isRelicCoin(costA)) {
            countA = countRelicCoins(player);
        } else {
            for (ItemStack stack : player.getInventory().items) {
                if (ItemStack.isSameItemSameComponents(stack, costA)) {
                    countA += stack.getCount();
                }
            }
        }

        if (!costB.isEmpty()) {
            if (isRelicCoin(costB)) {
                countB = countRelicCoins(player);
            } else {
                for (ItemStack stack : player.getInventory().items) {
                    if (ItemStack.isSameItemSameComponents(stack, costB)) {
                        countB += stack.getCount();
                    }
                }
            }
        }

        if (countA < costA.getCount()) {
            return false;
        }
        if (!costB.isEmpty() && countB < costB.getCount()) {
            return false;
        }

        // Remove items with special handling for relic coins
        if (isRelicCoin(costA)) {
            removeRelicCoins(player, costA.getCount());
        } else {
            removeItems(player.getInventory(), costA, costA.getCount());
        }

        if (!costB.isEmpty()) {
            if (isRelicCoin(costB)) {
                removeRelicCoins(player, costB.getCount());
            } else {
                removeItems(player.getInventory(), costB, costB.getCount());
            }
        }

        ItemStack result = offer.getResult().copy();
        if (!player.getInventory().add(result)) {
            player.drop(result, false);
        }

        offer.increaseUses();

        // Mark for sync on next broadcastChanges
        needsSync = true;

        return true;
    }

    /**
     * Removes items matching the ItemRequirement from inventory
     */
    private void removeItemsMatching(Inventory inventory, net.fit.cobblemonmerchants.merchant.config.ItemRequirement requirement, int count) {
        int remaining = count;

        for (int i = 0; i < inventory.items.size() && remaining > 0; i++) {
            ItemStack stack = inventory.items.get(i);
            if (requirement.matches(stack)) {
                int removeCount = Math.min(remaining, stack.getCount());
                stack.shrink(removeCount);
                remaining -= removeCount;
            }
        }
    }

    /**
     * Removes a specific number of items matching the given stack from the inventory
     */
    private void removeItems(Inventory inventory, ItemStack toRemove, int count) {
        int remaining = count;

        for (int i = 0; i < inventory.items.size() && remaining > 0; i++) {
            ItemStack stack = inventory.items.get(i);
            if (ItemStack.isSameItemSameComponents(stack, toRemove)) {
                int removeCount = Math.min(remaining, stack.getCount());
                stack.shrink(removeCount);
                remaining -= removeCount;
            }
        }
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        // No shift-clicking in this GUI
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        if (merchant == null) {
            return false;
        }
        return merchant.isAlive() && merchant.distanceTo(player) < 8.0;
    }

    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);
        if (merchant != null) {
            merchant.setTradingPlayer(null);
        }
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();

        // Sync offers to client when they change, after trades, or on first broadcast
        if (merchant != null) {
            MerchantOffers currentOffers = merchant.getOffers();
            boolean shouldSync = needsSync || currentOffers.size() != lastOfferCount;

            if (shouldSync) {
                net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("SERVER: Syncing offers - count: {}, needsSync: {}",
                    currentOffers.size(), needsSync);
                sendOffersToPlayers();
                lastOfferCount = currentOffers.size();
                needsSync = false;
            }
        }
    }

    private void sendOffersToPlayers() {
        if (merchant != null) {
            MerchantOffers currentOffers = merchant.getOffers();
            // Send to all players within range - the packet handler will check containerId
            if (merchant.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                for (ServerPlayer player : serverLevel.players()) {
                    // Send to players close enough to be trading with this merchant
                    if (player.distanceTo(merchant) < 8.0) {
                        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("SERVER: Syncing {} offers to player {} (containerId: {})",
                            currentOffers.size(), player.getName().getString(), this.containerId);
                        PacketDistributor.sendToPlayer(player,
                            new SyncMerchantOffersPacket(this.containerId, currentOffers));
                    }
                }
            }
        }
    }

    /**
     * Counts total relic coins available to the player (inventory + coin bag)
     */
    private int countRelicCoins(Player player) {
        int count = 0;
        ResourceLocation relicCoinId = ResourceLocation.fromNamespaceAndPath("cobblemon", "relic_coin");

        // Count coins in inventory
        for (ItemStack stack : player.getInventory().items) {
            ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId.equals(relicCoinId)) {
                count += stack.getCount();
            }
        }

        // Count coins in bag
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof net.fit.cobblemonmerchants.item.custom.RelicCoinBagItem) {
                int bagCoins = stack.getOrDefault(net.fit.cobblemonmerchants.item.component.ModDataComponents.RELIC_COIN_COUNT.get(), 0);
                count += bagCoins;
                break; // Only one bag should exist
            }
        }

        return count;
    }

    /**
     * Removes relic coins from player inventory and/or coin bag
     */
    private void removeRelicCoins(Player player, int amount) {
        ResourceLocation relicCoinId = ResourceLocation.fromNamespaceAndPath("cobblemon", "relic_coin");
        int remaining = amount;

        // First, try to remove from inventory
        for (int i = 0; i < player.getInventory().items.size() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().items.get(i);
            ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId.equals(relicCoinId)) {
                int removeCount = Math.min(remaining, stack.getCount());
                stack.shrink(removeCount);
                remaining -= removeCount;
            }
        }

        // If still need more, remove from bag
        if (remaining > 0) {
            for (int i = 0; i < player.getInventory().items.size(); i++) {
                ItemStack stack = player.getInventory().items.get(i);
                if (stack.getItem() instanceof net.fit.cobblemonmerchants.item.custom.RelicCoinBagItem) {
                    int bagCoins = stack.getOrDefault(net.fit.cobblemonmerchants.item.component.ModDataComponents.RELIC_COIN_COUNT.get(), 0);
                    int removeCount = Math.min(remaining, bagCoins);
                    stack.set(net.fit.cobblemonmerchants.item.component.ModDataComponents.RELIC_COIN_COUNT.get(), bagCoins - removeCount);
                    remaining -= removeCount;
                    break; // Only one bag should exist
                }
            }
        }
    }

    /**
     * Check if an ItemStack or ItemRequirement is for relic coins
     */
    private boolean isRelicCoin(ItemStack stack) {
        ResourceLocation relicCoinId = ResourceLocation.fromNamespaceAndPath("cobblemon", "relic_coin");
        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId.equals(relicCoinId);
    }

    private boolean isRelicCoin(net.fit.cobblemonmerchants.merchant.config.ItemRequirement requirement) {
        ResourceLocation relicCoinId = ResourceLocation.fromNamespaceAndPath("cobblemon", "relic_coin");
        // Get the display stack and check if it's a relic coin
        ItemStack displayStack = requirement.getDisplayStack();
        if (displayStack.isEmpty()) {
            return false;
        }
        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(displayStack.getItem());
        return itemId.equals(relicCoinId);
    }
}
