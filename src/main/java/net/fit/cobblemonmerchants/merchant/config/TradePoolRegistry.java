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
                CobblemonMerchants.LOGGER.info("Loaded trade pool: {} with {} entries (total weight: {})",
                    id, pool.entries().size(), pool.getTotalWeight());

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
     * Gets a trade pool by its string ID
     *
     * @param id The pool ID string (e.g., "cobblemoncustommerchants:rare_items")
     * @return The trade pool, or null if not found
     */
    public static TradePool getPool(String id) {
        try {
            return getPool(ResourceLocation.parse(id));
        } catch (Exception e) {
            CobblemonMerchants.LOGGER.warn("Invalid pool ID format: {}", id);
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
