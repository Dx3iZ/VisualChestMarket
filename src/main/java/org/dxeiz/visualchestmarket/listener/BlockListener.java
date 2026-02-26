package org.dxeiz.visualchestmarket.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.dxeiz.visualchestmarket.VisualChestMarket;
import org.dxeiz.visualchestmarket.model.Shop;
import org.dxeiz.visualchestmarket.model.ShopMode;

import java.util.Iterator;

public class BlockListener implements Listener {
    private final VisualChestMarket plugin;

    public BlockListener(VisualChestMarket plugin) {
        this.plugin = plugin;
    }

    // --- KORUMA VE GÜVENLİK ---

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();

        if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
            for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                Block relative = block.getRelative(face);

                if (relative.getType() == block.getType()) {
                    // Sadece konumu değil, sandık mantığını kontrol et
                    Shop shop = findShopFromChest(relative);

                    if (shop != null) {
                        // Market sahibi değilse birleşmeyi (yerleştirmeyi) engelle
                        if (!shop.getOwnerId().equals(event.getPlayer().getUniqueId())) {
                            event.setCancelled(true);
                            event.getPlayer().sendMessage(plugin.getConfigManager().getMessage("messages.double-chest-not-allowed"));
                            return;
                        }
                    }
                }
            }
        }
    }

    private Shop findShopFromChest(Block block) {
        if (!(block.getState() instanceof Chest chest)) return null;

        // 1. Mevcut bloğu kontrol et
        Shop shop = plugin.getShopManager().getShop(block.getLocation());
        if (shop != null) return shop;

        // 2. Çift sandık mı kontrol et ve diğer yarısına bak
        Inventory inv = chest.getInventory();
        if (inv instanceof org.bukkit.inventory.DoubleChestInventory doubleInv) {
            Location leftLoc = doubleInv.getLeftSide().getLocation();
            Location rightLoc = doubleInv.getRightSide().getLocation();

            // Hangisi mevcut blok değilse onu kontrol et
            Location otherSide = leftLoc.equals(block.getLocation()) ? rightLoc : leftLoc;
            return plugin.getShopManager().getShop(otherSide);
        }

        return null;
    }

    // Patlama korumasına tabelaları da ekliyoruz
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isShopPart);
    }

    // Blok Kırılma Koruması
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        Shop shop = plugin.getShopManager().getShopByComponent(block.getLocation());

        if (shop == null) return;

        // Market sahibi olsa bile kırmayı engelle
        // Ancak double chest'in eklenen kısmını kırmasına izin ver
        if (shop.getOwnerId().equals(player.getUniqueId())) {
            // Eğer kırılan blok ana market sandığı ise engelle
            if (shop.getLocation().getBlock().equals(block)) {
                event.setCancelled(true);
                player.sendMessage(plugin.getConfigManager().getMessage("messages.market-chest"));
            }
            // Diğer parçaları (double chest yarısı, tabela) normal kırabilir
            return;
        }

        // Market sahibi değilse her türlü engelle
        event.setCancelled(true);
        player.sendMessage(plugin.getConfigManager().getMessage("messages.break-not-owner"));
    }

    // YARDIMCI METOT: Bloğun bir markete ait olup olmadığını kesin olarak doğrular
    private boolean isShopPart(Block block) {
        if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
            return findShopFromBlock(block) != null;
        }

        // 1. Direkt konumu kontrol et
        if (plugin.getShopManager().getShop(block.getLocation()) != null) return true;

        // 2. DoubleChest kontrolü (Diğer yarıda market var mı?)
        if (block.getState() instanceof org.bukkit.block.Chest chest) {
            org.bukkit.inventory.Inventory inv = chest.getInventory();
            if (inv instanceof org.bukkit.inventory.DoubleChestInventory dInv) {
                Location left = dInv.getLeftSide().getLocation();
                Location right = dInv.getRightSide().getLocation();

                // Eğer diğer tarafta bir market varsa, bu blok da marketin parçasıdır
                return plugin.getShopManager().getShop(left) != null ||
                        plugin.getShopManager().getShop(right) != null;
            }
        }

        // Tabela kontrolü
        if (block.getType().name().contains("SIGN")) {
            if (block.getBlockData() instanceof Directional dir) {
                Block attached = block.getRelative(dir.getFacing().getOppositeFace());
                return findShopFromBlock(attached) != null;
            }
        }
        return false;
    }

    private Shop findShopFromBlock(Block block) {
        if (block.getState() instanceof Chest chest) {
            Shop shop = plugin.getShopManager().getShop(block.getLocation());
            if (shop != null) return shop;

            if (chest.getInventory() instanceof DoubleChestInventory dInv) {
                Shop sLeft = plugin.getShopManager().getShop(dInv.getLeftSide().getLocation());
                if (sLeft != null) return sLeft;
                return plugin.getShopManager().getShop(dInv.getRightSide().getLocation());
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            if (plugin.getShopManager().getShop(b.getLocation()) != null || isShopSign(b)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if (plugin.getShopManager().getShop(b.getLocation()) != null || isShopSign(b)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (plugin.getShopManager().getShop(event.getBlock().getLocation()) != null || isShopSign(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        if (isShopSign(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    // --- İŞLEM MANTIĞI ---

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Chest chest) {
            // Doğrudan getShop yerine findShopFromChest kullan
            Shop shop = findShopFromChest(chest.getBlock());
            if (shop != null) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.getShopManager().updateShopSign(shop), 2L);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder() instanceof Chest chest) {
            Shop shop = plugin.getShopManager().getShopByComponent(chest.getLocation());
            if (shop != null) {
                Player player = (Player) event.getPlayer();
                // Market sahibi veya admin değilse engelle
                if (!shop.getOwnerId().equals(player.getUniqueId()) && !player.hasPermission("vcm.admin")) {
                    event.setCancelled(true);
                    player.sendMessage(plugin.getConfigManager().getMessage("messages.market-chest"));
                }
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Block block = event.getClickedBlock();

        if (block.getType().name().contains("SIGN")) {
            Shop shop = findShopBySign(block);
            if (shop == null) return;

            event.setCancelled(true);
            Player player = event.getPlayer();

            if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                // Eğer oyuncu eğiliyorsa hiçbir şey yapma (Panel açılmasın)
                if (player.isSneaking()) return;

                player.sendMessage(plugin.getConfigManager().getMessage("messages.own-market", "%owner_name%", Bukkit.getOfflinePlayer(shop.getOwnerId()).getName()));
                return;
            }

            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                // Ban Kontrolü
                if (shop.isBanned(player.getUniqueId())) {
                    String reasonData = shop.getBanReason(player.getUniqueId());
                    String reason = reasonData.split("\\|")[0];
                    player.sendMessage(plugin.getConfigManager().getMessage("messages.player-is-banned", "%reason%", reason));
                    return;
                }

                sendDetailedPreview(player, shop);
                player.sendMessage(plugin.getConfigManager().getMessage("messages.proccess-market"));

                plugin.getInputManager().awaitInput(player.getUniqueId(), (input) -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        executeTransaction(player, shop, input);
                    });
                });
            }
        }
    }

    private void sendDetailedPreview(Player player, Shop shop) {
        ItemStack item = shop.getItem();
        Chest chest = (Chest) shop.getLocation().getBlock().getState();
        String moneyType = String.valueOf(plugin.getConfig().getString("money-type"));

        Component separator = Component.text("                                                                        ", NamedTextColor.DARK_GRAY, TextDecoration.STRIKETHROUGH);
        Component header = MiniMessage.miniMessage().deserialize("<gradient:#ffaa00:#ff5500><bold> ✦ MARKET DETAYLARI ✦ </bold></gradient>");

        Component itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().displayName()
                : Component.translatable(item.getType().translationKey());

        Component viewItem = MiniMessage.miniMessage().deserialize("<gradient:#99D98C:#B5E48C><bold> [Öğeyi Görüntüle] </bold></gradient>")
                .hoverEvent(item.asHoverEvent());

        player.sendMessage(Component.newline());
        player.sendMessage(separator);
        player.sendMessage(header);
        player.sendMessage(Component.newline());

        player.sendMessage(MiniMessage.miniMessage().deserialize("<#35E95F> \uD83D\uDCE6 Ürün: </#35E95F>")
                .append(itemName.color(NamedTextColor.WHITE))
                .append(Component.space())
                .append(viewItem));

        player.sendMessage(MiniMessage.miniMessage().deserialize("<#35E95F> \uD83D\uDCB0 Birim Fiyat:</#35E95F>")
                .append(MiniMessage.miniMessage().deserialize("<gray> "+plugin.getEconomyManager().formatPrice(shop.getPrice()) + " " + moneyType+"</gray>")));

        int maxAmount = 0;
        String actionText = "";

        if (shop.getMode() == ShopMode.SELL) {
            int stock = plugin.getShopManager().getStock(shop);
            double playerMoney = plugin.getEconomy().getBalance(player);

            int maxByMoney = (int) (playerMoney / shop.getPrice());
            int maxBySpace = getSpaceForItem(player.getInventory(), item);

            maxAmount = Math.min(maxByMoney, Math.min(stock, maxBySpace));
            actionText = "alabilirsiniz";

            player.sendMessage(MiniMessage.miniMessage().deserialize("<#35E95F> \uD83D\uDEE0 Durum: </#35E95F>").append(MiniMessage.miniMessage().deserialize("<gray>SATIŞ (Siz Alıyorsunuz)</gray>")));
            player.sendMessage(MiniMessage.miniMessage().deserialize("<#35E95F> ⛟ Stok: </#35E95F>").append(MiniMessage.miniMessage().deserialize("<gray>"+stock + " adet</gray>")));

        } else {
            double ownerMoney = plugin.getEconomy().getBalance(Bukkit.getOfflinePlayer(shop.getOwnerId()));

            int maxByOwnerMoney = (int) (ownerMoney / shop.getPrice());
            int maxByPlayerItem = getPlayerItemCount(player.getInventory(), item);
            int maxByChestSpace = getSpaceForItem(chest.getInventory(), item);

            maxAmount = Math.min(maxByOwnerMoney, Math.min(maxByPlayerItem, maxByChestSpace));
            actionText = "satabilirsiniz";

            player.sendMessage(MiniMessage.miniMessage().deserialize("<#35E95F> \uD83D\uDEE0 Durum: </#35E95F>").append(MiniMessage.miniMessage().deserialize("<gray>ALIŞ (Siz Satıyorsunuz)</gray>")));
        }

        if (maxAmount > 0) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<#35E95F> ⁂ Limit: </#35E95F>")
                    .append(Component.text("En fazla ", NamedTextColor.YELLOW))
                    .append(Component.text(maxAmount + " adet ", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text("daha " + actionText + ".", NamedTextColor.YELLOW)));
        } else {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<#35E95F> ⁂ Limit: </#35E95F>")
                    .append(Component.text("Şu an işlem yapamazsınız.", NamedTextColor.RED)));
        }

        player.sendMessage(Component.newline());
        player.sendMessage(separator);
        player.sendMessage(Component.newline());
    }

    private void executeTransaction(Player player, Shop shop, String input) {
        Block shopBlock = shop.getLocation().getBlock();
        if (!(shopBlock.getState() instanceof Chest chest)) return;

        int currentStock = plugin.getShopManager().getStock(shop);
        int amount;

        try {
            if (input.equalsIgnoreCase("all")) {

                if (shop.getMode() == ShopMode.SELL) {
                    // Oyuncu marketten alıyor
                    double playerMoney = plugin.getEconomy().getBalance(player);
                    int maxByMoney = (int) (playerMoney / shop.getPrice());
                    int maxBySpace = getSpaceForItem(player.getInventory(), shop.getItem());

                    amount = Math.min(maxByMoney, Math.min(currentStock, maxBySpace));

                    if (amount <= 0) {
                        if (currentStock <= 0) {
                            player.sendMessage(plugin.getConfigManager().getMessage("messages.not-length", "%current_stock%", String.valueOf(currentStock)));
                        } else if (maxByMoney <= 0) {
                            player.sendMessage(plugin.getConfigManager().getMessage("messages.insufficient-funds"));
                        } else {
                            player.sendMessage(plugin.getConfigManager().getMessage("messages.full-inventory"));
                        }
                        return;
                    }

                } else {
                    // Oyuncu markeye satıyor
                    amount = getItemAmount(player, shop.getItem());

                    if (amount <= 0) {
                        player.sendMessage(plugin.getConfigManager().getMessage("messages.not-have-items"));
                        return;
                    }
                }

            } else {
                amount = Integer.parseInt(input);
            }

            if (amount <= 0) {
                player.sendMessage(plugin.getConfigManager().getMessage("messages.invalid-amount"));
                return;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getConfigManager().getMessage("messages.cancel"));
            return;
        }

        double totalPrice = shop.getPrice() * amount;
        double tax = plugin.getEconomyManager().calculateTax(totalPrice);
        double finalPrice = totalPrice - tax;
        String formattedPrice = plugin.getEconomyManager().formatPrice(totalPrice);
        String formattedTax = plugin.getEconomyManager().formatPrice(tax);
        String formattedFinalPrice = plugin.getEconomyManager().formatPrice(finalPrice);
        String moneyType = plugin.getConfig().getString("money-type");

        ItemStack item = shop.getItem();
        Component itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().displayName()
                : Component.translatable(item.getType().translationKey());
        String coloredItemName = PlainTextComponentSerializer.plainText().serialize(
                itemName.color(TextColor.fromHexString("#F5ECFE"))
        );

        if (shop.getMode() == ShopMode.SELL) {
            if (currentStock < amount) {
                player.sendMessage(plugin.getConfigManager().getMessage("messages.not-length", "%current_stock%", String.valueOf(currentStock)));
                return;
            }
            if (!plugin.getEconomyManager().hasEnough(player.getUniqueId(), totalPrice)) {
                player.sendMessage(plugin.getConfigManager().getMessage("messages.dont-have-money", "%total_price%", formattedPrice, "%money_type%", moneyType));
                return;
            }
            if (!hasSpace(player.getInventory(), shop.getItem(), amount)) {
                player.sendMessage(plugin.getConfigManager().getMessage("messages.full-inventory"));
                return;
            }

            if (plugin.getEconomyManager().withdraw(player.getUniqueId(), totalPrice)) {
                plugin.getEconomyManager().deposit(shop.getOwnerId(), finalPrice);
                removeItemsFromChest(chest, shop.getItem(), amount);
                giveItemsToPlayer(player, shop.getItem(), amount);
                
                player.sendMessage(plugin.getConfigManager().getMessage("messages.buy-success", 
                        "%item%", coloredItemName, 
                        "%amount%", String.valueOf(amount), 
                        "%price%", formattedPrice, 
                        "%money-type%", moneyType));
                
                if (tax > 0) {
                    player.sendMessage(plugin.getConfigManager().getMessage("messages.tax-info", "%tax%", formattedTax, "%money-type%", moneyType));
                }
            }

        } else if (shop.getMode() == ShopMode.BUY) {
            int playerStock = getItemAmount(player, shop.getItem());
            if (playerStock < amount) {
                player.sendMessage(plugin.getConfigManager().getMessage("messages.not-have-items"));
                return;
            }
            if (!plugin.getEconomyManager().hasEnough(shop.getOwnerId(), totalPrice)) {
                player.sendMessage(plugin.getConfigManager().getMessage("messages.owner-not-money"));
                return;
            }
            if (!hasSpace(chest.getInventory(), shop.getItem(), amount)) {
                player.sendMessage(plugin.getConfigManager().getMessage("messages.shop-full", "%max_product%", String.valueOf(getSpaceForItem(chest.getInventory(), shop.getItem()))));
                return;
            }

            if (plugin.getEconomyManager().withdraw(shop.getOwnerId(), totalPrice)) {
                plugin.getEconomyManager().deposit(player.getUniqueId(), finalPrice);
                removeItemsFromPlayer(player, shop.getItem(), amount);
                addItemsToChest(chest, shop.getItem(), amount);
                
                player.sendMessage(plugin.getConfigManager().getMessage("messages.sell-success", 
                        "%item%", coloredItemName, 
                        "%amount%", String.valueOf(amount), 
                        "%price%", formattedPrice, 
                        "%money-type%", moneyType));
                
                if (tax > 0) {
                    player.sendMessage(plugin.getConfigManager().getMessage("messages.tax-info", "%tax%", formattedTax, "%money-type%", moneyType));
                }
            }
        }

        plugin.getShopManager().updateShopSign(shop);
        plugin.getTransactionManager().logTransaction(shop, player, amount, totalPrice, shop.getMode());

        // Market sahibine bildirim gönder
        Player owner = Bukkit.getPlayer(shop.getOwnerId());
        if (owner != null && owner.isOnline()) {
            String notificationKey = (shop.getMode() == ShopMode.SELL) ? "messages.owner-notification-sold" : "messages.owner-notification-bought";
            owner.sendMessage(plugin.getConfigManager().getMessage(notificationKey,
                    "%player%", player.getName(),
                    "%amount%", String.valueOf(amount),
                    "%item%", coloredItemName,
                    "%price%", formattedFinalPrice,
                    "%money-type%", moneyType
            ));
        }
    }

    private void removeItemsFromChest(Chest chest, ItemStack item, int amount) {
        ItemStack toRemove = item.clone();
        toRemove.setAmount(amount);
        chest.getInventory().removeItem(toRemove);
    }

    private void addItemsToChest(Chest chest, ItemStack item, int amount) {
        ItemStack toAdd = item.clone();
        toAdd.setAmount(amount);
        chest.getInventory().addItem(toAdd);
    }

    private int getItemAmount(Player player, ItemStack item) {
        int count = 0;
        for (ItemStack i : player.getInventory().getContents()) {
            if (i != null && i.isSimilar(item)) count += i.getAmount();
        }
        return count;
    }

    private void giveItemsToPlayer(Player player, ItemStack item, int amount) {
        ItemStack toGive = item.clone();
        toGive.setAmount(amount);
        player.getInventory().addItem(toGive).values().forEach(remaining ->
                player.getWorld().dropItemNaturally(player.getLocation(), remaining));
    }

    private void removeItemsFromPlayer(Player player, ItemStack item, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack is = contents[i];
            if (is != null && is.isSimilar(item)) {
                if (is.getAmount() > remaining) {
                    is.setAmount(is.getAmount() - remaining);
                    remaining = 0;
                    break;
                } else {
                    remaining -= is.getAmount();
                    player.getInventory().setItem(i, null);
                }
            }
            if (remaining <= 0) break;
        }
    }

    private int getSpaceForItem(Inventory inventory, ItemStack item) {
        int space = 0;
        int maxStack = item.getMaxStackSize();
        for (ItemStack i : inventory.getStorageContents()) {
            if (i == null || i.getType() == Material.AIR) {
                space += maxStack;
            } else if (i.isSimilar(item)) {
                space += Math.max(0, maxStack - i.getAmount());
            }
        }
        return space;
    }

    private int getPlayerItemCount(Inventory inventory, ItemStack item) {
        int count = 0;
        for (ItemStack i : inventory.getStorageContents()) {
            if (i != null && i.isSimilar(item)) count += i.getAmount();
        }
        return count;
    }

    private boolean hasSpace(Inventory inventory, ItemStack item, int amount) {
        return getSpaceForItem(inventory, item) >= amount;
    }

    private boolean isShopSign(Block block) {
        return findShopBySign(block) != null;
    }

    private Shop findShopBySign(Block signBlock) {
        if (signBlock.getBlockData() instanceof Directional dir) {
            Block attachedBlock = signBlock.getRelative(dir.getFacing().getOppositeFace());
            // Tabelanın bağlı olduğu blok çift sandıksa, diğer yarıdaki marketi de bulur
            return findShopFromChest(attachedBlock);
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMascotDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.getEntity() instanceof ArmorStand stand) {
            if (stand.getPersistentDataContainer().has(new NamespacedKey(plugin, "vcm_entity"), PersistentDataType.STRING)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onMascotInteract(org.bukkit.event.player.PlayerInteractAtEntityEvent event) {
        if (event.getRightClicked() instanceof ArmorStand stand) {
            if (stand.getPersistentDataContainer().has(new NamespacedKey(plugin, "vcm_entity"), PersistentDataType.STRING)) {
                event.setCancelled(true);
            }
        }
    }
}