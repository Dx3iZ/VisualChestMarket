package org.dxeiz.visualchestmarket.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.dxeiz.visualchestmarket.VisualChestMarket;
import org.dxeiz.visualchestmarket.model.Shop;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BanListGui implements InventoryHolder {

    private final VisualChestMarket plugin;
    private final Inventory inventory;
    private final Shop shop;

    public BanListGui(VisualChestMarket plugin, Shop shop) {
        this.plugin = plugin;
        this.shop = shop;
        this.inventory = Bukkit.createInventory(this, 54, plugin.getConfigManager().getComponent("gui-title-bans"));
        loadBans();
    }

    private void loadBans() {
        Map<UUID, String> bans = shop.getBannedPlayers();
        if (bans.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta meta = empty.getItemMeta();
            meta.displayName(Component.text("Yasaklı oyuncu yok.", NamedTextColor.GREEN));
            empty.setItemMeta(meta);
            inventory.setItem(22, empty);
        } else {
            int slot = 0;
            for (Map.Entry<UUID, String> entry : bans.entrySet()) {
                if (slot >= 54) break;
                inventory.setItem(slot++, createBanItem(entry.getKey(), entry.getValue()));
            }
        }
    }

    private ItemStack createBanItem(UUID uuid, String reasonData) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        meta.setOwningPlayer(player);
        
        String[] parts = reasonData.split("\\|");
        String reason = parts.length > 0 ? parts[0] : "Bilinmiyor";
        String date = parts.length > 1 ? parts[1] : "Bilinmiyor";

        meta.displayName(plugin.getConfigManager().getComponent("ban-item-name", "%player%", player.getName() != null ? player.getName() : "Bilinmeyen"));

        List<Component> lore = new ArrayList<>();
        lore.add(plugin.getConfigManager().getComponent("ban-item-lore", 
                "%reason%", reason,
                "%date%", date
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