package net.fit.cobblemonmerchants.action;

import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.fit.cobblemonmerchants.action.effect.ActionEffectData;
import net.fit.cobblemonmerchants.action.effect.ActionEffectRegistry;
import net.fit.cobblemonmerchants.merchant.CustomMerchantEntity;
import net.fit.cobblemonmerchants.npc.AssistantNPCEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
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
    // This is a fallback cache for when CrystalAscendancyManager is not available
    private static final Map<String, Long> SEEN_LINES = new ConcurrentHashMap<>();

    // How long to remember that a line was seen (30 minutes by default) - for fallback cache only
    private static final long SEEN_EXPIRY_MS = 30 * 60 * 1000;

    // Cached reflection methods for CrystalAscendancyManager
    private static boolean crystalManagerChecked = false;
    private static Method crystalManagerGetMethod = null;
    private static Method markDialogueSeenMethod = null;
    private static Method hasSeenDialogueMethod = null;
    private static Method clearSeenDialogueMethod = null;

    // Delay between dialogue lines in ticks (20 ticks = 1 second, 60 ticks = 3 seconds)
    private static final int DIALOGUE_DELAY_TICKS = 50; // 2.5 seconds

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
        executeDialogue(player, merchant, null, actionId, false, onComplete, null);
    }

    /**
     * Executes a dialogue sequence for a player with a merchant, requiring conditions.
     * Used when action_before_trade is true - only shows dialogue if quest conditions are met.
     *
     * @param player The player interacting
     * @param merchant The merchant entity
     * @param actionId The resource location of the action config to execute
     * @param requireConditions If true, only match lines with actual conditions (skip default greetings)
     * @param onComplete Callback to run when dialogue is complete (can be null)
     */
    public static void executeDialogueWithOptions(ServerPlayer player, @Nullable CustomMerchantEntity merchant,
                                                   ResourceLocation actionId, boolean requireConditions,
                                                   @Nullable Runnable onComplete) {
        executeDialogue(player, merchant, null, actionId, requireConditions, onComplete, null);
    }

    /**
     * Executes a dialogue sequence for a merchant with separate callbacks for dialogue found vs not found.
     * Used when action_before_trade is true - opens menu only if no quest dialogue conditions are met.
     *
     * @param player The player interacting
     * @param merchant The merchant entity
     * @param actionId The resource location of the action config to execute
     * @param onNoDialogue Callback when no dialogue conditions match (typically opens menu)
     */
    public static void executeDialogueOrFallback(ServerPlayer player, @Nullable CustomMerchantEntity merchant,
                                                  ResourceLocation actionId,
                                                  @Nullable Runnable onNoDialogue) {
        executeDialogue(player, merchant, null, actionId, true, null, onNoDialogue);
    }

    /**
     * Executes a dialogue sequence for a player with an NPC entity.
     *
     * @param player The player interacting
     * @param npc The NPC entity
     * @param actionId The resource location of the action config to execute
     * @param onComplete Callback to run when dialogue is complete (can be null)
     */
    public static void executeDialogueForNPC(ServerPlayer player, AssistantNPCEntity npc,
                                              ResourceLocation actionId, @Nullable Runnable onComplete) {
        String speakerName = npc.getNPCDisplayName();
        executeDialogue(player, null, speakerName, actionId, false, onComplete, null);
    }

    /**
     * Executes a dialogue sequence for a player with a merchant/NPC.
     *
     * @param player The player interacting
     * @param merchant The merchant entity (may be null for standalone NPCs)
     * @param defaultSpeaker The default speaker name to use when no speaker is specified (can be null)
     * @param actionId The resource location of the action config to execute
     * @param requireConditions If true, only match lines with actual conditions
     * @param onComplete Callback to run when dialogue is complete (can be null)
     * @param onNoDialogue Callback when no dialogue conditions match (can be null, falls back to onComplete)
     */
    public static void executeDialogue(ServerPlayer player, @Nullable CustomMerchantEntity merchant,
                                       @Nullable String defaultSpeaker,
                                       ResourceLocation actionId, boolean requireConditions,
                                       @Nullable Runnable onComplete,
                                       @Nullable Runnable onNoDialogue) {
        CobblemonMerchants.LOGGER.info("[ActionExecutor] Starting dialogue execution for action: {}, requireConditions: {}",
            actionId, requireConditions);

        // Load the action config
        ActionConfig config = ActionConfigRegistry.getConfig(actionId);
        if (config == null) {
            CobblemonMerchants.LOGGER.warn("[ActionExecutor] Action config not found: {}", actionId);
            // No config = no dialogue, use onNoDialogue callback
            if (onNoDialogue != null) {
                onNoDialogue.run();
            } else if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        CobblemonMerchants.LOGGER.info("[ActionExecutor] Config loaded with {} dialogue lines",
            config.dialogueLines().size());

        // Find the first applicable dialogue line (pass actionId for seen-line checking)
        Optional<DialogueLine> startLine = config.findFirstApplicable(player, merchant, requireConditions, actionId);
        if (startLine.isEmpty()) {
            CobblemonMerchants.LOGGER.info("[ActionExecutor] No applicable dialogue line found for {} - using fallback", actionId);
            // No matching dialogue, use onNoDialogue callback (typically opens menu)
            if (onNoDialogue != null) {
                onNoDialogue.run();
            } else if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        CobblemonMerchants.LOGGER.info("[ActionExecutor] Selected dialogue line: id='{}', priority={}",
            startLine.get().id(), startLine.get().priority());

        // Execute the dialogue sequence (first line has no delay)
        executeDialogueLine(player, merchant, defaultSpeaker, config, startLine.get(), actionId, onComplete, false);
    }

    /**
     * Executes a single dialogue line and handles progression.
     *
     * @param player The player to send dialogue to
     * @param merchant The merchant entity (may be null)
     * @param defaultSpeaker Default speaker name when no speaker is specified
     * @param config The action config
     * @param line The dialogue line to execute
     * @param actionId The action resource location
     * @param onComplete Callback when complete
     * @param withDelay Whether to delay this line's execution
     */
    private static void executeDialogueLine(ServerPlayer player, @Nullable CustomMerchantEntity merchant,
                                            @Nullable String defaultSpeaker,
                                            ActionConfig config, DialogueLine line,
                                            ResourceLocation actionId, @Nullable Runnable onComplete,
                                            boolean withDelay) {

        // Calculate hasText early so it can be used in the runnable for next line transitions
        // Only delay next line if current line had text (something to read)
        final boolean currentLineHasText = line.text().isPresent() && !line.getText().isEmpty();

        Runnable executeAction = () -> {
            // Mark non-repeatable lines as seen (uses persistent storage if available)
            if (!line.repeatable()) {
                markLineSeen(player, actionId, line.id());
            }

            // Execute dialogue text if present
            if (line.text().isPresent() && !line.getText().isEmpty()) {
                // Get speaker name from line, or default speaker, or merchant, or fallback
                String speaker = line.speaker().orElseGet(() -> {
                    if (defaultSpeaker != null && !defaultSpeaker.isEmpty()) {
                        return defaultSpeaker;
                    }
                    if (merchant != null) {
                        return merchant.getMerchantDisplayName();
                    }
                    return "???";
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
                            // Only delay if current line had text to read
                            executeDialogueLine(player, merchant, defaultSpeaker, config, nextLine.get(), actionId, onComplete, currentLineHasText);
                        }
                        return;
                    }
                }

                // No branch matched, try default
                if (line.defaultBranch().isPresent()) {
                    Optional<DialogueLine> nextLine = config.getLineById(line.defaultBranch().get());
                    if (nextLine.isPresent()) {
                        executeDialogueLine(player, merchant, defaultSpeaker, config, nextLine.get(), actionId, onComplete, currentLineHasText);
                        return;
                    }
                }
            }

            // Handle simple "next" progression
            if (line.next().isPresent()) {
                Optional<DialogueLine> nextLine = config.getLineById(line.next().get());
                if (nextLine.isPresent()) {
                    executeDialogueLine(player, merchant, defaultSpeaker, config, nextLine.get(), actionId, onComplete, currentLineHasText);
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
        };

        // Execute with or without delay
        // Skip delay for lines with no text (silent branch nodes)
        if (withDelay && currentLineHasText) {
            // Schedule the execution after a delay
            player.server.execute(() -> {
                // Use a delayed task via the server's scheduler
                scheduleDelayedTask(player, DIALOGUE_DELAY_TICKS, executeAction);
            });
        } else {
            executeAction.run();
        }
    }

    /**
     * Schedules a task to run after a delay.
     */
    private static void scheduleDelayedTask(ServerPlayer player, int delayTicks, Runnable task) {
        // Store the current tick count when scheduling
        final long startTick = player.server.getTickCount();
        final long targetTick = startTick + delayTicks;

        // Create a runnable that checks if enough time has passed
        Runnable checkAndRun = new Runnable() {
            @Override
            public void run() {
                if (player.server.getTickCount() >= targetTick) {
                    // Time has passed, execute the task
                    if (player.isAlive() && player.connection != null) {
                        task.run();
                    }
                } else {
                    // Not enough time has passed, reschedule
                    player.server.execute(this);
                }
            }
        };

        player.server.execute(checkAndRun);
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
     * Initializes the CrystalAscendancyManager reflection methods if available.
     * This allows dialogue tracking to persist across server restarts.
     */
    private static void initCrystalManager() {
        if (crystalManagerChecked) {
            return;
        }
        crystalManagerChecked = true;

        try {
            Class<?> managerClass = Class.forName("com.skys.cobblemoncosmetics.hunt.CrystalAscendancyManager");

            // Get the static get(MinecraftServer) method
            crystalManagerGetMethod = managerClass.getMethod("get", MinecraftServer.class);

            // Get the instance methods
            markDialogueSeenMethod = managerClass.getMethod("markDialogueSeen", UUID.class, String.class, String.class);
            hasSeenDialogueMethod = managerClass.getMethod("hasSeenDialogue", UUID.class, String.class, String.class);
            clearSeenDialogueMethod = managerClass.getMethod("clearSeenDialogue", UUID.class);

            CobblemonMerchants.LOGGER.info("[ActionExecutor] CrystalAscendancyManager integration initialized successfully");
        } catch (ClassNotFoundException e) {
            CobblemonMerchants.LOGGER.info("[ActionExecutor] CrystalAscendancyManager not found - using fallback dialogue tracking");
        } catch (NoSuchMethodException e) {
            CobblemonMerchants.LOGGER.warn("[ActionExecutor] CrystalAscendancyManager methods not found - using fallback dialogue tracking: {}", e.getMessage());
        }
    }

    /**
     * Gets the CrystalAscendancyManager instance for the given server.
     */
    private static Object getCrystalManager(MinecraftServer server) {
        initCrystalManager();
        if (crystalManagerGetMethod == null) {
            return null;
        }
        try {
            return crystalManagerGetMethod.invoke(null, server);
        } catch (Exception e) {
            CobblemonMerchants.LOGGER.debug("[ActionExecutor] Failed to get CrystalAscendancyManager: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Marks a dialogue line as seen by a player.
     * Uses CrystalAscendancyManager for persistent storage if available, otherwise falls back to in-memory cache.
     */
    private static void markLineSeen(ServerPlayer player, ResourceLocation actionId, String lineId) {
        UUID playerId = player.getUUID();
        String actionIdStr = actionId.toString();

        // Try to use CrystalAscendancyManager for persistent storage
        Object manager = getCrystalManager(player.server);
        if (manager != null && markDialogueSeenMethod != null) {
            try {
                markDialogueSeenMethod.invoke(manager, playerId, actionIdStr, lineId);
                CobblemonMerchants.LOGGER.debug("[ActionExecutor] Marked line seen via CrystalAscendancyManager: {}:{}", actionIdStr, lineId);
                return;
            } catch (Exception e) {
                CobblemonMerchants.LOGGER.debug("[ActionExecutor] Failed to mark via CrystalAscendancyManager, using fallback: {}", e.getMessage());
            }
        }

        // Fallback to in-memory cache
        String key = playerId + ":" + actionIdStr + ":" + lineId;
        SEEN_LINES.put(key, System.currentTimeMillis());
    }

    /**
     * Legacy method for compatibility - wraps to use ServerPlayer.
     */
    private static void markLineSeen(UUID playerId, ResourceLocation actionId, String lineId) {
        // This is called from executeDialogueLine which has access to the player
        // The SEEN_LINES map is used as fallback when player context is not available
        String key = playerId + ":" + actionId + ":" + lineId;
        SEEN_LINES.put(key, System.currentTimeMillis());
    }

    /**
     * Checks if a player has seen a dialogue line.
     * Uses CrystalAscendancyManager for persistent storage if available, otherwise falls back to in-memory cache.
     */
    public static boolean hasSeenLine(UUID playerId, ResourceLocation actionId, String lineId, @Nullable MinecraftServer server) {
        String actionIdStr = actionId.toString();

        // Try to use CrystalAscendancyManager for persistent storage
        if (server != null) {
            Object manager = getCrystalManager(server);
            if (manager != null && hasSeenDialogueMethod != null) {
                try {
                    Boolean result = (Boolean) hasSeenDialogueMethod.invoke(manager, playerId, actionIdStr, lineId);
                    CobblemonMerchants.LOGGER.debug("[ActionExecutor] Checked seen via CrystalAscendancyManager: {}:{} = {}", actionIdStr, lineId, result);
                    return result;
                } catch (Exception e) {
                    CobblemonMerchants.LOGGER.debug("[ActionExecutor] Failed to check via CrystalAscendancyManager, using fallback: {}", e.getMessage());
                }
            }
        }

        // Fallback to in-memory cache
        String key = playerId + ":" + actionIdStr + ":" + lineId;
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
     * Legacy method for compatibility - without server context.
     */
    public static boolean hasSeenLine(UUID playerId, ResourceLocation actionId, String lineId) {
        return hasSeenLine(playerId, actionId, lineId, null);
    }

    /**
     * Public method to mark a line as seen. Used by external code like MerchantTradeMenu.
     * Uses CrystalAscendancyManager for persistent storage if available, otherwise falls back to in-memory cache.
     *
     * @param playerId The player's UUID
     * @param actionId The action resource location string (e.g., "trade_action:mysterious_orb_trade")
     * @param lineId The line ID to mark as seen
     * @param server The server instance for persistent storage
     */
    public static void markLineSeenPublic(UUID playerId, String actionId, String lineId, @Nullable MinecraftServer server) {
        // Try to use CrystalAscendancyManager for persistent storage
        if (server != null) {
            Object manager = getCrystalManager(server);
            if (manager != null && markDialogueSeenMethod != null) {
                try {
                    markDialogueSeenMethod.invoke(manager, playerId, actionId, lineId);
                    CobblemonMerchants.LOGGER.debug("[ActionExecutor] Marked line seen via CrystalAscendancyManager: {}:{}", actionId, lineId);
                    return;
                } catch (Exception e) {
                    CobblemonMerchants.LOGGER.debug("[ActionExecutor] Failed to mark via CrystalAscendancyManager, using fallback: {}", e.getMessage());
                }
            }
        }

        // Fallback to in-memory cache
        String key = playerId + ":" + actionId + ":" + lineId;
        SEEN_LINES.put(key, System.currentTimeMillis());
        CobblemonMerchants.LOGGER.debug("[ActionExecutor] Marked line seen in fallback cache: {}", key);
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
     * Uses CrystalAscendancyManager if available.
     */
    public static void resetSeenLines(UUID playerId, @Nullable MinecraftServer server) {
        // Try to use CrystalAscendancyManager
        if (server != null) {
            Object manager = getCrystalManager(server);
            if (manager != null && clearSeenDialogueMethod != null) {
                try {
                    clearSeenDialogueMethod.invoke(manager, playerId);
                    CobblemonMerchants.LOGGER.info("[ActionExecutor] Reset seen lines via CrystalAscendancyManager for player {}", playerId);
                } catch (Exception e) {
                    CobblemonMerchants.LOGGER.warn("[ActionExecutor] Failed to reset via CrystalAscendancyManager: {}", e.getMessage());
                }
            }
        }

        // Also clear the fallback cache
        String prefix = playerId + ":";
        SEEN_LINES.keySet().removeIf(key -> key.startsWith(prefix));
    }

    /**
     * Legacy method for compatibility - without server context.
     */
    public static void resetSeenLines(UUID playerId) {
        resetSeenLines(playerId, null);
    }
}
