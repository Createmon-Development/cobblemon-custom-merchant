package net.fit.cobblemonmerchants.npc;

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
 * Registry for NPC configurations loaded from datapacks.
 * Configs are stored in data/<namespace>/npcs/<path>.json
 */
public class NPCConfigRegistry extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FOLDER = "npcs";

    private static final NPCConfigRegistry INSTANCE = new NPCConfigRegistry();
    private final Map<ResourceLocation, NPCConfig> configs = new HashMap<>();

    private NPCConfigRegistry() {
        super(GSON, FOLDER);
    }

    /**
     * Gets the singleton instance.
     */
    public static NPCConfigRegistry getInstance() {
        return INSTANCE;
    }

    @Override
    protected void apply(@NotNull Map<ResourceLocation, JsonElement> objects, @NotNull ResourceManager resourceManager,
                        @NotNull ProfilerFiller profiler) {
        configs.clear();

        CobblemonMerchants.LOGGER.debug("Starting NPC config loading, found {} JSON files", objects.size());

        for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
            ResourceLocation id = entry.getKey();
            JsonElement json = entry.getValue();

            try {
                var result = NPCConfig.CODEC.parse(JsonOps.INSTANCE, json);
                if (result.error().isPresent()) {
                    CobblemonMerchants.LOGGER.error("Failed to parse NPC config {}: {}",
                        id, result.error().get().message());
                    continue;
                }

                NPCConfig config = result.result().get();
                configs.put(id, config);
                CobblemonMerchants.LOGGER.info("Loaded NPC config: {} (displayName={})",
                    id, config.displayName());

            } catch (Exception e) {
                CobblemonMerchants.LOGGER.error("Failed to load NPC config: {}", id, e);
            }
        }

        CobblemonMerchants.LOGGER.info("Loaded {} NPC configs", configs.size());
    }

    /**
     * Gets an NPC config by its resource location.
     */
    public static NPCConfig getConfig(ResourceLocation id) {
        return INSTANCE.configs.get(id);
    }

    /**
     * Checks if an NPC config exists.
     */
    public static boolean hasConfig(ResourceLocation id) {
        return INSTANCE.configs.containsKey(id);
    }

    /**
     * Gets all loaded NPC configs.
     */
    public static Map<ResourceLocation, NPCConfig> getAllConfigs() {
        return Map.copyOf(INSTANCE.configs);
    }
}
