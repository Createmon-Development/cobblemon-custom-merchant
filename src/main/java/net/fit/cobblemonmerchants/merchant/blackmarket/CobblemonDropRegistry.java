package net.fit.cobblemonmerchants.merchant.blackmarket;

import net.fit.cobblemonmerchants.CobblemonMerchants;

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

    // Manually defined metadata for items (craftable, growable)
    private static final Map<String, ItemMetadata> ITEM_METADATA = new HashMap<>();

    private static boolean initialized = false;

    static {
        initializeItemMetadata();
    }

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

                ItemMetadata metadata = ITEM_METADATA.getOrDefault(itemId, new ItemMetadata(false, false, false));

                CobblemonDropData dropData = new CobblemonDropData(
                    itemId,
                    drops,
                    itemId.startsWith("cobblemon:"),
                    metadata.craftable,
                    metadata.growable
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

            // Log some example items for debugging
            if (!MINECRAFT_ITEMS.isEmpty()) {
                CobblemonMerchants.LOGGER.info("Example Minecraft items: {}", MINECRAFT_ITEMS.subList(0, Math.min(5, MINECRAFT_ITEMS.size())));
            }
            if (!COBBLEMON_EXCLUSIVE_ITEMS.isEmpty()) {
                CobblemonMerchants.LOGGER.info("Example Cobblemon items: {}", COBBLEMON_EXCLUSIVE_ITEMS.subList(0, Math.min(5, COBBLEMON_EXCLUSIVE_ITEMS.size())));
            }

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
        addTestItem("minecraft:diamond", 1.0, 0.05, false, false, false);
        addTestItem("minecraft:emerald", 1.0, 0.08, false, false, false);
        addTestItem("minecraft:gold_ingot", 1.0, 0.12, false, false, false);
        addTestItem("minecraft:iron_ingot", 1.0, 0.20, false, false, false);
        addTestItem("minecraft:ender_pearl", 1.0, 0.10, false, false, false);
        addTestItem("minecraft:blaze_rod", 1.0, 0.08, false, false, false);
        addTestItem("minecraft:ghast_tear", 1.0, 0.06, false, false, false);
        addTestItem("minecraft:slime_ball", 1.0, 0.15, false, false, false);
        addTestItem("minecraft:rabbit_foot", 1.0, 0.12, false, false, false);
        addTestItem("minecraft:phantom_membrane", 1.0, 0.10, false, false, false);

        // Add some Cobblemon items (these won't work unless Cobblemon is loaded, but they're here for structure)
        addTestItem("cobblemon:exp_candy_xs", 1.0, 0.30, false, false, true);
        addTestItem("cobblemon:exp_candy_s", 1.0, 0.20, false, false, true);
        addTestItem("cobblemon:exp_candy_m", 1.0, 0.12, false, false, true);
        addTestItem("cobblemon:exp_candy_l", 1.0, 0.08, false, false, true);

        CobblemonMerchants.LOGGER.info("Loaded {} fallback test items ({} Minecraft, {} Cobblemon)",
            DROP_DATA.size(), MINECRAFT_ITEMS.size(), COBBLEMON_EXCLUSIVE_ITEMS.size());
    }

    /**
     * Adds a test item to the registry
     */
    private static void addTestItem(String itemId, double quantity, double dropRate,
                                    boolean craftable, boolean growable, boolean cobblemonExclusive) {
        // Create a single test drop entry
        List<CobblemonDropData.PokemonDropInfo> drops = new ArrayList<>();
        drops.add(new CobblemonDropData.PokemonDropInfo("test_pokemon", dropRate, quantity));

        CobblemonDropData data = new CobblemonDropData(
            itemId,
            drops,
            cobblemonExclusive,
            craftable,
            growable
        );

        DROP_DATA.put(itemId, data);

        if (cobblemonExclusive) {
            COBBLEMON_EXCLUSIVE_ITEMS.add(itemId);
        } else {
            MINECRAFT_ITEMS.add(itemId);
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
     * Initializes metadata for specific items (craftable, growable status)
     */
    private static void initializeItemMetadata() {
        // Growable items
        ITEM_METADATA.put("minecraft:carrot", new ItemMetadata(false, true, false));
        ITEM_METADATA.put("minecraft:potato", new ItemMetadata(false, true, false));
        ITEM_METADATA.put("minecraft:wheat_seeds", new ItemMetadata(false, true, false));
        ITEM_METADATA.put("minecraft:beetroot_seeds", new ItemMetadata(false, true, false));
        ITEM_METADATA.put("minecraft:melon_seeds", new ItemMetadata(false, true, false));
        ITEM_METADATA.put("minecraft:pumpkin_seeds", new ItemMetadata(false, true, false));
        ITEM_METADATA.put("minecraft:apple", new ItemMetadata(false, true, false));
        ITEM_METADATA.put("minecraft:cocoa_beans", new ItemMetadata(false, true, false));

        // Craftable items
        ITEM_METADATA.put("minecraft:string", new ItemMetadata(true, false, false));
        ITEM_METADATA.put("minecraft:gold_ingot", new ItemMetadata(true, false, false));
        ITEM_METADATA.put("minecraft:iron_ingot", new ItemMetadata(true, false, false));
        ITEM_METADATA.put("minecraft:stick", new ItemMetadata(true, false, false));
        ITEM_METADATA.put("minecraft:leather", new ItemMetadata(true, false, false));

        // Neither craftable nor growable
        ITEM_METADATA.put("minecraft:ender_pearl", new ItemMetadata(false, false, false));
        ITEM_METADATA.put("minecraft:diamond", new ItemMetadata(false, false, false));
        ITEM_METADATA.put("minecraft:emerald", new ItemMetadata(false, false, false));
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
     * Metadata about an item's crafting/growing properties
     */
    private record ItemMetadata(boolean craftable, boolean growable, boolean reserved) {}
}