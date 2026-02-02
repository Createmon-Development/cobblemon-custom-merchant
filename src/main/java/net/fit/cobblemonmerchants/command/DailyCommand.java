package net.fit.cobblemonmerchants.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.fit.cobblemonmerchants.merchant.rewards.DailyRewardManager;
import net.fit.cobblemonmerchants.merchant.rotation.DailyRotatingTradeManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Unified command for managing daily systems:
 * - Daily rewards
 * - Daily rotating trades
 *
 * Commands:
 * /daily status - Show time until reset and server timezone
 * /daily reset rewards [player] - Reset daily rewards for a player (or all)
 * /daily reset trades [merchant] - Force refresh daily rotating trades (optionally for specific merchant)
 * /daily reset all [player] - Reset both rewards and trades
 * /daily refresh trades [merchant] - Reroll daily rotating trades (optionally for specific merchant)
 */
public class DailyCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("daily")
            .requires(source -> source.hasPermission(2))

            // /daily status - Show time until reset
            .then(Commands.literal("status")
                .executes(DailyCommand::showStatus))

            // /daily refresh - Refresh/reroll subcommands
            .then(Commands.literal("refresh")
                // /daily refresh trades - Reroll all rotating trades
                .then(Commands.literal("trades")
                    .executes(ctx -> refreshTrades(ctx, null))
                    // /daily refresh trades <merchant> - Reroll trades for specific merchant
                    .then(Commands.argument("merchant", StringArgumentType.string())
                        .suggests((ctx, builder) -> suggestMerchantIds(ctx, builder))
                        .executes(ctx -> refreshTrades(ctx, StringArgumentType.getString(ctx, "merchant"))))))

            // /daily reset - Reset subcommands
            .then(Commands.literal("reset")
                // /daily reset rewards - Reset all player rewards
                .then(Commands.literal("rewards")
                    .executes(ctx -> resetRewards(ctx, null))
                    // /daily reset rewards <player> - Reset specific player's rewards
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> resetRewards(ctx, EntityArgument.getPlayer(ctx, "player")))))

                // /daily reset trades - Force refresh rotating trades
                .then(Commands.literal("trades")
                    .executes(ctx -> refreshTrades(ctx, null))
                    // /daily reset trades <merchant> - Force refresh for specific merchant
                    .then(Commands.argument("merchant", StringArgumentType.string())
                        .suggests((ctx, builder) -> suggestMerchantIds(ctx, builder))
                        .executes(ctx -> refreshTrades(ctx, StringArgumentType.getString(ctx, "merchant")))))

                // /daily reset all - Reset everything
                .then(Commands.literal("all")
                    .executes(ctx -> resetAll(ctx, null))
                    // /daily reset all <player> - Reset everything for specific player
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> resetAll(ctx, EntityArgument.getPlayer(ctx, "player"))))))
        );
    }

    /**
     * Suggests merchant IDs that have daily rotating trades configured.
     */
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestMerchantIds(
            CommandContext<CommandSourceStack> ctx,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        // Get all merchant configs that have daily rotating trades
        var allConfigs = net.fit.cobblemonmerchants.merchant.config.MerchantConfigRegistry.getAllConfigs();
        for (var entry : allConfigs.entrySet()) {
            if (entry.getValue().dailyRotatingTrades().isPresent() &&
                !entry.getValue().dailyRotatingTrades().get().isEmpty()) {
                builder.suggest(entry.getKey().toString());
            }
        }
        return builder.buildFuture();
    }

    /**
     * Show status of daily systems including time until reset and server timezone.
     */
    private static int showStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        // Get server timezone info
        ZoneId serverZone = ZoneId.systemDefault();
        LocalDateTime now = LocalDateTime.now(serverZone);
        LocalDateTime midnight = LocalDateTime.of(LocalDate.now(serverZone).plusDays(1), LocalTime.MIDNIGHT);
        Duration timeUntilReset = Duration.between(now, midnight);

        long hours = timeUntilReset.toHours();
        long minutes = timeUntilReset.toMinutesPart();
        long seconds = timeUntilReset.toSecondsPart();

        // Build status message
        MutableComponent header = Component.literal("=== Daily Systems Status ===")
            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        source.sendSuccess(() -> header, false);

        // Server timezone
        String zoneDisplay = serverZone.getId();
        MutableComponent timezoneMsg = Component.literal("Server Timezone: ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal(zoneDisplay).withStyle(ChatFormatting.AQUA));
        source.sendSuccess(() -> timezoneMsg, false);

        // Current server time
        String currentTimeStr = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        MutableComponent currentTimeMsg = Component.literal("Current Time: ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal(currentTimeStr).withStyle(ChatFormatting.WHITE));
        source.sendSuccess(() -> currentTimeMsg, false);

        // Time until reset
        String timeStr = String.format("%dh %dm %ds", hours, minutes, seconds);
        MutableComponent resetMsg = Component.literal("Time Until Reset: ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal(timeStr).withStyle(ChatFormatting.GREEN));
        source.sendSuccess(() -> resetMsg, false);

        // Daily rotating trades info
        if (source.getLevel() instanceof ServerLevel level) {
            try {
                var tradeManager = DailyRotatingTradeManager.get(level);
                // We could show more info here if needed
                MutableComponent tradesMsg = Component.literal("Daily Rotating Trades: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("Active").withStyle(ChatFormatting.GREEN));
                source.sendSuccess(() -> tradesMsg, false);
            } catch (Exception e) {
                MutableComponent tradesMsg = Component.literal("Daily Rotating Trades: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("Error loading").withStyle(ChatFormatting.RED));
                source.sendSuccess(() -> tradesMsg, false);
            }
        }

        return 1;
    }

    /**
     * Reset daily rewards for a specific player or all players.
     */
    private static int resetRewards(CommandContext<CommandSourceStack> ctx, ServerPlayer targetPlayer) {
        CommandSourceStack source = ctx.getSource();

        if (!(source.getLevel() instanceof ServerLevel level)) {
            source.sendFailure(Component.literal("This command can only be run on the server"));
            return 0;
        }

        DailyRewardManager rewardManager = DailyRewardManager.get(level);

        if (targetPlayer != null) {
            // Reset for specific player
            rewardManager.resetClaims(targetPlayer.getUUID(), null);
            MutableComponent msg = Component.literal("Reset daily rewards for ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(targetPlayer.getName().getString()).withStyle(ChatFormatting.AQUA));
            source.sendSuccess(() -> msg, true);
            CobblemonMerchants.LOGGER.info("Reset daily rewards for player {} by {}",
                targetPlayer.getName().getString(), source.getTextName());
        } else {
            // Reset for all players
            rewardManager.resetAllClaims();
            MutableComponent msg = Component.literal("Reset daily rewards for all players")
                .withStyle(ChatFormatting.GREEN);
            source.sendSuccess(() -> msg, true);
            CobblemonMerchants.LOGGER.info("Reset all daily rewards by {}", source.getTextName());
        }

        return 1;
    }

    /**
     * Force refresh/reroll daily rotating trades.
     * If merchantId is null, refreshes all trades. Otherwise, only refreshes trades for the specified merchant.
     */
    private static int refreshTrades(CommandContext<CommandSourceStack> ctx, String merchantId) {
        CommandSourceStack source = ctx.getSource();

        if (!(source.getLevel() instanceof ServerLevel level)) {
            source.sendFailure(Component.literal("This command can only be run on the server"));
            return 0;
        }

        try {
            DailyRotatingTradeManager tradeManager = DailyRotatingTradeManager.get(level);

            if (merchantId == null) {
                // Refresh all trades
                tradeManager.forceRotation();

                // Also clear all slot-based usage records so refreshed trades start fresh
                net.fit.cobblemonmerchants.merchant.rewards.DailyTradeResetManager resetManager =
                    net.fit.cobblemonmerchants.merchant.rewards.DailyTradeResetManager.get(level);
                resetManager.clearAllSlotUsage();

                // Reload all merchant trades to pick up new rotating trades
                int updatedCount = reloadAllMerchantTrades(source.getServer());

                MutableComponent msg = Component.literal("Rerolled all daily rotating trades. Updated ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(String.valueOf(updatedCount)).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" merchants.").withStyle(ChatFormatting.GREEN));
                source.sendSuccess(() -> msg, true);

                CobblemonMerchants.LOGGER.info("Rerolled all daily rotating trades by {}, updated {} merchants",
                    source.getTextName(), updatedCount);
            } else {
                // Refresh trades for specific merchant
                net.minecraft.resources.ResourceLocation merchantLoc;
                try {
                    merchantLoc = net.minecraft.resources.ResourceLocation.parse(merchantId);
                } catch (Exception e) {
                    source.sendFailure(Component.literal("Invalid merchant ID: " + merchantId));
                    return 0;
                }

                var config = net.fit.cobblemonmerchants.merchant.config.MerchantConfigRegistry.getConfig(merchantLoc);
                if (config == null) {
                    source.sendFailure(Component.literal("Merchant not found: " + merchantId));
                    return 0;
                }

                if (config.dailyRotatingTrades().isEmpty() || config.dailyRotatingTrades().get().isEmpty()) {
                    source.sendFailure(Component.literal("Merchant has no daily rotating trades: " + merchantId));
                    return 0;
                }

                // Get the slot IDs for this merchant and clear them
                // Handle count > 1 by including all indexed slot IDs (slotId_0, slotId_1, etc.)
                java.util.List<String> slotIds = new java.util.ArrayList<>();
                for (var rotatingConfig : config.dailyRotatingTrades().get()) {
                    int count = rotatingConfig.getEffectiveCount();
                    for (int i = 0; i < count; i++) {
                        slotIds.add(rotatingConfig.getSlotIdForIndex(i));
                    }
                }

                int clearedCount = tradeManager.clearSlots(slotIds);

                // Also clear usage records for these slots so refreshed trades start fresh
                net.fit.cobblemonmerchants.merchant.rewards.DailyTradeResetManager resetManager =
                    net.fit.cobblemonmerchants.merchant.rewards.DailyTradeResetManager.get(level);
                resetManager.clearSlotUsage(slotIds);

                // Reload only merchants of this type
                int updatedCount = reloadMerchantsByType(source.getServer(), merchantLoc);

                MutableComponent msg = Component.literal("Rerolled ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(String.valueOf(clearedCount)).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" daily trade slot(s) for ").withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(merchantId).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(". Updated ").withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(String.valueOf(updatedCount)).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" merchant(s).").withStyle(ChatFormatting.GREEN));
                source.sendSuccess(() -> msg, true);

                CobblemonMerchants.LOGGER.info("Rerolled {} daily trade slots for merchant {} by {}, updated {} merchants",
                    clearedCount, merchantId, source.getTextName(), updatedCount);
            }
        } catch (Exception e) {
            source.sendFailure(Component.literal("Failed to refresh daily trades: " + e.getMessage()));
            CobblemonMerchants.LOGGER.error("Failed to refresh daily trades", e);
            return 0;
        }

        return 1;
    }

    /**
     * Reset both rewards and trades.
     */
    private static int resetAll(CommandContext<CommandSourceStack> ctx, ServerPlayer targetPlayer) {
        // Reset rewards first
        int rewardsResult = resetRewards(ctx, targetPlayer);
        if (rewardsResult == 0) {
            return 0;
        }

        // Then reset trades (only if no specific player, as trades are global)
        if (targetPlayer == null) {
            int tradesResult = refreshTrades(ctx, null);
            if (tradesResult == 0) {
                return 0;
            }
        } else {
            // Inform that trades are global
            ctx.getSource().sendSuccess(() ->
                Component.literal("Note: Daily rotating trades are global and were not reset.")
                    .withStyle(ChatFormatting.GRAY), false);
        }

        return 1;
    }

    /**
     * Reload trades for all merchant entities.
     */
    private static int reloadAllMerchantTrades(net.minecraft.server.MinecraftServer server) {
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                if (entity instanceof net.fit.cobblemonmerchants.merchant.CustomMerchantEntity merchant) {
                    merchant.reloadTradesFromConfig();
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Reload trades for merchants of a specific type.
     */
    private static int reloadMerchantsByType(net.minecraft.server.MinecraftServer server,
            net.minecraft.resources.ResourceLocation merchantType) {
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                if (entity instanceof net.fit.cobblemonmerchants.merchant.CustomMerchantEntity merchant) {
                    if (merchantType.equals(merchant.getTraderId())) {
                        merchant.reloadTradesFromConfig();
                        count++;
                    }
                }
            }
        }
        return count;
    }
}
