package net.fit.cobblemonmerchants.action;

import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.fit.cobblemonmerchants.action.effect.ActionEffectData;
import net.fit.cobblemonmerchants.action.effect.ActionEffectRegistry;
import net.fit.cobblemonmerchants.merchant.CustomMerchantEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Executes action/dialogue sequences for merchants and NPCs.
 * Handles dialogue flow, branching, variable substitution, and action effects.
 */
public class ActionExecutor {

    // Track which dialogue lines players have already seen (for non-repeatable lines)
    // Key: UUID + ":" + actionId + ":" + lineId
    private static final Map<String, Long> SEEN_LINES = new ConcurrentHashMap<>();

    // How long to remember that a line was seen (30 minutes by default)
    private static final long SEEN_EXPIRY_MS = 30 * 60 * 1000;

    /**
     * Executes a dialogue sequence for a player with a merchant/NPC.
     *
     * @param player The player interacting
     * @param merchant The merchant entity (may be null for standalone NPCs)
     * @param actionId The resource location of the action config to execute
     * @param onComplete Callback to run when dialogue is complete (can be null)
     */
    public static void executeDialogue(ServerPlayer player, @Nullable CustomMerchantEntity merchant,
                                       ResourceLocation actionId, @Nullable Runnable onComplete) {
        // Load the action config
        ActionConfig config = ActionConfigRegistry.getConfig(actionId);
        if (config == null) {
            CobblemonMerchants.LOGGER.warn("Action config not found: {}", actionId);
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        // Find the first applicable dialogue line
        Optional<DialogueLine> startLine = config.findFirstApplicable(player, merchant);
        if (startLine.isEmpty()) {
            CobblemonMerchants.LOGGER.debug("No applicable dialogue line found for {}", actionId);
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        // Execute the dialogue sequence
        executeDialogueLine(player, merchant, config, startLine.get(), actionId, onComplete);
    }

    /**
     * Executes a single dialogue line and handles progression.
     */
    private static void executeDialogueLine(ServerPlayer player, @Nullable CustomMerchantEntity merchant,
                                            ActionConfig config, DialogueLine line,
                                            ResourceLocation actionId, @Nullable Runnable onComplete) {

        // Mark non-repeatable lines as seen
        if (!line.repeatable()) {
            markLineSeen(player.getUUID(), actionId, line.id());
        }

        // Execute dialogue text if present
        if (line.text().isPresent() && !line.getText().isEmpty()) {
            // Get speaker name from line or merchant
            String speaker = line.speaker().orElseGet(() -> {
                if (merchant != null) {
                    return merchant.getMerchantDisplayName();
                }
                return "NPC";
            });

            // Create a dialogue effect for the text
            ActionEffectData dialogueData = new ActionEffectData(
                "dialogue",
                Optional.of(substituteVariables(line.getText(), player, merchant)),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(speaker),
                java.util.List.of()
            );
            ActionEffectRegistry.execute(dialogueData, player, merchant);

            // Play villager ambient sound
            playVillagerSound(player, merchant);
        }

        // Execute all actions for this line
        for (ActionEffectData action : line.actions()) {
            // Substitute variables in action text
            ActionEffectData processedAction = substituteActionVariables(action, player, merchant);
            ActionEffectRegistry.execute(processedAction, player, merchant);
        }

        // Handle progression
        if (line.end()) {
            // Dialogue sequence ends here
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        // Handle branching
        if (line.branches().isPresent() && !line.branches().get().isEmpty()) {
            // Find the first matching branch
            for (DialogueLine.BranchData branch : line.branches().get()) {
                if (branch.checkConditions(player, merchant)) {
                    Optional<DialogueLine> nextLine = config.getLineById(branch.next());
                    if (nextLine.isPresent()) {
                        // Small delay before next line (gives time to read)
                        executeDialogueLine(player, merchant, config, nextLine.get(), actionId, onComplete);
                    }
                    return;
                }
            }

            // No branch matched, try default
            if (line.defaultBranch().isPresent()) {
                Optional<DialogueLine> nextLine = config.getLineById(line.defaultBranch().get());
                if (nextLine.isPresent()) {
                    executeDialogueLine(player, merchant, config, nextLine.get(), actionId, onComplete);
                    return;
                }
            }
        }

        // Handle simple "next" progression
        if (line.next().isPresent()) {
            Optional<DialogueLine> nextLine = config.getLineById(line.next().get());
            if (nextLine.isPresent()) {
                executeDialogueLine(player, merchant, config, nextLine.get(), actionId, onComplete);
                return;
            } else {
                CobblemonMerchants.LOGGER.warn("Next dialogue line not found: {} in action {}",
                    line.next().get(), actionId);
            }
        }

        // End of sequence
        if (onComplete != null) {
            onComplete.run();
        }
    }

    /**
     * Plays a villager ambient sound at the merchant's location.
     */
    private static void playVillagerSound(ServerPlayer player, @Nullable CustomMerchantEntity merchant) {
        if (merchant != null) {
            // Random pitch variation for more natural speech
            float pitch = 0.9f + (float)(Math.random() * 0.2f);
            player.serverLevel().playSound(
                null,
                merchant.getX(), merchant.getY(), merchant.getZ(),
                SoundEvents.VILLAGER_AMBIENT,
                SoundSource.NEUTRAL,
                1.0f,
                pitch
            );
        }
    }

    /**
     * Substitutes variables in text with actual values.
     * Supported variables:
     * - <player_name> : Player's name
     * - <merchant_name> : Merchant's display name
     * - <pokemon_name> : Name of first Pokemon in party (Cobblemon)
     */
    public static String substituteVariables(String text, ServerPlayer player, @Nullable CustomMerchantEntity merchant) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String result = text;

        // Player name
        result = result.replace("<player_name>", player.getName().getString());

        // Merchant name
        if (merchant != null) {
            result = result.replace("<merchant_name>", merchant.getMerchantDisplayName());
        } else {
            result = result.replace("<merchant_name>", "NPC");
        }

        // Pokemon name (first in party) - requires Cobblemon
        if (result.contains("<pokemon_name>")) {
            String pokemonName = getFirstPokemonName(player);
            result = result.replace("<pokemon_name>", pokemonName);
        }

        return result;
    }

    /**
     * Substitutes variables in an ActionEffectData.
     */
    private static ActionEffectData substituteActionVariables(ActionEffectData data, ServerPlayer player,
                                                              @Nullable CustomMerchantEntity merchant) {
        Optional<String> newText = data.text().map(t -> substituteVariables(t, player, merchant));
        Optional<String> newHoverText = data.hoverText().map(t -> substituteVariables(t, player, merchant));

        // Only create a new instance if something changed
        if (newText.equals(data.text()) && newHoverText.equals(data.hoverText())) {
            return data;
        }

        return new ActionEffectData(
            data.type(),
            newText,
            newHoverText,
            data.item(),
            data.count(),
            data.destination(),
            data.effect(),
            data.duration(),
            data.amplifier(),
            data.sound(),
            data.volume(),
            data.pitch(),
            data.prefix(),
            data.conditions()
        );
    }

    /**
     * Gets the name of the player's first Pokemon using reflection.
     */
    private static String getFirstPokemonName(ServerPlayer player) {
        try {
            // Try to get from Cobblemon via reflection
            Class<?> cobblemonClass = Class.forName("com.cobblemon.mod.common.Cobblemon");
            Object storageInstance = cobblemonClass.getField("storage").get(null);
            java.lang.reflect.Method getPartyMethod = storageInstance.getClass()
                .getMethod("getParty", UUID.class);
            Object party = getPartyMethod.invoke(storageInstance, player.getUUID());

            if (party != null) {
                java.lang.reflect.Method iteratorMethod = party.getClass().getMethod("iterator");
                Iterable<?> slots = (Iterable<?>) iteratorMethod.invoke(party);

                for (Object pokemon : slots) {
                    if (pokemon != null) {
                        // Try to get nickname first, then species name
                        try {
                            java.lang.reflect.Method getNicknameMethod = pokemon.getClass()
                                .getMethod("getNickname");
                            Object nickname = getNicknameMethod.invoke(pokemon);
                            if (nickname != null) {
                                String nicknameStr = nickname.toString();
                                if (!nicknameStr.isEmpty() && !nicknameStr.equals("null")) {
                                    return nicknameStr;
                                }
                            }
                        } catch (Exception ignored) {}

                        // Fall back to species name
                        try {
                            java.lang.reflect.Method getSpeciesMethod = pokemon.getClass()
                                .getMethod("getSpecies");
                            Object species = getSpeciesMethod.invoke(pokemon);
                            if (species != null) {
                                java.lang.reflect.Method getNameMethod = species.getClass()
                                    .getMethod("getName");
                                Object name = getNameMethod.invoke(species);
                                if (name != null) {
                                    // Capitalize first letter
                                    String speciesName = name.toString();
                                    return speciesName.substring(0, 1).toUpperCase() +
                                           speciesName.substring(1).toLowerCase();
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            CobblemonMerchants.LOGGER.debug("Could not get Pokemon name: {}", e.getMessage());
        }

        return "your Pokemon";
    }

    /**
     * Marks a dialogue line as seen by a player.
     */
    private static void markLineSeen(UUID playerId, ResourceLocation actionId, String lineId) {
        String key = playerId + ":" + actionId + ":" + lineId;
        SEEN_LINES.put(key, System.currentTimeMillis());
    }

    /**
     * Checks if a player has seen a dialogue line recently.
     */
    public static boolean hasSeenLine(UUID playerId, ResourceLocation actionId, String lineId) {
        String key = playerId + ":" + actionId + ":" + lineId;
        Long seenTime = SEEN_LINES.get(key);
        if (seenTime == null) {
            return false;
        }
        // Check if it's expired
        if (System.currentTimeMillis() - seenTime > SEEN_EXPIRY_MS) {
            SEEN_LINES.remove(key);
            return false;
        }
        return true;
    }

    /**
     * Clears the seen lines cache.
     * Should be called periodically to prevent memory growth.
     */
    public static void cleanupSeenLines() {
        long now = System.currentTimeMillis();
        SEEN_LINES.entrySet().removeIf(entry ->
            now - entry.getValue() > SEEN_EXPIRY_MS
        );
    }

    /**
     * Resets all seen lines for a player (useful for testing/admin).
     */
    public static void resetSeenLines(UUID playerId) {
        String prefix = playerId + ":";
        SEEN_LINES.keySet().removeIf(key -> key.startsWith(prefix));
    }
}
