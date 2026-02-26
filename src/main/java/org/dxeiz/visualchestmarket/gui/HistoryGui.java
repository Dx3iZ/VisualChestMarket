package org.dxeiz.visualchestmarket.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.dxeiz.visualchestmarket.VisualChestMarket;
import org.dxeiz.visualchestmarket.core.ConfigManager;
import org.dxeiz.visualchestmarket.model.Shop;
import org.dxeiz.visualchestmarket.model.ShopMode;
import org.dxeiz.visualchestmarket.model.ShopTransaction;
import org.jetbrains.annotations.NotNull;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class HistoryGui implements InventoryHolder {

    private final VisualChestMarket plugin;
    private final Inventory inventory;
    private final DateTimeFormatter dateFormat;

    public HistoryGui(VisualChestMarket plugin, Shop shop) {
        this.plugin = plugin;
        ConfigManager configManager = plugin.getConfigManager();
        this.inventory = Bukkit.createInventory(this, 54, configManager.getComponent("gui-title-history"));
        this.dateFormat = DateTimeFormatter.ofPattern(configManager.getRawString("date-format"));
        loadHistory(shop);
    }

    private void loadHistory(Shop shop) {
        plugin.getTransactionManager().getHistory(shop.getId()).thenAccept(transactions -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (transactions.isEmpty()) {
                    ItemStack empty = new ItemStack(Material.BARRIER);
                    ItemMeta meta = empty.getItemMeta();
                    meta.displayName(plugin.getConfigManager().getComponent("history-empty"));
                    empty.setItemMeta(meta);
                    inventory.setItem(22, empty);
                } else {
                    int slot = 0;
                    for (ShopTransaction tx : transactions) {
                        if (slot >= 53) break;
                        inventory.setItem(slot++, createHistoryItem(tx));
                    }
                }
            });
        });
    }

    private ItemStack createHistoryItem(ShopTransaction tx) {
        ConfigManager configManager = plugin.getConfigManager();
        Material material = tx.getType() == ShopMode.BUY ? Material.PAPER : Material.MAP;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String typeKey = tx.getType() == ShopMode.BUY ? "history-type-buy" : "history-type-sell";
        String typeName = configManager.getRawString(typeKey);

        meta.displayName(configManager.getComponent("history-item-name", "%player%", tx.getPlayerName()));

        List<Component> lore = new ArrayList<>();
        lore.add(configManager.getComponent("history-entry",
                "%type%", typeName,
                "%amount%", String.valueOf(tx.getAmount()),
                "%price%", plugin.getEconomyManager().formatPrice(tx.getPrice()), // Fiyat formatlandı
                "%date%", tx.getTimestamp().format(dateFormat)
        ));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}