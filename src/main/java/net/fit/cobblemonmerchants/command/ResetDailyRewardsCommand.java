package net.fit.cobblemonmerchants.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fit.cobblemonmerchants.merchant.rewards.DailyRewardManager;
import net.fit.cobblemonmerchants.merchant.rewards.DailyTradeResetManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * Command to reset daily reward claims and trade limits: /resetdailyrewards [players] [merchant]
 * Without arguments: resets all daily rewards and trade limits for the command sender
 * With player selector: resets for specific players
 * With merchant argument: only resets claims for that specific merchant
 */
public class ResetDailyRewardsCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("resetdailyrewards")
                .requires(source -> source.hasPermission(2)) // OP level 2
                // Without arguments - reset for command sender, all merchants
                .executes(ResetDailyRewardsCommand::resetSelf)
                // With player selector - reset for specific players, all merchants
                .then(Commands.argument("players", EntityArgument.players())
                    .executes(ResetDailyRewardsCommand::resetPlayers)
                    // With optional merchant argument - reset only specific merchant
                    .then(Commands.argument("merchant", StringArgumentType.string())
                        .executes(ResetDailyRewardsCommand::resetPlayersForMerchant)
                    )
                )
        );
    }

    /**
     * Resets all daily rewards and trade limits for the command sender
     */
    private static int resetSelf(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();

        try {
            ServerPlayer player = source.getPlayerOrException();

            // Reset daily rewards
            DailyRewardManager rewardManager = DailyRewardManager.get(level);
            rewardManager.resetClaims(player.getUUID(), null);

            // Reset daily trade limits
            DailyTradeResetManager tradeManager = DailyTradeResetManager.get(level);
            tradeManager.resetPlayerUsage(player.getUUID());

            source.sendSuccess(() -> Component.literal("§aReset all your daily reward claims and trade limits! You can now claim and trade again."), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cThis command must be run by a player!"));
            return 0;
        }
    }

    /**
     * Resets all daily rewards and trade limits for specific players
     */
    private static int resetPlayers(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();

        try {
            Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");

            if (players.isEmpty()) {
                source.sendFailure(Component.literal("§cNo players found matching the selector."));
                return 0;
            }

            DailyRewardManager rewardManager = DailyRewardManager.get(level);
            DailyTradeResetManager tradeManager = DailyTradeResetManager.get(level);
            for (ServerPlayer player : players) {
                rewardManager.resetClaims(player.getUUID(), null);
                tradeManager.resetPlayerUsage(player.getUUID());
            }

            int playerCount = players.size();
            source.sendSuccess(() -> Component.literal(
                String.format("§aReset all daily reward claims and trade limits for %d player%s!",
                    playerCount, playerCount == 1 ? "" : "s")
            ), true);

            return playerCount;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cError resetting daily rewards: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Resets daily rewards and trade limits for specific players from a specific merchant
     */
    private static int resetPlayersForMerchant(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();

        try {
            Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
            String merchantId = StringArgumentType.getString(context, "merchant");

            if (players.isEmpty()) {
                source.sendFailure(Component.literal("§cNo players found matching the selector."));
                return 0;
            }

            DailyRewardManager rewardManager = DailyRewardManager.get(level);
            DailyTradeResetManager tradeManager = DailyTradeResetManager.get(level);
            for (ServerPlayer player : players) {
                rewardManager.resetClaims(player.getUUID(), merchantId);
                tradeManager.resetPlayerUsage(player.getUUID());
            }

            int playerCount = players.size();
            source.sendSuccess(() -> Component.literal(
                String.format("§aReset daily reward claims and trade limits from '%s' for %d player%s!",
                    merchantId, playerCount, playerCount == 1 ? "" : "s")
            ), true);

            return playerCount;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cError resetting daily rewards: " + e.getMessage()));
            return 0;
        }
    }
}
