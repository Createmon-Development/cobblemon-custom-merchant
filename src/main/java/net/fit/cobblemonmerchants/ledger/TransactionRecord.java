package net.fit.cobblemonmerchants.ledger;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Represents a single transaction (or bundled transactions) in the merchant ledger.
 */
public class TransactionRecord {
    private static final String RELIC_COIN_ID = "cobblemon:relic_coin";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss_MM-dd-yy")
            .withZone(ZoneId.systemDefault());

    private final UUID transactionId;
    private final UUID playerUuid;
    private final String playerName;
    private final String merchantId;
    private final String merchantName;
    private final String inputItem;
    private final int inputCount;
    private final String outputItem;
    private final int outputCount;
    private final Instant timestamp;
    private int quantity; // Number of identical trades bundled together

    public TransactionRecord(
            UUID playerUuid,
            String playerName,
            String merchantId,
            String merchantName,
            String inputItem,
            int inputCount,
            String outputItem,
            int outputCount
    ) {
        this.transactionId = UUID.randomUUID();
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.merchantId = merchantId;
        this.merchantName = merchantName;
        this.inputItem = inputItem;
        this.inputCount = inputCount;
        this.outputItem = outputItem;
        this.outputCount = outputCount;
        this.timestamp = Instant.now();
        this.quantity = 1;
    }

    // Constructor for loading from storage
    public TransactionRecord(
            UUID transactionId,
            UUID playerUuid,
            String playerName,
            String merchantId,
            String merchantName,
            String inputItem,
            int inputCount,
            String outputItem,
            int outputCount,
            Instant timestamp,
            int quantity
    ) {
        this.transactionId = transactionId;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.merchantId = merchantId;
        this.merchantName = merchantName;
        this.inputItem = inputItem;
        this.inputCount = inputCount;
        this.outputItem = outputItem;
        this.outputCount = outputCount;
        this.timestamp = timestamp;
        this.quantity = quantity;
    }

    /**
     * Checks if another transaction is identical (same player, merchant, items) for bundling
     */
    public boolean canBundleWith(TransactionRecord other) {
        return this.playerUuid.equals(other.playerUuid)
                && this.merchantId.equals(other.merchantId)
                && this.inputItem.equals(other.inputItem)
                && this.inputCount == other.inputCount
                && this.outputItem.equals(other.outputItem)
                && this.outputCount == other.outputCount;
    }

    /**
     * Increment the bundle quantity
     */
    public void incrementQuantity() {
        this.quantity++;
    }

    // Getters
    public UUID getTransactionId() { return transactionId; }
    public UUID getPlayerUuid() { return playerUuid; }
    public String getPlayerName() { return playerName; }
    public String getMerchantId() { return merchantId; }
    public String getMerchantName() { return merchantName; }
    public String getInputItem() { return inputItem; }
    public int getInputCount() { return inputCount; }
    public String getOutputItem() { return outputItem; }
    public int getOutputCount() { return outputCount; }
    public Instant getTimestamp() { return timestamp; }
    public int getQuantity() { return quantity; }

    /**
     * Get formatted timestamp (HH:mm:ss_dd-MM-yy)
     */
    public String getFormattedTimestamp() {
        return TIMESTAMP_FORMAT.format(timestamp);
    }

    /**
     * Get total input cost (inputCount * quantity)
     */
    public int getTotalInputCost() {
        return inputCount * quantity;
    }

    /**
     * Get total output received (outputCount * quantity)
     */
    public int getTotalOutputReceived() {
        return outputCount * quantity;
    }

    /**
     * Calculate the relic coin change for this transaction.
     * Positive = player gained coins (sold items for coins)
     * Negative = player spent coins (bought items with coins)
     * Zero = no coins involved
     */
    public int getCoinChange() {
        int coinsGained = 0;
        int coinsSpent = 0;

        // Check if output is relic coins (player gained coins)
        if (RELIC_COIN_ID.equals(outputItem)) {
            coinsGained = outputCount * quantity;
        }

        // Check if input is relic coins (player spent coins)
        if (RELIC_COIN_ID.equals(inputItem)) {
            coinsSpent = inputCount * quantity;
        }

        return coinsGained - coinsSpent;
    }

    /**
     * Get coin change as a formatted string (+X, -X, or 0)
     */
    public String getCoinChangeString() {
        int change = getCoinChange();
        if (change > 0) {
            return "+" + change;
        } else if (change < 0) {
            return String.valueOf(change);
        } else {
            return "0";
        }
    }

    /**
     * Convert to CSV row
     */
    public String toCsvRow() {
        return String.format("%s,%s,%s,%s,%s,%s,%d,%s,%d,%s,%d,%s",
                transactionId,
                getFormattedTimestamp(),
                playerUuid,
                escapeCsv(playerName),
                escapeCsv(shortName(merchantId)),
                escapeCsv(merchantName),
                quantity,
                escapeCsv(shortName(inputItem)),
                inputCount,
                escapeCsv(shortName(outputItem)),
                outputCount,
                getCoinChangeString()
        );
    }

    /**
     * CSV header row
     */
    public static String getCsvHeader() {
        return "transaction_id,timestamp,player_uuid,player_name,merchant_id,merchant_name,quantity,input_item,input_count,output_item,output_count,coin_change";
    }

    /**
     * Strip namespace from an ID (e.g., "cobblemon:relic_coin" -> "relic_coin")
     */
    private static String shortName(String fullId) {
        if (fullId == null) return "";
        int colonIndex = fullId.indexOf(':');
        if (colonIndex >= 0 && colonIndex < fullId.length() - 1) {
            return fullId.substring(colonIndex + 1);
        }
        return fullId;
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s traded %dx (%dx %s -> %dx %s) with %s [coins: %s]",
                getFormattedTimestamp(),
                playerName,
                quantity,
                inputCount, inputItem,
                outputCount, outputItem,
                merchantName,
                getCoinChangeString()
        );
    }
}
