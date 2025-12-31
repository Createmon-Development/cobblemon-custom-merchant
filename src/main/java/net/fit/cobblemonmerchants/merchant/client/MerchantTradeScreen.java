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
        java.util.List<net.fit.cobblemonmerchants.merchant.config.MerchantConfig.TradeEntry> tradeEntries = menu.getTradeEntries();
        CobblemonMerchants.LOGGER.info("CLIENT: Rendering {} offers", offers.size());

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // Build position mapping: position -> trade index
        java.util.Map<Integer, Integer> positionMap = new java.util.HashMap<>();
        java.util.Set<Integer> usedPositions = new java.util.HashSet<>();

        for (int i = 0; i < tradeEntries.size() && i < offers.size(); i++) {
            net.fit.cobblemonmerchants.merchant.config.MerchantConfig.TradeEntry entry = tradeEntries.get(i);
            if (entry.position().isPresent()) {
                int pos = entry.position().get();
                // Validate position is within bounds (0-26 for 3x9 grid)
                if (pos >= 0 && pos < TRADES_PER_ROW * MAX_VISIBLE_ROWS) {
                    positionMap.put(pos, i);
                    usedPositions.add(pos);
                }
            }
        }

        // Render positioned trades first
        for (java.util.Map.Entry<Integer, Integer> entry : positionMap.entrySet()) {
            int position = entry.getKey();
            int tradeIndex = entry.getValue();

            int row = position / TRADES_PER_ROW;
            int col = position % TRADES_PER_ROW;
            int slotX = x + 8 + col * 18;
            int slotY = y + 18 + row * 18;

            renderSingleTrade(guiGraphics, offers.get(tradeIndex), tradeEntries.get(tradeIndex), slotX, slotY);
        }

        // Render non-positioned trades in remaining slots
        int nextAvailablePosition = 0;
        for (int i = 0; i < offers.size(); i++) {
            // Skip trades that have explicit positions
            boolean hasPosition = i < tradeEntries.size() && tradeEntries.get(i).position().isPresent();
            if (hasPosition) {
                continue;
            }

            // Find next available position
            while (nextAvailablePosition < TRADES_PER_ROW * MAX_VISIBLE_ROWS && usedPositions.contains(nextAvailablePosition)) {
                nextAvailablePosition++;
            }

            if (nextAvailablePosition >= TRADES_PER_ROW * MAX_VISIBLE_ROWS) {
                break; // No more slots available
            }

            int row = nextAvailablePosition / TRADES_PER_ROW;
            int col = nextAvailablePosition % TRADES_PER_ROW;
            int slotX = x + 8 + col * 18;
            int slotY = y + 18 + row * 18;

            net.fit.cobblemonmerchants.merchant.config.MerchantConfig.TradeEntry entry =
                i < tradeEntries.size() ? tradeEntries.get(i) : null;
            renderSingleTrade(guiGraphics, offers.get(i), entry, slotX, slotY);

            usedPositions.add(nextAvailablePosition);
            nextAvailablePosition++;
        }
    }

    private void renderSingleTrade(GuiGraphics guiGraphics, MerchantOffer offer,
                                   net.fit.cobblemonmerchants.merchant.config.MerchantConfig.TradeEntry entry,
                                   int slotX, int slotY) {
        // Check if this trade is broken (failed to load)
        boolean isBroken = false;
        if (entry != null) {
            try {
                // Check if the display stack can be obtained (will fail if tag is empty or item doesn't exist)
                ItemStack testStack = entry.input().getDisplayStack();
                if (testStack.isEmpty()) {
                    isBroken = true;
                }
            } catch (Exception e) {
                isBroken = true;
            }
        }

        // Draw the display item or barrier block if broken
        ItemStack displayItem;
        if (isBroken) {
            // Display barrier block for broken trades
            displayItem = new ItemStack(net.minecraft.world.item.Items.BARRIER);
        } else {
            // For Black Market trades (where result is relic coins), show what the player is trading away
            displayItem = getDisplayItem(offer);
        }

        CobblemonMerchants.LOGGER.info("CLIENT: Rendering item {} at ({}, {})", displayItem, slotX, slotY);
        guiGraphics.renderItem(displayItem, slotX, slotY);
        guiGraphics.renderItemDecorations(this.font, displayItem, slotX, slotY);

        // Draw a red X if the trade is out of stock
        if (offer.isOutOfStock()) {
            guiGraphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x80FF0000);
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

    /**
     * Builds a mapping from grid position to trade index.
     * This handles both positioned and non-positioned trades.
     */
    private java.util.Map<Integer, Integer> buildPositionToTradeMap() {
        java.util.Map<Integer, Integer> map = new java.util.HashMap<>();
        java.util.Set<Integer> usedPositions = new java.util.HashSet<>();
        MerchantOffers offers = menu.getOffers();
        java.util.List<net.fit.cobblemonmerchants.merchant.config.MerchantConfig.TradeEntry> tradeEntries = menu.getTradeEntries();

        // First, map positioned trades
        for (int i = 0; i < tradeEntries.size() && i < offers.size(); i++) {
            net.fit.cobblemonmerchants.merchant.config.MerchantConfig.TradeEntry entry = tradeEntries.get(i);
            if (entry.position().isPresent()) {
                int pos = entry.position().get();
                if (pos >= 0 && pos < TRADES_PER_ROW * MAX_VISIBLE_ROWS) {
                    map.put(pos, i);
                    usedPositions.add(pos);
                }
            }
        }

        // Then, map non-positioned trades to remaining slots
        int nextAvailablePosition = 0;
        for (int i = 0; i < offers.size(); i++) {
            boolean hasPosition = i < tradeEntries.size() && tradeEntries.get(i).position().isPresent();
            if (hasPosition) {
                continue;
            }

            while (nextAvailablePosition < TRADES_PER_ROW * MAX_VISIBLE_ROWS && usedPositions.contains(nextAvailablePosition)) {
                nextAvailablePosition++;
            }

            if (nextAvailablePosition >= TRADES_PER_ROW * MAX_VISIBLE_ROWS) {
                break;
            }

            map.put(nextAvailablePosition, i);
            usedPositions.add(nextAvailablePosition);
            nextAvailablePosition++;
        }

        return map;
    }

    private int getHoveredTradeIndex(int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        java.util.Map<Integer, Integer> positionToTrade = buildPositionToTradeMap();

        for (java.util.Map.Entry<Integer, Integer> entry : positionToTrade.entrySet()) {
            int position = entry.getKey();
            int row = position / TRADES_PER_ROW;
            int col = position % TRADES_PER_ROW;

            int slotX = x + 8 + col * 18;
            int slotY = y + 18 + row * 18;

            if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                return entry.getValue();
            }
        }

        return -1;
    }

    private List<Component> createTradeTooltip(MerchantOffer offer) {
        return createTradeTooltip(offer, -1);
    }

    private List<Component> createTradeTooltip(MerchantOffer offer, int tradeIndex) {
        List<Component> tooltip = new ArrayList<>();

        // Try to get TradeEntry for display name and cost info
        java.util.List<net.fit.cobblemonmerchants.merchant.config.MerchantConfig.TradeEntry> tradeEntries =
            menu.getTradeEntries();

        // Check if trade is broken
        boolean isBroken = false;
        if (tradeIndex >= 0 && tradeIndex < tradeEntries.size()) {
            net.fit.cobblemonmerchants.merchant.config.MerchantConfig.TradeEntry entry = tradeEntries.get(tradeIndex);
            try {
                ItemStack testStack = entry.input().getDisplayStack();
                if (testStack.isEmpty()) {
                    isBroken = true;
                }
            } catch (Exception e) {
                isBroken = true;
            }
        }

        if (isBroken) {
            // Show error message for broken trades
            tooltip.add(Component.literal("§cBroken Trade"));
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("§7This trade failed to load."));
            tooltip.add(Component.literal("§7Check the configuration file."));
            return tooltip;
        }

        if (tradeIndex >= 0 && tradeIndex < tradeEntries.size()) {
            net.fit.cobblemonmerchants.merchant.config.MerchantConfig.TradeEntry entry = tradeEntries.get(tradeIndex);

            // Determine trade display name
            String tradeTitle;
            if (entry.tradeDisplayName().isPresent()) {
                // Use custom trade display name if provided
                tradeTitle = entry.tradeDisplayName().get();
            } else {
                // Auto-generate based on relic coins
                net.minecraft.resources.ResourceLocation relicCoinId =
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", "relic_coin");
                net.minecraft.world.item.Item relicCoinItem =
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.get(relicCoinId);

                boolean inputIsRelicCoin = entry.input().getDisplayStack().getItem() == relicCoinItem;
                boolean outputIsRelicCoin = entry.output().getItem() == relicCoinItem;

                // Get the name of the non-relic coin item
                String itemName;
                if (inputIsRelicCoin && !outputIsRelicCoin) {
                    // Buying item with relic coins - show output
                    itemName = entry.output().getHoverName().getString();
                    tradeTitle = "Buy " + itemName;
                } else if (!inputIsRelicCoin && outputIsRelicCoin) {
                    // Selling item for relic coins - show input
                    String inputName;
                    if (entry.input().getDisplayName() != null) {
                        inputName = entry.input().getDisplayName();
                    } else {
                        inputName = entry.input().getDisplayStack().getHoverName().getString();
                    }
                    tradeTitle = "Sell " + inputName;
                } else {
                    // Neither or both are relic coins - show input
                    String inputName;
                    if (entry.input().getDisplayName() != null) {
                        inputName = entry.input().getDisplayName();
                    } else {
                        inputName = entry.input().getDisplayStack().getHoverName().getString();
                    }
                    tradeTitle = "Trade " + inputName;
                }
            }

            tooltip.add(Component.literal(tradeTitle));
            tooltip.add(Component.empty());

            // Build cost line in format: "Cost: <count>x <name> -> <count>x <name>"
            net.fit.cobblemonmerchants.merchant.config.ItemRequirement inputReq = entry.input();
            StringBuilder costLine = new StringBuilder("§6Cost: §f");

            // Input item
            costLine.append(inputReq.getCount()).append("x §r");
            if (inputReq.getDisplayName() != null) {
                // Use custom display name from input
                costLine.append(inputReq.getDisplayName());
            } else {
                ItemStack displayStack = inputReq.getDisplayStack();
                costLine.append(displayStack.getHoverName().getString());
            }

            // Second input if exists
            if (entry.secondInput().isPresent()) {
                net.fit.cobblemonmerchants.merchant.config.ItemRequirement secondReq = entry.secondInput().get();
                costLine.append(" §f+ ").append(secondReq.getCount()).append("x §r");
                if (secondReq.getDisplayName() != null) {
                    // Use custom display name from second input
                    costLine.append(secondReq.getDisplayName());
                } else {
                    ItemStack displayStack = secondReq.getDisplayStack();
                    costLine.append(displayStack.getHoverName().getString());
                }
            }

            // Arrow and output
            costLine.append(" §f-> ").append(entry.output().getCount()).append("x §r");
            costLine.append(entry.output().getHoverName().getString());

            tooltip.add(Component.literal(costLine.toString()));
        } else {
            // Fallback to vanilla cost display
            tooltip.add(offer.getResult().getHoverName());
            tooltip.add(Component.empty());

            ItemStack costA = offer.getItemCostA().itemStack();
            StringBuilder costLine = new StringBuilder("§6Cost: §f");
            costLine.append(costA.getCount()).append("x §r").append(costA.getHoverName().getString());

            ItemStack costB = offer.getCostB();
            if (!costB.isEmpty()) {
                costLine.append(" §f+ ").append(costB.getCount()).append("x §r");
                costLine.append(costB.getHoverName().getString());
            }

            costLine.append(" §f-> ").append(offer.getResult().getCount()).append("x §r");
            costLine.append(offer.getResult().getHoverName().getString());

            tooltip.add(Component.literal(costLine.toString()));
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

}
