package net.fit.cobblemonmerchants.merchant.rewards;

import net.fit.cobblemonmerchants.CobblemonMerchants;
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
import java.util.UUID;

/**
 * Manages daily trade usage tracking for trades with daily_reset enabled.
 * Tracks how many times each player has used specific trades on the current day.
 */
public class DailyTradeResetManager extends SavedData {

    private static final String DATA_NAME = "cobblemon_merchant_daily_trades";

    // Key: "playerUUID:merchantId:tradeIndex" -> Value: TradeUsageRecord
    private final Map<String, TradeUsageRecord> usageRecords = new HashMap<>();

    public DailyTradeResetManager() {
    }

    /**
     * Get the DailyTradeResetManager for a server level
     */
    public static DailyTradeResetManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new Factory<>(DailyTradeResetManager::new, DailyTradeResetManager::load),
            DATA_NAME
        );
    }

    /**
     * Creates a unique key for tracking trade usage by trade index.
     */
    private static String createKey(UUID playerUUID, String merchantId, int tradeIndex) {
        return playerUUID.toString() + ":" + merchantId + ":" + tradeIndex;
    }

    /**
     * Creates a unique key for tracking trade usage by slot ID.
     * Slot-based keys are prefixed with "slot:" to distinguish from index-based keys.
     */
    private static String createSlotKey(UUID playerUUID, String slotId) {
        return playerUUID.toString() + ":slot:" + slotId;
    }

    /**
     * Get the number of times a player has used a specific trade today.
     * Returns 0 if never used or if the last use was on a previous day.
     */
    public int getUsesToday(UUID playerUUID, String merchantId, int tradeIndex) {
        String key = createKey(playerUUID, merchantId, tradeIndex);
        TradeUsageRecord record = usageRecords.get(key);

        if (record == null) {
            return 0;
        }

        long todayEpochDay = LocalDate.now(ZoneId.systemDefault()).toEpochDay();
        if (record.lastUseDay != todayEpochDay) {
            // Last use was on a different day, so today's count is 0
            return 0;
        }

        return record.usesToday;
    }

    /**
     * Check if a player can still use a daily-reset trade.
     * @param playerUUID The player's UUID
     * @param merchantId The merchant type ID
     * @param tradeIndex The index of the trade in the merchant's trade list
     * @param maxUses The maximum uses allowed per day
     * @return true if the player can use the trade, false if they've reached the daily limit
     */
    public boolean canUseTrade(UUID playerUUID, String merchantId, int tradeIndex, int maxUses) {
        int usesToday = getUsesToday(playerUUID, merchantId, tradeIndex);
        return usesToday < maxUses;
    }

    /**
     * Get the remaining uses for a daily-reset trade.
     */
    public int getRemainingUses(UUID playerUUID, String merchantId, int tradeIndex, int maxUses) {
        int usesToday = getUsesToday(playerUUID, merchantId, tradeIndex);
        return Math.max(0, maxUses - usesToday);
    }

    /**
     * Record that a player has used a trade.
     */
    public void recordTradeUse(UUID playerUUID, String merchantId, int tradeIndex) {
        String key = createKey(playerUUID, merchantId, tradeIndex);
        long todayEpochDay = LocalDate.now(ZoneId.systemDefault()).toEpochDay();

        TradeUsageRecord record = usageRecords.get(key);
        if (record == null || record.lastUseDay != todayEpochDay) {
            // New record or new day - start fresh
            usageRecords.put(key, new TradeUsageRecord(todayEpochDay, 1));
        } else {
            // Same day - increment
            usageRecords.put(key, new TradeUsageRecord(todayEpochDay, record.usesToday + 1));
        }
        setDirty();

        CobblemonMerchants.LOGGER.debug("Trade use recorded: player={}, merchant={}, trade={}, uses today={}",
            playerUUID, merchantId, tradeIndex, usageRecords.get(key).usesToday);
    }

    // ===== Slot-based tracking methods for daily rotating trades =====

    /**
     * Get the number of times a player has used a specific slot today.
     * This is used for daily rotating trades that are tracked by slot_id.
     */
    public int getUsesTodayBySlot(UUID playerUUID, String slotId) {
        String key = createSlotKey(playerUUID, slotId);
        TradeUsageRecord record = usageRecords.get(key);

        if (record == null) {
            return 0;
        }

        long todayEpochDay = LocalDate.now(ZoneId.systemDefault()).toEpochDay();
        if (record.lastUseDay != todayEpochDay) {
            return 0;
        }

        return record.usesToday;
    }

    /**
     * Check if a player can still use a slot-based trade.
     */
    public boolean canUseSlotTrade(UUID playerUUID, String slotId, int maxUses) {
        int usesToday = getUsesTodayBySlot(playerUUID, slotId);
        return usesToday < maxUses;
    }

    /**
     * Record that a player has used a slot-based trade.
     */
    public void recordSlotTradeUse(UUID playerUUID, String slotId) {
        String key = createSlotKey(playerUUID, slotId);
        long todayEpochDay = LocalDate.now(ZoneId.systemDefault()).toEpochDay();

        TradeUsageRecord record = usageRecords.get(key);
        if (record == null || record.lastUseDay != todayEpochDay) {
            usageRecords.put(key, new TradeUsageRecord(todayEpochDay, 1));
        } else {
            usageRecords.put(key, new TradeUsageRecord(todayEpochDay, record.usesToday + 1));
        }
        setDirty();

        CobblemonMerchants.LOGGER.debug("Slot trade use recorded: player={}, slotId={}, uses today={}",
            playerUUID, slotId, usageRecords.get(key).usesToday);
    }

    /**
     * Clear usage records for specific slot IDs.
     * Called when daily rotating trades are refreshed to reset usage.
     */
    public void clearSlotUsage(java.util.List<String> slotIds) {
        int clearedCount = 0;
        for (String slotId : slotIds) {
            // Remove all entries that match the slot ID pattern
            String slotSuffix = ":slot:" + slotId;
            var iterator = usageRecords.entrySet().iterator();
            while (iterator.hasNext()) {
                if (iterator.next().getKey().endsWith(slotSuffix)) {
                    iterator.remove();
                    clearedCount++;
                }
            }
        }
        if (clearedCount > 0) {
            setDirty();
            CobblemonMerchants.LOGGER.info("Cleared {} slot usage records for slots: {}", clearedCount, slotIds);
        }
    }

    /**
     * Clear all slot-based usage records.
     * Called when all daily rotating trades are force-rotated.
     */
    public void clearAllSlotUsage() {
        int clearedCount = 0;
        var iterator = usageRecords.entrySet().iterator();
        while (iterator.hasNext()) {
            String key = iterator.next().getKey();
            // Match keys with ":slot:" pattern
            if (key.contains(":slot:")) {
                iterator.remove();
                clearedCount++;
            }
        }
        if (clearedCount > 0) {
            setDirty();
            CobblemonMerchants.LOGGER.info("Cleared all {} slot usage records", clearedCount);
        }
    }

    // ===== Permanent one-time trade tracking (e.g., mysterious orb) =====

    /**
     * Creates a unique key for tracking permanent one-time trades.
     * These are NOT reset daily - they persist until explicitly reset.
     */
    private static String createPermanentKey(UUID playerUUID, String tradeKey) {
        return playerUUID.toString() + ":permanent:" + tradeKey;
    }

    /**
     * Check if a player has used a permanent one-time trade.
     * @param playerUUID The player's UUID
     * @param tradeKey A unique identifier for the trade (e.g., "mysterious_orb")
     * @return true if the player has already used this one-time trade
     */
    public boolean hasPermanentTradeBeenUsed(UUID playerUUID, String tradeKey) {
        String key = createPermanentKey(playerUUID, tradeKey);
        TradeUsageRecord record = usageRecords.get(key);
        return record != null && record.usesToday > 0;
    }

    /**
     * Get the number of times a player has used a permanent trade.
     * Unlike daily trades, this doesn't reset at midnight.
     */
    public int getPermanentTradeUses(UUID playerUUID, String tradeKey) {
        String key = createPermanentKey(playerUUID, tradeKey);
        TradeUsageRecord record = usageRecords.get(key);
        return record != null ? record.usesToday : 0;
    }

    /**
     * Record that a player has used a permanent one-time trade.
     * @param playerUUID The player's UUID
     * @param tradeKey A unique identifier for the trade (e.g., "mysterious_orb")
     */
    public void recordPermanentTradeUse(UUID playerUUID, String tradeKey) {
        String key = createPermanentKey(playerUUID, tradeKey);
        // Use day = -1 to indicate this is a permanent record that shouldn't be cleaned up
        TradeUsageRecord record = usageRecords.get(key);
        int newUses = (record != null ? record.usesToday : 0) + 1;
        usageRecords.put(key, new TradeUsageRecord(-1, newUses));
        setDirty();

        CobblemonMerchants.LOGGER.info("Permanent trade use recorded: player={}, tradeKey={}, total uses={}",
            playerUUID, tradeKey, newUses);
    }

    /**
     * Reset a permanent one-time trade for a player, allowing them to use it again.
     * Called by /hunt stage commands to reset quest-related trades.
     * @param playerUUID The player's UUID
     * @param tradeKey A unique identifier for the trade (e.g., "mysterious_orb")
     * @return true if a record was reset, false if no record existed
     */
    public boolean resetPermanentTrade(UUID playerUUID, String tradeKey) {
        String key = createPermanentKey(playerUUID, tradeKey);
        TradeUsageRecord removed = usageRecords.remove(key);
        if (removed != null) {
            setDirty();
            CobblemonMerchants.LOGGER.info("Reset permanent trade for player {}: {}", playerUUID, tradeKey);
            return true;
        }
        return false;
    }

    /**
     * Reset all permanent trades for a player.
     * @param playerUUID The player's UUID
     * @return The number of permanent trades reset
     */
    public int resetAllPermanentTrades(UUID playerUUID) {
        String prefix = playerUUID.toString() + ":permanent:";
        int count = 0;
        var iterator = usageRecords.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getKey().startsWith(prefix)) {
                iterator.remove();
                count++;
            }
        }
        if (count > 0) {
            setDirty();
            CobblemonMerchants.LOGGER.info("Reset {} permanent trades for player {}", count, playerUUID);
        }
        return count;
    }

    /**
     * Static method for external mods (like skyscobblemonitems) to reset the mysterious orb trade.
     * This can be called via reflection when /hunt stage is changed to 0 or 1.
     * @param level Any server level (used to get the manager instance)
     * @param playerUUID The player's UUID
     * @return true if the trade was reset
     */
    public static boolean resetMysteriousOrbTrade(ServerLevel level, UUID playerUUID) {
        DailyTradeResetManager manager = get(level);
        return manager.resetPermanentTrade(playerUUID, "mysterious_orb");
    }

    /**
     * Reset all trade usage for a specific player.
     */
    public void resetPlayerUsage(UUID playerUUID) {
        usageRecords.entrySet().removeIf(entry -> entry.getKey().startsWith(playerUUID.toString() + ":"));
        setDirty();
        CobblemonMerchants.LOGGER.info("Reset all daily trade usage for player {}", playerUUID);
    }

    /**
     * Clean up old records (from previous days) to prevent data bloat.
     * Called periodically or on server start.
     * NOTE: Permanent records (day = -1) are preserved.
     */
    public void cleanupOldRecords() {
        long todayEpochDay = LocalDate.now(ZoneId.systemDefault()).toEpochDay();
        int removed = 0;

        var iterator = usageRecords.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            long recordDay = entry.getValue().lastUseDay;
            // Skip permanent records (day = -1) and current day records
            if (recordDay != -1 && recordDay < todayEpochDay) {
                iterator.remove();
                removed++;
            }
        }

        if (removed > 0) {
            setDirty();
            CobblemonMerchants.LOGGER.info("Cleaned up {} old daily trade records", removed);
        }
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        ListTag recordsList = new ListTag();

        for (Map.Entry<String, TradeUsageRecord> entry : usageRecords.entrySet()) {
            CompoundTag recordTag = new CompoundTag();
            recordTag.putString("key", entry.getKey());
            recordTag.putLong("day", entry.getValue().lastUseDay);
            recordTag.putInt("uses", entry.getValue().usesToday);
            recordsList.add(recordTag);
        }

        tag.put("records", recordsList);
        return tag;
    }

    public static DailyTradeResetManager load(CompoundTag tag, HolderLookup.Provider registries) {
        DailyTradeResetManager manager = new DailyTradeResetManager();

        if (tag.contains("records", Tag.TAG_LIST)) {
            ListTag recordsList = tag.getList("records", Tag.TAG_COMPOUND);

            for (int i = 0; i < recordsList.size(); i++) {
                CompoundTag recordTag = recordsList.getCompound(i);
                String key = recordTag.getString("key");
                long day = recordTag.getLong("day");
                int uses = recordTag.getInt("uses");
                manager.usageRecords.put(key, new TradeUsageRecord(day, uses));
            }
        }

        // Clean up old records on load
        manager.cleanupOldRecords();

        CobblemonMerchants.LOGGER.info("Loaded {} daily trade usage records", manager.usageRecords.size());
        return manager;
    }

    /**
     * Simple record to track usage data
     */
    private record TradeUsageRecord(long lastUseDay, int usesToday) {}
}
