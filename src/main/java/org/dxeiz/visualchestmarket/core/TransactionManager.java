package org.dxeiz.visualchestmarket.core;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.dxeiz.visualchestmarket.VisualChestMarket;
import org.dxeiz.visualchestmarket.model.Shop;
import org.dxeiz.visualchestmarket.model.ShopMode;
import org.dxeiz.visualchestmarket.model.ShopTransaction;

import java.sql.Connection; // DatabaseManager'dan connection alacağız
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class TransactionManager {

    private final VisualChestMarket plugin;

    public TransactionManager(VisualChestMarket plugin) {
        this.plugin = plugin;
    }

    public void logTransaction(Shop shop, Player customer, int amount, double totalPrice, ShopMode type) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Not: DatabaseManager'a getConnection() metodu eklemelisin veya executeUpdate metodu yazmalısın.
                // Burada SQL örneği veriyorum:
                String query = "INSERT INTO shop_transactions (shop_id, player_uuid, player_name, amount, total_price, type, timestamp) VALUES (?,?,?,?,?,?,?)";

                // Bu kısım DatabaseManager içindeki connection üzerinden çalışmalı
                Connection conn = plugin.getDatabaseManager().getConnection();
                try (PreparedStatement ps = conn.prepareStatement(query)) {
                    ps.setInt(1, shop.getId());
                    ps.setString(2, customer.getUniqueId().toString());
                    ps.setString(3, customer.getName());
                    ps.setInt(4, amount);
                    ps.setDouble(5, totalPrice);
                    ps.setString(6, type.name());
                    ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
                    ps.executeUpdate();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // Async olarak geçmişi çeker
    public CompletableFuture<List<ShopTransaction>> getHistory(int shopId) {
        return CompletableFuture.supplyAsync(() -> {
            List<ShopTransaction> history = new ArrayList<>();
            String query = "SELECT * FROM shop_transactions WHERE shop_id = ? ORDER BY timestamp DESC LIMIT 45";

            try {
                Connection conn = plugin.getDatabaseManager().getConnection();
                try (PreparedStatement ps = conn.prepareStatement(query)) {
                    ps.setInt(1, shopId);
                    ResultSet rs = ps.executeQuery();

                    while (rs.next()) {
                        history.add(new ShopTransaction(
                                rs.getInt("id"),
                                rs.getInt("shop_id"),
                                UUID.fromString(rs.getString("player_uuid")),
                                rs.getString("player_name"),
                                rs.getInt("amount"),
                                rs.getDouble("total_price"),
                                ShopMode.valueOf(rs.getString("type")),
                                rs.getTimestamp("timestamp").toLocalDateTime()
                        ));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return history;
        });
    }
}