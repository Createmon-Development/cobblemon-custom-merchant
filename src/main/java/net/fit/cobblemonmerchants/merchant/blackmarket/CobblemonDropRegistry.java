package net.fit.cobblemonmerchants.merchant.blackmarket;

import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.*;

/**
 * Registry of Cobblemon drop data, dynamically loaded from Cobblemon species at runtime.
 * This analyzes all Pokemon species and their drops to calculate rarity/value.
 *
 * NOTE: This class uses reflection to access Cobblemon classes to avoid compile-time dependency issues.
 */
public class CobblemonDropRegistry {
    private static final Map<String, CobblemonDropData> DROP_DATA = new HashMap<>();
    private static final List<String> MINECRAFT_ITEMS = new ArrayList<>();
    private static final List<String> COBBLEMON_EXCLUSIVE_ITEMS = new ArrayList<>();

    // Tag for Cobblemon held items (items that can be held by Pokemon)
    // Items in this tag are considered "obtainable" and don't get the rarity multiplier
    private static final TagKey<Item> HELD_ITEMS_TAG = TagKey.create(
        Registries.ITEM,
        ResourceLocation.parse("cobblemon:held/is_held_item")
    );

    private static boolean initialized = false;

    /**
     * Initializes the drop registry by analyzing all Cobblemon species.
     * Should be called during mod initialization after Cobblemon is loaded.
     */
    public static void initialize() {
        if (initialized) {
            return;
        }

        CobblemonMerchants.LOGGER.info("Building Cobblemon drop registry from species data...");

        try {
            // Use reflection to access Cobblemon classes
            Class<?> pokemonSpeciesClass = Class.forName("com.cobblemon.mod.common.api.pokemon.PokemonSpecies");
            Class<?> speciesClass = Class.forName("com.cobblemon.mod.common.pokemon.Species");
            Class<?> dropEntryClass = Class.forName("com.cobblemon.mod.common.api.drop.DropEntry");
            Class<?> itemDropEntryClass = Class.forName("com.cobblemon.mod.common.api.drop.ItemDropEntry");

            // Get PokemonSpecies.INSTANCE
            Object pokemonSpeciesInstance = pokemonSpeciesClass.getField("INSTANCE").get(null);

            // Get species collection
            Object speciesCollection = pokemonSpeciesClass.getMethod("getSpecies").invoke(pokemonSpeciesInstance);

            // Map to accumulate drop info for each item
            Map<String, List<CobblemonDropData.PokemonDropInfo>> itemDrops = new HashMap<>();

            // Iterate through species
            for (Object species : (Iterable<?>) speciesCollection) {
                String pokemonName = (String) speciesClass.getMethod("getName").invoke(species);

                // Get drops
                Object dropTable = speciesClass.getMethod("getDrops").invoke(species);
                Object entries = dropTable.getClass().getMethod("getEntries").invoke(dropTable);

                // Iterate through drop entries
                for (Object dropEntry : (Iterable<?>) entries) {
                    if (!itemDropEntryClass.isInstance(dropEntry)) {
                        continue; // Skip non-item drops
                    }

                    // Get item ID
                    Object itemLocation = itemDropEntryClass.getMethod("getItem").invoke(dropEntry);
                    String itemId = itemLocation.toString();

                    // Get percentage
                    float percentage = (Float) dropEntryClass.getMethod("getPercentage").invoke(dropEntry);

                    // Calculate median quantity
                    double medianQuantity = calculateMedianQuantity(dropEntry, itemDropEntryClass);

                    // Add this Pokemon's drop info for this item
                    itemDrops.computeIfAbsent(itemId, k -> new ArrayList<>())
                        .add(new CobblemonDropData.PokemonDropInfo(pokemonName, percentage, medianQuantity));
                }
            }

            // Create CobblemonDropData for each item
            for (Map.Entry<String, List<CobblemonDropData.PokemonDropInfo>> entry : itemDrops.entrySet()) {
                String itemId = entry.getKey();
                List<CobblemonDropData.PokemonDropInfo> drops = entry.getValue();

                // Check if item should be excluded based on mod filtering
                if (isItemExcluded(itemId)) {
                    CobblemonMerchants.LOGGER.debug("Excluding item {} due to mod filter", itemId);
                    continue;
                }

                // Check if item is in the held items tag
                boolean isHeldItem = isItemInHeldTag(itemId);

                CobblemonDropData dropData = new CobblemonDropData(
                    itemId,
                    drops,
                    itemId.startsWith("cobblemon:"),
                    isHeldItem
                );

                DROP_DATA.put(itemId, dropData);

                // Categorize items
                if (dropData.isCobblemonExclusive()) {
                    COBBLEMON_EXCLUSIVE_ITEMS.add(itemId);
                } else {
                    MINECRAFT_ITEMS.add(itemId);
                }
            }

            CobblemonMerchants.LOGGER.info("Loaded {} items from Cobblemon drops ({} Minecraft, {} Cobblemon-exclusive)",
                DROP_DATA.size(), MINECRAFT_ITEMS.size(), COBBLEMON_EXCLUSIVE_ITEMS.size());

            // Log detailed information for all loaded items
            logDropRegistry();

            initialized = true;

        } catch (Exception e) {
            CobblemonMerchants.LOGGER.error("Failed to initialize Cobblemon drop registry - Cobblemon may not be loaded. Using fallback test data.", e);
            e.printStackTrace();
            // Load fallback test data instead of empty data
            loadFallbackTestData();
            initialized = true;
        }

        // Validate that we have data
        if (DROP_DATA.isEmpty()) {
            CobblemonMerchants.LOGGER.warn("Drop registry is empty after initialization! Loading fallback test data.");
            loadFallbackTestData();
        }

        CobblemonMerchants.LOGGER.info("Drop registry initialization complete. Total items: {}, Minecraft: {}, Cobblemon: {}",
            DROP_DATA.size(), MINECRAFT_ITEMS.size(), COBBLEMON_EXCLUSIVE_ITEMS.size());
    }

    /**
     * Loads fallback test data when Cobblemon drops can't be loaded.
     * This ensures the Black Market has items to trade even if Cobblemon integration fails.
     */
    private static void loadFallbackTestData() {
        CobblemonMerchants.LOGGER.info("Loading fallback test data for Black Market...");

        // Add common Minecraft items as test data
        addTestItem("minecraft:diamond", 1.0, 0.05, false);
        addTestItem("minecraft:emerald", 1.0, 0.08, false);
        addTestItem("minecraft:gold_ingot", 1.0, 0.12, false);
        addTestItem("minecraft:iron_ingot", 1.0, 0.20, false);
        addTestItem("minecraft:ender_pearl", 1.0, 0.10, false);
        addTestItem("minecraft:blaze_rod", 1.0, 0.08, false);
        addTestItem("minecraft:ghast_tear", 1.0, 0.06, false);
        addTestItem("minecraft:slime_ball", 1.0, 0.15, false);
        addTestItem("minecraft:rabbit_foot", 1.0, 0.12, false);
        addTestItem("minecraft:phantom_membrane", 1.0, 0.10, false);

        // Add some Cobblemon items (these won't work unless Cobblemon is loaded, but they're here for structure)
        addTestItem("cobblemon:exp_candy_xs", 1.0, 0.30, true);
        addTestItem("cobblemon:exp_candy_s", 1.0, 0.20, true);
        addTestItem("cobblemon:exp_candy_m", 1.0, 0.12, true);
        addTestItem("cobblemon:exp_candy_l", 1.0, 0.08, true);

        CobblemonMerchants.LOGGER.info("Loaded {} fallback test items ({} Minecraft, {} Cobblemon)",
            DROP_DATA.size(), MINECRAFT_ITEMS.size(), COBBLEMON_EXCLUSIVE_ITEMS.size());
    }

    /**
     * Adds a test item to the registry
     */
    private static void addTestItem(String itemId, double quantity, double dropRate, boolean cobblemonExclusive) {
        // Create a single test drop entry
        List<CobblemonDropData.PokemonDropInfo> drops = new ArrayList<>();
        drops.add(new CobblemonDropData.PokemonDropInfo("test_pokemon", dropRate, quantity));

        // Check if item is in held tag
        boolean isHeldItem = isItemInHeldTag(itemId);

        CobblemonDropData data = new CobblemonDropData(
            itemId,
            drops,
            cobblemonExclusive,
            isHeldItem
        );

        DROP_DATA.put(itemId, data);

        if (cobblemonExclusive) {
            COBBLEMON_EXCLUSIVE_ITEMS.add(itemId);
        } else {
            MINECRAFT_ITEMS.add(itemId);
        }
    }

    /**
     * Checks if an item should be excluded based on mod filtering
     * @param itemId The item ID to check
     * @return true if the item should be excluded
     */
    private static boolean isItemExcluded(String itemId) {
        if (BlackMarketConfig.EXCLUDED_MODS.isEmpty()) {
            return false;
        }

        for (String excludedMod : BlackMarketConfig.EXCLUDED_MODS) {
            if (itemId.startsWith(excludedMod + ":")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if an item is in the cobblemon:held/is_held_item tag
     * @param itemId The item ID to check
     * @return true if the item is in the held items tag
     */
    private static boolean isItemInHeldTag(String itemId) {
        try {
            // Parse the item ID as a ResourceLocation
            ResourceLocation itemLocation = ResourceLocation.parse(itemId);

            // Get the item from the registry
            net.minecraft.core.Registry<Item> itemRegistry =
                net.minecraft.core.registries.BuiltInRegistries.ITEM;

            Optional<Item> itemOptional = itemRegistry.getOptional(itemLocation);
            if (itemOptional.isEmpty()) {
                return false;
            }

            Item item = itemOptional.get();

            // Check if the item is in the held items tag
            return item.builtInRegistryHolder().is(HELD_ITEMS_TAG);
        } catch (Exception e) {
            CobblemonMerchants.LOGGER.warn("Failed to check held tag for item {}: {}", itemId, e.getMessage());
            return false;
        }
    }

    /**
     * Calculates the median quantity for an item drop entry using reflection
     */
    private static double calculateMedianQuantity(Object itemDrop, Class<?> itemDropEntryClass) {
        try {
            // Check if quantityRange is defined
            String quantityRange = (String) itemDropEntryClass.getMethod("getQuantityRange").invoke(itemDrop);
            if (quantityRange != null && !quantityRange.isEmpty()) {
                String[] parts = quantityRange.split("-");
                int min = Integer.parseInt(parts[0].trim());
                int max = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : min;
                return (min + max) / 2.0;
            }

            // Fall back to quantity field
            return (Integer) itemDropEntryClass.getMethod("getQuantity").invoke(itemDrop);
        } catch (Exception e) {
            return 1.0; // Default fallback
        }
    }

    /**
     * Gets drop data for a specific item
     */
    public static CobblemonDropData getDropData(String itemId) {
        if (!initialized) {
            initialize();
        }
        return DROP_DATA.get(itemId);
    }

    /**
     * Gets all registered drop data
     */
    public static Map<String, CobblemonDropData> getAllDropData() {
        if (!initialized) {
            initialize();
        }
        return Collections.unmodifiableMap(DROP_DATA);
    }

    /**
     * Gets list of Minecraft items (non-Cobblemon-exclusive)
     */
    public static List<String> getMinecraftItems() {
        if (!initialized) {
            initialize();
        }
        return Collections.unmodifiableList(MINECRAFT_ITEMS);
    }

    /**
     * Gets list of Cobblemon-exclusive items
     */
    public static List<String> getCobblemonExclusiveItems() {
        if (!initialized) {
            initialize();
        }
        return Collections.unmodifiableList(COBBLEMON_EXCLUSIVE_ITEMS);
    }

    /**
     * Checks if an item has registered drop data
     */
    public static boolean hasDropData(String itemId) {
        if (!initialized) {
            initialize();
        }
        return DROP_DATA.containsKey(itemId);
    }

    /**
     * Logs detailed information about all items in the drop registry with their calculation variables
     */
    private static void logDropRegistry() {
        CobblemonMerchants.LOGGER.info("==================== COBBLEMON DROP REGISTRY ====================");
        CobblemonMerchants.LOGGER.info("Total Items: {} (Minecraft: {}, Cobblemon-Exclusive: {})",
            DROP_DATA.size(), MINECRAFT_ITEMS.size(), COBBLEMON_EXCLUSIVE_ITEMS.size());
        CobblemonMerchants.LOGGER.info("=================================================================");

        // Sort items by category for better readability
        List<String> allItems = new ArrayList<>(DROP_DATA.keySet());
        Collections.sort(allItems);

        // Log Minecraft items first
        CobblemonMerchants.LOGGER.info("");
        CobblemonMerchants.LOGGER.info("--- MINECRAFT ITEMS ({}) ---", MINECRAFT_ITEMS.size());
        logItemCategory(allItems, false);

        // Then Cobblemon-exclusive items
        CobblemonMerchants.LOGGER.info("");
        CobblemonMerchants.LOGGER.info("--- COBBLEMON-EXCLUSIVE ITEMS ({}) ---", COBBLEMON_EXCLUSIVE_ITEMS.size());
        logItemCategory(allItems, true);

        CobblemonMerchants.LOGGER.info("");
        CobblemonMerchants.LOGGER.info("=================================================================");
    }

    /**
     * Logs items from a specific category with all their calculation variables
     */
    private static void logItemCategory(List<String> allItems, boolean cobblemonExclusive) {
        for (String itemId : allItems) {
            CobblemonDropData dropData = DROP_DATA.get(itemId);
            if (dropData.isCobblemonExclusive() != cobblemonExclusive) {
                continue; // Skip items not in this category
            }

            // Calculate all the variables used in the value formula
            double avgDropChance = dropData.getAverageDropChance();
            double avgDropQty = dropData.getAverageDropQuantity();
            int pokemonCount = dropData.getPokemonDrops().size();
            boolean isHeldItem = dropData.isHeldItem();
            boolean exclusive = dropData.isCobblemonExclusive();

            // Calculate formula components
            double rarityScore = 100.0 / avgDropChance;
            if (avgDropChance >= 100.0) {
                rarityScore *= (1.0 / avgDropQty); // Quantity penalty
            }
            double exclusivityMult = exclusive ? BlackMarketConfig.COBBLEMON_EXCLUSIVE_MULTIPLIER : 1.0;
            double battleUsefulnessMult = isHeldItem ? BlackMarketConfig.HELD_ITEM_MULTIPLIER : 1.0;
            double availability = Math.max(BlackMarketConfig.MIN_AVAILABILITY, pokemonCount);
            double gameplayMod = DropValueCalculator.getGameplayModifier(itemId);
            double baseValue = DropValueCalculator.calculateBaseValue(dropData);

            // Format the output
            CobblemonMerchants.LOGGER.info("  {}", itemId);
            CobblemonMerchants.LOGGER.info("    Drop Stats:");
            CobblemonMerchants.LOGGER.info("      - Avg Drop Chance: {}{}",
                String.format("%.2f", avgDropChance), "%");
            CobblemonMerchants.LOGGER.info("      - Avg Drop Quantity: {}",
                String.format("%.2f", avgDropQty));
            CobblemonMerchants.LOGGER.info("      - Pokemon Count: {} pokemon", pokemonCount);

            CobblemonMerchants.LOGGER.info("    Item Properties:");
            CobblemonMerchants.LOGGER.info("      - Cobblemon-Exclusive: {}", exclusive);
            CobblemonMerchants.LOGGER.info("      - Is Held Item: {}", isHeldItem);

            CobblemonMerchants.LOGGER.info("    Formula Variables:");
            CobblemonMerchants.LOGGER.info("      - Rarity Score: {} (100 / {}{}{})",
                String.format("%.4f", rarityScore),
                String.format("%.2f", avgDropChance),
                avgDropChance >= 100.0 ? " × quantity penalty " : "",
                avgDropChance >= 100.0 ? String.format("%.2f", 1.0 / avgDropQty) : "");
            CobblemonMerchants.LOGGER.info("      - Exclusivity Multiplier: {}x",
                String.format("%.2f", exclusivityMult));
            CobblemonMerchants.LOGGER.info("      - Battle Usefulness Multiplier: {}x{}",
                String.format("%.2f", battleUsefulnessMult),
                isHeldItem ? " (held item - battle useful)" : " (not held item)");
            CobblemonMerchants.LOGGER.info("      - Availability Divisor: {}",
                String.format("%.2f", availability));
            CobblemonMerchants.LOGGER.info("      - Gameplay Modifier: {}x{}",
                String.format("%.2f", gameplayMod),
                gameplayMod != 1.0 ? " (CUSTOM)" : "");
            CobblemonMerchants.LOGGER.info("      - Global Multiplier: {}x",
                String.format("%.2f", BlackMarketConfig.GLOBAL_PRICE_MULTIPLIER));

            CobblemonMerchants.LOGGER.info("    Calculated Base Value: {} relic coins",
                String.format("%.2f", baseValue));
            CobblemonMerchants.LOGGER.info("    Price Range (with variance): {}-{} coins",
                Math.max(1, (int)Math.round(baseValue * BlackMarketConfig.MIN_PRICE_VARIABILITY)),
                Math.max(1, (int)Math.round(baseValue * BlackMarketConfig.MAX_PRICE_VARIABILITY)));

            // List which Pokemon drop this item
            if (pokemonCount <= 5) {
                // If 5 or fewer, list them all
                StringBuilder pokemonList = new StringBuilder();
                for (CobblemonDropData.PokemonDropInfo info : dropData.getPokemonDrops()) {
                    if (pokemonList.length() > 0) pokemonList.append(", ");
                    pokemonList.append(String.format("%s (%.1f%%, qty:%.1f)",
                        info.pokemonName(), info.dropChance(), info.medianQuantity()));
                }
                CobblemonMerchants.LOGGER.info("    Dropped by: {}", pokemonList);
            } else {
                // If more than 5, just list first 3 and indicate there are more
                StringBuilder pokemonList = new StringBuilder();
                List<CobblemonDropData.PokemonDropInfo> drops = dropData.getPokemonDrops();
                for (int i = 0; i < Math.min(3, drops.size()); i++) {
                    CobblemonDropData.PokemonDropInfo info = drops.get(i);
                    if (pokemonList.length() > 0) pokemonList.append(", ");
                    pokemonList.append(String.format("%s (%.1f%%)", info.pokemonName(), info.dropChance()));
                }
                CobblemonMerchants.LOGGER.info("    Dropped by: {} ... and {} more",
                    pokemonList, pokemonCount - 3);
            }
            CobblemonMerchants.LOGGER.info("");
        }
    }

}