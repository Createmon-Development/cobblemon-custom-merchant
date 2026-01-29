package net.fit.cobblemonmerchants.action.condition;

import net.fit.cobblemonmerchants.cobblemon.CobblemonPartyHelper;
import net.fit.cobblemonmerchants.merchant.CustomMerchantEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Condition that checks if the player has a Pokemon with a specific move in their party.
 * Uses reflection-based Cobblemon integration for cross-mod compatibility.
 *
 * If Cobblemon is not loaded, this condition will always return false (or true if inverted).
 */
public class HasPokemonMoveCondition implements Condition {

    @Override
    public boolean evaluate(ConditionData data, Player player, @Nullable CustomMerchantEntity merchant) {
        if (data.move().isEmpty()) {
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.warn(
                "[HasPokemonMoveCondition] No move specified in condition data");
            return data.invert();
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.warn(
                "[HasPokemonMoveCondition] Player is not ServerPlayer: {}", player.getClass().getName());
            return data.invert();
        }

        String moveName = data.move().get();
        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info(
            "[HasPokemonMoveCondition] Checking if player {} has Pokemon with move '{}'",
            player.getName().getString(), moveName);

        boolean hasMove = CobblemonPartyHelper.hasPartyPokemonWithMove(serverPlayer, moveName);

        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info(
            "[HasPokemonMoveCondition] Result for move '{}': hasMove={}, invert={}, finalResult={}",
            moveName, hasMove, data.invert(), data.invert() ? !hasMove : hasMove);

        return data.invert() ? !hasMove : hasMove;
    }
}
