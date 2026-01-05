package net.fit.cobblemonmerchants.item.menu;

import net.fit.cobblemonmerchants.item.component.ModDataComponents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
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

    // Constructor for client side
    public RelicCoinBagMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, playerInventory.getSelected());
    }

    // Constructor for server side
    public RelicCoinBagMenu(int containerId, Inventory playerInventory, ItemStack bagStack) {
        super(ModMenuTypes.RELIC_COIN_BAG_MENU.get(), containerId);
        this.player = playerInventory.player;
        this.bagStack = bagStack;
        this.bagSlot = playerInventory.selected;

        // Add coin withdraw slot in the center (slot 0, row 1, col 4)
        // X = 8 + (4 * 18) = 80, Y = 18 + (1 * 18) = 36
        this.addSlot(new CoinWithdrawSlot(this, 80, 36));

        // Add toggle button slot (slot 1, row 2, col 4 - directly below coin slot)
        // X = 8 + (4 * 18) = 80, Y = 18 + (2 * 18) = 54
        this.addSlot(new ToggleSlot(this, 80, 54));

        // Add player inventory slots
        addPlayerInventorySlots(playerInventory);
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
     * Gets the current number of coins from bag NBT
     */
    public int getCoinCount() {
        return bagStack.getOrDefault(ModDataComponents.RELIC_COIN_COUNT.get(), 0);
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
        return bagStack.getOrDefault(ModDataComponents.AUTO_PICKUP_ENABLED.get(), false);
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
    public void clicked(int slotId, int button, @NotNull net.minecraft.world.inventory.ClickType clickType, @NotNull Player player) {
        // Handle clicking on the toggle slot (slot 1)
        if (slotId == 1) {
            // Toggle auto-pickup on any click
            toggleAutoPickup();
            return;
        }

        // Handle special clicking for the coin withdraw slot (slot 0)
        if (slotId == 0) {
            // Don't allow interaction if bag has 0 coins
            if (getCoinCount() <= 0) {
                ItemStack carried = getCarried();
                // Only allow depositing coins when bag is empty
                ResourceLocation carriedId = carried.isEmpty() ? null :
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(carried.getItem());
                boolean holdingCoins = carriedId != null && carriedId.toString().equals("cobblemon:relic_coin");

                if (holdingCoins && clickType == net.minecraft.world.inventory.ClickType.PICKUP) {
                    if (button == 1) {
                        // Right-click: deposit 1 coin
                        addCoins(1);
                        carried.shrink(1);
                        setCarried(carried);

                        // Update display
                        if (this.slots.get(0) instanceof CoinWithdrawSlot coinSlot) {
                            coinSlot.updateDisplayStack();
                        }
                        return;
                    } else if (button == 0) {
                        // Left-click with coins: deposit full stack
                        addCoins(carried.getCount());
                        setCarried(ItemStack.EMPTY);

                        // Update display
                        if (this.slots.get(0) instanceof CoinWithdrawSlot coinSlot) {
                            coinSlot.updateDisplayStack();
                        }
                        return;
                    }
                }
                // Don't allow withdrawing from empty bag
                return;
            }

            ItemStack carried = getCarried();

            // Check if clicking with relic coins in cursor
            ResourceLocation carriedId = carried.isEmpty() ? null :
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(carried.getItem());
            boolean holdingCoins = carriedId != null && carriedId.toString().equals("cobblemon:relic_coin");

            if (clickType == net.minecraft.world.inventory.ClickType.PICKUP) {
                if (holdingCoins) {
                    // Right-click (button 1) or Left-click (button 0) with coins in cursor
                    if (button == 1) {
                        // Right-click: deposit 1 coin
                        addCoins(1);
                        carried.shrink(1);
                        setCarried(carried);

                        // Update display
                        if (this.slots.get(0) instanceof CoinWithdrawSlot coinSlot) {
                            coinSlot.updateDisplayStack();
                        }
                        return;
                    } else if (button == 0) {
                        // Left-click with coins: deposit full stack
                        addCoins(carried.getCount());
                        setCarried(ItemStack.EMPTY);

                        // Update display
                        if (this.slots.get(0) instanceof CoinWithdrawSlot coinSlot) {
                            coinSlot.updateDisplayStack();
                        }
                        return;
                    }
                } else if (carried.isEmpty()) {
                    // Left-click without holding: withdraw full stack (64 coins)
                    if (button == 0) {
                        int toWithdraw = Math.min(getCoinCount(), 64);
                        net.minecraft.world.item.Item relicCoinItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                            ResourceLocation.fromNamespaceAndPath("cobblemon", "relic_coin"));

                        if (relicCoinItem != null && relicCoinItem != net.minecraft.world.item.Items.AIR) {
                            removeCoins(toWithdraw);
                            setCarried(new ItemStack(relicCoinItem, toWithdraw));

                            // Update display
                            if (this.slots.get(0) instanceof CoinWithdrawSlot coinSlot) {
                                coinSlot.updateDisplayStack();
                            }
                        }
                        return;
                    }
                    // Right-click without holding: withdraw half stack (32 coins)
                    else if (button == 1) {
                        int toWithdraw = Math.min(getCoinCount(), 32);
                        net.minecraft.world.item.Item relicCoinItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                            ResourceLocation.fromNamespaceAndPath("cobblemon", "relic_coin"));

                        if (relicCoinItem != null && relicCoinItem != net.minecraft.world.item.Items.AIR) {
                            removeCoins(toWithdraw);
                            setCarried(new ItemStack(relicCoinItem, toWithdraw));

                            // Update display
                            if (this.slots.get(0) instanceof CoinWithdrawSlot coinSlot) {
                                coinSlot.updateDisplayStack();
                            }
                        }
                        return;
                    }
                }
            } else if (clickType == net.minecraft.world.inventory.ClickType.QUICK_MOVE) {
                // Shift-click: withdraw exactly 1 stack (64 coins) to inventory - no rapid fire
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

                        // Update display
                        if (this.slots.get(0) instanceof CoinWithdrawSlot coinSlot) {
                            coinSlot.updateDisplayStack();
                        }
                    }
                }
                return;
            }
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
