package net.fit.cobblemonmerchants.ledger;

import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * Manages the transaction ledger for all merchant trades.
 * Handles bundling, CSV export, and diagnostics.
 */
public class TransactionLedger extends SavedData {
    private static final String DATA_NAME = "cobblemon_merchant_ledger";
    private static final long BUNDLE_WINDOW_MS = 10_000; // 10 seconds

    // Server level reference for world save path
    private ServerLevel serverLevel;

    // Recent transactions for bundling (per player)
    private final Map<UUID, TransactionRecord> pendingTransactions = new HashMap<>();
    private final Map<UUID, Long> lastTransactionTime = new HashMap<>();

    // All finalized transactions
    private final List<TransactionRecord> transactions = new ArrayList<>();

    // Queue for async CSV writing
    private final ConcurrentLinkedQueue<TransactionRecord> csvWriteQueue = new ConcurrentLinkedQueue<>();

    // Webhook config
    private String webhookUrl = null;
    private boolean webhookEnabled = false;

    // Track which transactions have been synced to webhook (by transaction ID)
    private final Set<UUID> webhookSyncedTransactions = new HashSet<>();

    // Statistics cache
    private final Map<String, Integer> tradeCountByMerchant = new HashMap<>();
    private final Map<UUID, Integer> tradeCountByPlayer = new HashMap<>();
    private final Map<String, Integer> tradeCountByOutputItem = new HashMap<>();

    // Coin statistics cache
    private final Map<UUID, Integer> coinChangeByPlayer = new HashMap<>();
    private final Map<String, Integer> coinChangeByMerchant = new HashMap<>();

    public TransactionLedger() {
        super();
    }

    public static TransactionLedger get(ServerLevel level) {
        TransactionLedger ledger = level.getServer().overworld().getDataStorage().computeIfAbsent(
                new Factory<>(TransactionLedger::new, TransactionLedger::load),
                DATA_NAME
        );
        ledger.serverLevel = level.getServer().overworld();
        return ledger;
    }

    public static TransactionLedger load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        TransactionLedger ledger = new TransactionLedger();

        // Load webhook config
        if (tag.contains("webhookUrl")) {
            ledger.webhookUrl = tag.getString("webhookUrl");
        }
        ledger.webhookEnabled = tag.getBoolean("webhookEnabled");

        // Load synced transaction IDs
        if (tag.contains("webhookSyncedTransactions")) {
            ListTag syncedList = tag.getList("webhookSyncedTransactions", Tag.TAG_STRING);
            for (int i = 0; i < syncedList.size(); i++) {
                try {
                    ledger.webhookSyncedTransactions.add(UUID.fromString(syncedList.getString(i)));
                } catch (IllegalArgumentException e) {
                    // Skip invalid UUIDs
                }
            }
        }

        // Load transactions
        if (tag.contains("transactions")) {
            ListTag transactionsList = tag.getList("transactions", Tag.TAG_COMPOUND);
            for (int i = 0; i < transactionsList.size(); i++) {
                CompoundTag txTag = transactionsList.getCompound(i);
                TransactionRecord record = loadTransaction(txTag);
                if (record != null) {
                    ledger.transactions.add(record);
                    ledger.updateStatistics(record);
                }
            }
        }

        CobblemonMerchants.LOGGER.info("Loaded {} transactions from ledger", ledger.transactions.size());
        return ledger;
    }

    private static TransactionRecord loadTransaction(CompoundTag tag) {
        try {
            return new TransactionRecord(
                    UUID.fromString(tag.getString("transactionId")),
                    UUID.fromString(tag.getString("playerUuid")),
                    tag.getString("playerName"),
                    tag.getString("merchantId"),
                    tag.getString("merchantName"),
                    tag.getString("inputItem"),
                    tag.getInt("inputCount"),
                    tag.getString("outputItem"),
                    tag.getInt("outputCount"),
                    Instant.parse(tag.getString("timestamp")),
                    tag.getInt("quantity")
            );
        } catch (Exception e) {
            CobblemonMerchants.LOGGER.error("Failed to load transaction: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, net.minecraft.core.HolderLookup.@NotNull Provider registries) {
        // Save webhook config
        if (webhookUrl != null) {
            tag.putString("webhookUrl", webhookUrl);
        }
        tag.putBoolean("webhookEnabled", webhookEnabled);

        // Save synced transaction IDs (only keep IDs for transactions we still have)
        ListTag syncedList = new ListTag();
        Set<UUID> currentTransactionIds = transactions.stream()
                .map(TransactionRecord::getTransactionId)
                .collect(Collectors.toSet());
        for (UUID syncedId : webhookSyncedTransactions) {
            if (currentTransactionIds.contains(syncedId)) {
                syncedList.add(net.minecraft.nbt.StringTag.valueOf(syncedId.toString()));
            }
        }
        tag.put("webhookSyncedTransactions", syncedList);

        // Save transactions (limit to last 10000 to prevent bloat)
        ListTag transactionsList = new ListTag();
        int startIndex = Math.max(0, transactions.size() - 10000);
        for (int i = startIndex; i < transactions.size(); i++) {
            transactionsList.add(saveTransaction(transactions.get(i)));
        }
        tag.put("transactions", transactionsList);

        return tag;
    }

    private CompoundTag saveTransaction(TransactionRecord record) {
        CompoundTag tag = new CompoundTag();
        tag.putString("transactionId", record.getTransactionId().toString());
        tag.putString("playerUuid", record.getPlayerUuid().toString());
        tag.putString("playerName", record.getPlayerName());
        tag.putString("merchantId", record.getMerchantId());
        tag.putString("merchantName", record.getMerchantName());
        tag.putString("inputItem", record.getInputItem());
        tag.putInt("inputCount", record.getInputCount());
        tag.putString("outputItem", record.getOutputItem());
        tag.putInt("outputCount", record.getOutputCount());
        tag.putString("timestamp", record.getTimestamp().toString());
        tag.putInt("quantity", record.getQuantity());
        return tag;
    }

    /**
     * Record a new transaction, handling bundling automatically
     */
    public void recordTransaction(
            UUID playerUuid,
            String playerName,
            String merchantId,
            String merchantName,
            String inputItem,
            int inputCount,
            String outputItem,
            int outputCount
    ) {
        TransactionRecord newRecord = new TransactionRecord(
                playerUuid, playerName, merchantId, merchantName,
                inputItem, inputCount, outputItem, outputCount
        );

        long now = System.currentTimeMillis();
        Long lastTime = lastTransactionTime.get(playerUuid);
        TransactionRecord pending = pendingTransactions.get(playerUuid);

        // Check if we can bundle with pending transaction
        if (pending != null && lastTime != null && (now - lastTime) <= BUNDLE_WINDOW_MS) {
            if (pending.canBundleWith(newRecord)) {
                // Bundle with existing
                pending.incrementQuantity();
                lastTransactionTime.put(playerUuid, now);
                setDirty();
                CobblemonMerchants.LOGGER.debug("Bundled transaction for {}, quantity now: {}",
                        playerName, pending.getQuantity());
                return;
            } else {
                // Different trade - finalize pending and start new
                finalizeTransaction(pending);
            }
        } else if (pending != null) {
            // Time window expired - finalize pending
            finalizeTransaction(pending);
        }

        // Start new pending transaction
        pendingTransactions.put(playerUuid, newRecord);
        lastTransactionTime.put(playerUuid, now);
        setDirty();

        CobblemonMerchants.LOGGER.debug("New pending transaction for {}: {} -> {}",
                playerName, inputItem, outputItem);
    }

    /**
     * Finalize a pending transaction (add to list, write to CSV)
     */
    private void finalizeTransaction(TransactionRecord record) {
        transactions.add(record);
        updateStatistics(record);
        csvWriteQueue.add(record);
        setDirty();

        CobblemonMerchants.LOGGER.info("Finalized transaction: {}", record);

        // Trigger async CSV write
        writePendingToCsv();

        // If webhook enabled, send to webhook
        if (webhookEnabled && webhookUrl != null) {
            sendToWebhook(record);
        }
    }

    /**
     * Force finalize all pending transactions (call on server shutdown/save)
     */
    public void finalizeAllPending() {
        for (TransactionRecord pending : pendingTransactions.values()) {
            finalizeTransaction(pending);
        }
        pendingTransactions.clear();
        lastTransactionTime.clear();
    }

    /**
     * Check for and finalize any pending transactions that have expired their bundle window.
     * Call this periodically (e.g., every second via server tick).
     */
    public void finalizeExpiredPending() {
        if (pendingTransactions.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        List<UUID> toFinalize = new ArrayList<>();

        for (Map.Entry<UUID, Long> entry : lastTransactionTime.entrySet()) {
            if ((now - entry.getValue()) > BUNDLE_WINDOW_MS) {
                toFinalize.add(entry.getKey());
            }
        }

        for (UUID playerUuid : toFinalize) {
            TransactionRecord pending = pendingTransactions.remove(playerUuid);
            lastTransactionTime.remove(playerUuid);
            if (pending != null) {
                finalizeTransaction(pending);
                CobblemonMerchants.LOGGER.debug("Auto-finalized expired pending transaction for player {}",
                        pending.getPlayerName());
            }
        }
    }

    /**
     * Update statistics cache
     */
    private void updateStatistics(TransactionRecord record) {
        tradeCountByMerchant.merge(record.getMerchantId(), record.getQuantity(), Integer::sum);
        tradeCountByPlayer.merge(record.getPlayerUuid(), record.getQuantity(), Integer::sum);
        tradeCountByOutputItem.merge(record.getOutputItem(), record.getQuantity(), Integer::sum);

        // Update coin statistics
        int coinChange = record.getCoinChange();
        coinChangeByPlayer.merge(record.getPlayerUuid(), coinChange, Integer::sum);
        coinChangeByMerchant.merge(record.getMerchantId(), coinChange, Integer::sum);
    }

    // ==================== CSV Export ====================

    /**
     * Get the CSV path in the world save directory
     */
    private Path getCsvPath() {
        if (serverLevel != null) {
            Path worldDir = serverLevel.getServer().getWorldPath(LevelResource.ROOT);
            return worldDir.resolve("merchant_transactions.csv");
        }
        // Fallback to current directory if server level not set
        return Path.of("merchant_transactions.csv");
    }

    /**
     * Write pending transactions to CSV file
     */
    private void writePendingToCsv() {
        Path csvPath = getCsvPath();

        try {
            // Create parent directories if needed
            if (csvPath.getParent() != null) {
                Files.createDirectories(csvPath.getParent());
            }

            // Create file with header if it doesn't exist
            if (!Files.exists(csvPath)) {
                Files.writeString(csvPath, TransactionRecord.getCsvHeader() + "\n");
            }

            // Append all queued transactions
            try (BufferedWriter writer = Files.newBufferedWriter(csvPath,
                    StandardOpenOption.APPEND, StandardOpenOption.CREATE)) {
                TransactionRecord record;
                while ((record = csvWriteQueue.poll()) != null) {
                    writer.write(record.toCsvRow());
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            CobblemonMerchants.LOGGER.error("Failed to write to CSV: {}", e.getMessage());
        }
    }

    /**
     * Export all transactions to a new CSV file in world save directory
     */
    public Path exportFullCsv() throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path exportPath;

        if (serverLevel != null) {
            Path worldDir = serverLevel.getServer().getWorldPath(LevelResource.ROOT);
            exportPath = worldDir.resolve("merchant_transactions_export_" + timestamp + ".csv");
        } else {
            exportPath = Path.of("merchant_transactions_export_" + timestamp + ".csv");
        }

        // Create parent directories if needed
        if (exportPath.getParent() != null) {
            Files.createDirectories(exportPath.getParent());
        }

        try (BufferedWriter writer = Files.newBufferedWriter(exportPath)) {
            writer.write(TransactionRecord.getCsvHeader());
            writer.newLine();

            for (TransactionRecord record : transactions) {
                writer.write(record.toCsvRow());
                writer.newLine();
            }
        }

        CobblemonMerchants.LOGGER.info("Exported {} transactions to {}", transactions.size(), exportPath);
        return exportPath;
    }

    // ==================== Webhook Integration ====================

    public void configureWebhook(String url) {
        this.webhookUrl = url;
        this.webhookEnabled = true;
        setDirty();
        CobblemonMerchants.LOGGER.info("Webhook configured: {}", url);
    }

    public void disableWebhook() {
        this.webhookEnabled = false;
        setDirty();
    }

    public boolean isWebhookEnabled() {
        return webhookEnabled;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    private void sendToWebhook(TransactionRecord record) {
        Thread webhookThread = new Thread(() -> {
            try {
                URL url = URI.create(webhookUrl).toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                String json = buildTransactionJson(record);

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = json.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    CobblemonMerchants.LOGGER.debug("Webhook sent successfully for transaction: {}", record.getTransactionId());
                    // Mark as synced
                    webhookSyncedTransactions.add(record.getTransactionId());
                    setDirty();
                } else {
                    CobblemonMerchants.LOGGER.warn("Webhook returned status {}: {}", responseCode, record.getTransactionId());
                }
            } catch (Exception e) {
                CobblemonMerchants.LOGGER.error("Failed to send webhook: {}", e.getMessage());
            }
        }, "Webhook-Sender");
        webhookThread.setDaemon(true);
        webhookThread.start();
    }

    private String buildTransactionJson(TransactionRecord record) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"transactionId\":\"").append(escapeJson(record.getTransactionId().toString())).append("\",");
        json.append("\"timestamp\":\"").append(escapeJson(record.getFormattedTimestamp())).append("\",");
        json.append("\"playerUuid\":\"").append(escapeJson(record.getPlayerUuid().toString())).append("\",");
        json.append("\"playerName\":\"").append(escapeJson(record.getPlayerName())).append("\",");
        json.append("\"merchantId\":\"").append(escapeJson(shortName(record.getMerchantId()))).append("\",");
        json.append("\"merchantName\":\"").append(escapeJson(record.getMerchantName())).append("\",");
        json.append("\"quantity\":").append(record.getQuantity()).append(",");
        json.append("\"inputItem\":\"").append(escapeJson(shortName(record.getInputItem()))).append("\",");
        json.append("\"inputCount\":").append(record.getInputCount()).append(",");
        json.append("\"outputItem\":\"").append(escapeJson(shortName(record.getOutputItem()))).append("\",");
        json.append("\"outputCount\":").append(record.getOutputCount()).append(",");
        json.append("\"coinChange\":\"").append(record.getCoinChangeString()).append("\"");
        json.append("}");
        return json.toString();
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Sync unsynced transactions to webhook.
     * Returns array: [total, alreadySynced, toSync]
     */
    public int[] syncAllToWebhook() {
        if (!webhookEnabled || webhookUrl == null) {
            CobblemonMerchants.LOGGER.warn("Webhook not configured");
            return new int[]{0, 0, 0};
        }

        // Filter to only unsynced transactions
        List<TransactionRecord> unsyncedTransactions = transactions.stream()
                .filter(t -> !webhookSyncedTransactions.contains(t.getTransactionId()))
                .collect(Collectors.toList());

        int total = transactions.size();
        int alreadySynced = total - unsyncedTransactions.size();
        int toSync = unsyncedTransactions.size();

        if (unsyncedTransactions.isEmpty()) {
            CobblemonMerchants.LOGGER.info("All {} transactions already synced to webhook", total);
            return new int[]{total, alreadySynced, 0};
        }

        CobblemonMerchants.LOGGER.info("Syncing {} unsynced transactions to webhook ({} already synced)...",
                toSync, alreadySynced);

        Thread syncThread = new Thread(() -> {
            int sent = 0;
            int failed = 0;
            for (TransactionRecord record : unsyncedTransactions) {
                if (sendToWebhookSync(record)) {
                    sent++;
                    webhookSyncedTransactions.add(record.getTransactionId());
                } else {
                    failed++;
                }
                if ((sent + failed) % 50 == 0) {
                    CobblemonMerchants.LOGGER.info("Synced {}/{} transactions ({} failed)",
                            sent, unsyncedTransactions.size(), failed);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
            setDirty();
            CobblemonMerchants.LOGGER.info("Webhook sync complete: {} sent, {} failed", sent, failed);
        }, "Webhook-Bulk-Sync");
        syncThread.setDaemon(true);
        syncThread.start();

        return new int[]{total, alreadySynced, toSync};
    }

    /**
     * Send a transaction to webhook synchronously.
     * Returns true if successful.
     */
    private boolean sendToWebhookSync(TransactionRecord record) {
        try {
            URL url = URI.create(webhookUrl).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            String json = buildTransactionJson(record);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = json.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (Exception e) {
            CobblemonMerchants.LOGGER.debug("Webhook sync failed for {}: {}", record.getTransactionId(), e.getMessage());
            return false;
        }
    }

    /**
     * Get count of unsynced transactions
     */
    public int getUnsyncedCount() {
        return (int) transactions.stream()
                .filter(t -> !webhookSyncedTransactions.contains(t.getTransactionId()))
                .count();
    }

    /**
     * Clear all webhook synced transaction tracking.
     * This allows a full re-sync to the webhook.
     * Returns the number of synced transaction IDs that were cleared.
     */
    public int clearWebhookSyncedTransactions() {
        int count = webhookSyncedTransactions.size();
        webhookSyncedTransactions.clear();
        setDirty();
        CobblemonMerchants.LOGGER.info("Cleared {} webhook synced transaction IDs", count);
        return count;
    }

    // ==================== Diagnostics ====================

    public int getTotalTradeCount() {
        return transactions.stream().mapToInt(TransactionRecord::getQuantity).sum();
    }

    public Map<String, Integer> getTradeCountByMerchant() {
        return new HashMap<>(tradeCountByMerchant);
    }

    public Map<UUID, Integer> getTradeCountByPlayer() {
        return new HashMap<>(tradeCountByPlayer);
    }

    public List<Map.Entry<String, Integer>> getMostPopularItems(int limit) {
        return tradeCountByOutputItem.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<Map.Entry<UUID, Integer>> getMostActiveTraders(int limit) {
        return tradeCountByPlayer.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<Map.Entry<String, Integer>> getBusiestMerchants(int limit) {
        return tradeCountByMerchant.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    // ==================== Coin Statistics ====================

    /**
     * Get total coin change for a player (positive = gained, negative = spent)
     */
    public int getPlayerCoinChange(UUID playerUuid) {
        return coinChangeByPlayer.getOrDefault(playerUuid, 0);
    }

    /**
     * Get total coin change for a merchant (positive = players gained, negative = players spent)
     */
    public int getMerchantCoinChange(String merchantId) {
        return coinChangeByMerchant.getOrDefault(merchantId, 0);
    }

    /**
     * Get coins gained by player (positive trades only)
     */
    public int getPlayerCoinsGained(UUID playerUuid) {
        return transactions.stream()
                .filter(t -> t.getPlayerUuid().equals(playerUuid))
                .mapToInt(t -> Math.max(0, t.getCoinChange()))
                .sum();
    }

    /**
     * Get coins spent by player (negative trades only, returned as positive)
     */
    public int getPlayerCoinsSpent(UUID playerUuid) {
        return transactions.stream()
                .filter(t -> t.getPlayerUuid().equals(playerUuid))
                .mapToInt(t -> Math.abs(Math.min(0, t.getCoinChange())))
                .sum();
    }

    /**
     * Get coins given out by merchant (to players)
     */
    public int getMerchantCoinsGiven(String merchantId) {
        return transactions.stream()
                .filter(t -> t.getMerchantId().equals(merchantId))
                .mapToInt(t -> Math.max(0, t.getCoinChange()))
                .sum();
    }

    /**
     * Get coins collected by merchant (from players)
     */
    public int getMerchantCoinsCollected(String merchantId) {
        return transactions.stream()
                .filter(t -> t.getMerchantId().equals(merchantId))
                .mapToInt(t -> Math.abs(Math.min(0, t.getCoinChange())))
                .sum();
    }

    /**
     * Get total coins circulating (net change across all transactions)
     */
    public int getTotalCoinCirculation() {
        return transactions.stream().mapToInt(TransactionRecord::getCoinChange).sum();
    }

    /**
     * Get top players by coins gained
     */
    public List<Map.Entry<UUID, Integer>> getTopCoinEarners(int limit) {
        Map<UUID, Integer> coinsGained = new HashMap<>();
        for (TransactionRecord t : transactions) {
            int gain = Math.max(0, t.getCoinChange());
            if (gain > 0) {
                coinsGained.merge(t.getPlayerUuid(), gain, Integer::sum);
            }
        }
        return coinsGained.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Get top players by coins spent
     */
    public List<Map.Entry<UUID, Integer>> getTopCoinSpenders(int limit) {
        Map<UUID, Integer> coinsSpent = new HashMap<>();
        for (TransactionRecord t : transactions) {
            int spent = Math.abs(Math.min(0, t.getCoinChange()));
            if (spent > 0) {
                coinsSpent.merge(t.getPlayerUuid(), spent, Integer::sum);
            }
        }
        return coinsSpent.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<TransactionRecord> getTransactionsInRange(Instant start, Instant end) {
        return transactions.stream()
                .filter(t -> !t.getTimestamp().isBefore(start) && !t.getTimestamp().isAfter(end))
                .collect(Collectors.toList());
    }

    public List<TransactionRecord> getTransactionsForPlayer(UUID playerUuid) {
        return transactions.stream()
                .filter(t -> t.getPlayerUuid().equals(playerUuid))
                .collect(Collectors.toList());
    }

    public List<TransactionRecord> getTransactionsForMerchant(String merchantId) {
        return transactions.stream()
                .filter(t -> t.getMerchantId().equals(merchantId))
                .collect(Collectors.toList());
    }

    public String generateSummaryReport(java.util.function.Function<UUID, String> playerNameResolver) {
        StringBuilder report = new StringBuilder();
        report.append("=== Merchant Transaction Summary ===\n\n");

        report.append("Total Transactions: ").append(getTotalTradeCount()).append("\n");
        report.append("Unique Entries: ").append(transactions.size()).append("\n");
        report.append("Net Coin Circulation: ").append(formatCoinChange(getTotalCoinCirculation())).append("\n\n");

        report.append("--- Top 5 Merchants ---\n");
        for (Map.Entry<String, Integer> entry : getBusiestMerchants(5)) {
            int coinChange = getMerchantCoinChange(entry.getKey());
            report.append(String.format("  %s: %d trades (coins: %s)\n",
                    shortName(entry.getKey()), entry.getValue(), formatCoinChange(coinChange)));
        }

        report.append("\n--- Top 5 Items ---\n");
        for (Map.Entry<String, Integer> entry : getMostPopularItems(5)) {
            report.append(String.format("  %s: %d purchased\n", shortName(entry.getKey()), entry.getValue()));
        }

        report.append("\n--- Top 5 Traders ---\n");
        for (Map.Entry<UUID, Integer> entry : getMostActiveTraders(5)) {
            String playerName = playerNameResolver != null
                    ? playerNameResolver.apply(entry.getKey())
                    : entry.getKey().toString().substring(0, 8) + "...";
            int coinChange = getPlayerCoinChange(entry.getKey());
            report.append(String.format("  %s: %d trades (coins: %s)\n",
                    playerName, entry.getValue(), formatCoinChange(coinChange)));
        }

        return report.toString();
    }

    public String generateSummaryReport() {
        return generateSummaryReport(null);
    }

    private static String shortName(String fullId) {
        if (fullId == null) return "";
        int colonIndex = fullId.indexOf(':');
        if (colonIndex >= 0 && colonIndex < fullId.length() - 1) {
            return fullId.substring(colonIndex + 1);
        }
        return fullId;
    }

    private static String formatCoinChange(int change) {
        if (change > 0) return "+" + change;
        if (change < 0) return String.valueOf(change);
        return "0";
    }

    public void clearAllData() {
        transactions.clear();
        pendingTransactions.clear();
        lastTransactionTime.clear();
        tradeCountByMerchant.clear();
        tradeCountByPlayer.clear();
        tradeCountByOutputItem.clear();
        coinChangeByPlayer.clear();
        coinChangeByMerchant.clear();
        webhookSyncedTransactions.clear();
        csvWriteQueue.clear();
        setDirty();
        CobblemonMerchants.LOGGER.warn("All transaction data cleared!");
    }
}
