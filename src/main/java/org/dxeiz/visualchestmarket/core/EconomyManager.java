package org.dxeiz.visualchestmarket.core;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.dxeiz.visualchestmarket.VisualChestMarket;

import java.text.DecimalFormat;
import java.util.UUID;

public class EconomyManager {

    private final VisualChestMarket plugin;
    private final Economy econ;
    private DecimalFormat priceFormat;
    private double taxRate;

    public EconomyManager(VisualChestMarket plugin) {
        this.plugin = plugin;
        this.econ = plugin.getEconomy();
        loadConfigValues();
    }

    /**
     * Config'den değerleri yükler veya yeniler.
     */
    public void reload() {
        loadConfigValues();
    }

    private void loadConfigValues() {
        this.priceFormat = new DecimalFormat(plugin.getConfig().getString("price-format", "#,###.##"));
        this.taxRate = plugin.getConfig().getDouble("tax-rate", 0.0);
    }

    /**
     * Oyuncunun yeterli parası olup olmadığını kontrol eder.
     */
    public boolean hasEnough(UUID uuid, double amount) {
        OfflinePlayer player = plugin.getServer().getOfflinePlayer(uuid);
        return econ.has(player, amount);
    }

    /**
     * Oyuncudan para çeker (Satın alma işlemi).
     */
    public boolean withdraw(UUID uuid, double amount) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        if (econ == null) {
            VisualChestMarket.LOGGER.severe("Ekonomi sağlayıcısı (Vault) bulunamadı!");
            return false;
        }

        EconomyResponse r = econ.withdrawPlayer(player, amount);

        if (r.transactionSuccess()) {
            return true;
        } else {
            VisualChestMarket.LOGGER.warning("Para çekme başarısız: " + player.getName() + " - " + r.errorMessage);
            return false;
        }
    }

    /**
     * Oyuncuya para yatırır (Satış işlemi).
     */
    public boolean deposit(UUID uuid, double amount) {
        OfflinePlayer player = plugin.getServer().getOfflinePlayer(uuid);
        EconomyResponse r = econ.depositPlayer(player, amount);
        return r.transactionSuccess();
    }

    /**
     * Parayı formatlar (Örn: 1000 -> 1,000.00)
     */
    public String formatPrice(double amount) {
        return priceFormat.format(amount);
    }

    /**
     * Vergi miktarını hesaplar.
     */
    public double calculateTax(double amount) {
        if (taxRate <= 0) return 0;
        return amount * (taxRate / 100.0);
    }
}