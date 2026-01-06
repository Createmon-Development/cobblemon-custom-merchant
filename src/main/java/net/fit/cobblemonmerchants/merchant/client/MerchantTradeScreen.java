package net.fit.cobblemonmerchants.merchant.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.fit.cobblemonmerchants.merchant.menu.MerchantTradeMenu;
import net.fit.cobblemonmerchants.merchant.rewards.DailyRewardManager;
import net.fit.cobblemonmerchants.network.ClaimDailyRewardPacket;
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

    // Timer for live countdown updates
    private long lastTimerUpdate = 0;
    private static final long TIMER_UPDATE_INTERVAL_MS = 1000; // Update every second

    // Animation tick for shimmer effect
    private float shimmerTick = 0;

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

        // Update shimmer animation
        shimmerTick += partialTick;

        // Update timer periodically for live countdown
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastTimerUpdate > TIMER_UPDATE_INTERVAL_MS) {
            lastTimerUpdate = currentTime;
            // Update the time display from local calculation
            menu.updateTimeUntilReset(DailyRewardManager.getFormattedTimeUntilReset());
        }

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

        // Reserve daily reward position
        if (menu.hasDailyRewardDisplay() && menu.getDailyRewardPosition() >= 0) {
            usedPositions.add(menu.getDailyRewardPosition());
        }

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

        // Render daily reward item if configured
        renderDailyReward(guiGraphics, x, y);
    }

    /**
     * Renders the daily reward item at its configured position with shimmer or grey overlay
     */
    private void renderDailyReward(GuiGraphics guiGraphics, int baseX, int baseY) {
        if (!menu.hasDailyRewardDisplay() || menu.getDailyRewardPosition() < 0) {
            return;
        }

        int position = menu.getDailyRewardPosition();
        int row = position / TRADES_PER_ROW;
        int col = position % TRADES_PER_ROW;
        int slotX = baseX + 8 + col * 18;
        int slotY = baseY + 18 + row * 18;

        ItemStack rewardItem = menu.getDailyRewardItem();
        boolean claimed = menu.isDailyRewardClaimed();

        // Render the item
        guiGraphics.renderItem(rewardItem, slotX, slotY);
        guiGraphics.renderItemDecorations(this.font, rewardItem, slotX, slotY);

        if (claimed) {
            // Grey semi-transparent overlay when already claimed
            guiGraphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0xAA808080);
        } else {
            // Shimmer effect when available - animated golden glow
            renderShimmerEffect(guiGraphics, slotX, slotY);
        }
    }

    /**
     * Renders an animated shimmer/glow effect around a slot
     */
    private void renderShimmerEffect(GuiGraphics guiGraphics, int slotX, int slotY) {
        // Calculate shimmer intensity using sine wave for smooth pulsing
        float shimmerPhase = (shimmerTick * 0.1f) % (float)(Math.PI * 2);
        float shimmerIntensity = (float)(Math.sin(shimmerPhase) * 0.5 + 0.5); // 0.0 to 1.0

        // Gold color with varying alpha for shimmer effect
        int baseAlpha = 80; // Minimum visibility
        int maxAlpha = 180; // Maximum visibility
        int alpha = (int)(baseAlpha + (maxAlpha - baseAlpha) * shimmerIntensity);

        // Golden color: RGB(255, 215, 0) with animated alpha
        int shimmerColor = (alpha << 24) | 0xFFD700;

        // Draw shimmering border (1 pixel thick)
        // Top edge
        guiGraphics.fill(slotX - 1, slotY - 1, slotX + 17, slotY, shimmerColor);
        // Bottom edge
        guiGraphics.fill(slotX - 1, slotY + 16, slotX + 17, slotY + 17, shimmerColor);
        // Left edge
        guiGraphics.fill(slotX - 1, slotY, slotX, slotY + 16, shimmerColor);
        // Right edge
        guiGraphics.fill(slotX + 16, slotY, slotX + 17, slotY + 16, shimmerColor);

        // Optional: Add a subtle inner glow at peak shimmer
        if (shimmerIntensity > 0.7f) {
            int innerAlpha = (int)((shimmerIntensity - 0.7f) / 0.3f * 40);
            int innerColor = (innerAlpha << 24) | 0xFFD700;
            guiGraphics.fill(slotX, slotY, slotX + 16, slotY + 16, innerColor);
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

        // Check if hovering over daily reward
        if (isHoveringDailyReward(mouseX, mouseY)) {
            List<Component> tooltip = createDailyRewardTooltip();
            guiGraphics.renderTooltip(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
            return;
        }

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
     * Checks if the mouse is hovering over the daily reward slot
     */
    private boolean isHoveringDailyReward(int mouseX, int mouseY) {
        if (!menu.hasDailyRewardDisplay() || menu.getDailyRewardPosition() < 0) {
            return false;
        }

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        int position = menu.getDailyRewardPosition();
        int row = position / TRADES_PER_ROW;
        int col = position % TRADES_PER_ROW;
        int slotX = x + 8 + col * 18;
        int slotY = y + 18 + row * 18;

        return mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16;
    }

    /**
     * Creates the tooltip for the daily reward item
     */
    private List<Component> createDailyRewardTooltip() {
        List<Component> tooltip = new ArrayList<>();

        ItemStack rewardItem = menu.getDailyRewardItem();
        boolean claimed = menu.isDailyRewardClaimed();
        int minCount = menu.getDailyRewardMinCount();
        int maxCount = menu.getDailyRewardMaxCount();

        tooltip.add(Component.literal("§6Daily Reward"));
        tooltip.add(Component.empty());

        // Show count range if min != max, otherwise show single count
        String countText;
        if (minCount == maxCount) {
            countText = minCount + "x";
        } else {
            countText = minCount + "-" + maxCount + "x";
        }
        tooltip.add(Component.literal("§7Reward: §f" + countText + " " + rewardItem.getHoverName().getString()));
        tooltip.add(Component.empty());

        if (claimed) {
            tooltip.add(Component.literal("§cAlready claimed today!"));
            tooltip.add(Component.literal("§7Resets in: §e" + menu.getTimeUntilReset()));
        } else {
            tooltip.add(Component.literal("§aClick to claim!"));
        }

        return tooltip;
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

            // Arrow and output (use outputCount() for uncapped count)
            costLine.append(" §f-> ").append(entry.outputCount()).append("x §r");
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
            // Check if clicking on daily reward first
            if (isHoveringDailyReward((int) mouseX, (int) mouseY)) {
                if (!menu.isDailyRewardClaimed()) {
                    // Send packet to claim the daily reward
                    ClaimDailyRewardPacket.send();

                    // Optimistically update UI (server will validate)
                    menu.setDailyRewardClaimed(true);

                    // Play a click sound
                    Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F
                        )
                    );
                } else {
                    // Already claimed - play error sound
                    Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            SoundEvents.VILLAGER_NO, 0.5F, 1.0F
                        )
                    );
                }
                return true;
            }

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
                                SoundEvents.EXPERIENCE_ORB_PICKUP, 0.2F, 0.9F
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
            // Special handling for relic coins - check bag too
            if (isRelicCoinRequirement(inputReq)) {
                countA = countRelicCoinsClient(inventory);
            } else {
                for (ItemStack stack : inventory.items) {
                    if (inputReq.matches(stack)) {
                        countA += stack.getCount();
                    }
                }
            }

            int countB = 0;
            if (tradeEntry.secondInput().isPresent()) {
                net.fit.cobblemonmerchants.merchant.config.ItemRequirement secondInputReq = tradeEntry.secondInput().get();
                // Special handling for relic coins - check bag too
                if (isRelicCoinRequirement(secondInputReq)) {
                    countB = countRelicCoinsClient(inventory);
                } else {
                    for (ItemStack stack : inventory.items) {
                        if (secondInputReq.matches(stack)) {
                            countB += stack.getCount();
                        }
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

            // Special handling for relic coins - check bag too
            if (isRelicCoinStack(costA)) {
                countA = countRelicCoinsClient(inventory);
            } else {
                for (ItemStack stack : inventory.items) {
                    if (ItemStack.isSameItemSameComponents(stack, costA)) {
                        countA += stack.getCount();
                    }
                }
            }

            if (!costB.isEmpty()) {
                if (isRelicCoinStack(costB)) {
                    countB = countRelicCoinsClient(inventory);
                } else {
                    for (ItemStack stack : inventory.items) {
                        if (ItemStack.isSameItemSameComponents(stack, costB)) {
                            countB += stack.getCount();
                        }
                    }
                }
            }

            boolean hasA = countA >= costA.getCount();
            boolean hasB = costB.isEmpty() || countB >= costB.getCount();

            return hasA && hasB;
        }
    }

    /**
     * Counts total relic coins available to the player (inventory + coin bag) - Client side
     */
    private int countRelicCoinsClient(Inventory inventory) {
        int count = 0;
        ResourceLocation relicCoinId = ResourceLocation.fromNamespaceAndPath("cobblemon", "relic_coin");

        // Count coins in inventory
        for (ItemStack stack : inventory.items) {
            ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId.equals(relicCoinId)) {
                count += stack.getCount();
            }
        }

        // Count coins in bag
        for (ItemStack stack : inventory.items) {
            if (stack.getItem() instanceof net.fit.cobblemonmerchants.item.custom.RelicCoinBagItem) {
                int bagCoins = stack.getOrDefault(net.fit.cobblemonmerchants.item.component.ModDataComponents.RELIC_COIN_COUNT.get(), 0);
                count += bagCoins;
                break; // Only one bag should exist
            }
        }

        return count;
    }

    /**
     * Check if an ItemStack is a relic coin
     */
    private boolean isRelicCoinStack(ItemStack stack) {
        ResourceLocation relicCoinId = ResourceLocation.fromNamespaceAndPath("cobblemon", "relic_coin");
        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId.equals(relicCoinId);
    }

    /**
     * Check if an ItemRequirement is for relic coins
     */
    private boolean isRelicCoinRequirement(net.fit.cobblemonmerchants.merchant.config.ItemRequirement requirement) {
        ResourceLocation relicCoinId = ResourceLocation.fromNamespaceAndPath("cobblemon", "relic_coin");
        ItemStack displayStack = requirement.getDisplayStack();
        if (displayStack.isEmpty()) {
            return false;
        }
        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(displayStack.getItem());
        return itemId.equals(relicCoinId);
    }

}
