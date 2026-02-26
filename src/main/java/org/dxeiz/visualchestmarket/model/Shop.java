package org.dxeiz.visualchestmarket.model;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Shop {
    private final int id;
    private UUID ownerId;
    private final Location location;
    private final ItemStack item;
    private double price;
    private ShopMode mode;
    private DisplayMode displayMode;
    private int stock;
    private double shopBalance;
    private final Map<UUID, String> members = new ConcurrentHashMap<>();
    private final Map<UUID, String> bannedPlayers = new ConcurrentHashMap<>();

    public Shop(int id, UUID ownerId, Location location, ItemStack item, double price, ShopMode mode, DisplayMode displayMode, int stock, double shopBalance) {
        this.id = id;
        this.ownerId = ownerId;
        this.location = location;
        this.item = item;
        this.price = price;
        this.mode = mode;
        this.displayMode = displayMode;
        this.stock = stock;
        this.shopBalance = shopBalance;
    }

    // Getters
    public int getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public Location getLocation() { return location; }
    public ItemStack getItem() { return item; }
    public double getPrice() { return price; }
    public ShopMode getMode() { return mode; }
    public DisplayMode getDisplayMode() { return displayMode; }
    public int getStock() { return stock; }
    public double getShopBalance() { return shopBalance; }
    public Map<UUID, String> getMembers() { return members; }
    public Map<UUID, String> getBannedPlayers() { return bannedPlayers; }

    // Setters
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
    public void setPrice(double price) { this.price = price; }
    public void setMode(ShopMode mode) { this.mode = mode; }
    public void setDisplayMode(DisplayMode displayMode) { this.displayMode = displayMode; }
    public void setStock(int stock) { this.stock = stock; }
    public void setShopBalance(double shopBalance) { this.shopBalance = shopBalance; }
    
    public void addMember(UUID uuid, String role) {
        members.put(uuid, role);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    public boolean isMember(UUID uuid) {
        return members.containsKey(uuid);
    }

    public String getMemberRole(UUID uuid) {
        return members.get(uuid);
    }

    public void banPlayer(UUID uuid, String reason) {
        bannedPlayers.put(uuid, reason);
    }

    public void unbanPlayer(UUID uuid) {
        bannedPlayers.remove(uuid);
    }

    public boolean isBanned(UUID uuid) {
        return bannedPlayers.containsKey(uuid);
    }

    public String getBanReason(UUID uuid) {
        return bannedPlayers.get(uuid);
    }
}