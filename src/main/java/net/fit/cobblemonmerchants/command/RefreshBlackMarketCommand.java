package net.fit.cobblemonmerchants.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fit.cobblemonmerchants.merchant.blackmarket.BlackMarketInventory;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * Command to force refresh all Black Market inventories: /refreshblackmarket
 */
public class RefreshBlackMarketCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("refreshblackmarket")
                .requires(source -> source.hasPermission(2)) // OP level 2
                .executes(RefreshBlackMarketCommand::refreshBlackMarket)
        );
    }

    private static int refreshBlackMarket(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();

        // Force refresh all Black Market inventories
        BlackMarketInventory inventory = BlackMarketInventory.get(level);
        inventory.forceRefreshAll();

        source.sendSuccess(() -> Component.literal("§aForced refresh of all Black Market inventories! Players will get new offers on next access."), true);

        return 1;
    }
}
