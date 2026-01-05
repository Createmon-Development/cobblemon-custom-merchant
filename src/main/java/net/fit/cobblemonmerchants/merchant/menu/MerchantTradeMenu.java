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
    private final Player menuPlayer; // The player who has this menu open (server-side only)

    // Daily reward display info (client-side)
    private boolean hasDailyRewardDisplay = false;
    private ItemStack dailyRewardItem = ItemStack.EMPTY;
    private int dailyRewardPosition = -1;
    private boolean dailyRewardClaimed = false;
    private String timeUntilReset = "";
    private int dailyRewardMinCount = 1;
    private int dailyRewardMaxCount = 1;
    private boolean dailyRewardSharedCooldown = true;
    private java.util.UUID merchantEntityUUID = null;

    // Constructor for client side
    public MerchantTradeMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        super(ModMenuTypes.MERCHANT_TRADE_MENU.get(), containerId);
        this.menuPlayer = null; // Client-side doesn't need player reference
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
            int outputCount = extraData.readInt(); // Uncapped output count for counts > 64
            int maxUses = extraData.readInt();
            int villagerXp = extraData.readInt();
            float priceMultiplier = extraData.readFloat();
            java.util.Optional<String> tradeDisplayName = extraData.readBoolean() ? java.util.Optional.of(extraData.readUtf()) : java.util.Optional.empty();
            java.util.Optional<Integer> position = extraData.readBoolean() ? java.util.Optional.of(extraData.readInt()) : java.util.Optional.empty();

            this.tradeEntries.add(new net.fit.cobblemonmerchants.merchant.config.MerchantConfig.TradeEntry(
                input, secondInput, output, outputCount, maxUses, villagerXp, priceMultiplier, tradeDisplayName, position,
                java.util.Optional.empty(), // variantOverrides not needed client-side
                false, // dailyReset not needed client-side
                java.util.Optional.empty() // variants not needed client-side
            ));
        }

        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("CLIENT: Received {} trade entries from server", this.tradeEntries.size());

        // Read daily reward display info
        this.hasDailyRewardDisplay = extraData.readBoolean();
        if (this.hasDailyRewardDisplay) {
            this.dailyRewardItem = ItemStack.STREAM_CODEC.decode(registryBuf);
            this.dailyRewardPosition = extraData.readInt();
            this.dailyRewardClaimed = extraData.readBoolean();
            this.timeUntilReset = extraData.readUtf();
            this.dailyRewardMinCount = extraData.readInt();
            this.dailyRewardMaxCount = extraData.readInt();
            this.dailyRewardSharedCooldown = extraData.readBoolean();
            this.merchantEntityUUID = extraData.readUUID();
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("CLIENT: Daily reward at position {}, claimed: {}, reset in: {}, count: {}-{}, sharedCooldown: {}",
                this.dailyRewardPosition, this.dailyRewardClaimed, this.timeUntilReset, this.dailyRewardMinCount, this.dailyRewardMaxCount, this.dailyRewardSharedCooldown);
        }

        // Try to get the merchant from the world
        if (playerInventory.player.level().getEntity(merchantId) instanceof CustomMerchantEntity entity) {
            this.merchant = entity;
            // Don't read offers from entity - wait for SyncMerchantOffersPacket from server
            // The entity on client side may not have offers synced
            this.offers = new MerchantOffers();
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("CLIENT: Found merchant, waiting for offers sync");
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
        this.menuPlayer = playerInventory.player; // Store player reference for syncing
        this.merchant = merchant;
        this.merchantId = merchant != null ? merchant.getId() : -1;
        this.tradeEntries = merchant != null ? merchant.getTradeEntries() : new java.util.ArrayList<>();

        // Copy offers and initialize daily reset trade usage based on player's usage
        MerchantOffers originalOffers = merchant != null ? merchant.getOffers() : new MerchantOffers();
        this.offers = new MerchantOffers();

        Player player = playerInventory.player;
        String merchantIdStr = merchant != null && merchant.getTraderId() != null
            ? merchant.getTraderId().toString() : "unknown";

        for (int i = 0; i < originalOffers.size(); i++) {
            MerchantOffer original = originalOffers.get(i);

            // Create a copy of the offer
            MerchantOffer copy = new MerchantOffer(
                original.getItemCostA(),
                original.getItemCostB(),
                original.getResult().copy(),
                original.getUses(),
                original.getMaxUses(),
                original.getXp(),
                original.getPriceMultiplier(),
                original.getDemand()
            );

            // For daily reset trades, initialize uses from DailyTradeResetManager
            if (i < tradeEntries.size() && tradeEntries.get(i).dailyReset()) {
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer &&
                    serverPlayer.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    net.fit.cobblemonmerchants.merchant.rewards.DailyTradeResetManager resetManager =
                        net.fit.cobblemonmerchants.merchant.rewards.DailyTradeResetManager.get(serverLevel);
                    int usesToday = resetManager.getUsesToday(player.getUUID(), merchantIdStr, i);

                    // Set the uses on the copy to reflect player's daily usage
                    for (int u = 0; u < usesToday; u++) {
                        copy.increaseUses();
                    }

                    net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info(
                        "SERVER: Initialized daily reset trade {} with uses={}/{} for player {}",
                        i, copy.getUses(), copy.getMaxUses(), player.getName().getString());
                }
            }

            this.offers.add(copy);
        }

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

    // Daily reward display getters
    public boolean hasDailyRewardDisplay() {
        return hasDailyRewardDisplay;
    }

    public ItemStack getDailyRewardItem() {
        return dailyRewardItem;
    }

    public int getDailyRewardPosition() {
        return dailyRewardPosition;
    }

    public boolean isDailyRewardClaimed() {
        return dailyRewardClaimed;
    }

    public String getTimeUntilReset() {
        return timeUntilReset;
    }

    public int getDailyRewardMinCount() {
        return dailyRewardMinCount;
    }

    public int getDailyRewardMaxCount() {
        return dailyRewardMaxCount;
    }

    public boolean isDailyRewardSharedCooldown() {
        return dailyRewardSharedCooldown;
    }

    public java.util.UUID getMerchantEntityUUID() {
        return merchantEntityUUID;
    }

    /**
     * Sets the daily reward as claimed (called after successful claim)
     */
    public void setDailyRewardClaimed(boolean claimed) {
        this.dailyRewardClaimed = claimed;
    }

    /**
     * Updates the time until reset string (for live updates)
     */
    public void updateTimeUntilReset(String time) {
        this.timeUntilReset = time;
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

        // Check if this is a broken trade (barrier block in input or output) - prevent trading
        if (offer.getResult().getItem() == net.minecraft.world.item.Items.BARRIER ||
            offer.getItemCostA().itemStack().getItem() == net.minecraft.world.item.Items.BARRIER ||
            (!offer.getCostB().isEmpty() && offer.getCostB().getItem() == net.minecraft.world.item.Items.BARRIER)) {
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.warn(
                "Player {} attempted to complete a broken trade (barrier item) at index {}",
                player.getName().getString(), tradeIndex);
            return false;
        }

        // Get the original trade entry for tag-based validation
        net.fit.cobblemonmerchants.merchant.config.MerchantConfig.TradeEntry tradeEntry =
            tradeIndex < tradeEntries.size() ? tradeEntries.get(tradeIndex) : null;

        if (tradeEntry == null) {
            // Fallback to vanilla validation for trades without TradeEntry (e.g., Black Market)
            return executeTradeLegacy(tradeIndex, player, offer);
        }

        // Check daily reset limits if enabled
        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info(
            "SERVER: Trade {} dailyReset={}, maxUses={}",
            tradeIndex, tradeEntry.dailyReset(), tradeEntry.maxUses());
        if (tradeEntry.dailyReset() && player instanceof ServerPlayer serverPlayer) {
            if (serverPlayer.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                net.fit.cobblemonmerchants.merchant.rewards.DailyTradeResetManager resetManager =
                    net.fit.cobblemonmerchants.merchant.rewards.DailyTradeResetManager.get(serverLevel);
                String merchantId = merchant.getTraderId() != null ? merchant.getTraderId().toString() : "unknown";
                int maxUses = tradeEntry.maxUses();
                int usesToday = resetManager.getUsesToday(player.getUUID(), merchantId, tradeIndex);

                net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info(
                    "SERVER: Daily trade check - player={}, merchant={}, trade={}, usesToday={}, maxUses={}, canUse={}",
                    player.getName().getString(), merchantId, tradeIndex, usesToday, maxUses, usesToday < maxUses);

                if (!resetManager.canUseTrade(player.getUUID(), merchantId, tradeIndex, maxUses)) {
                    // Player has used up their daily limit for this trade
                    net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info(
                        "SERVER: Player {} has reached daily limit for trade {} from merchant {}",
                        player.getName().getString(), tradeIndex, merchantId);
                    return false;
                }
            }
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

        // Update the offer usage counter for GUI display
        offer.increaseUses();
        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info(
            "SERVER: Trade {} uses incremented to {}/{} (dailyReset={})",
            tradeIndex, offer.getUses(), offer.getMaxUses(), tradeEntry.dailyReset());

        // Record daily trade use if enabled (for daily limit checking)
        if (tradeEntry.dailyReset() && player instanceof ServerPlayer serverPlayer) {
            if (serverPlayer.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                net.fit.cobblemonmerchants.merchant.rewards.DailyTradeResetManager resetManager =
                    net.fit.cobblemonmerchants.merchant.rewards.DailyTradeResetManager.get(serverLevel);
                String merchantId = merchant.getTraderId() != null ? merchant.getTraderId().toString() : "unknown";
                resetManager.recordTradeUse(player.getUUID(), merchantId, tradeIndex);
            }
        }

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
        // Use this.offers (the menu's copy with player-specific usage) not merchant.getOffers()
        boolean shouldSync = needsSync || this.offers.size() != lastOfferCount;

        if (shouldSync) {
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("SERVER: Syncing offers - count: {}, needsSync: {}",
                this.offers.size(), needsSync);
            sendOffersToPlayer();
            lastOfferCount = this.offers.size();
            needsSync = false;
        }
    }

    private void sendOffersToPlayer() {
        // Send the menu's offers (with player-specific usage counts) to the player who has this menu open
        // Use stored menuPlayer reference instead of searching - this ensures we can sync even
        // during initial menu creation before containerMenu is set
        if (menuPlayer instanceof ServerPlayer serverPlayer) {
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("SERVER: Syncing {} offers to player {} (containerId: {})",
                this.offers.size(), serverPlayer.getName().getString(), this.containerId);
            PacketDistributor.sendToPlayer(serverPlayer,
                new SyncMerchantOffersPacket(this.containerId, this.offers));
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
