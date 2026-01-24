package net.fit.cobblemonmerchants.action.effect;

import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.fit.cobblemonmerchants.merchant.CustomMerchantEntity;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for action effect implementations.
 * Maps action type strings to their implementations.
 */
public class ActionEffectRegistry {
    private static final Map<String, ActionEffect> EFFECTS = new HashMap<>();
    private static boolean initialized = false;

    /**
     * Registers an action effect implementation.
     */
    public static void register(String type, ActionEffect effect) {
        EFFECTS.put(type, effect);
    }

    /**
     * Executes an action effect using the registered implementation.
     */
    public static void execute(ActionEffectData data, ServerPlayer player, @Nullable CustomMerchantEntity merchant) {
        ensureInitialized();

        ActionEffect effect = EFFECTS.get(data.type());
        if (effect == null) {
            CobblemonMerchants.LOGGER.warn("Unknown action effect type: {}", data.type());
            return;
        }

        // Check action-specific conditions
        if (!data.checkConditions(player, merchant)) {
            return;
        }

        effect.execute(data, player, merchant);
    }

    /**
     * Initializes all built-in action effects.
     */
    public static void ensureInitialized() {
        if (initialized) return;
        initialized = true;

        register("dialogue", new DialogueEffect());
        register("atmospheric", new AtmosphericEffect());
        register("give_item", new GiveItemEffect());
        register("teleport_option", new TeleportOptionEffect());
        register("apply_effect", new ApplyPotionEffect());
        register("play_sound", new PlaySoundEffect());
        register("broadcast", new BroadcastEffect());

        CobblemonMerchants.LOGGER.info("Registered {} action effect types", EFFECTS.size());
    }
}
