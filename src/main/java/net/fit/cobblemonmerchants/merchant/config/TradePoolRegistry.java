package net.fit.cobblemonmerchants.merchant.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for trade pools loaded from datapacks.
 * Pools are stored in data/<namespace>/pools/<path>.json
 *
 * Example pool file (data/cobblemoncustommerchants/pools/rare_items.json):
 * {
 *   "description": "Pool of rare items for daily rotating trades",
 *   "input_item": "cobblemon:relic_coin",
 *   "entries": [
 *     {
 *       "item_id": "cobblemon:rare_candy",
 *       "input_amount": 50,
 *       "output_amount": 1,
 *       "weight": 10,
 *       "input_variance": 0.2,
 *       "output_variance": 0.0,
 *       "max_uses": 3,
 *       "display_name": "Rare Candy"
 *     },
 *     {
 *       "item_id": "minecraft:diamond",
 *       "input_amount": 25,
 *       "output_amount": 1,
 *       "weight": 20
 *     }
 *   ]
 * }
 */
public class TradePoolRegistry extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FOLDER = "pools";

    private static final TradePoolRegistry INSTANCE = new TradePoolRegistry();
    private final Map<ResourceLocation, TradePool> pools = new HashMap<>();

    private TradePoolRegistry() {
        super(GSON, FOLDER);
    }

    /**
     * Gets the singleton instance
     */
    public static TradePoolRegistry getInstance() {
        return INSTANCE;
    }

    @Override
    protected void apply(@NotNull Map<ResourceLocation, JsonElement> objects, @NotNull ResourceManager resourceManager,
                         @NotNull ProfilerFiller profiler) {
        pools.clear();

        CobblemonMerchants.LOGGER.info("Starting trade pool loading, found {} JSON files", objects.size());

        for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
            ResourceLocation id = entry.getKey();
            JsonElement json = entry.getValue();

            try {
                var result = TradePool.CODEC.parse(JsonOps.INSTANCE, json);
                if (result.error().isPresent()) {
                    CobblemonMerchants.LOGGER.error("Failed to parse trade pool {}: {}", id, result.error().get().message());
                    CobblemonMerchants.LOGGER.error("JSON content: {}", json);
                    continue;
                }

                TradePool pool = result.result().get();
                pools.put(id, pool);

                // Log pool info with tables if present
                if (pool.hasTables()) {
                    int totalEntries = pool.tables().get().values().stream().mapToInt(List::size).sum();
                    CobblemonMerchants.LOGGER.info("Loaded trade pool: {} with {} tables ({} total entries): {}",
                        id, pool.tables().get().size(), totalEntries, pool.getTableNames());
                } else {
                    CobblemonMerchants.LOGGER.info("Loaded trade pool: {} with {} entries (total weight: {})",
                        id, pool.entries().size(), pool.getTotalWeight());
                }

            } catch (Exception e) {
                CobblemonMerchants.LOGGER.error("Failed to load trade pool: {}", id, e);
            }
        }

        CobblemonMerchants.LOGGER.info("Loaded {} trade pools", pools.size());
    }

    /**
     * Gets a trade pool by its resource location
     *
     * @param id The pool ID (e.g., "cobblemoncustommerchants:rare_items")
     * @return The trade pool, or null if not found
     */
    public static TradePool getPool(ResourceLocation id) {
        if (INSTANCE == null) {
            return null;
        }
        return INSTANCE.pools.get(id);
    }

    /**
     * Gets a trade pool by its string ID.
     * Supports dot notation for named sub-pools: "namespace:pool_name.table_name"
     * Examples:
     *   - "cobblemoncustommerchants:rare_items" - returns the whole pool
     *   - "cobblemoncustommerchants:baker.bakery" - returns the "bakery" table from the "baker" pool
     *
     * @param id The pool ID string, optionally with table name after a dot
     * @return The trade pool (or sub-pool), or null if not found
     */
    public static TradePool getPool(String id) {
        try {
            // Check for dot notation: "namespace:pool_name.table_name"
            int colonIndex = id.indexOf(':');
            if (colonIndex == -1) {
                // No namespace, try parsing directly
                return getPool(ResourceLocation.parse(id));
            }

            String namespace = id.substring(0, colonIndex);
            String pathPart = id.substring(colonIndex + 1);

            // Check if there's a dot in the path (indicating table name)
            int dotIndex = pathPart.indexOf('.');
            if (dotIndex == -1) {
                // No table name, return the whole pool
                return getPool(ResourceLocation.parse(id));
            }

            // Extract pool name and table name
            String poolName = pathPart.substring(0, dotIndex);
            String tableName = pathPart.substring(dotIndex + 1);

            // Get the base pool
            ResourceLocation poolId = ResourceLocation.fromNamespaceAndPath(namespace, poolName);
            TradePool basePool = getPool(poolId);
            if (basePool == null) {
                CobblemonMerchants.LOGGER.warn("Base pool not found for sub-pool reference: {}", id);
                return null;
            }

            // Get the sub-pool by table name
            TradePool subPool = basePool.getSubPool(tableName);
            if (subPool == null) {
                CobblemonMerchants.LOGGER.warn("Table '{}' not found in pool '{}'. Available tables: {}",
                    tableName, poolId, basePool.getTableNames());
                return null;
            }

            return subPool;
        } catch (Exception e) {
            CobblemonMerchants.LOGGER.warn("Invalid pool ID format: {}", id, e);
            return null;
        }
    }

    /**
     * Checks if a trade pool exists
     */
    public static boolean hasPool(ResourceLocation id) {
        if (INSTANCE == null) {
            return false;
        }
        return INSTANCE.pools.containsKey(id);
    }

    /**
     * Gets all loaded trade pools
     */
    public static Map<ResourceLocation, TradePool> getAllPools() {
        if (INSTANCE == null) {
            return Map.of();
        }
        return Map.copyOf(INSTANCE.pools);
    }
}
