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
 * Registry for merchant configurations loaded from datapacks.
 * Configs are stored in data/<namespace>/merchants/<path>.json
 */
public class MerchantConfigRegistry extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FOLDER = "merchants";

    private static final MerchantConfigRegistry INSTANCE = new MerchantConfigRegistry();
    private final Map<ResourceLocation, MerchantConfig> configs = new HashMap<>();

    private MerchantConfigRegistry() {
        super(GSON, FOLDER);
    }

    /**
     * Gets the singleton instance
     */
    public static MerchantConfigRegistry getInstance() {
        return INSTANCE;
    }

    @Override
    protected void apply(@NotNull Map<ResourceLocation, JsonElement> objects, @NotNull ResourceManager resourceManager,
                        @NotNull ProfilerFiller profiler) {
        configs.clear();

        CobblemonMerchants.LOGGER.info("DEBUG: Starting merchant config loading, found {} JSON files", objects.size());
        for (ResourceLocation id : objects.keySet()) {
            CobblemonMerchants.LOGGER.info("DEBUG: Found merchant JSON: {}", id);
        }

        for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
            ResourceLocation id = entry.getKey();
            JsonElement json = entry.getValue();

            try {
                var result = MerchantConfig.CODEC.parse(JsonOps.INSTANCE, json);
                if (result.error().isPresent()) {
                    CobblemonMerchants.LOGGER.error("Failed to parse merchant config {}: {}", id, result.error().get().message());
                    CobblemonMerchants.LOGGER.error("JSON content: {}", json);
                    continue;
                }

                MerchantConfig config = result.result().get();
                configs.put(id, config);
                CobblemonMerchants.LOGGER.info("Loaded merchant config: {}", id);

                // Check if this is the black_market config and load its settings
                if (id.getPath().contains("black_market")) {
                    if (config.blackMarketConfig().isPresent()) {
                        net.fit.cobblemonmerchants.merchant.blackmarket.BlackMarketConfig.loadFromData(config.blackMarketConfig().get());
                        CobblemonMerchants.LOGGER.info("Loaded Black Market configuration from: {}", id);
                    } else {
                        CobblemonMerchants.LOGGER.warn("Black Market merchant {} found but missing black_market_config section - using defaults", id);
                    }
                }
            } catch (Exception e) {
                CobblemonMerchants.LOGGER.error("Failed to load merchant config: {}", id, e);
            }
        }

        CobblemonMerchants.LOGGER.info("Loaded {} merchant configs", configs.size());
    }

    /**
     * Gets a merchant config by its resource location
     */
    public static MerchantConfig getConfig(ResourceLocation id) {
        if (INSTANCE == null) {
            return null;
        }
        return INSTANCE.configs.get(id);
    }

    /**
     * Checks if a merchant config exists
     */
    public static boolean hasConfig(ResourceLocation id) {
        if (INSTANCE == null) {
            return false;
        }
        return INSTANCE.configs.containsKey(id);
    }

    /**
     * Gets all loaded merchant configs
     */
    public static Map<ResourceLocation, MerchantConfig> getAllConfigs() {
        if (INSTANCE == null) {
            return Map.of();
        }
        return Map.copyOf(INSTANCE.configs);
    }
}
