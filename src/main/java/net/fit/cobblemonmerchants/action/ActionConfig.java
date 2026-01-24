package net.fit.cobblemonmerchants.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fit.cobblemonmerchants.merchant.CustomMerchantEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Configuration for an action/dialogue sequence loaded from datapacks.
 * Contains dialogue lines with conditions, branching, and action effects.
 */
public record ActionConfig(
    Optional<String> description,
    List<DialogueLine> dialogueLines
) {
    public static final Codec<ActionConfig> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.optionalFieldOf("description").forGetter(ActionConfig::description),
            DialogueLine.CODEC.listOf().fieldOf("dialogue_lines").forGetter(ActionConfig::dialogueLines)
        ).apply(instance, ActionConfig::new)
    );

    /**
     * Finds the first applicable dialogue line based on conditions.
     * Higher priority lines are checked first.
     * Non-repeatable lines are preferred over repeatable ones.
     *
     * @param player The player interacting
     * @param merchant The merchant entity (may be null)
     * @return The first applicable dialogue line, or empty if none match
     */
    public Optional<DialogueLine> findFirstApplicable(Player player, @Nullable CustomMerchantEntity merchant) {
        // First, try to find a non-repeatable line that matches
        Optional<DialogueLine> nonRepeatable = dialogueLines.stream()
            .filter(line -> !line.repeatable())
            .filter(line -> line.checkConditions(player, merchant))
            .max(Comparator.comparingInt(DialogueLine::priority));

        if (nonRepeatable.isPresent()) {
            return nonRepeatable;
        }

        // Fall back to repeatable lines
        return dialogueLines.stream()
            .filter(DialogueLine::repeatable)
            .filter(line -> line.checkConditions(player, merchant))
            .max(Comparator.comparingInt(DialogueLine::priority));
    }

    /**
     * Gets a dialogue line by its ID.
     */
    public Optional<DialogueLine> getLineById(String id) {
        return dialogueLines.stream()
            .filter(line -> line.id().equals(id))
            .findFirst();
    }

    /**
     * Creates a map of dialogue lines by ID for quick lookup.
     */
    public Map<String, DialogueLine> getLineMap() {
        return dialogueLines.stream()
            .collect(Collectors.toMap(DialogueLine::id, line -> line, (a, b) -> a));
    }
}
