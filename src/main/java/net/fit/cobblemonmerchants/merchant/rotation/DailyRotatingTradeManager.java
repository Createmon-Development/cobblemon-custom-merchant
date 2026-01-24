package net.fit.cobblemonmerchants.merchant.rotation;

import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.fit.cobblemonmerchants.merchant.config.DailyRotatingTradeConfig;
import net.fit.cobblemonmerchants.merchant.config.TradePool;
import net.fit.cobblemonmerchants.merchant.config.TradePoolEntry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Manager for daily rotating trades. Handles selection and persistence of daily trade items.
 *
 * Each slot_id gets a single item selected per day, which is shared across all merchants
 * using that slot_id. This allows different merchants to offer the same "daily special".
 *
 * The selection is deterministic based on the current date and slot_id, ensuring
 * all players see the same daily trades.
 */
public class DailyRotatingTradeManager extends SavedData {
    private static final String DATA_NAME = "cobblemoncustommerchants_daily_rotating_trades";

    // Map of slotId -> selected trade data for today
    private final Map<String, SelectedTrade> selectedTrades = new HashMap<>();
    private String lastRotationDate = "";
    // Counter that increments on each forced refresh to ensure different random results
    private long rotationCounter = 0;

    /**
     * Represents a selected trade for a slot
     */
    public record SelectedTrade(
        String itemId,
        int inputAmount,
        int outputAmount,
        int maxUses,
        String displayName,
        String poolId
    ) {
        public CompoundTag toNbt() {
            CompoundTag tag = new CompoundTag();
            tag.putString("itemId", itemId);
            tag.putInt("inputAmount", inputAmount);
            tag.putInt("outputAmount", outputAmount);
            tag.putInt("maxUses", maxUses);
            tag.putString("displayName", displayName != null ? displayName : "");
            tag.putString("poolId", poolId);
            return tag;
        }

        public static SelectedTrade fromNbt(CompoundTag tag) {
            return new SelectedTrade(
                tag.getString("itemId"),
                tag.getInt("inputAmount"),
                tag.getInt("outputAmount"),
                tag.getInt("maxUses"),
                tag.getString("displayName").isEmpty() ? null : tag.getString("displayName"),
                tag.getString("poolId")
            );
        }
    }

    public DailyRotatingTradeManager() {
    }

    /**
     * Gets the current date string for rotation tracking
     */
    private static String getCurrentDateString() {
        return LocalDate.now(ZoneId.systemDefault()).toString();
    }

    /**
     * Generates a seed for deterministic random selection based on date, slot ID, and rotation counter.
     * The rotation counter ensures that forced refreshes produce different results.
     */
    private long generateSeed(String slotId) {
        String dateStr = getCurrentDateString();
        return (dateStr + ":" + slotId + ":" + rotationCounter).hashCode();
    }

    /**
     * Checks if rotation is needed (new day) and performs it if necessary.
     */
    private void checkAndRotate() {
        String currentDate = getCurrentDateString();
        if (!currentDate.equals(lastRotationDate)) {
            CobblemonMerchants.LOGGER.info("Daily rotating trades: New day detected ({}), clearing selections", currentDate);
            selectedTrades.clear();
            lastRotationDate = currentDate;
            setDirty();
        }
    }

    /**
     * Gets the selected trade for a slot, selecting a new one if needed.
     *
     * @param config The rotating trade configuration
     * @return The selected trade, or null if the pool is invalid
     */
    public SelectedTrade getSelectedTrade(DailyRotatingTradeConfig config) {
        checkAndRotate();

        String slotId = config.slotId();

        // Check if we already have a selection for this slot today
        if (selectedTrades.containsKey(slotId)) {
            return selectedTrades.get(slotId);
        }

        // Need to select a new trade
        TradePool pool = config.getPool();
        if (pool == null) {
            CobblemonMerchants.LOGGER.warn("Trade pool not found for rotating trade slot '{}': {}", slotId, config.poolId());
            return null;
        }

        // Use deterministic random based on date and slot ID
        long seed = generateSeed(slotId);
        Random random = new Random(seed);

        TradePoolEntry entry = pool.selectRandomEntry(random);
        if (entry == null) {
            CobblemonMerchants.LOGGER.warn("Trade pool '{}' is empty for slot '{}'", config.poolId(), slotId);
            return null;
        }

        // Apply variance using the same seed for consistency
        int effectiveInputAmount = entry.getEffectiveInputAmount(random);
        int effectiveOutputAmount = entry.getEffectiveOutputAmount(random);

        SelectedTrade selected = new SelectedTrade(
            entry.itemId(),
            effectiveInputAmount,
            effectiveOutputAmount,
            entry.getMaxUsesOrDefault(),
            entry.displayName().orElse(null),
            config.poolId()
        );

        selectedTrades.put(slotId, selected);
        setDirty();

        CobblemonMerchants.LOGGER.info("Selected daily rotating trade for slot '{}': {} (input: {}, output: {})",
            slotId, entry.itemId(), effectiveInputAmount, effectiveOutputAmount);

        return selected;
    }

    /**
     * Gets the input item ID for a pool.
     *
     * @param poolId The pool resource location string
     * @return The input item ID, or "cobblemon:relic_coin" as default
     */
    public static String getInputItemForPool(String poolId) {
        TradePool pool = net.fit.cobblemonmerchants.merchant.config.TradePoolRegistry.getPool(poolId);
        if (pool != null) {
            return pool.inputItem();
        }
        return "cobblemon:relic_coin";
    }

    /**
     * Force a rotation, clearing all selections and allowing new ones to be picked.
     * Increments the rotation counter to ensure different random results.
     */
    public void forceRotation() {
        int count = selectedTrades.size();
        selectedTrades.clear();
        rotationCounter++; // Increment to get different random results
        setDirty();
        CobblemonMerchants.LOGGER.info("Forced rotation of daily rotating trades, cleared {} selections (rotation #{})",
            count, rotationCounter);
    }

    /**
     * Clears specific slot IDs, allowing them to be re-selected.
     * This is used for merchant-specific refreshes.
     * Increments the rotation counter to ensure different random results.
     *
     * @param slotIds The list of slot IDs to clear
     * @return The number of slots that were actually cleared
     */
    public int clearSlots(java.util.List<String> slotIds) {
        int clearedCount = 0;
        for (String slotId : slotIds) {
            if (selectedTrades.remove(slotId) != null) {
                clearedCount++;
            }
        }
        if (clearedCount > 0) {
            rotationCounter++; // Increment to get different random results
            setDirty();
            CobblemonMerchants.LOGGER.info("Cleared {} specific slot(s) from daily rotating trades: {} (rotation #{})",
                clearedCount, slotIds, rotationCounter);
        }
        return clearedCount;
    }

    // === SavedData implementation ===

    public static DailyRotatingTradeManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new Factory<>(
                DailyRotatingTradeManager::new,
                DailyRotatingTradeManager::load
            ),
            DATA_NAME
        );
    }

    public static DailyRotatingTradeManager load(CompoundTag tag, HolderLookup.Provider provider) {
        DailyRotatingTradeManager manager = new DailyRotatingTradeManager();
        manager.lastRotationDate = tag.getString("lastRotationDate");
        manager.rotationCounter = tag.getLong("rotationCounter");

        if (tag.contains("selectedTrades", Tag.TAG_LIST)) {
            ListTag tradesList = tag.getList("selectedTrades", Tag.TAG_COMPOUND);
            for (int i = 0; i < tradesList.size(); i++) {
                CompoundTag tradeTag = tradesList.getCompound(i);
                String slotId = tradeTag.getString("slotId");
                SelectedTrade trade = SelectedTrade.fromNbt(tradeTag.getCompound("trade"));
                manager.selectedTrades.put(slotId, trade);
            }
        }

        CobblemonMerchants.LOGGER.info("Loaded daily rotating trade manager: {} selections for date {}, rotation #{}",
            manager.selectedTrades.size(), manager.lastRotationDate, manager.rotationCounter);

        return manager;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        tag.putString("lastRotationDate", lastRotationDate);
        tag.putLong("rotationCounter", rotationCounter);

        ListTag tradesList = new ListTag();
        for (Map.Entry<String, SelectedTrade> entry : selectedTrades.entrySet()) {
            CompoundTag tradeTag = new CompoundTag();
            tradeTag.putString("slotId", entry.getKey());
            tradeTag.put("trade", entry.getValue().toNbt());
            tradesList.add(tradeTag);
        }
        tag.put("selectedTrades", tradesList);

        return tag;
    }
}
