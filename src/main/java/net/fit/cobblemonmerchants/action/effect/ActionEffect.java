package net.fit.cobblemonmerchants.action.effect;

import net.minecraft.server.level.ServerPlayer;
import net.fit.cobblemonmerchants.merchant.CustomMerchantEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for action effects that execute when dialogue lines are processed.
 */
public interface ActionEffect {
    /**
     * Executes this action effect.
     *
     * @param data The action effect data from JSON
     * @param player The player receiving the action
     * @param merchant The merchant entity (may be null for standalone NPCs)
     */
    void execute(ActionEffectData data, ServerPlayer player, @Nullable CustomMerchantEntity merchant);
}
