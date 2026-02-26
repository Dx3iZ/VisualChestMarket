package org.dxeiz.visualchestmarket.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.dxeiz.visualchestmarket.VisualChestMarket;

public class ConfigManager {
    private final VisualChestMarket plugin;
    private final MiniMessage mm;
    private String prefix;

    public ConfigManager(VisualChestMarket plugin) {
        this.plugin = plugin;
        this.mm = MiniMessage.miniMessage();
        loadConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        loadConfig();
    }

    private void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        // Prefix'i config'den al, yoksa varsayılanı kullan
        this.prefix = config.getString("messages.prefix", "<gradient:#ffaa00:#ff5500><bold>MARKET</bold></gradient> <dark_gray>»</dark_gray> ");
    }

    /**
     * Config'den mesajı alır ve başına otomatik olarak prefix ekler.
     * @param path Config'deki mesaj yolu (örn: "messages.chunk-limit")
     * @param placeholders Değişkenler (örn: "%limit%", "10")
     * @return Prefix eklenmiş Component
     */
    public Component getMessage(String path, String... placeholders) {
        String msg = plugin.getConfig().getString(path);
        if (msg == null) msg = plugin.getConfig().getString("messages." + path);
        if (msg == null) return Component.text("Message missing: " + path);

        for (int i = 0; i < placeholders.length; i += 2) {
            String key = placeholders[i];
            String value = placeholders[i+1];
            if (value == null) value = "";
            msg = msg.replace(key, value);
        }

        return mm.deserialize(prefix + msg);
    }

    /**
     * Config'den mesajı alır ancak prefix EKLEMEZ. (GUI başlıkları, lore vb. için)
     */
    public Component getComponent(String path, String... placeholders) {
        String msg = plugin.getConfig().getString(path);
        if (msg == null) msg = plugin.getConfig().getString("messages." + path);
        if (msg == null) return Component.text("Message missing: " + path);

        for (int i = 0; i < placeholders.length; i += 2) {
            String key = placeholders[i];
            String value = placeholders[i+1];
            if (value == null) value = "";
            msg = msg.replace(key, value);
        }

        return mm.deserialize(msg);
    }

    public String getRawString(String path) {
        String msg = plugin.getConfig().getString(path);
        if (msg == null) msg = plugin.getConfig().getString("messages." + path);
        return msg != null ? msg : path;
    }
}