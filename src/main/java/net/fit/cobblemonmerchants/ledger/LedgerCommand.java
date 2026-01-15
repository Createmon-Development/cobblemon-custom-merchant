package net.fit.cobblemonmerchants.ledger;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Commands for managing the transaction ledger
 *
 * /ledger summary - Show transaction summary with coin statistics
 * /ledger export - Export all transactions to CSV
 * /ledger player <player> history - Show transactions for a player with coin stats
 * /ledger player <player> summary - Show summary for a player (player can view own)
 * /ledger me - View your own summary (no OP required)
 * /ledger merchant <id> - Show transactions for a merchant with coin stats
 * /ledger top merchants - Show busiest merchants with coin stats
 * /ledger top items - Show most popular items
 * /ledger top players - Show most active traders with coin stats
 * /ledger webhook set <url> - Set webhook URL for syncing
 * /ledger webhook sync - Sync unsynced transactions to webhook
 * /ledger webhook sync reset - Clear webhook sync tracking for full re-sync
 * /ledger webhook disable - Disable webhook sync
 * /ledger webhook status - Check webhook status
 * /ledger clear confirm - Clear all transaction data (dangerous!)
 */
public class LedgerCommand {

    private static final DateTimeFormatter SHORT_TIMESTAMP = DateTimeFormatter.ofPattern("HH:mm-MM/dd/yy")
            .withZone(ZoneId.systemDefault());

    /**
     * Suggestion provider for merchant IDs - provides tab completion from known merchants
     */
    private static final SuggestionProvider<CommandSourceStack> MERCHANT_SUGGESTIONS = (ctx, builder) -> {
        TransactionLedger ledger = getLedger(ctx);
        // Get all known merchant IDs from transactions
        List<String> merchantIds = ledger.getTradeCountByMerchant().keySet().stream()
                .sorted()
                .collect(Collectors.toList());
        return SharedSuggestionProvider.suggest(merchantIds, builder);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Main ledger commands (require OP level 2)
        dispatcher.register(
            Commands.literal("ledger")
                .requires(source -> source.hasPermission(2)) // Require OP level 2
                .then(Commands.literal("summary")
                    .executes(LedgerCommand::showSummary))
                .then(Commands.literal("export")
                    .executes(LedgerCommand::exportCsv))
                .then(Commands.literal("player")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.literal("history")
                            .executes(LedgerCommand::showPlayerHistory))
                        .then(Commands.literal("summary")
                            .executes(LedgerCommand::showPlayerSummary))))
                .then(Commands.literal("merchant")
                    .then(Commands.argument("merchantId", StringArgumentType.greedyString())
                        .suggests(MERCHANT_SUGGESTIONS)
                        .executes(LedgerCommand::showMerchantTransactions)))
                .then(Commands.literal("top")
                    .then(Commands.literal("merchants")
                        .executes(LedgerCommand::showTopMerchants))
                    .then(Commands.literal("items")
                        .executes(LedgerCommand::showTopItems))
                    .then(Commands.literal("players")
                        .executes(LedgerCommand::showTopPlayers)))
                .then(Commands.literal("webhook")
                    .then(Commands.literal("set")
                        .then(Commands.argument("url", StringArgumentType.string())
                            .executes(LedgerCommand::setWebhook)))
                    .then(Commands.literal("sync")
                        .executes(LedgerCommand::syncWebhook)
                        .then(Commands.literal("reset")
                            .executes(LedgerCommand::resetWebhookSync)))
                    .then(Commands.literal("disable")
                        .executes(LedgerCommand::disableWebhook))
                    .then(Commands.literal("status")
                        .executes(LedgerCommand::webhookStatus)))
                .then(Commands.literal("clear")
                    .then(Commands.literal("confirm")
                        .executes(LedgerCommand::clearData)))
        );

        // Player self-access command (no OP required - players can view their own summary)
        dispatcher.register(
            Commands.literal("ledger")
                .then(Commands.literal("me")
                    .executes(LedgerCommand::showOwnSummary))
        );
    }

    private static TransactionLedger getLedger(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        return TransactionLedger.get(level);
    }

    /**
     * Strip the namespace from an item/merchant ID for cleaner display
     */
    private static String shortName(String fullId) {
        if (fullId == null) return "";
        int colonIndex = fullId.indexOf(':');
        if (colonIndex >= 0 && colonIndex < fullId.length() - 1) {
            return fullId.substring(colonIndex + 1);
        }
        return fullId;
    }

    /**
     * Get player name from UUID, with fallback to truncated UUID
     */
    private static String getPlayerName(CommandContext<CommandSourceStack> ctx, UUID uuid) {
        return ctx.getSource().getServer().getProfileCache()
                .get(uuid)
                .map(profile -> profile.getName())
                .orElse(uuid.toString().substring(0, 8) + "...");
    }

    /**
     * Format coin change as +X, -X, or 0
     */
    private static String formatCoinChange(int change) {
        if (change > 0) return "+" + change;
        if (change < 0) return String.valueOf(change);
        return "0";
    }

    /**
     * Create a colored coin change component
     */
    private static MutableComponent coloredCoinChange(int change) {
        String text = formatCoinChange(change);
        if (change > 0) {
            return Component.literal(text).withStyle(ChatFormatting.GREEN);
        } else if (change < 0) {
            return Component.literal(text).withStyle(ChatFormatting.RED);
        } else {
            return Component.literal(text).withStyle(ChatFormatting.GRAY);
        }
    }

    /**
     * Create a colored coin change component from string
     */
    private static MutableComponent coloredCoinChange(String coinChangeStr) {
        if (coinChangeStr.startsWith("+")) {
            return Component.literal(coinChangeStr).withStyle(ChatFormatting.GREEN);
        } else if (coinChangeStr.startsWith("-")) {
            return Component.literal(coinChangeStr).withStyle(ChatFormatting.RED);
        } else {
            return Component.literal(coinChangeStr).withStyle(ChatFormatting.GRAY);
        }
    }

    /**
     * Format a short timestamp from an Instant
     */
    private static String formatShortTimestamp(Instant timestamp) {
        return SHORT_TIMESTAMP.format(timestamp);
    }

    /**
     * Build a formatted transaction line with colors and delimiters
     * Format: timestamp | inputCount inputItem -> outputCount outputItem | merchant | coinChange
     */
    private static Component formatTransactionLine(TransactionRecord t) {
        MutableComponent line = Component.empty();

        // Timestamp in gray
        line.append(Component.literal(formatShortTimestamp(t.getTimestamp()))
                .withStyle(ChatFormatting.GRAY));
        line.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));

        // Trade: input -> output in yellow
        line.append(Component.literal(String.format("%dx %s",
                t.getQuantity() * t.getInputCount(), shortName(t.getInputItem())))
                .withStyle(ChatFormatting.YELLOW));
        line.append(Component.literal(" -> ").withStyle(ChatFormatting.WHITE));
        line.append(Component.literal(String.format("%dx %s",
                t.getQuantity() * t.getOutputCount(), shortName(t.getOutputItem())))
                .withStyle(ChatFormatting.YELLOW));

        line.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));

        // Merchant name in dark gray
        line.append(Component.literal(t.getMerchantName())
                .withStyle(ChatFormatting.DARK_GRAY));

        line.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));

        // Coin change colored
        line.append(coloredCoinChange(t.getCoinChangeString()));

        return line;
    }

    /**
     * Build a formatted transaction line for merchant view (shows player name instead of merchant)
     */
    private static Component formatTransactionLineForMerchant(TransactionRecord t) {
        MutableComponent line = Component.empty();

        // Timestamp in gray
        line.append(Component.literal(formatShortTimestamp(t.getTimestamp()))
                .withStyle(ChatFormatting.GRAY));
        line.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));

        // Player name in white
        line.append(Component.literal(t.getPlayerName())
                .withStyle(ChatFormatting.WHITE));

        line.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));

        // Trade: input -> output in yellow
        line.append(Component.literal(String.format("%dx %s",
                t.getQuantity() * t.getInputCount(), shortName(t.getInputItem())))
                .withStyle(ChatFormatting.YELLOW));
        line.append(Component.literal(" -> ").withStyle(ChatFormatting.WHITE));
        line.append(Component.literal(String.format("%dx %s",
                t.getQuantity() * t.getOutputCount(), shortName(t.getOutputItem())))
                .withStyle(ChatFormatting.YELLOW));

        line.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));

        // Coin change colored
        line.append(coloredCoinChange(t.getCoinChangeString()));

        return line;
    }

    private static int showSummary(CommandContext<CommandSourceStack> ctx) {
        TransactionLedger ledger = getLedger(ctx);

        // Header
        ctx.getSource().sendSuccess(() -> Component.literal("=== Merchant Transaction Summary ===")
                .withStyle(ChatFormatting.GOLD), false);

        // Overview stats
        int totalTrades = ledger.getTotalTradeCount();
        int totalCoinCirculation = ledger.getTotalCoinCirculation();

        MutableComponent overviewLine = Component.literal("Total Trades: ").withStyle(ChatFormatting.WHITE);
        overviewLine.append(Component.literal(String.valueOf(totalTrades)).withStyle(ChatFormatting.YELLOW));
        overviewLine.append(Component.literal(" | Net Coin Flow: ").withStyle(ChatFormatting.WHITE));
        overviewLine.append(coloredCoinChange(totalCoinCirculation));
        ctx.getSource().sendSuccess(() -> overviewLine, false);

        // Top 5 Merchants
        ctx.getSource().sendSuccess(() -> Component.literal("--- Top 5 Merchants ---")
                .withStyle(ChatFormatting.GOLD), false);

        int rank = 1;
        for (Map.Entry<String, Integer> entry : ledger.getBusiestMerchants(5)) {
            int r = rank++;
            String merchantName = shortName(entry.getKey());
            int coinChange = ledger.getMerchantCoinChange(entry.getKey());

            MutableComponent line = Component.literal(r + ". ").withStyle(ChatFormatting.WHITE);
            line.append(Component.literal(merchantName).withStyle(ChatFormatting.DARK_GRAY));
            line.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(Component.literal(entry.getValue() + " trades").withStyle(ChatFormatting.YELLOW));
            line.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(coloredCoinChange(-coinChange)); // Invert for merchant perspective

            ctx.getSource().sendSuccess(() -> line, false);
        }

        // Top 5 Items
        ctx.getSource().sendSuccess(() -> Component.literal("--- Top 5 Items ---")
                .withStyle(ChatFormatting.GOLD), false);

        rank = 1;
        for (Map.Entry<String, Integer> entry : ledger.getMostPopularItems(5)) {
            int r = rank++;
            String itemName = shortName(entry.getKey());

            MutableComponent line = Component.literal(r + ". ").withStyle(ChatFormatting.WHITE);
            line.append(Component.literal(itemName).withStyle(ChatFormatting.YELLOW));
            line.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(Component.literal(entry.getValue() + " purchased").withStyle(ChatFormatting.WHITE));

            ctx.getSource().sendSuccess(() -> line, false);
        }

        // Top 5 Traders
        ctx.getSource().sendSuccess(() -> Component.literal("--- Top 5 Traders ---")
                .withStyle(ChatFormatting.GOLD), false);

        rank = 1;
        for (Map.Entry<UUID, Integer> entry : ledger.getMostActiveTraders(5)) {
            int r = rank++;
            String name = getPlayerName(ctx, entry.getKey());
            int coinChange = ledger.getPlayerCoinChange(entry.getKey());

            MutableComponent line = Component.literal(r + ". ").withStyle(ChatFormatting.WHITE);
            line.append(Component.literal(name).withStyle(ChatFormatting.WHITE));
            line.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(Component.literal(entry.getValue() + " trades").withStyle(ChatFormatting.YELLOW));
            line.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(coloredCoinChange(coinChange));

            ctx.getSource().sendSuccess(() -> line, false);
        }

        return 1;
    }

    private static int exportCsv(CommandContext<CommandSourceStack> ctx) {
        TransactionLedger ledger = getLedger(ctx);

        ledger.finalizeAllPending();

        try {
            Path exportPath = ledger.exportFullCsv();
            ctx.getSource().sendSuccess(() -> Component.literal("Exported transactions to: " + exportPath)
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("Failed to export: " + e.getMessage()));
            return 0;
        }
    }

    private static int showPlayerHistory(CommandContext<CommandSourceStack> ctx) {
        TransactionLedger ledger = getLedger(ctx);

        try {
            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
            UUID playerUuid = player.getUUID();
            List<TransactionRecord> transactions = ledger.getTransactionsForPlayer(playerUuid);

            ctx.getSource().sendSuccess(() -> Component.literal("=== Transaction History for " + player.getName().getString() + " ===")
                    .withStyle(ChatFormatting.GOLD), false);

            if (transactions.isEmpty()) {
                ctx.getSource().sendSuccess(() -> Component.literal("No transactions found.").withStyle(ChatFormatting.GRAY), false);
            } else {
                int total = transactions.stream().mapToInt(TransactionRecord::getQuantity).sum();
                int coinsGained = ledger.getPlayerCoinsGained(playerUuid);
                int coinsSpent = ledger.getPlayerCoinsSpent(playerUuid);
                int netCoins = ledger.getPlayerCoinChange(playerUuid);

                // Summary line with colored coin values
                MutableComponent summaryLine = Component.literal("Trades: " + total + " | Earned: ")
                        .withStyle(ChatFormatting.WHITE);
                summaryLine.append(Component.literal(String.valueOf(coinsGained)).withStyle(ChatFormatting.GREEN));
                summaryLine.append(Component.literal(" | Spent: ").withStyle(ChatFormatting.WHITE));
                summaryLine.append(Component.literal(String.valueOf(coinsSpent)).withStyle(ChatFormatting.RED));
                summaryLine.append(Component.literal(" | Net: ").withStyle(ChatFormatting.WHITE));
                summaryLine.append(coloredCoinChange(netCoins));

                ctx.getSource().sendSuccess(() -> summaryLine, false);

                // Show last 10 transactions
                int start = Math.max(0, transactions.size() - 10);
                for (int i = start; i < transactions.size(); i++) {
                    TransactionRecord t = transactions.get(i);
                    ctx.getSource().sendSuccess(() -> formatTransactionLine(t), false);
                }
            }

            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Player not found"));
            return 0;
        }
    }

    private static int showPlayerSummary(CommandContext<CommandSourceStack> ctx) {
        TransactionLedger ledger = getLedger(ctx);

        try {
            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
            return displayPlayerSummary(ctx, ledger, player.getUUID(), player.getName().getString());
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Player not found"));
            return 0;
        }
    }

    private static int showOwnSummary(CommandContext<CommandSourceStack> ctx) {
        // This command doesn't require OP - any player can view their own summary
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("This command can only be used by players"));
            return 0;
        }

        TransactionLedger ledger = getLedger(ctx);
        return displayPlayerSummary(ctx, ledger, player.getUUID(), player.getName().getString());
    }

    /**
     * Display a summary for a specific player (shared logic)
     */
    private static int displayPlayerSummary(CommandContext<CommandSourceStack> ctx, TransactionLedger ledger, UUID playerUuid, String playerName) {
        List<TransactionRecord> transactions = ledger.getTransactionsForPlayer(playerUuid);

        ctx.getSource().sendSuccess(() -> Component.literal("=== Summary for " + playerName + " ===")
                .withStyle(ChatFormatting.GOLD), false);

        if (transactions.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No transactions found.").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        int totalTrades = transactions.stream().mapToInt(TransactionRecord::getQuantity).sum();
        int coinsGained = ledger.getPlayerCoinsGained(playerUuid);
        int coinsSpent = ledger.getPlayerCoinsSpent(playerUuid);
        int netCoins = ledger.getPlayerCoinChange(playerUuid);

        // Overview stats
        MutableComponent overviewLine = Component.literal("Total Trades: ").withStyle(ChatFormatting.WHITE);
        overviewLine.append(Component.literal(String.valueOf(totalTrades)).withStyle(ChatFormatting.YELLOW));
        ctx.getSource().sendSuccess(() -> overviewLine, false);

        // Coin stats
        MutableComponent coinLine = Component.literal("Coins Earned: ").withStyle(ChatFormatting.WHITE);
        coinLine.append(Component.literal(String.valueOf(coinsGained)).withStyle(ChatFormatting.GREEN));
        coinLine.append(Component.literal(" | Spent: ").withStyle(ChatFormatting.WHITE));
        coinLine.append(Component.literal(String.valueOf(coinsSpent)).withStyle(ChatFormatting.RED));
        coinLine.append(Component.literal(" | Net: ").withStyle(ChatFormatting.WHITE));
        coinLine.append(coloredCoinChange(netCoins));
        ctx.getSource().sendSuccess(() -> coinLine, false);

        // Top items purchased by this player (excluding relic coins)
        // Track both item counts and coin changes per item
        Map<String, Integer> itemCounts = new HashMap<>();
        Map<String, Integer> itemCoinChanges = new HashMap<>();
        String relicCoinId = "cobblemon:relic_coin";

        for (TransactionRecord t : transactions) {
            String outputItem = t.getOutputItem();
            // Skip relic coins in the items list
            if (!relicCoinId.equals(outputItem)) {
                itemCounts.merge(outputItem, t.getQuantity() * t.getOutputCount(), Integer::sum);
                itemCoinChanges.merge(outputItem, t.getCoinChange(), Integer::sum);
            }
        }

        if (!itemCounts.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("--- Top 5 Items Purchased ---")
                    .withStyle(ChatFormatting.GOLD), false);

            int rank = 1;
            List<Map.Entry<String, Integer>> topItems = itemCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(5)
                    .collect(Collectors.toList());

            for (Map.Entry<String, Integer> entry : topItems) {
                int r = rank++;
                int coinChange = itemCoinChanges.getOrDefault(entry.getKey(), 0);
                MutableComponent line = Component.literal(r + ". ").withStyle(ChatFormatting.WHITE);
                line.append(Component.literal(shortName(entry.getKey())).withStyle(ChatFormatting.YELLOW));
                line.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
                line.append(Component.literal(entry.getValue() + " received").withStyle(ChatFormatting.WHITE));
                line.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
                line.append(coloredCoinChange(coinChange));
                ctx.getSource().sendSuccess(() -> line, false);
            }
        }

        // Top merchants used by this player with coin changes
        Map<String, Integer> merchantCounts = new HashMap<>();
        Map<String, Integer> merchantCoinChanges = new HashMap<>();
        for (TransactionRecord t : transactions) {
            merchantCounts.merge(t.getMerchantId(), t.getQuantity(), Integer::sum);
            merchantCoinChanges.merge(t.getMerchantId(), t.getCoinChange(), Integer::sum);
        }

        if (!merchantCounts.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("--- Top 5 Merchants Used ---")
                    .withStyle(ChatFormatting.GOLD), false);

            int rank = 1;
            List<Map.Entry<String, Integer>> topMerchants = merchantCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(5)
                    .collect(Collectors.toList());

            for (Map.Entry<String, Integer> entry : topMerchants) {
                int r = rank++;
                int coinChange = merchantCoinChanges.getOrDefault(entry.getKey(), 0);
                MutableComponent line = Component.literal(r + ". ").withStyle(ChatFormatting.WHITE);
                line.append(Component.literal(shortName(entry.getKey())).withStyle(ChatFormatting.DARK_GRAY));
                line.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
                line.append(Component.literal(entry.getValue() + " trades").withStyle(ChatFormatting.YELLOW));
                line.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
                line.append(coloredCoinChange(coinChange));
                ctx.getSource().sendSuccess(() -> line, false);
            }
        }

        return 1;
    }

    private static int showMerchantTransactions(CommandContext<CommandSourceStack> ctx) {
        TransactionLedger ledger = getLedger(ctx);
        String merchantId = StringArgumentType.getString(ctx, "merchantId").trim();
        List<TransactionRecord> transactions = ledger.getTransactionsForMerchant(merchantId);

        ctx.getSource().sendSuccess(() -> Component.literal("=== Transactions for " + shortName(merchantId) + " ===")
                .withStyle(ChatFormatting.GOLD), false);

        if (transactions.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No transactions found.").withStyle(ChatFormatting.GRAY), false);
        } else {
            int total = transactions.stream().mapToInt(TransactionRecord::getQuantity).sum();
            int coinsGiven = ledger.getMerchantCoinsGiven(merchantId);
            int coinsCollected = ledger.getMerchantCoinsCollected(merchantId);
            int netCoins = ledger.getMerchantCoinChange(merchantId);

            // Summary line with colored coin values
            MutableComponent summaryLine = Component.literal("Trades: " + total + " | Given: ")
                    .withStyle(ChatFormatting.WHITE);
            summaryLine.append(Component.literal(String.valueOf(coinsGiven)).withStyle(ChatFormatting.RED));
            summaryLine.append(Component.literal(" | Collected: ").withStyle(ChatFormatting.WHITE));
            summaryLine.append(Component.literal(String.valueOf(coinsCollected)).withStyle(ChatFormatting.GREEN));
            summaryLine.append(Component.literal(" | Net: ").withStyle(ChatFormatting.WHITE));
            summaryLine.append(coloredCoinChange(-netCoins)); // Invert for merchant perspective

            ctx.getSource().sendSuccess(() -> summaryLine, false);

            // Show last 10 transactions
            int start = Math.max(0, transactions.size() - 10);
            for (int i = start; i < transactions.size(); i++) {
                TransactionRecord t = transactions.get(i);
                ctx.getSource().sendSuccess(() -> formatTransactionLineForMerchant(t), false);
            }
        }

        return 1;
    }

    private static int showTopMerchants(CommandContext<CommandSourceStack> ctx) {
        TransactionLedger ledger = getLedger(ctx);
        List<Map.Entry<String, Integer>> top = ledger.getBusiestMerchants(10);

        ctx.getSource().sendSuccess(() -> Component.literal("=== Top 10 Merchants ===")
                .withStyle(ChatFormatting.GOLD), false);

        int rank = 1;
        for (Map.Entry<String, Integer> entry : top) {
            int r = rank++;
            String merchantName = shortName(entry.getKey());
            int coinChange = ledger.getMerchantCoinChange(entry.getKey());

            MutableComponent line = Component.literal(r + ". ").withStyle(ChatFormatting.WHITE);
            line.append(Component.literal(merchantName).withStyle(ChatFormatting.DARK_GRAY));
            line.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(Component.literal(entry.getValue() + " trades").withStyle(ChatFormatting.YELLOW));
            line.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(coloredCoinChange(-coinChange)); // Invert for merchant perspective

            ctx.getSource().sendSuccess(() -> line, false);
        }

        return 1;
    }

    private static int showTopItems(CommandContext<CommandSourceStack> ctx) {
        TransactionLedger ledger = getLedger(ctx);
        List<Map.Entry<String, Integer>> top = ledger.getMostPopularItems(10);

        ctx.getSource().sendSuccess(() -> Component.literal("=== Top 10 Items ===")
                .withStyle(ChatFormatting.GOLD), false);

        int rank = 1;
        for (Map.Entry<String, Integer> entry : top) {
            int r = rank++;
            String itemName = shortName(entry.getKey());

            MutableComponent line = Component.literal(r + ". ").withStyle(ChatFormatting.WHITE);
            line.append(Component.literal(itemName).withStyle(ChatFormatting.YELLOW));
            line.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(Component.literal(entry.getValue() + " purchased").withStyle(ChatFormatting.WHITE));

            ctx.getSource().sendSuccess(() -> line, false);
        }

        return 1;
    }

    private static int showTopPlayers(CommandContext<CommandSourceStack> ctx) {
        TransactionLedger ledger = getLedger(ctx);
        List<Map.Entry<UUID, Integer>> top = ledger.getMostActiveTraders(10);

        ctx.getSource().sendSuccess(() -> Component.literal("=== Top 10 Traders ===")
                .withStyle(ChatFormatting.GOLD), false);

        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : top) {
            int r = rank++;
            String name = getPlayerName(ctx, entry.getKey());
            int coinChange = ledger.getPlayerCoinChange(entry.getKey());

            MutableComponent line = Component.literal(r + ". ").withStyle(ChatFormatting.WHITE);
            line.append(Component.literal(name).withStyle(ChatFormatting.WHITE));
            line.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(Component.literal(entry.getValue() + " trades").withStyle(ChatFormatting.YELLOW));
            line.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(coloredCoinChange(coinChange));

            ctx.getSource().sendSuccess(() -> line, false);
        }

        return 1;
    }

    private static int clearData(CommandContext<CommandSourceStack> ctx) {
        TransactionLedger ledger = getLedger(ctx);
        ledger.clearAllData();

        ctx.getSource().sendSuccess(() -> Component.literal("All transaction data has been cleared!")
                .withStyle(ChatFormatting.RED), true);

        return 1;
    }

    // ==================== Webhook Commands ====================

    private static int setWebhook(CommandContext<CommandSourceStack> ctx) {
        TransactionLedger ledger = getLedger(ctx);
        String url = StringArgumentType.getString(ctx, "url");

        if (!url.startsWith("https://")) {
            ctx.getSource().sendFailure(Component.literal("Webhook URL must start with https://"));
            return 0;
        }

        ledger.configureWebhook(url);

        ctx.getSource().sendSuccess(() -> Component.literal("Webhook configured!")
                .withStyle(ChatFormatting.GREEN), true);
        ctx.getSource().sendSuccess(() -> Component.literal("URL: " + url), false);
        ctx.getSource().sendSuccess(() -> Component.literal("New transactions will be sent to this webhook automatically."), false);

        return 1;
    }

    private static int syncWebhook(CommandContext<CommandSourceStack> ctx) {
        TransactionLedger ledger = getLedger(ctx);

        if (!ledger.isWebhookEnabled()) {
            ctx.getSource().sendFailure(Component.literal("Webhook is not configured. Use /ledger webhook set <url> first."));
            return 0;
        }

        int[] stats = ledger.syncAllToWebhook();
        int total = stats[0];
        int alreadySynced = stats[1];
        int toSync = stats[2];

        if (toSync == 0) {
            ctx.getSource().sendSuccess(() -> Component.literal("All " + total + " transactions already synced!")
                    .withStyle(ChatFormatting.GREEN), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                    "Syncing %d new transactions (%d already synced, %d total)",
                    toSync, alreadySynced, total
            )).withStyle(ChatFormatting.YELLOW), false);
            ctx.getSource().sendSuccess(() -> Component.literal("Check logs for progress...")
                    .withStyle(ChatFormatting.GRAY), false);
        }

        return 1;
    }

    private static int resetWebhookSync(CommandContext<CommandSourceStack> ctx) {
        TransactionLedger ledger = getLedger(ctx);

        if (!ledger.isWebhookEnabled()) {
            ctx.getSource().sendFailure(Component.literal("Webhook is not configured. Use /ledger webhook set <url> first."));
            return 0;
        }

        int cleared = ledger.clearWebhookSyncedTransactions();
        int total = ledger.getTotalTradeCount();

        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "Reset webhook sync tracking! Cleared %d synced IDs.", cleared
        )).withStyle(ChatFormatting.GREEN), true);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "%d transactions are now ready for re-sync. Use /ledger webhook sync to send them.", total
        )).withStyle(ChatFormatting.YELLOW), false);

        return 1;
    }

    private static int disableWebhook(CommandContext<CommandSourceStack> ctx) {
        TransactionLedger ledger = getLedger(ctx);
        ledger.disableWebhook();

        ctx.getSource().sendSuccess(() -> Component.literal("Webhook sync disabled.")
                .withStyle(ChatFormatting.YELLOW), true);

        return 1;
    }

    private static int webhookStatus(CommandContext<CommandSourceStack> ctx) {
        TransactionLedger ledger = getLedger(ctx);

        if (ledger.isWebhookEnabled()) {
            ctx.getSource().sendSuccess(() -> Component.literal("Webhook: ENABLED")
                    .withStyle(ChatFormatting.GREEN), false);
            String webhookUrl = ledger.getWebhookUrl();
            String displayUrl = webhookUrl.length() > 60 ? webhookUrl.substring(0, 57) + "..." : webhookUrl;
            ctx.getSource().sendSuccess(() -> Component.literal("URL: " + displayUrl), false);

            // Show sync status
            int unsynced = ledger.getUnsyncedCount();
            int total = ledger.getTotalTradeCount();
            if (unsynced == 0) {
                ctx.getSource().sendSuccess(() -> Component.literal("Sync: All transactions synced")
                        .withStyle(ChatFormatting.GREEN), false);
            } else {
                ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                        "Sync: %d unsynced transactions (use /ledger webhook sync)", unsynced
                )).withStyle(ChatFormatting.YELLOW), false);
            }
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("Webhook: DISABLED")
                    .withStyle(ChatFormatting.GRAY), false);
        }

        return 1;
    }
}
