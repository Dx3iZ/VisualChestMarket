package org.dxeiz.visualchestmarket.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.StringUtil;
import org.dxeiz.visualchestmarket.VisualChestMarket;
import org.dxeiz.visualchestmarket.gui.BanListGui;
import org.dxeiz.visualchestmarket.gui.HistoryGui;
import org.dxeiz.visualchestmarket.gui.MembersGui;
import org.dxeiz.visualchestmarket.model.DisplayMode;
import org.dxeiz.visualchestmarket.model.Shop;
import org.dxeiz.visualchestmarket.model.ShopMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MarketCommand implements CommandExecutor, TabCompleter {

    private final VisualChestMarket plugin;
    private final Map<UUID, Integer> pendingDeletions = new ConcurrentHashMap<>();

    public MarketCommand(VisualChestMarket plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.equals("reload")) {
            if (!sender.hasPermission("vcm.admin")) {
                sender.sendMessage(plugin.getConfigManager().getMessage("messages.no-permission"));
                return true;
            }
            plugin.reload();
            sender.sendMessage(plugin.getConfigManager().getMessage("messages.reload-success"));
            return true;
        }

        if (subCommand.equals("transferall")) {
            if (args.length < 2) {
                sender.sendMessage(Component.text("Kullanım: /vcm transferall <yeni_sahip> [eski_sahip]", NamedTextColor.RED));
                return true;
            }

            final OfflinePlayer oldOwner;
            final OfflinePlayer newOwner;

            if (args.length == 2) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("messages.only-players"));
                    return true;
                }
                oldOwner = player;
                newOwner = Bukkit.getOfflinePlayer(args[1]);
            } else {
                if (!sender.hasPermission("vcm.admin")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("messages.no-permission"));
                    return true;
                }
                oldOwner = Bukkit.getOfflinePlayer(args[1]);
                newOwner = Bukkit.getOfflinePlayer(args[2]);
            }

            plugin.getShopManager().transferAllShops(oldOwner.getUniqueId(), newOwner.getUniqueId(), (count) -> {
                sender.sendMessage(plugin.getConfigManager().getMessage("messages.transfer-all-success",
                        "%old_owner%", oldOwner.getName() != null ? oldOwner.getName() : "Bilinmeyen",
                        "%new_owner%", newOwner.getName() != null ? newOwner.getName() : "Bilinmeyen",
                        "%count%", String.valueOf(count)));
            });
            return true;
        }

        if (subCommand.equals("help")) {
            sendHelp(sender);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("messages.only-players"));
            return true;
        }

        if (subCommand.equals("confirmdelete")) {
            Integer shopId = pendingDeletions.remove(player.getUniqueId());
            if (shopId == null) {
                player.sendMessage(plugin.getConfigManager().getMessage("messages.no-delete-pending"));
                return true;
            }
            Shop shop = plugin.getShopManager().getShopById(shopId);
            if (shop != null) {
                plugin.getShopManager().deleteShop(shop);
                player.sendMessage(plugin.getConfigManager().getMessage("messages.shop-deleted"));
            } else {
                player.sendMessage(plugin.getConfigManager().getMessage("messages.shop-not-found"));
            }
            return true;
        }

        if (subCommand.equals("canceldelete")) {
            if (pendingDeletions.remove(player.getUniqueId()) != null) {
                player.sendMessage(plugin.getConfigManager().getMessage("messages.delete-cancelled"));
            } else {
                player.sendMessage(plugin.getConfigManager().getMessage("messages.no-delete-pending"));
            }
            return true;
        }
        
        Shop shop = getTargetedShop(player);
        if (shop == null) return true;

        if (!shop.getOwnerId().equals(player.getUniqueId()) && !player.hasPermission("vcm.admin") && !shop.isMember(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage("messages.not-owner"));
            return true;
        }

        final Shop finalShop = shop;

        switch (subCommand) {
            case "menu":
                plugin.getShopManager().sendShopMenu(player, finalShop);
                break;
            case "setprice":
                if (hasPermission(player, finalShop, "setprice")) {
                    player.sendMessage(plugin.getConfigManager().getMessage("messages.price-update-prompt"));
                    plugin.getInputManager().awaitInput(player.getUniqueId(), str -> {
                        try {
                            double val = Double.parseDouble(str);
                            if (val <= 0) throw new NumberFormatException();
                            
                            finalShop.setPrice(val);
                            plugin.getShopManager().updateShopSettings(finalShop);
                            player.sendMessage(plugin.getConfigManager().getMessage("messages.price-updated", "%price%", plugin.getEconomyManager().formatPrice(val), "%money-type%", String.valueOf(plugin.getConfig().getString("money-type"))));
                        } catch (NumberFormatException e) {
                            player.sendMessage(plugin.getConfigManager().getMessage("messages.invalid-price"));
                        }
                    });
                } else {
                    player.sendMessage(plugin.getConfigManager().getMessage("messages.no-permission"));
                }
                break;
            case "togglemode":
                if (hasPermission(player, finalShop, "togglemode")) {
                    ShopMode newMode = (finalShop.getMode() == ShopMode.SELL) ? ShopMode.BUY : ShopMode.SELL;
                    finalShop.setMode(newMode);
                    plugin.getShopManager().updateShopSettings(finalShop);
                    player.sendMessage(plugin.getConfigManager().getMessage("messages.mode-changed", "%mode%", plugin.getConfigManager().getRawString("messages.shop-mode-" + newMode.name().toLowerCase())));
                } else {
                    player.sendMessage(plugin.getConfigManager().getMessage("messages.no-permission"));
                }
                break;
            case "toggledisplay":
                if (hasPermission(player, finalShop, "toggledisplay")) {
                    DisplayMode currentDisplay = finalShop.getDisplayMode();
                    DisplayMode nextDisplay;

                    if (currentDisplay == DisplayMode.ARMORSTAND) {
                        nextDisplay = DisplayMode.HOLOGRAM;
                    } else if (currentDisplay == DisplayMode.HOLOGRAM) {
                        nextDisplay = DisplayMode.NONE;
                    } else { // NONE ise
                        nextDisplay = DisplayMode.ARMORSTAND;
                    }
                    finalShop.setDisplayMode(nextDisplay);
                    plugin.getShopManager().updateShopSettings(finalShop);
                    player.sendMessage(plugin.getConfigManager().getMessage("messages.display-changed", "%mode%", plugin.getConfigManager().getRawString("display-mode-names." + nextDisplay.name())));
                } else {
                    player.sendMessage(plugin.getConfigManager().getMessage("messages.no-permission"));
                }
                break;
            case "history":
                if (hasPermission(player, finalShop, "history")) {
                    HistoryGui gui = new HistoryGui(plugin, finalShop);
                    player.openInventory(gui.getInventory());
                } else {
                    player.sendMessage(plugin.getConfigManager().getMessage("messages.no-permission"));
                }
                break;
            case "delete":
                if (shop.getOwnerId().equals(player.getUniqueId()) || player.hasPermission("vcm.admin")) {
                    pendingDeletions.put(player.getUniqueId(), finalShop.getId());
                    player.sendMessage(plugin.getConfigManager().getMessage("messages.delete-confirm"));
                } else {
                    player.sendMessage(plugin.getConfigManager().getMessage("messages.no-permission"));
                }
                break;
            case "transfer":
                if (shop.getOwnerId().equals(player.getUniqueId()) || player.hasPermission("vcm.admin")) {
                    if (args.length < 2) {
                        player.sendMessage(Component.text("Kullanım: /vcm transfer <yeni_sahip>", NamedTextColor.RED));
                        return true;
                    }
                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                    plugin.getShopManager().transferShop(finalShop, target.getUniqueId());
                    player.sendMessage(plugin.getConfigManager().getMessage("messages.transfer-success", "%player%", target.getName() != null ? target.getName() : "Bilinmeyen"));
                } else {
                    player.sendMessage(plugin.getConfigManager().getMessage("messages.no-permission"));
                }
                break;
            case "addmember":
                if (shop.getOwnerId().equals(player.getUniqueId()) || player.hasPermission("vcm.admin")) {
                    if (args.length < 3) {
                        player.sendMessage(Component.text("Kullanım: /vcm addmember <oyuncu> <rol>", NamedTextColor.RED));
                        return true;
                    }
                    
                    // Üye Limiti Kontrolü
                    int maxMembers = plugin.getConfig().getInt("max-members-per-shop", 5);
                    if (finalShop.getMembers().size() >= maxMembers) {
                        player.sendMessage(plugin.getConfigManager().getMessage("messages.member-limit-reached", "%limit%", String.valueOf(maxMembers)));
                        return true;
                    }

                    OfflinePlayer member = Bukkit.getOfflinePlayer(args[1]);
                    String role = args[2].toUpperCase();
                    if (!role.equals("MANAGER") && !role.equals("HELPER")) {
                        player.sendMessage(plugin.getConfigManager().getMessage("messages.invalid-role"));
                        return true;
                    }
                    plugin.getShopManager().addMember(finalShop, member.getUniqueId(), role);
                    player.sendMessage(plugin.getConfigManager().getMessage("messages.member-added", "%player%", member.getName() != null ? member.getName() : "Bilinmeyen", "%role%", role));
                } else {
                    player.sendMessage(plugin.getConfigManager().getMessage("messages.no-permission"));
                }
                break;
            case "removemember":
                if (shop.getOwnerId().equals(player.getUniqueId()) || player.hasPermission("vcm.admin")) {
                    if (args.length < 2) {
                        player.sendMessage(Component.text("Kullanım: /vcm removemember <oyuncu>", NamedTextColor.RED));
                        return true;
                    }
                    OfflinePlayer memberToRemove = Bukkit.getOfflinePlayer(args[1]);
                    plugin.getShopManager().removeMember(finalShop, memberToRemove.getUniqueId());
                    player.sendMessage(plugin.getConfigManager().getMessage("messages.member-removed", "%player%", memberToRemove.getName() != null ? memberToRemove.getName() : "Bilinmeyen"));
                } else {
                    player.sendMessage(plugin.getConfigManager().getMessage("messages.no-permission"));
                }
                break;
            case "members":
                if (shop.getOwnerId().equals(player.getUniqueId()) || player.hasPermission("vcm.admin")) {
                    MembersGui gui = new MembersGui(plugin, finalShop);
                    player.openInventory(gui.getInventory());
                } else {
                    player.sendMessage(plugin.getConfigManager().getMessage("messages.no-permission"));
                }
                break;
            case "ban":
                if (shop.getOwnerId().equals(player.getUniqueId()) || player.hasPermission("vcm.admin")) {
                    if (args.length < 2) {
                        player.sendMessage(Component.text("Kullanım: /vcm ban <oyuncu> [sebep]", NamedTextColor.RED));
                        return true;
                    }
                    OfflinePlayer bannedPlayer = Bukkit.getOfflinePlayer(args[1]);
                    
                    // Kendini banlamayı engelle
                    if (bannedPlayer.getUniqueId().equals(player.getUniqueId())) {
                        player.sendMessage(Component.text("Kendinizi yasaklayamazsınız!", NamedTextColor.RED));
                        return true;
                    }

                    String reason = "Sebep belirtilmedi";
                    if (args.length > 2) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 2; i < args.length; i++) {
                            sb.append(args[i]).append(" ");
                        }
                        reason = sb.toString().trim();
                    }
                    
                    plugin.getShopManager().banPlayer(finalShop, bannedPlayer.getUniqueId(), reason);
                    player.sendMessage(plugin.getConfigManager().getMessage("messages.player-banned", "%player%", bannedPlayer.getName() != null ? bannedPlayer.getName() : "Bilinmeyen"));
                } else {
                    player.sendMessage(plugin.getConfigManager().getMessage("messages.no-permission"));
                }
                break;
            case "unban":
                if (shop.getOwnerId().equals(player.getUniqueId()) || player.hasPermission("vcm.admin")) {
                    if (args.length < 2) {
                        player.sendMessage(Component.text("Kullanım: /vcm unban <oyuncu>", NamedTextColor.RED));
                        return true;
                    }
                    OfflinePlayer unbannedPlayer = Bukkit.getOfflinePlayer(args[1]);
                    plugin.getShopManager().unbanPlayer(finalShop, unbannedPlayer.getUniqueId());
                    player.sendMessage(plugin.getConfigManager().getMessage("messages.player-unbanned", "%player%", unbannedPlayer.getName() != null ? unbannedPlayer.getName() : "Bilinmeyen"));
                } else {
                    player.sendMessage(plugin.getConfigManager().getMessage("messages.no-permission"));
                }
                break;
            case "banlist":
                if (shop.getOwnerId().equals(player.getUniqueId()) || player.hasPermission("vcm.admin")) {
                    BanListGui gui = new BanListGui(plugin, finalShop);
                    player.openInventory(gui.getInventory());
                } else {
                    player.sendMessage(plugin.getConfigManager().getMessage("messages.no-permission"));
                }
                break;
            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    private boolean hasPermission(Player player, Shop shop, String action) {
        if (shop.getOwnerId().equals(player.getUniqueId()) || player.hasPermission("vcm.admin")) {
            return true;
        }
        String role = shop.getMemberRole(player.getUniqueId());
        if (role == null) return false;

        if (role.equals("MANAGER")) {
            return true; // Manager her şeyi yapabilir (silme ve transfer hariç)
        }
        if (role.equals("HELPER")) {
            return action.equals("setprice") || action.equals("togglemode") || action.equals("history");
        }
        return false;
    }

    private Shop getTargetedShop(Player player) {
        RayTraceResult result = player.rayTraceBlocks(5.0);
        Block targetBlock = (result != null) ? result.getHitBlock() : null;

        if (targetBlock == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("messages.look-at-shop"));
            return null;
        }
        Shop shop = plugin.getShopManager().getShopByComponent(targetBlock.getLocation());
        if (shop == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("messages.shop-not-found"));
            return null;
        }
        return shop;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<gradient:#ffaa00:#ff5500><bold> VISUAL CHEST MARKET </bold></gradient> <dark_gray>- v1.0.0"));
        sender.sendMessage(Component.text(" "));
        
        sender.sendMessage(formatHelp("/vcm menu", "Baktığınız marketin menüsünü açar."));
        sender.sendMessage(formatHelp("/vcm history", "Baktığınız marketin geçmişini görüntüler."));
        sender.sendMessage(formatHelp("/vcm setprice", "Baktığınız marketin fiyatını günceller."));
        sender.sendMessage(formatHelp("/vcm togglemode", "Baktığınız marketin modunu değiştirir."));
        sender.sendMessage(formatHelp("/vcm toggledisplay", "Baktığınız marketin görünümünü değiştirir."));
        sender.sendMessage(formatHelp("/vcm delete", "Baktığınız marketi silme işlemini başlatır."));
        sender.sendMessage(formatHelp("/vcm transfer <oyuncu>", "Baktığınız marketi devreder."));
        sender.sendMessage(formatHelp("/vcm transferall <yeni_sahip>", "Tüm marketlerinizi devreder."));
        sender.sendMessage(formatHelp("/vcm addmember <oyuncu> <rol>", "Markete üye ekler."));
        sender.sendMessage(formatHelp("/vcm removemember <oyuncu>", "Marketten üye çıkarır."));
        sender.sendMessage(formatHelp("/vcm members", "Market üyelerini yönetir."));
        sender.sendMessage(formatHelp("/vcm ban <oyuncu> [sebep]", "Oyuncuyu marketten yasaklar."));
        sender.sendMessage(formatHelp("/vcm unban <oyuncu>", "Oyuncunun yasağını kaldırır."));
        sender.sendMessage(formatHelp("/vcm banlist", "Yasaklı oyuncuları listeler."));
        sender.sendMessage(formatHelp("/vcm confirmdelete", "Bekleyen silme işlemini onaylar."));
        sender.sendMessage(formatHelp("/vcm canceldelete", "Bekleyen silme işlemini iptal eder."));

        if (sender.hasPermission("vcm.admin")) {
            sender.sendMessage(Component.text(" "));
            sender.sendMessage(Component.text(" Yönetici Komutları:", NamedTextColor.RED));
            sender.sendMessage(formatHelp("/vcm reload", "Eklenti ayarlarını yeniler."));
            sender.sendMessage(formatHelp("/vcm transferall <eski> <yeni>", "Başkasının tüm marketlerini devreder."));
        }
        
        sender.sendMessage(Component.text(" "));
    }

    private Component formatHelp(String command, String description) {
        return MiniMessage.miniMessage().deserialize("<yellow>" + command + "</yellow> <dark_gray>-</dark_gray> <gray>" + description + "</gray>");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            List<String> commands = new ArrayList<>(Arrays.asList("menu", "setprice", "togglemode", "toggledisplay", "history", "delete", "transfer", "transferall", "addmember", "removemember", "members", "ban", "unban", "banlist", "help", "confirmdelete", "canceldelete"));
            if (sender.hasPermission("vcm.admin")) {
                commands.add("reload");
            }
            StringUtil.copyPartialMatches(args[0], commands, completions);
            Collections.sort(completions);
            return completions;
        }
        return null;
    }
}