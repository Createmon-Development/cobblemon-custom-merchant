package net.fit.cobblemonmerchants.action.condition;

import net.minecraft.world.entity.player.Player;
import net.fit.cobblemonmerchants.merchant.CustomMerchantEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for dialogue conditions that determine if a dialogue line or action should execute.
 */
public interface Condition {
    /**
     * Evaluates whether this condition is met.
     *
     * @param data The condition data from JSON
     * @param player The player interacting with the merchant/NPC
     * @param merchant The merchant entity (may be null for standalone NPCs)
     * @return true if the condition is satisfied
     */
    boolean evaluate(ConditionData data, Player player, @Nullable CustomMerchantEntity merchant);
}
