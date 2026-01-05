package net.fit.cobblemonmerchants.item.menu;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Special slot that handles coin deposit/withdraw interactions
 * The visual display is handled by RelicCoinBagScreen.renderCoinSlot()
 */
public class CoinWithdrawSlot extends Slot {
    private final RelicCoinBagMenu menu;

    public CoinWithdrawSlot(RelicCoinBagMenu menu, int x, int y) {
        super(new SimpleContainer(1), 0, x, y);
        this.menu = menu;
    }

    /**
     * No longer needed - rendering is handled by the screen
     * Kept for compatibility with existing code that calls it
     */
    public void updateDisplayStack() {
        // Display is now handled by RelicCoinBagScreen.renderCoinSlot()
    }

    @Override
    public boolean mayPlace(@NotNull ItemStack stack) {
        // Only allow relic coins to be placed
        net.minecraft.resources.ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId != null && itemId.toString().equals("cobblemon:relic_coin");
    }

    @Override
    public void setChanged() {
        super.setChanged();
        updateDisplayStack();
    }

    @Override
    public void set(@NotNull ItemStack stack) {
        // This is called when player tries to place an item in the slot
        // We need to consume the coins from their cursor and add to bag
        if (!stack.isEmpty() && mayPlace(stack)) {
            // Add coins to bag
            menu.addCoins(stack.getCount());
            // Update the display
            updateDisplayStack();
        }
        // Don't call super.set() - we don't want to actually place the item
    }

    @Override
    public @NotNull ItemStack remove(int amount) {
        int coinCount = menu.getCoinCount();
        if (coinCount <= 0) {
            return ItemStack.EMPTY;
        }

        // Determine how many coins to withdraw
        int coinsToWithdraw;
        if (amount == Integer.MAX_VALUE) {
            // Normal pickup - take 1 coin
            coinsToWithdraw = 1;
        } else {
            // Shift-click or specific amount
            coinsToWithdraw = Math.min(amount, Math.min(coinCount, 64));
        }

        if (menu.removeCoins(coinsToWithdraw)) {
            net.minecraft.world.item.Item relicCoinItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("cobblemon", "relic_coin"));

            if (relicCoinItem != null && relicCoinItem != net.minecraft.world.item.Items.AIR) {
                updateDisplayStack();
                // Return a CLEAN stack without any NBT from the bag
                return new ItemStack(relicCoinItem, coinsToWithdraw);
            }
        }

        return ItemStack.EMPTY;
    }

    @Override
    public void onTake(@NotNull Player player, @NotNull ItemStack stack) {
        // Update display after taking
        updateDisplayStack();
        super.onTake(player, stack);
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public boolean mayPickup(@NotNull Player player) {
        // Allow picking up if there are coins
        return menu.getCoinCount() > 0;
    }

    @Override
    public @NotNull ItemStack getItem() {
        // Return a display item if there are coins, so vanilla knows there's something to click
        if (menu.getCoinCount() > 0) {
            net.minecraft.world.item.Item relicCoinItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("cobblemon", "relic_coin"));
            if (relicCoinItem != null && relicCoinItem != net.minecraft.world.item.Items.AIR) {
                return new ItemStack(relicCoinItem, 1);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean hasItem() {
        // Return true if there are coins available
        return menu.getCoinCount() > 0;
    }
}
