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
            // Open the coin bag GUI
            openBagScreen(serverPlayer, stack);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
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

        // Add coin count below soulbound
        String formattedCount = net.fit.cobblemonmerchants.item.client.CoinCountRenderer.formatCoinCount(coinCount);
        tooltipComponents.add(Component.literal("Coins: " + formattedCount).withStyle(ChatFormatting.GOLD));

        // Add blank line before flavor text
        tooltipComponents.add(Component.literal(""));

        // Add flavor text in dark gray italic
        tooltipComponents.add(Component.literal("The burden of avarice persists through death~")
            .withStyle(ChatFormatting.DARK_GRAY)
            .withStyle(ChatFormatting.ITALIC));

        // Add blank line after flavor text
        tooltipComponents.add(Component.literal(""));
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
