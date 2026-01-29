package net.fit.cobblemonmerchants.action.condition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * Data record for condition definitions loaded from JSON.
 * Supports various condition types with flexible parameters.
 */
public record ConditionData(
    String type,
    Optional<String> item,
    Optional<String> tag,
    Optional<String> component,
    Optional<Integer> intValue,
    Optional<Boolean> boolValue,
    Optional<String> stringValue,
    Optional<String> move,
    Optional<String> variant,
    int count,
    boolean invert
) {
    public static final Codec<ConditionData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("type").forGetter(ConditionData::type),
            Codec.STRING.optionalFieldOf("item").forGetter(ConditionData::item),
            Codec.STRING.optionalFieldOf("tag").forGetter(ConditionData::tag),
            Codec.STRING.optionalFieldOf("component").forGetter(ConditionData::component),
            Codec.INT.optionalFieldOf("value").forGetter(ConditionData::intValue),
            Codec.BOOL.optionalFieldOf("bool_value").forGetter(ConditionData::boolValue),
            Codec.STRING.optionalFieldOf("string_value").forGetter(ConditionData::stringValue),
            Codec.STRING.optionalFieldOf("move").forGetter(ConditionData::move),
            Codec.STRING.optionalFieldOf("variant").forGetter(ConditionData::variant),
            Codec.INT.optionalFieldOf("count", 1).forGetter(ConditionData::count),
            Codec.BOOL.optionalFieldOf("invert", false).forGetter(ConditionData::invert)
        ).apply(instance, ConditionData::new)
    );

    /**
     * Gets the value as an object, checking int, bool, and string in order.
     */
    public Object getValue() {
        if (intValue.isPresent()) return intValue.get();
        if (boolValue.isPresent()) return boolValue.get();
        if (stringValue.isPresent()) return stringValue.get();
        return null;
    }
}
