package net.fit.cobblemonmerchants.item.custom;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Relic Coin Bag - stores unlimited relic coins
 * - Right-click to open GUI
 * - Texture changes when opened (handled client-side via model predicate)
 * - Soulbound - stays in inventory on death
 */
public class RelicCoinBagItem extends Item {

    /**
     * Toggle for enchantment glow effect
     * Set to false to disable the enchantment glint
     */
    private static final boolean ENABLE_ENCHANTMENT_GLOW = false;

    public RelicCoinBagItem(Properties properties) {
        super(properties.fireResistant()); // Make bags immune to fire/lava
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            // If player is sneaking (shift-right-click), deposit all relic coins from inventory
            if (player.isCrouching()) {
                int coinsDeposited = depositAllCoins(serverPlayer, stack);
                if (coinsDeposited > 0) {
                    // Play pickup sound to indicate success
                    player.playSound(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 0.5F, 1.0F);
                    return InteractionResultHolder.success(stack);
                }
                // If no coins were deposited, still open the GUI
            }

            // Check if offhand is empty - if so, pull out a stack instead of opening GUI
            InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack otherHandStack = player.getItemInHand(otherHand);

            if (otherHandStack.isEmpty() && !player.isCrouching()) {
                // Try to withdraw a stack of coins
                int withdrawn = withdrawCoins(serverPlayer, stack, 64);
                if (withdrawn > 0) {
                    // Play pickup sound
                    player.playSound(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 0.5F, 1.0F);
                    return InteractionResultHolder.success(stack);
                }
            }

            // Normal right-click: open the coin bag GUI
            openBagScreen(serverPlayer, stack);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * Withdraws coins from the bag and gives them to the player
     * @param amount the maximum number of coins to withdraw
     * @return the actual number of coins withdrawn
     */
    private int withdrawCoins(ServerPlayer player, ItemStack bagStack, int amount) {
        int currentCoins = bagStack.getOrDefault(
            net.fit.cobblemonmerchants.item.component.ModDataComponents.RELIC_COIN_COUNT.get(), 0);

        if (currentCoins <= 0) {
            return 0; // No coins to withdraw
        }

        // Calculate how many coins to withdraw
        int toWithdraw = Math.min(currentCoins, amount);

        // Get relic coin item
        net.minecraft.world.item.Item relicCoinItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", "relic_coin"));

        if (relicCoinItem == null || relicCoinItem == net.minecraft.world.item.Items.AIR) {
            return 0;
        }

        // Create coin stack
        ItemStack coinStack = new ItemStack(relicCoinItem, toWithdraw);

        // Try to add to player's inventory
        if (!player.getInventory().add(coinStack)) {
            // Inventory full, drop at player's feet
            player.drop(coinStack, false);
        }

        // Remove coins from bag
        bagStack.set(
            net.fit.cobblemonmerchants.item.component.ModDataComponents.RELIC_COIN_COUNT.get(),
            currentCoins - toWithdraw);

        return toWithdraw;
    }

    /**
     * Deposits all relic coins from player's inventory into the bag
     * @return the number of coins deposited
     */
    private int depositAllCoins(ServerPlayer player, ItemStack bagStack) {
        int totalDeposited = 0;
        net.minecraft.resources.ResourceLocation relicCoinId =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", "relic_coin");

        // Search through all inventory slots
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack slotStack = player.getInventory().getItem(i);

            // Skip the bag itself
            if (slotStack == bagStack) {
                continue;
            }

            // Check if this slot contains relic coins
            net.minecraft.resources.ResourceLocation itemId =
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(slotStack.getItem());

            if (itemId != null && itemId.equals(relicCoinId)) {
                // Add coins to bag
                int coinCount = slotStack.getCount();
                totalDeposited += coinCount;

                // Remove coins from inventory
                player.getInventory().removeItem(i, coinCount);
            }
        }

        // Update bag's coin count if any were deposited
        if (totalDeposited > 0) {
            int currentCoins = bagStack.getOrDefault(
                net.fit.cobblemonmerchants.item.component.ModDataComponents.RELIC_COIN_COUNT.get(), 0);
            bagStack.set(
                net.fit.cobblemonmerchants.item.component.ModDataComponents.RELIC_COIN_COUNT.get(),
                currentCoins + totalDeposited);
        }

        return totalDeposited;
    }

    /**
     * Opens the coin bag GUI for a player
     */
    private void openBagScreen(ServerPlayer player, ItemStack bagStack) {
        player.openMenu(new MenuProvider() {
            @Override
            public @NotNull Component getDisplayName() {
                return Component.translatable("container.cobblemoncustommerchants.relic_coin_bag");
            }

            @Override
            public @NotNull AbstractContainerMenu createMenu(int containerId, @NotNull Inventory playerInventory, @NotNull Player player) {
                return new net.fit.cobblemonmerchants.item.menu.RelicCoinBagMenu(
                    containerId, playerInventory, bagStack);
            }
        });
    }

    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        // Add enchantment glint for soulbound effect (configurable)
        return ENABLE_ENCHANTMENT_GLOW;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        // Get coin count from bag
        int coinCount = stack.getOrDefault(net.fit.cobblemonmerchants.item.component.ModDataComponents.RELIC_COIN_COUNT.get(), 0);

        // Add "Soulbound" in dark purple
        tooltipComponents.add(Component.literal("Soulbound").withStyle(ChatFormatting.DARK_PURPLE));

        // Add coin count below soulbound (exact count with commas)
        String formattedCount = net.fit.cobblemonmerchants.item.client.CoinCountRenderer.formatCoinCountExact(coinCount);
        tooltipComponents.add(Component.literal("Coins: " + formattedCount).withStyle(ChatFormatting.GOLD));

        // Add blank line before flavor text
        tooltipComponents.add(Component.literal(""));

        // Add flavor text in dark gray italic (multi-line)
        tooltipComponents.add(Component.literal("The burden of avaraice persists through death,")
            .withStyle(ChatFormatting.DARK_GRAY)
            .withStyle(ChatFormatting.ITALIC));
        tooltipComponents.add(Component.literal("but only the heavist is safe from theft~")
            .withStyle(ChatFormatting.DARK_GRAY)
            .withStyle(ChatFormatting.ITALIC));

        // Add blank line after flavor text
        tooltipComponents.add(Component.literal(""));

        // Add usage hints
        tooltipComponents.add(Component.literal("Right-click (empty offhand) to withdraw stack")
            .withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.literal("Shift + Right-click to deposit all coins")
            .withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.literal("Right-click with coins in cursor to deposit")
            .withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.literal("Right-click to open")
            .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean overrideStackedOnOther(@NotNull ItemStack bagStack, @NotNull net.minecraft.world.inventory.Slot slot, @NotNull net.minecraft.world.inventory.ClickAction action, @NotNull Player player) {
        // Handle right-clicking on the bag with coins in cursor
        if (action == net.minecraft.world.inventory.ClickAction.SECONDARY) {
            ItemStack slotStack = slot.getItem();

            // Check if the slot contains relic coins
            net.minecraft.resources.ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(slotStack.getItem());
            if (itemId != null && itemId.toString().equals("cobblemon:relic_coin")) {
                // Deposit coins from slot into bag
                int coinCount = slotStack.getCount();
                int currentCoins = bagStack.getOrDefault(
                    net.fit.cobblemonmerchants.item.component.ModDataComponents.RELIC_COIN_COUNT.get(), 0);
                bagStack.set(
                    net.fit.cobblemonmerchants.item.component.ModDataComponents.RELIC_COIN_COUNT.get(),
                    currentCoins + coinCount);

                // Remove coins from slot
                slot.set(ItemStack.EMPTY);

                // Play pickup sound
                player.playSound(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 0.5F, 1.0F);

                return true; // Handled
            }
        }

        return super.overrideStackedOnOther(bagStack, slot, action, player);
    }

    @Override
    public boolean overrideOtherStackedOnMe(@NotNull ItemStack bagStack, @NotNull ItemStack otherStack, @NotNull net.minecraft.world.inventory.Slot slot, @NotNull net.minecraft.world.inventory.ClickAction action, @NotNull Player player, @NotNull net.minecraft.world.entity.SlotAccess cursorStackReference) {
        // Handle clicking coins on the bag in inventory
        if (action == net.minecraft.world.inventory.ClickAction.SECONDARY && !otherStack.isEmpty()) {
            // Check if the other stack is relic coins
            net.minecraft.resources.ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(otherStack.getItem());
            if (itemId != null && itemId.toString().equals("cobblemon:relic_coin")) {
                // Deposit coins into bag
                int coinCount = otherStack.getCount();
                int currentCoins = bagStack.getOrDefault(
                    net.fit.cobblemonmerchants.item.component.ModDataComponents.RELIC_COIN_COUNT.get(), 0);
                bagStack.set(
                    net.fit.cobblemonmerchants.item.component.ModDataComponents.RELIC_COIN_COUNT.get(),
                    currentCoins + coinCount);

                // Remove coins from cursor
                cursorStackReference.set(ItemStack.EMPTY);

                // Play pickup sound
                player.playSound(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 0.5F, 1.0F);

                return true; // Handled
            }
        }

        return super.overrideOtherStackedOnMe(bagStack, otherStack, slot, action, player, cursorStackReference);
    }

    @Override
    public boolean onEntityItemUpdate(@NotNull ItemStack stack, net.minecraft.world.entity.item.ItemEntity entity) {
        // Make bag indestructible ONLY when it contains coins
        int coinCount = stack.getOrDefault(net.fit.cobblemonmerchants.item.component.ModDataComponents.RELIC_COIN_COUNT.get(), 0);
        if (coinCount > 0) {
            // Prevent all damage
            entity.setInvulnerable(true);
            // Never despawn
            entity.setUnlimitedLifetime();
        } else {
            // Remove protections if bag is empty
            entity.setInvulnerable(false);
            // Can't remove unlimited lifetime, but this is fine since empty bags are rare drops
        }
        return false;
    }
}
