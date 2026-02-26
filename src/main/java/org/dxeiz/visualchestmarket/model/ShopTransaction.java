package org.dxeiz.visualchestmarket.model;
import java.time.LocalDateTime;
import java.util.UUID;

public class ShopTransaction {
    private final int id;
    private final int shopId;
    private final UUID playerUuid;
    private final String playerName; // Veritabanında ismi de saklamak performans için iyidir
    private final int amount;
    private final double price;
    private final ShopMode type; // BUY veya SELL
    private final LocalDateTime timestamp;

    public ShopTransaction(int id, int shopId, UUID playerUuid, String playerName, int amount, double price, ShopMode type, LocalDateTime timestamp) {
        this.id = id;
        this.shopId = shopId;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.amount = amount;
        this.price = price;
        this.type = type;
        this.timestamp = timestamp;
    }

    public String getPlayerName() { return playerName; }
    public int getAmount() { return amount; }
    public double getPrice() { return price; }
    public ShopMode getType() { return type; }
    public LocalDateTime getTimestamp() { return timestamp; }
}