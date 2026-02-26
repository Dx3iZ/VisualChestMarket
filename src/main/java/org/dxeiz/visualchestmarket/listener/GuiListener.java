package org.dxeiz.visualchestmarket.listener;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.dxeiz.visualchestmarket.VisualChestMarket;
import org.dxeiz.visualchestmarket.gui.BanListGui;
import org.dxeiz.visualchestmarket.gui.MembersGui;
import org.dxeiz.visualchestmarket.model.Shop;

import java.util.UUID;

public class GuiListener implements Listener {

    private final VisualChestMarket plugin;

    public GuiListener(VisualChestMarket plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof MembersGui) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();

            if (clickedItem == null || !(clickedItem.getItemMeta() instanceof SkullMeta skullMeta)) {
                return;
            }

            OfflinePlayer member = skullMeta.getOwningPlayer();
            if (member == null) return;

            Shop shop = plugin.getShopManager().getShopByComponent(player.getTargetBlock(5).getLocation());
            if (shop == null) return;

            if (event.isLeftClick()) {
                // Rol değiştirme mantığı
                String currentRole = shop.getMemberRole(member.getUniqueId());
                String newRole = currentRole.equals("MANAGER") ? "HELPER" : "MANAGER";
                plugin.getShopManager().addMember(shop, member.getUniqueId(), newRole);
                player.sendMessage(plugin.getConfigManager().getMessage("messages.member-role-updated", "%player%", member.getName(), "%role%", newRole));
                player.closeInventory();
                player.performCommand("vcm members"); // GUI'yi yenile
            } else if (event.isRightClick()) {
                // Üye silme mantığı
                plugin.getShopManager().removeMember(shop, member.getUniqueId());
                player.sendMessage(plugin.getConfigManager().getMessage("messages.member-removed", "%player%", member.getName()));
                player.closeInventory();
                player.performCommand("vcm members"); // GUI'yi yenile
            }
        }
        else if (event.getInventory().getHolder() instanceof BanListGui) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();

            if (clickedItem == null || !(clickedItem.getItemMeta() instanceof SkullMeta skullMeta)) {
                return;
            }

            OfflinePlayer bannedPlayer = skullMeta.getOwningPlayer();
            if (bannedPlayer == null) return;

            Shop shop = plugin.getShopManager().getShopByComponent(player.getTargetBlock(5).getLocation());
            if (shop == null) return;

            if (event.isLeftClick()) {
                // Yasağı kaldırma mantığı
                plugin.getShopManager().unbanPlayer(shop, bannedPlayer.getUniqueId());
                player.sendMessage(plugin.getConfigManager().getMessage("messages.player-unbanned", "%player%", bannedPlayer.getName()));
                player.closeInventory();
                player.performCommand("vcm banlist"); // GUI'yi yenile
            }
        }
    }
}