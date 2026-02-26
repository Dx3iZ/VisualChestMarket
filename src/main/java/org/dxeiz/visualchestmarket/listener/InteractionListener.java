package org.dxeiz.visualchestmarket.listener;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.dxeiz.visualchestmarket.VisualChestMarket;
import org.dxeiz.visualchestmarket.model.Shop;

import java.util.List;

public class InteractionListener implements Listener {
    private final VisualChestMarket plugin;

    public InteractionListener(VisualChestMarket plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;

        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        
        if (block.getType() != Material.CHEST && !block.getType().name().contains("SIGN")) return;

        Shop shop = plugin.getShopManager().getShopByComponent(block.getLocation());

        // 1. Yeni Market Olusturma (Sol Tık + Eğilme + Eşya)
        if (shop == null && block.getType() == Material.CHEST && event.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (!player.isSneaking()) return;

            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType() == Material.AIR) {
                player.sendMessage(plugin.getConfigManager().getMessage("messages.invalid-item"));
                return;
            }

            List<String> blacklist = plugin.getConfig().getStringList("blacklist");
            if (blacklist.contains(hand.getType().name())) {
                player.sendMessage(plugin.getConfigManager().getMessage("messages.blacklisted-item"));
                return;
            }

            if (block.getBlockData() instanceof Directional dir) {
                Block frontBlock = block.getRelative(dir.getFacing());
                if (frontBlock.getType() != Material.AIR && !frontBlock.getType().name().contains("SIGN")) {
                    player.sendMessage(plugin.getConfigManager().getMessage("messages.block-in-front"));
                    return;
                }
            }

            event.setCancelled(true);
            player.sendMessage(plugin.getConfigManager().getMessage("messages.price-update-prompt"));

            plugin.getInputManager().awaitInput(player.getUniqueId(), (priceStr) -> {
                try {
                    double price = Double.parseDouble(priceStr);
                    if (price <= 0) throw new NumberFormatException();

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        plugin.getShopManager().createShop(player.getUniqueId(), block.getLocation(), hand.clone(), price, 0, (success) -> {
                            if (success) {
                                player.sendMessage(plugin.getConfigManager().getMessage("messages.shop-created", 
                                        "%price%", plugin.getEconomyManager().formatPrice(price),
                                        "%money-type%", String.valueOf(plugin.getConfig().getString("money-type"))));
                            }
                        });
                    });
                } catch (NumberFormatException e) {
                    player.sendMessage(plugin.getConfigManager().getMessage("messages.invalid-price"));
                }
            });
            return;
        }

        if (shop == null) return;

        // 2. Market Etkileşimi
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Her durumda olayı iptal et, varsayılan sandık açma/tabela düzenleme engellensin.
            event.setCancelled(true);

            // Eğilerek sağ tıklandığında (Menü açma)
            if (player.isSneaking()) {
                if (shop.getOwnerId().equals(player.getUniqueId()) || player.hasPermission("vcm.admin")) {
                    plugin.getShopManager().sendShopMenu(player, shop);
                } else {
                    // Yetkisi yoksa, marketin sahibi olmadığını belirten bir mesaj gönder.
                    player.sendMessage(plugin.getConfigManager().getMessage("messages.not-owner"));
                }
            } 
            // Eğilmeden sağ tıklandığında (Sandık açma / Müşteri)
            else {
                if (block.getType() == Material.CHEST) {
                    if (shop.getOwnerId().equals(player.getUniqueId()) || player.hasPermission("vcm.admin")) {
                        // Olay iptal edildiği için sandık açılmaz, bu yüzden manuel açıyoruz.
                        player.openInventory(((org.bukkit.block.Chest) block.getState()).getInventory());
                    } else {
                        // Müşteriye bilgi vermek için tabelaya tıklamasını söyleyebiliriz.
                        player.sendMessage(plugin.getConfigManager().getMessage("messages.customer-sign"));
                    }
                }
            }
        }
    }
}