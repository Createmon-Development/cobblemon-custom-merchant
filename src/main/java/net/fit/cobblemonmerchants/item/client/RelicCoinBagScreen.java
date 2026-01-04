package net.fit.cobblemonmerchants.item.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.fit.cobblemonmerchants.item.menu.RelicCoinBagMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Screen for the Relic Coin Bag GUI
 * Shows a chest-like interface with only the center slot active
 */
public class RelicCoinBagScreen extends AbstractContainerScreen<RelicCoinBagMenu> {
    // Use the generic container texture
    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/container/generic_54.png");

    public RelicCoinBagScreen(RelicCoinBagMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        // Match merchant GUI dimensions: 114 + 3 rows * 18 = 168
        this.imageHeight = 114 + 3 * 18; // 168
        this.imageWidth = 176;
        this.inventoryLabelY = this.imageHeight - 94; // Position for "Inventory" label
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // Draw the chest background (same as merchant GUI)
        // Top part: chest slots (3 rows = 54 pixels + 17 pixels for border)
        guiGraphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, 3 * 18 + 17);
        // Bottom part: player inventory (96 pixels)
        guiGraphics.blit(TEXTURE, x, y + 3 * 18 + 17, 0, 126, this.imageWidth, 96);

        // Draw gray overlay covering all disabled slots and grid lines
        // Strategy: Cover entire 3x9 grid except center slot (row 1, col 4)
        // Slot grid starts at (x+7, y+17) and each slot is 18x18 including borders

        // Draw three large sections to create a seamless flat gray texture

        // Section 1: Left side (columns 0-3, all 3 rows)
        guiGraphics.fill(x + 7, y + 17, x + 7 + 4 * 18, y + 17 + 3 * 18, 0xAA8B8B8B);

        // Section 2: Right side (columns 5-8, all 3 rows)
        guiGraphics.fill(x + 7 + 5 * 18, y + 17, x + 7 + 9 * 18, y + 17 + 3 * 18, 0xAA8B8B8B);

        // Section 3: Center column above and below the coin slot
        // Column 4, row 0 (above coin)
        guiGraphics.fill(x + 7 + 4 * 18, y + 17, x + 7 + 5 * 18, y + 17 + 18, 0xAA8B8B8B);
        // Column 4, row 2 (below coin)
        guiGraphics.fill(x + 7 + 4 * 18, y + 17 + 2 * 18, x + 7 + 5 * 18, y + 17 + 3 * 18, 0xAA8B8B8B);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Render relic coin icon and count directly (like merchant GUI does)
        renderCoinSlot(guiGraphics);

        this.renderTooltip(guiGraphics, mouseX, mouseY);

        // Render coin count tooltip when hovering over the center slot
        renderCoinTooltip(guiGraphics, mouseX, mouseY);
    }

    /**
     * Renders tooltip showing coin count when hovering over the center slot
     */
    private void renderCoinTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // Center slot position (row 1, col 4)
        int slotX = x + 8 + 4 * 18;
        int slotY = y + 18 + 1 * 18;

        // Check if mouse is hovering over the center slot (16x16 area)
        if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
            int coinCount = menu.getCoinCount();
            String formattedCount = CoinCountRenderer.formatCoinCountExact(coinCount);

            // Create tooltip text (exact count with commas)
            Component tooltip = Component.literal("Relic Coins: " + formattedCount)
                .withStyle(net.minecraft.ChatFormatting.GOLD);

            // Render the tooltip
            guiGraphics.renderTooltip(this.font, tooltip, mouseX, mouseY);
        }
    }

    /**
     * Renders the relic coin icon and count directly on the center slot
     * Similar to how MerchantTradeScreen renders trade items
     */
    private void renderCoinSlot(GuiGraphics guiGraphics) {
        int coinCount = menu.getCoinCount();

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // Center slot position (row 1, col 4)
        int slotX = x + 8 + 4 * 18;
        int slotY = y + 18 + 1 * 18;

        // Get relic coin item
        net.minecraft.world.item.Item relicCoinItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", "relic_coin"));

        if (relicCoinItem != null && relicCoinItem != net.minecraft.world.item.Items.AIR) {
            // Create display stack with count of 1 (to prevent vanilla count rendering)
            ItemStack displayStack = new ItemStack(relicCoinItem, 1);

            // Render the item sprite directly (like merchants do)
            guiGraphics.renderItem(displayStack, slotX, slotY);

            // Render custom count text on top with high z-level
            String countText = CoinCountRenderer.formatCoinCount(coinCount);
            int color = coinCount > 0 ? 0xFFFFFF : 0x808080; // White if has coins, gray if empty

            // Push pose to render at higher z-level (above the item sprite)
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0, 0.0, 200.0); // Render above item (items are at z=100)

            // Render count in bottom-right corner (like vanilla stack counts)
            guiGraphics.drawString(
                this.font,
                countText,
                slotX + 17 - this.font.width(countText),
                slotY + 9,
                color,
                true // Drop shadow
            );

            guiGraphics.pose().popPose();
        }
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Render title
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        // Render "Inventory" label
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);
    }
}
