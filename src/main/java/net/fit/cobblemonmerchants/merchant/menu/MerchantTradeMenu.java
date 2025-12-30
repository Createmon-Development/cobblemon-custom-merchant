package net.fit.cobblemonmerchants.merchant.menu;

import net.fit.cobblemonmerchants.merchant.CustomMerchantEntity;
import net.fit.cobblemonmerchants.network.SyncMerchantOffersPacket;
import net.minecraft.network.FriendlyByteBuf;
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
    private final int merchantId;
    private int lastOfferCount = -1; // Start at -1 to trigger initial sync

    // Constructor for client side
    public MerchantTradeMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        super(ModMenuTypes.MERCHANT_TRADE_MENU.get(), containerId);
        this.merchantId = extraData.readInt();
        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("CLIENT: Creating menu for merchant ID: {}", merchantId);
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

        // Check if player has the required items
        ItemStack costA = offer.getItemCostA().itemStack();
        ItemStack costB = offer.getCostB();

        // Count how many of each item the player has
        int countA = 0;
        int countB = 0;

        for (ItemStack stack : player.getInventory().items) {
            if (ItemStack.isSameItemSameComponents(stack, costA)) {
                countA += stack.getCount();
            }
            if (!costB.isEmpty() && ItemStack.isSameItemSameComponents(stack, costB)) {
                countB += stack.getCount();
            }
        }

        // Check if player has enough items
        if (countA < costA.getCount()) {
            return false;
        }
        if (!costB.isEmpty() && countB < costB.getCount()) {
            return false;
        }

        // Remove the cost items from player inventory
        removeItems(player.getInventory(), costA, costA.getCount());
        if (!costB.isEmpty()) {
            removeItems(player.getInventory(), costB, costB.getCount());
        }

        // Give the player the result item
        ItemStack result = offer.getResult().copy();
        if (!player.getInventory().add(result)) {
            // If inventory is full, drop the item
            player.drop(result, false);
        }

        // Update the offer usage
        offer.increaseUses();

        return true;
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

        // Sync offers to client when they change or on first broadcast (lastOfferCount == -1)
        if (merchant != null) {
            MerchantOffers currentOffers = merchant.getOffers();
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("SERVER: broadcastChanges called, offers: {}, lastCount: {}", currentOffers.size(), lastOfferCount);
            if (currentOffers.size() != lastOfferCount) {
                // Send BEFORE updating lastOfferCount
                sendOffersToPlayers();
                lastOfferCount = currentOffers.size();
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
}
