package net.fit.cobblemonmerchants.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fit.cobblemonmerchants.merchant.blackmarket.BlackMarketInventory;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * Command to force refresh Black Market inventories: /refreshblackmarket [players]
 * Without arguments: refreshes only the command sender
 * With player selector: refreshes specific players (@a for all, @p, @r, player name, etc.)
 */
public class RefreshBlackMarketCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("refreshblackmarket")
                .requires(source -> source.hasPermission(2)) // OP level 2
                // Without arguments - refresh all players
                .executes(RefreshBlackMarketCommand::refreshAllPlayers)
                // With player selector argument - refresh specific players
                .then(Commands.argument("players", EntityArgument.players())
                    .executes(RefreshBlackMarketCommand::refreshSelectedPlayers)
                )
        );
    }

    /**
     * Refreshes Black Market inventory for the command sender only
     */
    private static int refreshAllPlayers(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();

        // Get the player who sent the command
        try {
            ServerPlayer player = source.getPlayerOrException();

            // Refresh inventory for just this player
            BlackMarketInventory inventory = BlackMarketInventory.get(level);
            inventory.forceRefreshPlayer(player.getUUID());

            source.sendSuccess(() -> Component.literal("§aForced refresh of your Black Market inventory! You will get new offers on next access."), true);

            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cThis command must be run by a player!"));
            return 0;
        }
    }

    /**
     * Refreshes Black Market inventory for specific players
     */
    private static int refreshSelectedPlayers(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();

        try {
            // Get the selected players from the selector
            Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");

            if (players.isEmpty()) {
                source.sendFailure(Component.literal("§cNo players found matching the selector."));
                return 0;
            }

            // Refresh inventory for each selected player
            BlackMarketInventory inventory = BlackMarketInventory.get(level);
            for (ServerPlayer player : players) {
                inventory.forceRefreshPlayer(player.getUUID());
            }

            int playerCount = players.size();
            source.sendSuccess(() -> Component.literal(
                String.format("§aForced refresh of Black Market inventory for %d player%s!",
                    playerCount, playerCount == 1 ? "" : "s")
            ), true);

            return playerCount;

        } catch (Exception e) {
            source.sendFailure(Component.literal("§cError refreshing Black Market: " + e.getMessage()));
            return 0;
        }
    }
}
