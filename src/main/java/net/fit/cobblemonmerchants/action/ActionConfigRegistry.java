package net.fit.cobblemonmerchants.action;

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
 * Registry for action configurations loaded from datapacks.
 * Configs are stored in data/<namespace>/actions/<path>.json
 */
public class ActionConfigRegistry extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FOLDER = "actions";

    private static final ActionConfigRegistry INSTANCE = new ActionConfigRegistry();
    private final Map<ResourceLocation, ActionConfig> configs = new HashMap<>();

    private ActionConfigRegistry() {
        super(GSON, FOLDER);
    }

    /**
     * Gets the singleton instance
     */
    public static ActionConfigRegistry getInstance() {
        return INSTANCE;
    }

    @Override
    protected void apply(@NotNull Map<ResourceLocation, JsonElement> objects, @NotNull ResourceManager resourceManager,
                        @NotNull ProfilerFiller profiler) {
        configs.clear();

        CobblemonMerchants.LOGGER.info("Loading action configs, found {} JSON files", objects.size());

        for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
            ResourceLocation id = entry.getKey();
            JsonElement json = entry.getValue();

            try {
                var result = ActionConfig.CODEC.parse(JsonOps.INSTANCE, json);
                if (result.error().isPresent()) {
                    CobblemonMerchants.LOGGER.error("Failed to parse action config {}: {}", id, result.error().get().message());
                    continue;
                }

                ActionConfig config = result.result().get();
                configs.put(id, config);
                CobblemonMerchants.LOGGER.info("Loaded action config: {} ({} dialogue lines)",
                    id, config.dialogueLines().size());
            } catch (Exception e) {
                CobblemonMerchants.LOGGER.error("Failed to load action config: {}", id, e);
            }
        }

        CobblemonMerchants.LOGGER.info("Loaded {} action configs", configs.size());
    }

    /**
     * Gets an action config by its resource location
     */
    public static ActionConfig getConfig(ResourceLocation id) {
        if (INSTANCE == null) {
            return null;
        }
        return INSTANCE.configs.get(id);
    }

    /**
     * Checks if an action config exists
     */
    public static boolean hasConfig(ResourceLocation id) {
        if (INSTANCE == null) {
            return false;
        }
        return INSTANCE.configs.containsKey(id);
    }

    /**
     * Gets all loaded action configs
     */
    public static Map<ResourceLocation, ActionConfig> getAllConfigs() {
        if (INSTANCE == null) {
            return Map.of();
        }
        return Map.copyOf(INSTANCE.configs);
    }
}
