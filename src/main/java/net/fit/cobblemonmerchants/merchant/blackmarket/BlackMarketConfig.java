package net.fit.cobblemonmerchants.merchant.blackmarket;

import net.fit.cobblemonmerchants.merchant.config.MerchantConfig;

/**
 * Configuration for Black Market merchant system.
 * Values are loaded from black_market.json datapack configuration.
 * This class provides static access to the loaded configuration.
 */
public class BlackMarketConfig {
    /**
     * Loads configuration from a BlackMarketConfigData instance
     * @param configData Configuration data from JSON
     */
    public static void loadFromData(MerchantConfig.BlackMarketConfigData configData) {
        ROTATION_DAYS = configData.rotationDays();
        MINECRAFT_ITEMS_COUNT = configData.minecraftItemsCount();
        COBBLEMON_ITEMS_COUNT = configData.cobblemonItemsCount();
        GLOBAL_PRICE_MULTIPLIER = configData.globalPriceMultiplier();
        COBBLEMON_EXCLUSIVE_MULTIPLIER = configData.cobblemonExclusiveMultiplier();
        HELD_ITEM_MULTIPLIER = configData.heldItemMultiplier();
        MIN_TRADE_USES = configData.minTradeUses();
        BASE_MAX_TRADE_USES = configData.baseTradeUses();
        MAX_TRADE_USES = configData.maxTradeUses();
        TRADE_USE_THRESHOLD = configData.tradeUseThreshold();
        MIN_PRICE_VARIABILITY = configData.minPriceVariability();
        MAX_PRICE_VARIABILITY = configData.maxPriceVariability();
        MIN_COUNT_VARIABILITY = configData.minCountVariability();
        MAX_COUNT_VARIABILITY = configData.maxCountVariability();
        MIN_TRADE_USE_VARIABILITY = configData.minTradeUseVariability();
        MAX_TRADE_USE_VARIABILITY = configData.maxTradeUseVariability();
        LUCKY_TRADES_ENABLED = configData.luckyTradesEnabled();
        LUCKY_TRADE_CHANCE = configData.luckyTradeChance();
        LUCKY_TRADE_PRICE_MULTIPLIER = configData.luckyTradePriceMultiplier();
        LUCKY_TRADE_USES_MULTIPLIER = configData.luckyTradeUsesMultiplier();
        LOW_COST_MULTI_ITEM_THRESHOLD = configData.lowCostMultiItemThreshold();
        MIN_SINGLE_RC_PRICE = configData.minSingleRcPrice();
        MAX_SINGLE_RC_PRICE = configData.maxSingleRcPrice();
        OUTLIER_DETECTION_ENABLED = configData.outlierDetectionEnabled();
        OUTLIER_PENALTY_MULTIPLIER = configData.outlierPenaltyMultiplier();
        CRAFTABLE_ITEM_MAX_VALUE = configData.craftableItemMaxValue();
        CRAFTING_RECIPE_CHECK_ENABLED = configData.craftingRecipeCheckEnabled();
        EXCLUDED_MODS = new java.util.ArrayList<>(configData.excludedMods());

        // Load gameplay modifiers
        net.fit.cobblemonmerchants.merchant.blackmarket.DropValueCalculator.clearGameplayModifiers();
        for (java.util.Map.Entry<String, Double> entry : configData.gameplayModifiers().entrySet()) {
            net.fit.cobblemonmerchants.merchant.blackmarket.DropValueCalculator.setGameplayModifier(entry.getKey(), entry.getValue());
        }
    }
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
     * Multiplier for held items (items in cobblemon:held/is_held_item tag) (default: 1.3)
     * These items are battle-useful and thus more valuable
     */
    public static double HELD_ITEM_MULTIPLIER = 1.3;

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
     * Base maximum uses per trade before it locks (default: 3)
     * NOTE: This is now calculated dynamically based on item value
     * This value is used as a reference point in the formula
     */
    public static int BASE_MAX_TRADE_USES = 3;

    /**
     * Minimum trade uses for any item (default: 1)
     */
    public static int MIN_TRADE_USES = 1;

    /**
     * Maximum trade uses for very cheap items (default: 10)
     */
    public static int MAX_TRADE_USES = 10;

    /**
     * Base value threshold where trades transition from many to few uses (default: 10 coins)
     * Items below this get more trades, items above get fewer
     */
    public static double TRADE_USE_THRESHOLD = 10.0;

    /**
     * Minimum variance for trade uses (default: 0.7 = -30%)
     */
    public static double MIN_TRADE_USE_VARIABILITY = 0.7;

    /**
     * Maximum variance for trade uses (default: 1.3 = +30%)
     * Variance scales with base trade uses (more uses = more variance)
     */
    public static double MAX_TRADE_USE_VARIABILITY = 1.3;

    /**
     * Villager XP granted per trade (default: 0)
     */
    public static int VILLAGER_XP_PER_TRADE = 0;

    // ===== LUCKY TRADE SETTINGS =====
    /**
     * Whether lucky trades are enabled (default: true)
     */
    public static boolean LUCKY_TRADES_ENABLED = true;

    /**
     * Chance for a trade to be lucky (default: 0.01 = 1%)
     */
    public static double LUCKY_TRADE_CHANCE = 0.01;

    /**
     * Price multiplier for lucky trades (default: 1.5 = 50% bonus)
     */
    public static double LUCKY_TRADE_PRICE_MULTIPLIER = 1.5;

    /**
     * Trade uses multiplier for lucky trades (default: 2.0 = double trades)
     */
    public static double LUCKY_TRADE_USES_MULTIPLIER = 2.0;

    // ===== LOW COST TRADE SETTINGS =====
    /**
     * Threshold for low-cost trades that require multiple items (default: 3 RC)
     * Trades at or below this cost will require multiple items
     */
    public static int LOW_COST_MULTI_ITEM_THRESHOLD = 3;

    /**
     * Minimum price for single RC trades after variation (default: 1)
     */
    public static int MIN_SINGLE_RC_PRICE = 1;

    /**
     * Maximum price for single RC trades after variation (default: 5)
     */
    public static int MAX_SINGLE_RC_PRICE = 5;

    // ===== VALUE CAPPING SETTINGS =====
    /**
     * Whether to detect and penalize outlier Pokemon (100% drop rate on rare items)
     */
    public static boolean OUTLIER_DETECTION_ENABLED = true;

    /**
     * If a Pokemon drops an item 100% and is the only/main dropper, apply this penalty (default: 0.5)
     */
    public static double OUTLIER_PENALTY_MULTIPLIER = 0.5;

    /**
     * Maximum value cap for craftable items (default: 10 RC)
     * Items with crafting recipes won't exceed this value
     */
    public static int CRAFTABLE_ITEM_MAX_VALUE = 10;

    /**
     * Whether crafting recipe detection is enabled (default: true)
     */
    public static boolean CRAFTING_RECIPE_CHECK_ENABLED = true;

    // ===== MOD FILTERING SETTINGS =====
    /**
     * List of mod IDs to exclude from drop pool (e.g., "modid:*")
     * Loaded from configuration
     */
    public static java.util.List<String> EXCLUDED_MODS = new java.util.ArrayList<>();

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