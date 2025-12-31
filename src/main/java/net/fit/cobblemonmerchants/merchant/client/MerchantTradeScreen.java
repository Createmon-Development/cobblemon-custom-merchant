package net.fit.cobblemonmerchants.merchant.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.fit.cobblemonmerchants.merchant.menu.MerchantTradeMenu;
import net.fit.cobblemonmerchants.network.TradeClickPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom chest-style GUI for merchant trades.
 * Displays trades as items in a chest-like interface.
 */
public class MerchantTradeScreen extends AbstractContainerScreen<MerchantTradeMenu> {
    private static final ResourceLocation CHEST_TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    private static final int TRADES_PER_ROW = 9;
    private static final int MAX_VISIBLE_ROWS = 3; // Single chest has 3 rows

    public MerchantTradeScreen(MerchantTradeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        // Vanilla chest dimensions: 176 width, 166 height (for 3 rows)
        this.imageHeight = 114 + MAX_VISIBLE_ROWS * 18; // 168
        this.imageWidth = 176;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, CHEST_TEXTURE);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // Draw the chest background
        guiGraphics.blit(CHEST_TEXTURE, x, y, 0, 0, this.imageWidth, MAX_VISIBLE_ROWS * 18 + 17);
        guiGraphics.blit(CHEST_TEXTURE, x, y + MAX_VISIBLE_ROWS * 18 + 17, 0, 126, this.imageWidth, 96);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Render trade items
        renderTradeItems(guiGraphics, mouseX, mouseY);

        // Render tooltips
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private void renderTradeItems(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        MerchantOffers offers = menu.getOffers();
        CobblemonMerchants.LOGGER.info("CLIENT: Rendering {} offers", offers.size());

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        for (int i = 0; i < Math.min(offers.size(), TRADES_PER_ROW * MAX_VISIBLE_ROWS); i++) {
            MerchantOffer offer = offers.get(i);
            int row = i / TRADES_PER_ROW;
            int col = i % TRADES_PER_ROW;

            int slotX = x + 8 + col * 18;
            int slotY = y + 18 + row * 18;

            // Draw the display item
            // For Black Market trades (where result is relic coins), show what the player is trading away
            ItemStack displayItem = getDisplayItem(offer);
            CobblemonMerchants.LOGGER.info("CLIENT: Rendering item {} at ({}, {})", displayItem, slotX, slotY);
            guiGraphics.renderItem(displayItem, slotX, slotY);
            guiGraphics.renderItemDecorations(this.font, displayItem, slotX, slotY);

            // Draw a red X if the trade is out of stock
            if (offer.isOutOfStock()) {
                guiGraphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x80FF0000);
            }
        }
    }

    @Override
    protected void renderTooltip(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderTooltip(guiGraphics, mouseX, mouseY);

        // Check if hovering over a trade item
        int hoveredTradeIndex = getHoveredTradeIndex(mouseX, mouseY);
        if (hoveredTradeIndex >= 0) {
            MerchantOffers offers = menu.getOffers();
            if (hoveredTradeIndex < offers.size()) {
                MerchantOffer offer = offers.get(hoveredTradeIndex);
                // Pass trade index for dynamic updates
                List<Component> tooltip = createTradeTooltip(offer, hoveredTradeIndex);
                guiGraphics.renderTooltip(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
            }
        }
    }

    private int getHoveredTradeIndex(int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        MerchantOffers offers = menu.getOffers();

        for (int i = 0; i < Math.min(offers.size(), TRADES_PER_ROW * MAX_VISIBLE_ROWS); i++) {
            int row = i / TRADES_PER_ROW;
            int col = i % TRADES_PER_ROW;

            int slotX = x + 8 + col * 18;
            int slotY = y + 18 + row * 18;

            if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                return i;
            }
        }

        return -1;
    }

    private List<Component> createTradeTooltip(MerchantOffer offer) {
        return createTradeTooltip(offer, -1);
    }

    private List<Component> createTradeTooltip(MerchantOffer offer, int tradeIndex) {
        List<Component> tooltip = new ArrayList<>();

        // Result item name
        tooltip.add(offer.getResult().getHoverName());

        // Separator
        tooltip.add(Component.empty());

        // Cost information
        tooltip.add(Component.literal("§6Cost:"));

        // Try to get TradeEntry for tag display
        java.util.List<net.fit.cobblemonmerchants.merchant.config.MerchantConfig.TradeEntry> tradeEntries =
            menu.getTradeEntries();

        if (tradeIndex >= 0 && tradeIndex < tradeEntries.size()) {
            net.fit.cobblemonmerchants.merchant.config.MerchantConfig.TradeEntry entry = tradeEntries.get(tradeIndex);

            // Display input requirement
            net.fit.cobblemonmerchants.merchant.config.ItemRequirement inputReq = entry.input();
            if (inputReq.isTag()) {
                String tagName = inputReq.getTag().location().getPath();
                String displayName = formatTagName(tagName);
                tooltip.add(Component.literal("  §7• §f" + inputReq.getCount() + "x §eAny " + displayName));
            } else {
                tooltip.add(Component.literal("  §7• §f" + inputReq.getCount() + "x §r")
                    .append(inputReq.getDisplayStack().getHoverName()));
            }

            // Display second input if exists
            if (entry.secondInput().isPresent()) {
                net.fit.cobblemonmerchants.merchant.config.ItemRequirement secondReq = entry.secondInput().get();
                if (secondReq.isTag()) {
                    String tagName = secondReq.getTag().location().getPath();
                    String displayName = formatTagName(tagName);
                    tooltip.add(Component.literal("  §7• §f" + secondReq.getCount() + "x §eAny " + displayName));
                } else {
                    tooltip.add(Component.literal("  §7• §f" + secondReq.getCount() + "x §r")
                        .append(secondReq.getDisplayStack().getHoverName()));
                }
            }
        } else {
            // Fallback to vanilla cost display
            ItemStack costA = offer.getItemCostA().itemStack();
            tooltip.add(Component.literal("  §7• §f" + costA.getCount() + "x §r")
                .append(costA.getHoverName()));

            ItemStack costB = offer.getCostB();
            if (!costB.isEmpty()) {
                tooltip.add(Component.literal("  §7• §f" + costB.getCount() + "x §r")
                    .append(costB.getHoverName()));
            }
        }

        // Uses remaining
        if (offer.getMaxUses() < Integer.MAX_VALUE / 2) {
            int usesRemaining = offer.getMaxUses() - offer.getUses();
            tooltip.add(Component.empty());
            if (usesRemaining > 0) {
                tooltip.add(Component.literal("§aUses remaining: " + usesRemaining));
            } else {
                tooltip.add(Component.literal("§cOut of stock!"));
            }
        }

        // Click to trade hint
        if (!offer.isOutOfStock()) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("§eClick to trade"));
        }

        return tooltip;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // Left click
            int tradeIndex = getHoveredTradeIndex((int) mouseX, (int) mouseY);
            if (tradeIndex >= 0) {
                // Send packet to server to execute trade
                TradeClickPacket.send(tradeIndex);

                // Play appropriate sound
                MerchantOffers offers = menu.getOffers();
                if (tradeIndex < offers.size()) {
                    MerchantOffer offer = offers.get(tradeIndex);
                    if (!offer.isOutOfStock() && canAffordTrade(offer, tradeIndex)) {
                        // Success sound (experience orb at 30% volume, slightly lower pitch for single clean note)
                        Minecraft.getInstance().getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                SoundEvents.EXPERIENCE_ORB_PICKUP, 0.3F, 0.9F
                            )
                        );
                    } else {
                        // Failure sound (button click at 60% volume)
                        Minecraft.getInstance().getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                SoundEvents.UI_BUTTON_CLICK.value(), 0.6F, 0.8F
                            )
                        );
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Gets the item to display for this offer.
     * For Black Market trades (result is relic coins), shows the item being traded.
     * For regular trades, shows the result.
     */
    private ItemStack getDisplayItem(MerchantOffer offer) {
        ItemStack result = offer.getResult();

        // Check if result is relic coins (Black Market trade)
        net.minecraft.resources.ResourceLocation relicCoinId =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", "relic_coin");
        net.minecraft.world.item.Item relicCoinItem =
            net.minecraft.core.registries.BuiltInRegistries.ITEM.get(relicCoinId);

        if (result.getItem() == relicCoinItem) {
            // This is a Black Market trade - show the item being traded (cost)
            return offer.getItemCostA().itemStack();
        }

        // Regular trade - show the result
        return result;
    }

    private boolean canAffordTrade(MerchantOffer offer) {
        return canAffordTrade(offer, -1);
    }

    private boolean canAffordTrade(MerchantOffer offer, int tradeIndex) {
        Inventory inventory = Minecraft.getInstance().player.getInventory();

        // Try to get the trade entry for tag-based validation
        java.util.List<net.fit.cobblemonmerchants.merchant.config.MerchantConfig.TradeEntry> tradeEntries =
            menu.getTradeEntries();

        if (tradeIndex >= 0 && tradeIndex < tradeEntries.size()) {
            // Use ItemRequirement-based validation
            net.fit.cobblemonmerchants.merchant.config.MerchantConfig.TradeEntry tradeEntry = tradeEntries.get(tradeIndex);
            net.fit.cobblemonmerchants.merchant.config.ItemRequirement inputReq = tradeEntry.input();

            int countA = 0;
            for (ItemStack stack : inventory.items) {
                if (inputReq.matches(stack)) {
                    countA += stack.getCount();
                }
            }

            int countB = 0;
            if (tradeEntry.secondInput().isPresent()) {
                net.fit.cobblemonmerchants.merchant.config.ItemRequirement secondInputReq = tradeEntry.secondInput().get();
                for (ItemStack stack : inventory.items) {
                    if (secondInputReq.matches(stack)) {
                        countB += stack.getCount();
                    }
                }
            }

            boolean hasA = countA >= inputReq.getCount();
            boolean hasB = !tradeEntry.secondInput().isPresent() || countB >= tradeEntry.secondInput().get().getCount();

            return hasA && hasB;
        } else {
            // Fallback to vanilla validation
            ItemStack costA = offer.getItemCostA().itemStack();
            ItemStack costB = offer.getCostB();

            int countA = 0;
            int countB = 0;

            for (ItemStack stack : inventory.items) {
                if (ItemStack.isSameItemSameComponents(stack, costA)) {
                    countA += stack.getCount();
                }
                if (!costB.isEmpty() && ItemStack.isSameItemSameComponents(stack, costB)) {
                    countB += stack.getCount();
                }
            }

            boolean hasA = countA >= costA.getCount();
            boolean hasB = costB.isEmpty() || countB >= costB.getCount();

            return hasA && hasB;
        }
    }

    /**
     * Converts tag path to display name (e.g., "decorated_pot_sherds" -> "Pottery Sherd")
     */
    private String formatTagName(String tagPath) {
        // Handle special cases
        if (tagPath.equals("decorated_pot_sherds")) {
            return "Pottery Sherd";
        }

        // Generic formatting: remove underscores, capitalize words
        String[] words = tagPath.split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1))
                      .append(" ");
            }
        }
        return result.toString().trim();
    }
}
