package org.dxeiz.visualchestmarket.storage;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.dxeiz.visualchestmarket.VisualChestMarket;
import org.dxeiz.visualchestmarket.model.DisplayMode;
import org.dxeiz.visualchestmarket.model.Shop;
import org.dxeiz.visualchestmarket.model.ShopMode;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DatabaseManager {
    private final VisualChestMarket plugin;
    private Connection connection;
    // For production, use HikariCP DataSource instead of raw Connection for MySQL

    public DatabaseManager(VisualChestMarket plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        try {
            // Simplified SQLite connection
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + plugin.getDataFolder() + "/database.db");
            createTables();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            // Yeniden bağlanma mantığı (Reconnection logic) buraya eklenebilir
            initialize();
        }
        return connection;
    }

    private void createTables() throws SQLException {
        Statement st = connection.createStatement();
        
        // 1. Tabloları oluştur (Eğer yoksa)
        st.execute("CREATE TABLE IF NOT EXISTS shops (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "owner_uuid VARCHAR(36)," +
                "world VARCHAR(50)," +
                "x INTEGER, y INTEGER, z INTEGER," +
                "item_base64 TEXT," +
                "price DOUBLE," +
                "mode VARCHAR(10)," +
                "display_mode VARCHAR(10)," +
                "stock INT," +
                "balance DOUBLE)");
                
        st.execute("CREATE TABLE IF NOT EXISTS shop_transactions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "shop_id INTEGER," +
                "player_uuid VARCHAR(36)," +
                "player_name VARCHAR(16)," +
                "amount INTEGER," +
                "total_price DOUBLE," +
                "type VARCHAR(10)," +
                "timestamp TIMESTAMP)");

        st.execute("CREATE TABLE IF NOT EXISTS shop_members (" +
                "shop_id INTEGER," +
                "member_uuid VARCHAR(36)," +
                "role VARCHAR(10)," +
                "PRIMARY KEY (shop_id, member_uuid))");

        st.execute("CREATE TABLE IF NOT EXISTS shop_bans (" +
                "shop_id INTEGER," +
                "banned_uuid VARCHAR(36)," +
                "reason TEXT," +
                "timestamp TIMESTAMP," +
                "PRIMARY KEY (shop_id, banned_uuid))");

        // 2. Eksik sütunları kontrol et ve ekle (Migration)
        checkAndAddColumn(st, "shops", "owner_uuid", "VARCHAR(36)");
        checkAndAddColumn(st, "shops", "world", "VARCHAR(50)");
        checkAndAddColumn(st, "shops", "x", "INTEGER");
        checkAndAddColumn(st, "shops", "y", "INTEGER");
        checkAndAddColumn(st, "shops", "z", "INTEGER");
        checkAndAddColumn(st, "shops", "item_base64", "TEXT");
        checkAndAddColumn(st, "shops", "price", "DOUBLE");
        checkAndAddColumn(st, "shops", "mode", "VARCHAR(10)");
        checkAndAddColumn(st, "shops", "display_mode", "VARCHAR(10)");
        checkAndAddColumn(st, "shops", "stock", "INT");
        checkAndAddColumn(st, "shops", "balance", "DOUBLE");
        
        checkAndAddColumn(st, "shop_transactions", "shop_id", "INTEGER");
        checkAndAddColumn(st, "shop_transactions", "player_uuid", "VARCHAR(36)");
        checkAndAddColumn(st, "shop_transactions", "player_name", "VARCHAR(16)");
        checkAndAddColumn(st, "shop_transactions", "amount", "INTEGER");
        checkAndAddColumn(st, "shop_transactions", "total_price", "DOUBLE");
        checkAndAddColumn(st, "shop_transactions", "type", "VARCHAR(10)");
        checkAndAddColumn(st, "shop_transactions", "timestamp", "TIMESTAMP");

        st.close();
    }

    private void checkAndAddColumn(Statement st, String table, String column, String type) {
        try {
            // Sütunun var olup olmadığını kontrol et
            ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")");
            boolean exists = false;
            while (rs.next()) {
                if (rs.getString("name").equalsIgnoreCase(column)) {
                    exists = true;
                    break;
                }
            }
            rs.close();

            if (!exists) {
                st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
                VisualChestMarket.LOGGER.info("Veritabanı güncellendi: " + table + " tablosuna " + column + " sütunu eklendi.");
            }
        } catch (SQLException e) {
            // Hata oluşursa logla ama eklentiyi durdurma
            VisualChestMarket.LOGGER.warning("Sütun kontrolü sırasında hata (" + table + "." + column + "): " + e.getMessage());
        }
    }

    public int createShop(UUID owner, Location loc, ItemStack item, double price, int stock) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO shops (owner_uuid, world, x, y, z, item_base64, price, mode, display_mode, stock, balance) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS);

        ps.setString(1, owner.toString());
        ps.setString(2, loc.getWorld().getName());
        ps.setInt(3, loc.getBlockX());
        ps.setInt(4, loc.getBlockY());
        ps.setInt(5, loc.getBlockZ());
        ps.setString(6, itemToBase64(item));
        ps.setDouble(7, price);
        ps.setString(8, ShopMode.SELL.name());
        ps.setString(9, DisplayMode.HOLOGRAM.name());
        ps.setInt(10, stock);
        ps.setDouble(11, 0.0);

        ps.executeUpdate();
        ResultSet rs = ps.getGeneratedKeys();
        if (rs.next()) return rs.getInt(1);
        return -1;
    }

    public List<Shop> loadAllShops() {
        List<Shop> shops = new ArrayList<>();
        try {
            Statement st = connection.createStatement();
            ResultSet rs = st.executeQuery("SELECT * FROM shops");
            while (rs.next()) {
                Location loc = new Location(
                        Bukkit.getWorld(rs.getString("world")),
                        rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));

                if (loc.getWorld() == null) continue;

                shops.add(new Shop(
                        rs.getInt("id"),
                        UUID.fromString(rs.getString("owner_uuid")),
                        loc,
                        itemFromBase64(rs.getString("item_base64")),
                        rs.getDouble("price"),
                        ShopMode.valueOf(rs.getString("mode")),
                        DisplayMode.valueOf(rs.getString("display_mode")),
                        rs.getInt("stock"),
                        rs.getDouble("balance")
                ));
            }
            rs.close();
            st.close();
        } catch (Exception e) { e.printStackTrace(); }
        return shops;
    }

    public void deleteShop(int id) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("DELETE FROM shops WHERE id = ?");
        ps.setInt(1, id);
        ps.executeUpdate();
        ps.close();
    }

    public void updateShop(Shop shop) {
        try {
            PreparedStatement ps = connection.prepareStatement("UPDATE shops SET owner_uuid=?, price=?, mode=?, display_mode=?, stock=?, balance=? WHERE id=?");
            ps.setString(1, shop.getOwnerId().toString());
            ps.setDouble(2, shop.getPrice());
            ps.setString(3, shop.getMode().name());
            ps.setString(4, shop.getDisplayMode().name());
            ps.setInt(5, shop.getStock());
            ps.setDouble(6, shop.getShopBalance());
            ps.setInt(7, shop.getId());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public int transferAllShops(UUID oldOwner, UUID newOwner) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("UPDATE shops SET owner_uuid=? WHERE owner_uuid=?");
        ps.setString(1, newOwner.toString());
        ps.setString(2, oldOwner.toString());
        int count = ps.executeUpdate();
        ps.close();
        return count;
    }

    public void addMember(int shopId, UUID memberUuid, String role) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("INSERT OR REPLACE INTO shop_members (shop_id, member_uuid, role) VALUES (?,?,?)");
        ps.setInt(1, shopId);
        ps.setString(2, memberUuid.toString());
        ps.setString(3, role);
        ps.executeUpdate();
        ps.close();
    }

    public void removeMember(int shopId, UUID memberUuid) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("DELETE FROM shop_members WHERE shop_id = ? AND member_uuid = ?");
        ps.setInt(1, shopId);
        ps.setString(2, memberUuid.toString());
        ps.executeUpdate();
        ps.close();
    }

    public Map<UUID, String> getMembers(int shopId) throws SQLException {
        Map<UUID, String> members = new HashMap<>();
        PreparedStatement ps = connection.prepareStatement("SELECT * FROM shop_members WHERE shop_id = ?");
        ps.setInt(1, shopId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            members.put(UUID.fromString(rs.getString("member_uuid")), rs.getString("role"));
        }
        rs.close();
        ps.close();
        return members;
    }

    public void banPlayer(int shopId, UUID bannedUuid, String reason) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("INSERT OR REPLACE INTO shop_bans (shop_id, banned_uuid, reason, timestamp) VALUES (?,?,?,?)");
        ps.setInt(1, shopId);
        ps.setString(2, bannedUuid.toString());
        ps.setString(3, reason);
        ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
        ps.executeUpdate();
        ps.close();
    }

    public void unbanPlayer(int shopId, UUID bannedUuid) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("DELETE FROM shop_bans WHERE shop_id = ? AND banned_uuid = ?");
        ps.setInt(1, shopId);
        ps.setString(2, bannedUuid.toString());
        ps.executeUpdate();
        ps.close();
    }

    public Map<UUID, String> getBannedPlayers(int shopId) throws SQLException {
        Map<UUID, String> bans = new HashMap<>();
        PreparedStatement ps = connection.prepareStatement("SELECT * FROM shop_bans WHERE shop_id = ?");
        ps.setInt(1, shopId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            // Sebep ve tarih bilgisini birleştirip saklayabiliriz veya ayrı bir obje kullanabiliriz.
            // Şimdilik basitlik için sebebi saklıyoruz.
            bans.put(UUID.fromString(rs.getString("banned_uuid")), rs.getString("reason") + "|" + rs.getTimestamp("timestamp").toString());
        }
        rs.close();
        ps.close();
        return bans;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // Utility Base64 (Using Bukkit Serialization)
    private String itemToBase64(ItemStack item) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(item);
            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) { throw new RuntimeException("Item serialize error", e); }
    }

    private ItemStack itemFromBase64(String data) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            return (ItemStack) dataInput.readObject();
        } catch (Exception e) { throw new RuntimeException("Item deserialize error", e); }
    }
}