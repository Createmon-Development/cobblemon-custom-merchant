package net.fit.cobblemonmerchants.merchant.rewards;

import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages per-player permanent trade usage tracking for non-daily-reset trades.
 * Unlike DailyTradeResetManager, these records do NOT reset at midnight.
 *
 * Key format depends on sync_trades config:
 * - sync_trades: true  → "playerUUID:merchantTypeId:tradeIndex" (shared across all entities of same type)
 * - sync_trades: false → "playerUUID:entity:entityUUID:tradeIndex" (unique per entity)
 */
public class PermanentTradeUsageManager extends SavedData {

    private static final String DATA_NAME = "cobblemon_merchant_permanent_trade_usage";

    // Key → total uses count
    private final Map<String, Integer> usageRecords = new HashMap<>();

    public PermanentTradeUsageManager() {
    }

    /**
     * Get the PermanentTradeUsageManager for a server level.
     */
    public static PermanentTradeUsageManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new Factory<>(PermanentTradeUsageManager::new, PermanentTradeUsageManager::load),
            DATA_NAME
        );
    }

    /**
     * Build a key for sync_trades=true (shared across all entities of same merchant type).
     */
    public static String buildSharedKey(UUID playerUUID, String merchantTypeId, int tradeIndex) {
        return playerUUID.toString() + ":" + merchantTypeId + ":" + tradeIndex;
    }

    /**
     * Build a key for sync_trades=false (unique per entity).
     */
    public static String buildEntityKey(UUID playerUUID, UUID entityUUID, int tradeIndex) {
        return playerUUID.toString() + ":entity:" + entityUUID.toString() + ":" + tradeIndex;
    }

    /**
     * Get the number of times a player has used a specific trade.
     */
    public int getUses(String key) {
        return usageRecords.getOrDefault(key, 0);
    }

    /**
     * Check if a player can still use a trade.
     */
    public boolean canUse(String key, int maxUses) {
        return getUses(key) < maxUses;
    }

    /**
     * Record that a player has used a trade (increment by 1).
     */
    public void recordUse(String key) {
        int current = usageRecords.getOrDefault(key, 0);
        usageRecords.put(key, current + 1);
        setDirty();

        CobblemonMerchants.LOGGER.debug("Permanent trade use recorded: key={}, total uses={}",
            key, current + 1);
    }

    /**
     * Reset usage for a specific key.
     */
    public boolean resetUsage(String key) {
        Integer removed = usageRecords.remove(key);
        if (removed != null) {
            setDirty();
            return true;
        }
        return false;
    }

    /**
     * Reset all permanent trade usage for a specific player.
     */
    public int resetPlayerUsage(UUID playerUUID) {
        String prefix = playerUUID.toString() + ":";
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
            CobblemonMerchants.LOGGER.info("Reset {} permanent trade records for player {}", count, playerUUID);
        }
        return count;
    }

    /**
     * Clear all permanent trade usage records (all players, all merchants).
     * Called when /daily reset trades is used to fully reset all trade stock.
     */
    public int clearAll() {
        int count = usageRecords.size();
        if (count > 0) {
            usageRecords.clear();
            setDirty();
            CobblemonMerchants.LOGGER.info("Cleared all {} permanent trade usage records", count);
        }
        return count;
    }

    /**
     * Reset all permanent trade usage for a specific merchant type (all players).
     */
    public int resetMerchantTypeUsage(String merchantTypeId) {
        int count = 0;
        var iterator = usageRecords.entrySet().iterator();
        while (iterator.hasNext()) {
            String key = iterator.next().getKey();
            // Match keys containing the merchant type ID (for shared keys)
            if (key.contains(":" + merchantTypeId + ":")) {
                iterator.remove();
                count++;
            }
        }
        if (count > 0) {
            setDirty();
            CobblemonMerchants.LOGGER.info("Reset {} permanent trade records for merchant type {}", count, merchantTypeId);
        }
        return count;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        ListTag recordsList = new ListTag();

        for (Map.Entry<String, Integer> entry : usageRecords.entrySet()) {
            CompoundTag recordTag = new CompoundTag();
            recordTag.putString("key", entry.getKey());
            recordTag.putInt("uses", entry.getValue());
            recordsList.add(recordTag);
        }

        tag.put("records", recordsList);
        return tag;
    }

    public static PermanentTradeUsageManager load(CompoundTag tag, HolderLookup.Provider registries) {
        PermanentTradeUsageManager manager = new PermanentTradeUsageManager();

        if (tag.contains("records", Tag.TAG_LIST)) {
            ListTag recordsList = tag.getList("records", Tag.TAG_COMPOUND);

            for (int i = 0; i < recordsList.size(); i++) {
                CompoundTag recordTag = recordsList.getCompound(i);
                String key = recordTag.getString("key");
                int uses = recordTag.getInt("uses");
                manager.usageRecords.put(key, uses);
            }
        }

        CobblemonMerchants.LOGGER.info("Loaded {} permanent trade usage records", manager.usageRecords.size());
        return manager;
    }
}
