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
        // Strategy: Cover entire 3x9 grid except:
        // - Center slot (coin slot at row 1, col 4)
        // - Top right slot (toggle at row 0, col 8)

        // Section 1: Left side (columns 0-3, all 3 rows)
        guiGraphics.fill(x + 7, y + 17, x + 7 + 4 * 18, y + 17 + 3 * 18, 0xAA8B8B8B);

        // Section 2: Right side columns 5-7, all 3 rows
        guiGraphics.fill(x + 7 + 5 * 18, y + 17, x + 7 + 8 * 18, y + 17 + 3 * 18, 0xAA8B8B8B);

        // Section 3: Far right column (col 8) rows 1-2 (below toggle)
        guiGraphics.fill(x + 7 + 8 * 18, y + 17 + 18, x + 7 + 9 * 18, y + 17 + 3 * 18, 0xAA8B8B8B);

        // Section 4: Center column (col 4) rows 0 and 2 (above and below coin slot)
        guiGraphics.fill(x + 7 + 4 * 18, y + 17, x + 7 + 5 * 18, y + 17 + 18, 0xAA8B8B8B); // Row 0
        guiGraphics.fill(x + 7 + 4 * 18, y + 17 + 2 * 18, x + 7 + 5 * 18, y + 17 + 3 * 18, 0xAA8B8B8B); // Row 2
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Render relic coin icon and count directly (like merchant GUI does)
        // This is called every frame, so it will update in real-time
        // NOTE: We render this AFTER super.render() so it appears on top
        renderCoinSlot(guiGraphics);

        // Render toggle button (green/red glass pane)
        renderToggleButton(guiGraphics);

        this.renderTooltip(guiGraphics, mouseX, mouseY);

        // Render coin count tooltip when hovering over the center slot
        renderCoinTooltip(guiGraphics, mouseX, mouseY);

        // Render toggle tooltip when hovering over the toggle slot
        renderToggleTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderSlot(@NotNull GuiGraphics guiGraphics, @NotNull net.minecraft.world.inventory.Slot slot) {
        // Skip rendering for coin slot (slot 0) and toggle slot (slot 1) - we handle those manually
        if (slot.index == 0 || slot.index == 1) {
            return;
        }
        super.renderSlot(guiGraphics, slot);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // Force the screen to refresh every tick to show real-time coin count updates
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

    /**
     * Renders the toggle button (green or red glass pane)
     */
    private void renderToggleButton(GuiGraphics guiGraphics) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // Toggle slot position (top right - row 0, col 8)
        int slotX = x + 8 + 8 * 18; // x + 8 + 144 = x + 152
        int slotY = y + 18 + 0 * 18; // y + 18

        // Get the appropriate glass pane based on autoPickup mode state
        boolean autoPickupEnabled = menu.isAutoPickupEnabled();
        net.minecraft.world.item.Item glassPane;

        if (autoPickupEnabled) {
            // Green glass pane - auto pickup mode ON
            glassPane = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "lime_stained_glass_pane"));
        } else {
            // Red glass pane - auto pickup mode OFF
            glassPane = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "red_stained_glass_pane"));
        }

        if (glassPane != null && glassPane != net.minecraft.world.item.Items.AIR) {
            ItemStack displayStack = new ItemStack(glassPane, 1);
            guiGraphics.renderItem(displayStack, slotX, slotY);
        }
    }

    /**
     * Renders tooltip when hovering over the toggle button
     */
    private void renderToggleTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // Toggle slot position (top right - row 0, col 8)
        int slotX = x + 8 + 8 * 18; // x + 8 + 144 = x + 152
        int slotY = y + 18 + 0 * 18; // y + 18

        // Check if mouse is hovering over the toggle slot (16x16 area)
        if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
            boolean autoPickupEnabled = menu.isAutoPickupEnabled();

            Component tooltip;
            if (autoPickupEnabled) {
                tooltip = Component.literal("Auto Pickup: ON")
                    .withStyle(net.minecraft.ChatFormatting.GREEN);
            } else {
                tooltip = Component.literal("Auto Pickup: OFF")
                    .withStyle(net.minecraft.ChatFormatting.RED);
            }

            // Render the tooltip
            guiGraphics.renderTooltip(this.font, tooltip, mouseX, mouseY);
        }
    }
}
