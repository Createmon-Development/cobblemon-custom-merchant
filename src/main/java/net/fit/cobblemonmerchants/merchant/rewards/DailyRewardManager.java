package net.fit.cobblemonmerchants.merchant.rewards;

import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages daily reward claims for merchants across the server.
 * Tracks which players have claimed rewards from which merchants on which day.
 */
public class DailyRewardManager extends SavedData {

    private static final String DATA_NAME = "cobblemon_merchant_daily_rewards";

    // Key: "playerUUID:merchantId:variant" -> Value: Last claim date (epoch day)
    private final Map<String, Long> claimRecords = new HashMap<>();

    public DailyRewardManager() {
    }

    /**
     * Get the DailyRewardManager for a server level
     */
    public static DailyRewardManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new Factory<>(DailyRewardManager::new, DailyRewardManager::load),
            DATA_NAME
        );
    }

    /**
     * Creates a unique key for tracking claims.
     * When sharedCooldown is true: key is "playerUUID:merchantId" (all merchants of same type share cooldown)
     * When sharedCooldown is false: key is "playerUUID:merchantId:entityUUID" (each entity has own cooldown)
     * Note: Variants are NOT included in the key - all variants always share the same cooldown.
     */
    private static String createKey(UUID playerUUID, String merchantId, UUID merchantEntityUUID) {
        if (merchantEntityUUID != null) {
            // Per-entity cooldown (sharedCooldown = false)
            return playerUUID.toString() + ":" + merchantId + ":" + merchantEntityUUID.toString();
        } else {
            // Shared cooldown (sharedCooldown = true) - all merchants of this type share cooldown
            return playerUUID.toString() + ":" + merchantId;
        }
    }

    /**
     * Check if a player has already claimed their daily reward today.
     * @param playerUUID The player's UUID
     * @param merchantId The merchant type ID (e.g., "cobblemoncustommerchants:gambler")
     * @param merchantEntityUUID The specific merchant entity's UUID, or null for shared cooldown
     */
    public boolean hasClaimedToday(UUID playerUUID, String merchantId, UUID merchantEntityUUID) {
        String key = createKey(playerUUID, merchantId, merchantEntityUUID);
        Long lastClaimDay = claimRecords.get(key);

        if (lastClaimDay == null) {
            return false;
        }

        long todayEpochDay = LocalDate.now(ZoneId.systemDefault()).toEpochDay();
        return lastClaimDay == todayEpochDay;
    }

    /**
     * Record that a player has claimed their daily reward.
     * @param playerUUID The player's UUID
     * @param merchantId The merchant type ID (e.g., "cobblemoncustommerchants:gambler")
     * @param merchantEntityUUID The specific merchant entity's UUID, or null for shared cooldown
     */
    public void recordClaim(UUID playerUUID, String merchantId, UUID merchantEntityUUID) {
        String key = createKey(playerUUID, merchantId, merchantEntityUUID);
        long todayEpochDay = LocalDate.now(ZoneId.systemDefault()).toEpochDay();
        claimRecords.put(key, todayEpochDay);
        setDirty();

        CobblemonMerchants.LOGGER.info("Daily reward claimed: player={}, merchant={}, entityUUID={}",
            playerUUID, merchantId, merchantEntityUUID != null ? merchantEntityUUID : "shared");
    }

    /**
     * Reset daily reward claims for a specific player.
     * If merchantId is null, resets all claims for the player.
     * If merchantId is specified, only resets claims for that merchant.
     */
    public void resetClaims(UUID playerUUID, String merchantId) {
        if (merchantId == null) {
            // Reset all claims for this player
            claimRecords.entrySet().removeIf(entry -> entry.getKey().startsWith(playerUUID.toString() + ":"));
            CobblemonMerchants.LOGGER.info("Reset all daily reward claims for player {}", playerUUID);
        } else {
            // Reset claims for specific merchant (all variants)
            String prefix = playerUUID.toString() + ":" + merchantId + ":";
            claimRecords.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix));
            CobblemonMerchants.LOGGER.info("Reset daily reward claims for player {} from merchant {}", playerUUID, merchantId);
        }
        setDirty();
    }

    /**
     * Get the number of days since a player last claimed from a specific merchant.
     * Returns -1 if never claimed.
     * @param playerUUID The player's UUID
     * @param merchantId The merchant type ID
     * @param merchantEntityUUID The specific merchant entity's UUID, or null for shared cooldown
     */
    public int getDaysSinceLastClaim(UUID playerUUID, String merchantId, UUID merchantEntityUUID) {
        String key = createKey(playerUUID, merchantId, merchantEntityUUID);
        Long lastClaimDay = claimRecords.get(key);

        if (lastClaimDay == null) {
            return -1;
        }

        long todayEpochDay = LocalDate.now(ZoneId.systemDefault()).toEpochDay();
        return (int) (todayEpochDay - lastClaimDay);
    }

    /**
     * Get the duration until the next daily reset (midnight in server timezone)
     */
    public static Duration getTimeUntilReset() {
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        LocalDateTime midnight = LocalDateTime.of(LocalDate.now(ZoneId.systemDefault()).plusDays(1), LocalTime.MIDNIGHT);
        return Duration.between(now, midnight);
    }

    /**
     * Get a formatted string showing time until reset (e.g., "5h 23m")
     */
    public static String getFormattedTimeUntilReset() {
        Duration duration = getTimeUntilReset();
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();

        if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else {
            return minutes + "m";
        }
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        ListTag claimsList = new ListTag();

        for (Map.Entry<String, Long> entry : claimRecords.entrySet()) {
            CompoundTag claimTag = new CompoundTag();
            claimTag.putString("key", entry.getKey());
            claimTag.putLong("day", entry.getValue());
            claimsList.add(claimTag);
        }

        tag.put("claims", claimsList);
        return tag;
    }

    public static DailyRewardManager load(CompoundTag tag, HolderLookup.Provider registries) {
        DailyRewardManager manager = new DailyRewardManager();

        if (tag.contains("claims", Tag.TAG_LIST)) {
            ListTag claimsList = tag.getList("claims", Tag.TAG_COMPOUND);

            for (int i = 0; i < claimsList.size(); i++) {
                CompoundTag claimTag = claimsList.getCompound(i);
                String key = claimTag.getString("key");
                long day = claimTag.getLong("day");
                manager.claimRecords.put(key, day);
            }
        }

        CobblemonMerchants.LOGGER.info("Loaded {} daily reward claim records", manager.claimRecords.size());
        return manager;
    }
}
