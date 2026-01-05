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
     * Creates a unique key for tracking trade usage.
     */
    private static String createKey(UUID playerUUID, String merchantId, int tradeIndex) {
        return playerUUID.toString() + ":" + merchantId + ":" + tradeIndex;
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
     */
    public void cleanupOldRecords() {
        long todayEpochDay = LocalDate.now(ZoneId.systemDefault()).toEpochDay();
        int removed = 0;

        var iterator = usageRecords.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().lastUseDay < todayEpochDay) {
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
