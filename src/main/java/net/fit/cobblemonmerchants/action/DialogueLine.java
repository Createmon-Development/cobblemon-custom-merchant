package net.fit.cobblemonmerchants.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fit.cobblemonmerchants.action.condition.ConditionData;
import net.fit.cobblemonmerchants.action.condition.ConditionRegistry;
import net.fit.cobblemonmerchants.action.effect.ActionEffectData;
import net.fit.cobblemonmerchants.merchant.CustomMerchantEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Represents a single dialogue line in an action configuration.
 * Can be a regular dialogue, a branch point, or an action-only entry.
 */
public record DialogueLine(
    String id,
    String type,
    Optional<String> speaker,
    Optional<String> text,
    List<ConditionData> conditions,
    int priority,
    List<ActionEffectData> actions,
    Optional<String> next,
    boolean end,
    boolean repeatable,
    Optional<List<BranchData>> branches,
    Optional<String> defaultBranch
) {
    public static final Codec<DialogueLine> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("id").forGetter(DialogueLine::id),
            Codec.STRING.optionalFieldOf("type", "dialogue").forGetter(DialogueLine::type),
            Codec.STRING.optionalFieldOf("speaker").forGetter(DialogueLine::speaker),
            Codec.STRING.optionalFieldOf("text").forGetter(DialogueLine::text),
            ConditionData.CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter(DialogueLine::conditions),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(DialogueLine::priority),
            ActionEffectData.CODEC.listOf().optionalFieldOf("actions", List.of()).forGetter(DialogueLine::actions),
            Codec.STRING.optionalFieldOf("next").forGetter(DialogueLine::next),
            Codec.BOOL.optionalFieldOf("end", false).forGetter(DialogueLine::end),
            Codec.BOOL.optionalFieldOf("repeatable", false).forGetter(DialogueLine::repeatable),
            BranchData.CODEC.listOf().optionalFieldOf("branches").forGetter(DialogueLine::branches),
            Codec.STRING.optionalFieldOf("default").forGetter(DialogueLine::defaultBranch)
        ).apply(instance, DialogueLine::new)
    );

    /**
     * Checks if all conditions for this dialogue line are met.
     */
    public boolean checkConditions(Player player, @Nullable CustomMerchantEntity merchant) {
        return conditions.stream().allMatch(c -> ConditionRegistry.evaluate(c, player, merchant));
    }

    /**
     * Gets the speaker name, defaulting to "NPC" if not specified.
     */
    public String getSpeaker() {
        return speaker.orElse("NPC");
    }

    /**
     * Gets the dialogue text, defaulting to empty string if not specified.
     */
    public String getText() {
        return text.orElse("");
    }

    /**
     * Branch data for conditional branching.
     */
    public record BranchData(
        List<ConditionData> conditions,
        String next
    ) {
        public static final Codec<BranchData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                ConditionData.CODEC.listOf().fieldOf("conditions").forGetter(BranchData::conditions),
                Codec.STRING.fieldOf("next").forGetter(BranchData::next)
            ).apply(instance, BranchData::new)
        );

        /**
         * Checks if all conditions for this branch are met.
         */
        public boolean checkConditions(Player player, @Nullable CustomMerchantEntity merchant) {
            return conditions.stream().allMatch(c -> ConditionRegistry.evaluate(c, player, merchant));
        }
    }
}
