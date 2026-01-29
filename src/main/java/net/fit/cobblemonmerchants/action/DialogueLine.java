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
    Optional<String> defaultBranch,
    boolean entryPoint,
    Optional<String> returnLine,
    Optional<String> comment
) {
    // Constructor that ignores comment field (for backwards compatibility)
    public DialogueLine(String id, String type, Optional<String> speaker, Optional<String> text,
                        List<ConditionData> conditions, int priority, List<ActionEffectData> actions,
                        Optional<String> next, boolean end, boolean repeatable,
                        Optional<List<BranchData>> branches, Optional<String> defaultBranch,
                        boolean entryPoint, Optional<String> returnLine) {
        this(id, type, speaker, text, conditions, priority, actions, next, end, repeatable,
             branches, defaultBranch, entryPoint, returnLine, Optional.empty());
    }

    public static final Codec<DialogueLine> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.optionalFieldOf("id", "__comment__").forGetter(DialogueLine::id),
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
            Codec.STRING.optionalFieldOf("default").forGetter(DialogueLine::defaultBranch),
            Codec.BOOL.optionalFieldOf("entry_point", true).forGetter(DialogueLine::entryPoint),
            Codec.STRING.optionalFieldOf("return_line").forGetter(DialogueLine::returnLine),
            Codec.STRING.optionalFieldOf("_comment").forGetter(DialogueLine::comment)
        ).apply(instance, DialogueLine::new)
    );

    /**
     * Returns true if this is a comment-only entry (not a real dialogue line).
     */
    public boolean isComment() {
        return "__comment__".equals(id);
    }

    /**
     * Checks if this line can be selected as an initial entry point.
     * Lines that are only reached via "next" should have entry_point: false.
     * Comment-only entries are never entry points.
     */
    public boolean canBeEntryPoint() {
        return entryPoint && !isComment();
    }

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
