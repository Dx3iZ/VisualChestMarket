package org.dxeiz.visualchestmarket.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.dxeiz.visualchestmarket.VisualChestMarket;
import org.dxeiz.visualchestmarket.model.DisplayMode;
import org.dxeiz.visualchestmarket.model.Shop;
import org.dxeiz.visualchestmarket.model.ShopMode;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ShopManager {
    private final VisualChestMarket plugin;
    private final Map<Location, Shop> shopCache = new ConcurrentHashMap<>();

    public ShopManager(VisualChestMarket plugin) {
        this.plugin = plugin;
    }

    /**
     * Config yenilendiğinde tüm marketlerin görünümünü ve tabelalarını günceller.
     */
    public void reload() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Shop shop : shopCache.values()) {
                // Sadece chunk yüklüyse güncelle (Performans için)
                if (shop.getLocation().getChunk().isLoaded()) {
                    plugin.getDisplayManager().updateDisplay(shop);
                    updateShopSign(shop);
                }
            }
        });
    }

    public void createShop(UUID owner, Location loc, ItemStack item, double price, int stock, Consumer<Boolean> callback) {
        Player p = Bukkit.getPlayer(owner);
        if (p == null) {
            callback.accept(false);
            return;
        }

        // Oyuncu Limiti Kontrolü
        int playerLimit = plugin.getConfig().getInt("shop-limit-per-player", -1);
        if (playerLimit != -1 && getShopCountByPlayer(owner) >= playerLimit) {
            p.sendMessage(plugin.getConfigManager().getMessage("messages.player-shop-limit", "%limit%", String.valueOf(playerLimit)));
            callback.accept(false);
            return;
        }

        // Chunk Limiti Kontrolü
        int chunkLimit = plugin.getConfig().getInt("chunk-limit", 16);
        if (getShopCountInChunk(loc.getChunk()) >= chunkLimit) {
            p.sendMessage(plugin.getConfigManager().getMessage("messages.chunk-limit-message", "%limit%", String.valueOf(chunkLimit)));
            callback.accept(false);
            return;
        }

        // Blacklist Kontrolü
        List<String> blacklist = plugin.getConfig().getStringList("blacklist");
        if (blacklist.contains(item.getType().name())) {
            p.sendMessage(plugin.getConfigManager().getMessage("messages.blacklisted-item"));
            callback.accept(false);
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int id = plugin.getDatabaseManager().createShop(owner, loc, item, price, stock);
                
                String defaultDisplayStr = plugin.getConfig().getString("default-display-mode", "ARMORSTAND");
                DisplayMode defaultDisplay = DisplayMode.ARMORSTAND;
                try {
                    defaultDisplay = DisplayMode.valueOf(defaultDisplayStr.toUpperCase());
                } catch (IllegalArgumentException ignored) {}

                Shop shop = new Shop(id, owner, loc, item, price, ShopMode.SELL, defaultDisplay, stock, 0.0);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    shopCache.put(loc, shop);
                    plugin.getDisplayManager().updateDisplay(shop);
                    updateShopSign(shop);
                    callback.accept(true);
                });
            } catch (SQLException e) {
                e.printStackTrace();
                callback.accept(false);
            }
        });
    }

    // Overload for backward compatibility if needed, though InteractionListener uses the callback version
    public void createShop(UUID owner, Location loc, ItemStack item, double price, int stock) {
        createShop(owner, loc, item, price, stock, (success) -> {});
    }

    private int getShopCountInChunk(Chunk chunk) {
        return (int) shopCache.values().stream().filter(s -> s.getLocation().getChunk().equals(chunk)).count();
    }

    public int getShopCountByPlayer(UUID owner) {
        return (int) shopCache.values().stream().filter(s -> s.getOwnerId().equals(owner)).count();
    }

    public void deleteShop(Shop shop) {
        shopCache.remove(shop.getLocation());
        plugin.getDisplayManager().removeDisplay(shop.getLocation());

        Block signBlock = findSignBlock(shop.getLocation().getBlock());
        if (signBlock != null) {
            signBlock.setType(Material.AIR);
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getDatabaseManager().deleteShop(shop.getId());
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public void updateShopSettings(Shop shop) {
        plugin.getDisplayManager().updateDisplay(shop);
        updateShopSign(shop);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                plugin.getDatabaseManager().updateShop(shop)
        );
    }

    public void transferShop(Shop shop, UUID newOwner) {
        shop.setOwnerId(newOwner);
        updateShopSettings(shop);
    }

    public void transferAllShops(UUID oldOwner, UUID newOwner, Consumer<Integer> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int count = plugin.getDatabaseManager().transferAllShops(oldOwner, newOwner);
                // Cache güncelleme
                for (Shop shop : shopCache.values()) {
                    if (shop.getOwnerId().equals(oldOwner)) {
                        shop.setOwnerId(newOwner);
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            plugin.getDisplayManager().updateDisplay(shop);
                            updateShopSign(shop);
                        });
                    }
                }
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(count));
            } catch (SQLException e) {
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(0));
            }
        });
    }

    public void addMember(Shop shop, UUID memberUuid, String role) {
        shop.addMember(memberUuid, role);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getDatabaseManager().addMember(shop.getId(), memberUuid, role);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public void removeMember(Shop shop, UUID memberUuid) {
        shop.removeMember(memberUuid);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getDatabaseManager().removeMember(shop.getId(), memberUuid);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public void banPlayer(Shop shop, UUID bannedUuid, String reason) {
        shop.banPlayer(bannedUuid, reason);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getDatabaseManager().banPlayer(shop.getId(), bannedUuid, reason);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public void unbanPlayer(Shop shop, UUID bannedUuid) {
        shop.unbanPlayer(bannedUuid);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getDatabaseManager().unbanPlayer(shop.getId(), bannedUuid);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public void sendShopMenu(Player player, Shop shop) {
        String moneyType = plugin.getConfig().getString("money-type", "TL");

        Component separator = Component.text("                                                                        ", NamedTextColor.DARK_GRAY, TextDecoration.STRIKETHROUGH);
        Component header = MiniMessage.miniMessage().deserialize("<gradient:#ffaa00:#ff5500><bold> ✦ MARKET AYARLARI ✦</bold></gradient>");

        Component infoPrice = MiniMessage.miniMessage().deserialize("<#35E95F> \uD83D\uDCB0 Fiyat:</#35E95F>")
                .append(MiniMessage.miniMessage().deserialize("<#FFCF70> " + plugin.getEconomyManager().formatPrice(shop.getPrice()) + " " + moneyType)
                        .clickEvent(ClickEvent.runCommand("/vcm setprice"))
                        .hoverEvent(HoverEvent.showText(Component.text("Değiştirmek için tıkla", NamedTextColor.GRAY))));

        String modeTxt = (shop.getMode() == ShopMode.SELL) ? "SATIŞ" : "ALIŞ";
        Component infoMode = MiniMessage.miniMessage().deserialize("<#35E95F> ⚡ Mod: </#35E95F>")
                .append(Component.text(modeTxt, shop.getMode() == ShopMode.SELL ? NamedTextColor.GREEN : NamedTextColor.BLUE)
                        .clickEvent(ClickEvent.runCommand("/vcm togglemode"))
                        .hoverEvent(HoverEvent.showText(Component.text("Değiştirmek için tıkla", NamedTextColor.GRAY))));

        String displayModeName = plugin.getConfig().getString("display-mode-names." + shop.getDisplayMode().name(), shop.getDisplayMode().name());
        Component infoDisplay = MiniMessage.miniMessage().deserialize("<#35E95F> \uD83D\uDEE0 Görünüm: </#35E95F>")
                .append(Component.text(displayModeName, NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.runCommand("/vcm toggledisplay"))
                        .hoverEvent(HoverEvent.showText(Component.text("Değiştirmek için tıkla", NamedTextColor.GRAY))));

        Component btnHistory = MiniMessage.miniMessage().deserialize("<gradient:#07BEB8:#73EEDC><bold> [⌛ GEÇMİŞ] </bold></gradient>")
                .clickEvent(ClickEvent.runCommand("/vcm history"))
                .hoverEvent(HoverEvent.showText(Component.text("İşlem geçmişini gör", NamedTextColor.GRAY)));

        Component btnDelete = MiniMessage.miniMessage().deserialize("<gradient:#B8001C:#FF193B><bold> [\uD83D\uDEB0 SİL] </bold></gradient>")
                .clickEvent(ClickEvent.runCommand("/vcm delete"))
                .hoverEvent(HoverEvent.showText(Component.text("Marketi kaldır", NamedTextColor.GRAY)));
        
        Component btnMembers = MiniMessage.miniMessage().deserialize("<gradient:#4CAF50:#8BC34A><bold> [👥 ÜYELER] </bold></gradient>")
                .clickEvent(ClickEvent.runCommand("/vcm members"))
                .hoverEvent(HoverEvent.showText(Component.text("Üyeleri yönet", NamedTextColor.GRAY)));

        Component btnBans = MiniMessage.miniMessage().deserialize("<gradient:#FF5252:#FF1744><bold> [🚫 YASAKLILAR] </bold></gradient>")
                .clickEvent(ClickEvent.runCommand("/vcm banlist"))
                .hoverEvent(HoverEvent.showText(Component.text("Yasaklı oyuncuları yönet", NamedTextColor.GRAY)));

        player.sendMessage(Component.newline());
        player.sendMessage(separator);
        player.sendMessage(header);
        player.sendMessage(Component.newline());

        ItemStack item = shop.getItem();
        Component itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().displayName()
                : Component.translatable(item.getType().translationKey());

        Component viewItem = MiniMessage.miniMessage().deserialize("<gradient:#99D98C:#B5E48C><bold> [Öğeyi Görüntüle] </bold></gradient>")
                .hoverEvent(item.asHoverEvent());

        player.sendMessage(MiniMessage.miniMessage().deserialize("<#35E95F> \uD83D\uDCE6 Ürün: </#35E95F>")
                .append(itemName.color(TextColor.fromHexString("#F5ECFE")))
                .append(viewItem));
        player.sendMessage(infoPrice);
        player.sendMessage(infoMode);
        player.sendMessage(infoDisplay);
        player.sendMessage(Component.newline());
        player.sendMessage(btnHistory.append(Component.space()).append(btnDelete).append(Component.space()).append(btnMembers).append(Component.space()).append(btnBans));
        player.sendMessage(separator);
        player.sendMessage(Component.newline());
    }

    public void updateShopSign(Shop shop) {
        Block chestBlock = shop.getLocation().getBlock();
        if (!(chestBlock.getState() instanceof Chest)) return;

        Block signBlock = findSignBlock(chestBlock);
        if (signBlock == null) return;

        if (signBlock.getType() == Material.AIR) {
            signBlock.setType(Material.OAK_WALL_SIGN);
            if (signBlock.getBlockData() instanceof Directional signData && chestBlock.getBlockData() instanceof org.bukkit.block.data.type.Chest chestData) {
                signData.setFacing(chestData.getFacing());
                signBlock.setBlockData(signData);
            }
        }

        if (signBlock.getState() instanceof Sign sign) {
            int stock = getStock(shop);
            boolean hasStock = (shop.getMode() == ShopMode.SELL) ? stock > 0 : hasSpace(shop);
            String statusColor = hasStock ? "<green>" : "<red>";
            String modeKey = (shop.getMode() == ShopMode.SELL) ? "messages.shop-mode-sell" : "messages.shop-mode-buy";
            String modeText = plugin.getConfigManager().getRawString(modeKey);

            var mm = MiniMessage.miniMessage();

            for (int i = 0; i < 4; i++) {
                String line = plugin.getConfig().getString("sign-lines.line" + (i + 1), "");
                line = line.replace("%owner_name%", Bukkit.getOfflinePlayer(shop.getOwnerId()).getName() != null ? Bukkit.getOfflinePlayer(shop.getOwnerId()).getName() : "Bilinmeyen")
                           .replace("%mode%", modeText)
                           .replace("%stock%", String.valueOf(stock))
                           .replace("%price%", plugin.getEconomyManager().formatPrice(shop.getPrice()))
                           .replace("%money_type%", plugin.getConfig().getString("money-type", "TL"))
                           .replace("%item_name%", getFormattedItemName(shop.getItem()))
                           .replace("%status_color%", statusColor);
                sign.getSide(org.bukkit.block.sign.Side.FRONT).line(i, mm.deserialize(line));
            }
            sign.update();
        }
    }

    private String getFormattedItemName(ItemStack item) {
        String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().getDisplayName()
                : item.getType().name().replace("_", " ").toLowerCase();
        
        if (itemName.length() > 15) {
            itemName = itemName.substring(0, 12) + "...";
        }
        return itemName;
    }

    private Block findSignBlock(Block chestBlock) {
        if (chestBlock.getBlockData() instanceof org.bukkit.block.data.type.Chest chestData) {
            return chestBlock.getRelative(chestData.getFacing());
        }
        return null;
    }

    public int getStock(Shop shop) {
        if (!(shop.getLocation().getBlock().getState() instanceof Chest chest)) return 0;
        int count = 0;
        for (ItemStack i : chest.getInventory().getStorageContents()) {
            if (i != null && i.isSimilar(shop.getItem())) count += i.getAmount();
        }
        return count;
    }

    public boolean hasSpace(Shop shop) {
        if (!(shop.getLocation().getBlock().getState() instanceof Chest chest)) return false;
        Inventory inv = chest.getInventory();
        if (inv.firstEmpty() != -1) return true;
        for (ItemStack i : inv.getStorageContents()) {
            if (i.isSimilar(shop.getItem()) && i.getAmount() < i.getMaxStackSize()) return true;
        }
        return false;
    }

    public Shop getShop(Location loc) {
        return shopCache.get(loc);
    }

    public Shop getShopById(int id) {
        return shopCache.values().stream().filter(s -> s.getId() == id).findFirst().orElse(null);
    }

    public Shop getShopByComponent(Location location) {
        Block block = location.getBlock();
        // 1. Direct check
        Shop shop = getShop(location);
        if (shop != null) return shop;

        // 2. Double chest check
        if (block.getState() instanceof Chest chest) {
            Inventory inv = chest.getInventory();
            if (inv instanceof org.bukkit.inventory.DoubleChestInventory doubleInv) {
                Location leftLoc = doubleInv.getLeftSide().getLocation();
                Location rightLoc = doubleInv.getRightSide().getLocation();
                Shop leftShop = getShop(leftLoc);
                if (leftShop != null) return leftShop;
                Shop rightShop = getShop(rightLoc);
                if (rightShop != null) return rightShop;
            }
        }

        // 3. Sign check
        if (block.getState() instanceof Sign) {
            if(block.getBlockData() instanceof Directional) {
                Block attachedBlock = block.getRelative(((Directional) block.getBlockData()).getFacing().getOppositeFace());
                return getShopByComponent(attachedBlock.getLocation());
            }
        }
        
        return null;
    }

    public void loadShops() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            var shops = plugin.getDatabaseManager().loadAllShops();
            Bukkit.getScheduler().runTask(plugin, () -> {
                shops.forEach(s -> {
                    try {
                        s.getMembers().putAll(plugin.getDatabaseManager().getMembers(s.getId()));
                        s.getBannedPlayers().putAll(plugin.getDatabaseManager().getBannedPlayers(s.getId()));
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    shopCache.put(s.getLocation(), s);
                    if (s.getLocation().getChunk().isLoaded()) {
                        plugin.getDisplayManager().updateDisplay(s);
                        updateShopSign(s);
                    }
                });
            });
        });
    }
}