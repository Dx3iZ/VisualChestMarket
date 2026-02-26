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

public class MembersGui implements InventoryHolder {

    private final VisualChestMarket plugin;
    private final Inventory inventory;
    private final Shop shop;

    public MembersGui(VisualChestMarket plugin, Shop shop) {
        this.plugin = plugin;
        this.shop = shop;
        this.inventory = Bukkit.createInventory(this, 54, plugin.getConfigManager().getComponent("gui-title-members"));
        loadMembers();
    }

    private void loadMembers() {
        Map<UUID, String> members = shop.getMembers();
        if (members.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta meta = empty.getItemMeta();
            meta.displayName(Component.text("Henüz üye yok.", NamedTextColor.RED));
            empty.setItemMeta(meta);
            inventory.setItem(22, empty);
        } else {
            int slot = 0;
            for (Map.Entry<UUID, String> entry : members.entrySet()) {
                if (slot >= 54) break;
                inventory.setItem(slot++, createMemberItem(entry.getKey(), entry.getValue()));
            }
        }
    }

    private ItemStack createMemberItem(UUID uuid, String role) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        meta.setOwningPlayer(player);
        meta.displayName(Component.text(player.getName() != null ? player.getName() : "Bilinmeyen", NamedTextColor.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Rol: ", NamedTextColor.GRAY).append(Component.text(role, NamedTextColor.YELLOW)));
        lore.add(Component.text(" "));
        lore.add(Component.text("Sol Tık: Rolü Değiştir", NamedTextColor.GREEN));
        lore.add(Component.text("Sağ Tık: Üyeyi Sil", NamedTextColor.RED));
        
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}