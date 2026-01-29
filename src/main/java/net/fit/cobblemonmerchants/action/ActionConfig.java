package net.fit.cobblemonmerchants.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.fit.cobblemonmerchants.merchant.CustomMerchantEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
     * Non-repeatable lines that have already been seen are skipped.
     * Only considers lines marked as entry points.
     *
     * @param player The player interacting
     * @param merchant The merchant entity (may be null)
     * @param requireConditions If true, only match lines that have at least one condition
     *                          (used for action_before_trade to skip default greetings)
     * @param actionId The action resource location (for checking seen status)
     * @return The first applicable dialogue line, or empty if none match
     */
    public Optional<DialogueLine> findFirstApplicable(Player player, @Nullable CustomMerchantEntity merchant,
                                                       boolean requireConditions, @Nullable ResourceLocation actionId) {
        String merchantVariant = merchant != null ? merchant.getMerchantVariant() : "none";
        UUID playerId = player.getUUID();
        // Get server for persistent dialogue tracking
        net.minecraft.server.MinecraftServer server = player instanceof net.minecraft.server.level.ServerPlayer sp ? sp.server : null;

        CobblemonMerchants.LOGGER.info("[ActionConfig] Finding applicable line for merchant variant: {}, requireConditions: {}, actionId: {}",
            merchantVariant, requireConditions, actionId);

        // Filter to only entry point lines
        List<DialogueLine> entryLines = dialogueLines.stream()
            .filter(DialogueLine::canBeEntryPoint)
            .filter(line -> !requireConditions || !line.conditions().isEmpty())
            .toList();

        CobblemonMerchants.LOGGER.info("[ActionConfig] {} entry point lines to check", entryLines.size());

        final net.minecraft.server.MinecraftServer finalServer = server;

        // First, try to find a non-repeatable line that matches AND hasn't been seen
        for (DialogueLine line : entryLines) {
            if (!line.repeatable()) {
                boolean passes = line.checkConditions(player, merchant);
                boolean seen = actionId != null && ActionExecutor.hasSeenLine(playerId, actionId, line.id(), finalServer);
                CobblemonMerchants.LOGGER.info("[ActionConfig] Line '{}' (priority={}, repeatable=false, return_line={}): conditions {}, seen={}",
                    line.id(), line.priority(), line.returnLine().orElse("none"), passes ? "PASSED" : "failed", seen);
            }
        }

        Optional<DialogueLine> nonRepeatable = entryLines.stream()
            .filter(line -> !line.repeatable())
            .filter(line -> actionId == null || !ActionExecutor.hasSeenLine(playerId, actionId, line.id(), finalServer)) // Skip seen lines
            .filter(line -> line.checkConditions(player, merchant))
            .max(Comparator.comparingInt(DialogueLine::priority));

        if (nonRepeatable.isPresent()) {
            CobblemonMerchants.LOGGER.info("[ActionConfig] Selected non-repeatable line: '{}'", nonRepeatable.get().id());
            return nonRepeatable;
        }

        // Check for seen non-repeatable lines that have a return_line - use return_line on subsequent visits
        CobblemonMerchants.LOGGER.info("[ActionConfig] Checking for return_line on seen non-repeatable lines...");

        Optional<DialogueLine> seenWithReturn = entryLines.stream()
            .filter(line -> !line.repeatable())
            .filter(line -> line.returnLine().isPresent()) // Has a return_line
            .filter(line -> actionId != null && ActionExecutor.hasSeenLine(playerId, actionId, line.id(), finalServer)) // Already seen
            .filter(line -> line.checkConditions(player, merchant)) // Still matches conditions
            .max(Comparator.comparingInt(DialogueLine::priority));

        if (seenWithReturn.isPresent()) {
            String returnLineId = seenWithReturn.get().returnLine().get();
            Optional<DialogueLine> returnLine = getLineById(returnLineId);
            if (returnLine.isPresent()) {
                CobblemonMerchants.LOGGER.info("[ActionConfig] Using return_line '{}' for seen line '{}'",
                    returnLineId, seenWithReturn.get().id());
                return returnLine;
            } else {
                CobblemonMerchants.LOGGER.warn("[ActionConfig] return_line '{}' not found for line '{}'",
                    returnLineId, seenWithReturn.get().id());
            }
        }

        // Fall back to repeatable lines (these can always be shown again)
        CobblemonMerchants.LOGGER.info("[ActionConfig] No non-repeatable match, checking repeatable lines...");

        for (DialogueLine line : entryLines) {
            if (line.repeatable()) {
                boolean passes = line.checkConditions(player, merchant);
                CobblemonMerchants.LOGGER.info("[ActionConfig] Line '{}' (priority={}, repeatable=true): conditions {}",
                    line.id(), line.priority(), passes ? "PASSED" : "failed");
            }
        }

        Optional<DialogueLine> repeatable = entryLines.stream()
            .filter(DialogueLine::repeatable)
            .filter(line -> line.checkConditions(player, merchant))
            .max(Comparator.comparingInt(DialogueLine::priority));

        if (repeatable.isPresent()) {
            CobblemonMerchants.LOGGER.info("[ActionConfig] Selected repeatable line: '{}'", repeatable.get().id());
        } else {
            CobblemonMerchants.LOGGER.info("[ActionConfig] No matching lines found");
        }

        return repeatable;
    }

    /**
     * Finds the first applicable dialogue line (without seen-line checking).
     * @see #findFirstApplicable(Player, CustomMerchantEntity, boolean, ResourceLocation)
     */
    public Optional<DialogueLine> findFirstApplicable(Player player, @Nullable CustomMerchantEntity merchant,
                                                       boolean requireConditions) {
        return findFirstApplicable(player, merchant, requireConditions, null);
    }

    /**
     * Finds the first applicable dialogue line (default behavior).
     * @see #findFirstApplicable(Player, CustomMerchantEntity, boolean, ResourceLocation)
     */
    public Optional<DialogueLine> findFirstApplicable(Player player, @Nullable CustomMerchantEntity merchant) {
        return findFirstApplicable(player, merchant, false, null);
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
