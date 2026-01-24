package net.fit.cobblemonmerchants.action.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fit.cobblemonmerchants.action.condition.ConditionData;
import net.fit.cobblemonmerchants.action.condition.ConditionRegistry;
import net.fit.cobblemonmerchants.merchant.CustomMerchantEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Data record for action effect definitions loaded from JSON.
 * Supports various action types with flexible parameters.
 */
public record ActionEffectData(
    String type,
    Optional<String> text,
    Optional<String> hoverText,
    Optional<String> item,
    Optional<Integer> count,
    Optional<DestinationData> destination,
    Optional<String> effect,
    Optional<Integer> duration,
    Optional<Integer> amplifier,
    Optional<String> sound,
    Optional<Float> volume,
    Optional<Float> pitch,
    Optional<String> prefix,
    List<ConditionData> conditions
) {
    public static final Codec<ActionEffectData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("type").forGetter(ActionEffectData::type),
            Codec.STRING.optionalFieldOf("text").forGetter(ActionEffectData::text),
            Codec.STRING.optionalFieldOf("hover_text").forGetter(ActionEffectData::hoverText),
            Codec.STRING.optionalFieldOf("item").forGetter(ActionEffectData::item),
            Codec.INT.optionalFieldOf("count").forGetter(ActionEffectData::count),
            DestinationData.CODEC.optionalFieldOf("destination").forGetter(ActionEffectData::destination),
            Codec.STRING.optionalFieldOf("effect").forGetter(ActionEffectData::effect),
            Codec.INT.optionalFieldOf("duration").forGetter(ActionEffectData::duration),
            Codec.INT.optionalFieldOf("amplifier").forGetter(ActionEffectData::amplifier),
            Codec.STRING.optionalFieldOf("sound").forGetter(ActionEffectData::sound),
            Codec.FLOAT.optionalFieldOf("volume").forGetter(ActionEffectData::volume),
            Codec.FLOAT.optionalFieldOf("pitch").forGetter(ActionEffectData::pitch),
            Codec.STRING.optionalFieldOf("prefix").forGetter(ActionEffectData::prefix),
            ConditionData.CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter(ActionEffectData::conditions)
        ).apply(instance, ActionEffectData::new)
    );

    /**
     * Checks if all conditions for this action are met.
     */
    public boolean checkConditions(Player player, @Nullable CustomMerchantEntity merchant) {
        return conditions.stream().allMatch(c -> ConditionRegistry.evaluate(c, player, merchant));
    }

    /**
     * Helper to get text with default.
     */
    public String getText(String defaultValue) {
        return text.orElse(defaultValue);
    }

    /**
     * Helper to get count with default.
     */
    public int getCount(int defaultValue) {
        return count.orElse(defaultValue);
    }

    /**
     * Helper to get volume with default.
     */
    public float getVolume(float defaultValue) {
        return volume.orElse(defaultValue);
    }

    /**
     * Helper to get pitch with default.
     */
    public float getPitch(float defaultValue) {
        return pitch.orElse(defaultValue);
    }

    /**
     * Destination data for teleport actions.
     */
    public record DestinationData(
        int x,
        int y,
        int z,
        Optional<String> dimension
    ) {
        public static final Codec<DestinationData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.INT.fieldOf("x").forGetter(DestinationData::x),
                Codec.INT.fieldOf("y").forGetter(DestinationData::y),
                Codec.INT.fieldOf("z").forGetter(DestinationData::z),
                Codec.STRING.optionalFieldOf("dimension").forGetter(DestinationData::dimension)
            ).apply(instance, DestinationData::new)
        );

        public String getDimension() {
            return dimension.orElse("minecraft:overworld");
        }
    }
}
