package net.fit.cobblemonmerchants.npc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Configuration for NPC entities loaded from datapacks.
 * NPCs are dialogue-focused entities that don't have trade menus.
 *
 * Configs are stored in data/<namespace>/npcs/<path>.json
 */
public record NPCConfig(
    String displayName,
    Optional<String> villagerBiome,
    Optional<String> villagerProfession,
    Optional<String> actionId,
    boolean tradeMenuEnabled,
    Map<String, VariantData> variants,
    Optional<String> defaultVariant
) {
    public static final Codec<NPCConfig> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("display_name").forGetter(NPCConfig::displayName),
            Codec.STRING.optionalFieldOf("villager_biome").forGetter(NPCConfig::villagerBiome),
            Codec.STRING.optionalFieldOf("villager_profession").forGetter(NPCConfig::villagerProfession),
            Codec.STRING.optionalFieldOf("action_id").forGetter(NPCConfig::actionId),
            Codec.BOOL.optionalFieldOf("trade_menu_enabled", false).forGetter(NPCConfig::tradeMenuEnabled),
            VariantData.CODEC.optionalFieldOf("variants", Map.of()).forGetter(NPCConfig::variants),
            Codec.STRING.optionalFieldOf("default_variant").forGetter(NPCConfig::defaultVariant)
        ).apply(instance, NPCConfig::new)
    );

    /**
     * Gets the villager biome or returns desert as default.
     */
    public String getBiome() {
        return villagerBiome.orElse("minecraft:desert");
    }

    /**
     * Gets the villager profession or returns none as default.
     */
    public String getProfession() {
        return villagerProfession.orElse("minecraft:none");
    }

    /**
     * Gets the default variant for this NPC, or "default" if not specified.
     */
    public String getDefaultVariant() {
        return defaultVariant.orElse("default");
    }

    /**
     * Gets all available variant names for this NPC.
     * Always includes the default variant.
     */
    public Set<String> getAvailableVariants() {
        Set<String> available = new HashSet<>(variants.keySet());
        available.add(getDefaultVariant());
        return available;
    }

    /**
     * Gets the action ID for a specific variant, falling back to the default.
     */
    public Optional<String> getActionIdForVariant(String variant) {
        if (variant != null && variants.containsKey(variant)) {
            Optional<String> variantAction = variants.get(variant).actionId();
            if (variantAction.isPresent()) {
                return variantAction;
            }
        }
        return actionId;
    }

    /**
     * Variant-specific configuration data.
     */
    public record VariantData(
        Optional<String> actionId,
        Optional<String> displayName
    ) {
        public static final Codec<Map<String, VariantData>> CODEC =
            Codec.unboundedMap(Codec.STRING, RecordCodecBuilder.<VariantData>create(instance ->
                instance.group(
                    Codec.STRING.optionalFieldOf("action_id").forGetter(VariantData::actionId),
                    Codec.STRING.optionalFieldOf("display_name").forGetter(VariantData::displayName)
                ).apply(instance, VariantData::new)
            ));
    }
}
