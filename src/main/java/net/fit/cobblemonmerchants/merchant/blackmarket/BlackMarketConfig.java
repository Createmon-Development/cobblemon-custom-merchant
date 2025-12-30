package net.fit.cobblemonmerchants.merchant.blackmarket;

/**
 * Configuration for Black Market merchant system.
 * All values can be modified to adjust shop behavior.
 */
public class BlackMarketConfig {
    // ===== ROTATION SETTINGS =====
    /**
     * Number of Minecraft days before shop inventory rotates (default: 14 days = 336000 ticks)
     */
    public static int ROTATION_DAYS = 14;

    /**
     * Number of trades offered per rotation (default: 4)
     */
    public static int TRADES_PER_ROTATION = 4;

    /**
     * Number of Minecraft items in rotation (default: 3)
     */
    public static int MINECRAFT_ITEMS_COUNT = 3;

    /**
     * Number of Cobblemon-exclusive items in rotation (default: 1)
     */
    public static int COBBLEMON_ITEMS_COUNT = 1;

    // ===== VALUE CALCULATION SETTINGS =====
    /**
     * Global price multiplier applied to all calculated values (default: 1.0)
     */
    public static double GLOBAL_PRICE_MULTIPLIER = 1.0;

    /**
     * Multiplier for Cobblemon-exclusive items (default: 1.5)
     */
    public static double COBBLEMON_EXCLUSIVE_MULTIPLIER = 1.5;

    /**
     * Multiplier for non-craftable items (default: 1.3)
     * Note: Growable items don't get this multiplier
     */
    public static double NON_CRAFTABLE_MULTIPLIER = 1.3;

    /**
     * Minimum multiplier for availability (prevents division by zero) (default: 1.0)
     */
    public static double MIN_AVAILABILITY = 1.0;

    // ===== PER-PLAYER VARIABILITY SETTINGS =====
    /**
     * Minimum price variability multiplier (default: 0.6 = -40%)
     */
    public static double MIN_PRICE_VARIABILITY = 0.6;

    /**
     * Maximum price variability multiplier (default: 1.2 = +20%)
     */
    public static double MAX_PRICE_VARIABILITY = 1.2;

    /**
     * Minimum item count variability multiplier (default: 0.7)
     */
    public static double MIN_COUNT_VARIABILITY = 0.7;

    /**
     * Maximum item count variability multiplier (default: 1.3)
     */
    public static double MAX_COUNT_VARIABILITY = 1.3;

    // ===== TRADE LIMITS =====
    /**
     * Maximum uses per trade before it locks (default: 3)
     */
    public static int MAX_TRADE_USES = 3;

    /**
     * Villager XP granted per trade (default: 0)
     */
    public static int VILLAGER_XP_PER_TRADE = 0;

    // ===== CALCULATED VALUES =====
    /**
     * Gets the rotation period in ticks
     * @return Ticks per rotation (24000 ticks = 1 Minecraft day)
     */
    public static long getRotationTicks() {
        return ROTATION_DAYS * 24000L;
    }

    /**
     * Calculates the current rotation ID based on world time
     * @param worldTime Current world time in ticks
     * @return Rotation ID (increments every ROTATION_DAYS)
     */
    public static long getCurrentRotationId(long worldTime) {
        return worldTime / getRotationTicks();
    }

    /**
     * Calculates ticks remaining until next rotation
     * @param worldTime Current world time in ticks
     * @return Ticks until rotation
     */
    public static long getTicksUntilRotation(long worldTime) {
        return getRotationTicks() - (worldTime % getRotationTicks());
    }

    /**
     * Converts ticks to a readable time format
     * @param ticks Number of ticks
     * @return Formatted string "Xd Yh Zm"
     */
    public static String formatTicksAsTime(long ticks) {
        long totalMinutes = ticks / 1000; // 1000 ticks = 1 Minecraft minute
        long days = totalMinutes / 1440; // 1440 minutes = 1 Minecraft day
        long hours = (totalMinutes % 1440) / 60;
        long minutes = totalMinutes % 60;

        return String.format("%dd %dh %dm", days, hours, minutes);
    }
}