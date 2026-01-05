package net.fit.cobblemonmerchants.item.menu;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Special slot that displays a green/red glass pane toggle for auto-pickup
 * Green = auto-pickup enabled, Red = auto-pickup disabled
 */
public class ToggleSlot extends Slot {
    private final RelicCoinBagMenu menu;

    public ToggleSlot(RelicCoinBagMenu menu, int x, int y) {
        super(new SimpleContainer(1), 0, x, y);
        this.menu = menu;
    }

    @Override
    public boolean mayPlace(@NotNull ItemStack stack) {
        // Don't allow placing items in this slot
        return false;
    }

    @Override
    public void set(@NotNull ItemStack stack) {
        // Don't allow setting items in this slot
    }

    @Override
    public @NotNull ItemStack remove(int amount) {
        // Don't allow removing items from this slot
        return ItemStack.EMPTY;
    }

    @Override
    public void onTake(@NotNull Player player, @NotNull ItemStack stack) {
        // Don't allow taking items from this slot
    }

    @Override
    public int getMaxStackSize() {
        return 0;
    }

    @Override
    public boolean mayPickup(@NotNull Player player) {
        // Don't allow picking up
        return false;
    }

    @Override
    public @NotNull ItemStack getItem() {
        // Return empty - rendering is handled by screen
        return ItemStack.EMPTY;
    }

    @Override
    public boolean hasItem() {
        // Return false - no actual item in slot
        return false;
    }
}
