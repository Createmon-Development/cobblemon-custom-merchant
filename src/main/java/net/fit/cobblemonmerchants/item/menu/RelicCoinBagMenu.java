package net.fit.cobblemonmerchants.item.menu;

import net.fit.cobblemonmerchants.item.component.ModDataComponents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Menu for the Relic Coin Bag
 * Displays a chest-like GUI with a coin counter in the center
 * Coins are stored in the bag's NBT data
 */
public class RelicCoinBagMenu extends AbstractContainerMenu {
    private final Player player;
    private final ItemStack bagStack;
    private final int bagSlot;
    private final ContainerData data;

    // Constructor for client side
    public RelicCoinBagMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, playerInventory.getSelected(), new SimpleContainerData(1));
    }

    // Constructor for server side
    public RelicCoinBagMenu(int containerId, Inventory playerInventory, ItemStack bagStack) {
        this(containerId, playerInventory, bagStack, new SimpleContainerData(1));
    }

    // Constructor with ContainerData for synchronization
    private RelicCoinBagMenu(int containerId, Inventory playerInventory, ItemStack bagStack, ContainerData data) {
        super(ModMenuTypes.RELIC_COIN_BAG_MENU.get(), containerId);
        this.player = playerInventory.player;
        this.bagStack = bagStack;
        this.bagSlot = playerInventory.selected;
        this.data = data;

        // Sync coin count from bag to container data
        this.data.set(0, getCoinCount());

        // Add coin withdraw slot in the center (slot 0, row 1, col 4)
        // X = 8 + (4 * 18) = 80, Y = 18 + (1 * 18) = 36
        this.addSlot(new CoinWithdrawSlot(this, 80, 36));

        // Add toggle button slot in top right corner (slot 1, row 0, col 8)
        // X = 8 + (8 * 18) = 152, Y = 18 + (0 * 18) = 18
        this.addSlot(new ToggleSlot(this, 152, 18));

        // Add player inventory slots
        addPlayerInventorySlots(playerInventory);

        // Add container data for client-server sync
        this.addDataSlots(this.data);
    }

    private void addPlayerInventorySlots(Inventory playerInventory) {
        // Player inventory slots (3 rows of 9)
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

    /**
     * Gets the current number of coins from bag NBT (server) or synced data (client)
     */
    public int getCoinCount() {
        if (player.level().isClientSide) {
            // On client, use synced data
            return data.get(0);
        } else {
            // On server, read from bag
            return bagStack.getOrDefault(ModDataComponents.RELIC_COIN_COUNT.get(), 0);
        }
    }

    /**
     * Sets the number of coins in bag NBT
     */
    public void setCoinCount(int count) {
        bagStack.set(ModDataComponents.RELIC_COIN_COUNT.get(), Math.max(0, count));
    }

    /**
     * Adds coins to the bag
     */
    public void addCoins(int amount) {
        int current = getCoinCount();
        setCoinCount(current + amount);
    }

    /**
     * Removes coins from the bag
     * @return true if successful, false if not enough coins
     */
    public boolean removeCoins(int amount) {
        int current = getCoinCount();
        if (current >= amount) {
            setCoinCount(current - amount);
            return true;
        }
        return false;
    }

    /**
     * Gets the auto-pickup toggle state
     * @return true if auto-pickup is enabled, false otherwise
     */
    public boolean isAutoPickupEnabled() {
        return bagStack.getOrDefault(ModDataComponents.AUTO_PICKUP_ENABLED.get(), true);
    }

    /**
     * Sets the auto-pickup toggle state
     */
    public void setAutoPickupEnabled(boolean enabled) {
        bagStack.set(ModDataComponents.AUTO_PICKUP_ENABLED.get(), enabled);
    }

    /**
     * Toggles the auto-pickup state
     */
    public void toggleAutoPickup() {
        setAutoPickupEnabled(!isAutoPickupEnabled());
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();

        // Sync coin count from bag to client every tick
        if (!player.level().isClientSide) {
            int currentCount = bagStack.getOrDefault(ModDataComponents.RELIC_COIN_COUNT.get(), 0);
            this.data.set(0, currentCount);
            player.getInventory().setChanged();
        }
    }

    @Override
    public void clicked(int slotId, int button, @NotNull net.minecraft.world.inventory.ClickType clickType, @NotNull Player player) {
        // Handle clicking on the toggle slot (slot 1)
        if (slotId == 1) {
            // Toggle auto-pickup on any click
            toggleAutoPickup();
            return;
        }

        // Handle special clicking for the coin withdraw slot (slot 0)
        if (slotId == 0) {
            ItemStack carried = getCarried();

            // Check if clicking with relic coins in cursor
            ResourceLocation carriedId = carried.isEmpty() ? null :
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(carried.getItem());
            boolean holdingCoins = carriedId != null && carriedId.toString().equals("cobblemon:relic_coin");

            if (holdingCoins) {
                // Clicking with coins: deposit them (works for any click type)
                addCoins(carried.getCount());
                setCarried(ItemStack.EMPTY);
            } else if (carried.isEmpty() && getCoinCount() > 0) {
                // Empty cursor - withdraw coins
                if (clickType == net.minecraft.world.inventory.ClickType.PICKUP) {
                    net.minecraft.world.item.Item relicCoinItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("cobblemon", "relic_coin"));

                    if (relicCoinItem != null && relicCoinItem != net.minecraft.world.item.Items.AIR) {
                        int toWithdraw;
                        if (button == 0) {
                            // Left-click: withdraw full stack (64 coins)
                            toWithdraw = Math.min(getCoinCount(), 64);
                        } else {
                            // Right-click: withdraw half stack (32 coins)
                            toWithdraw = Math.min(getCoinCount(), 32);
                        }
                        removeCoins(toWithdraw);
                        setCarried(new ItemStack(relicCoinItem, toWithdraw));
                    }
                } else if (clickType == net.minecraft.world.inventory.ClickType.QUICK_MOVE) {
                    // Shift-click: withdraw exactly 1 stack (64 coins) to inventory
                    int toWithdraw = Math.min(getCoinCount(), 64);
                    net.minecraft.world.item.Item relicCoinItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("cobblemon", "relic_coin"));

                    if (relicCoinItem != null && relicCoinItem != net.minecraft.world.item.Items.AIR) {
                        ItemStack coinStack = new ItemStack(relicCoinItem, toWithdraw);

                        // Try to add exactly this stack to inventory
                        if (!player.getInventory().add(coinStack)) {
                            // Inventory full, don't remove coins
                            return;
                        }

                        // Only remove the coins that were successfully added
                        int coinsAdded = toWithdraw - coinStack.getCount();
                        if (coinsAdded > 0) {
                            removeCoins(coinsAdded);
                        }
                    }
                }
            }

            // ALWAYS return for slot 0 to prevent vanilla behavior
            return;
        }

        // For all other slots, use default behavior
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = this.slots.get(index);

        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack slotStack = slot.getItem();

        // Slots 0 and 1 are handled by clicked() method (coin slot and toggle slot)
        if (index == 0 || index == 1) {
            return ItemStack.EMPTY;
        }

        // Check if it's a relic coin from player inventory - deposit it
        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM
            .getKey(slotStack.getItem());

        if (itemId != null && itemId.toString().equals("cobblemon:relic_coin")) {
            // Add coins to bag and remove from slot
            int coinAmount = slotStack.getCount();
            addCoins(coinAmount);
            slotStack.shrink(coinAmount);
            slot.setChanged();

            // Update the coin display slot
            if (this.slots.get(0) instanceof CoinWithdrawSlot coinSlot) {
                coinSlot.updateDisplayStack();
            }

            return ItemStack.EMPTY;
        }

        // Not a relic coin - try normal shift-click behavior to move it around inventory
        if (index >= 2 && index < this.slots.size()) {
            // From player inventory to hotbar or vice versa
            if (index < 29) {
                // From main inventory to hotbar
                if (!this.moveItemStackTo(slotStack, 29, 38, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // From hotbar to main inventory
                if (!this.moveItemStackTo(slotStack, 2, 29, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (slotStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        // Check that player still has the bag in their hand
        ItemStack heldItem = player.getInventory().getItem(bagSlot);
        return heldItem == bagStack;
    }

    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);
        // Menu closed - texture will revert via client-side model predicate
    }
}
