package org.dxeiz.visualchestmarket.listener;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.dxeiz.visualchestmarket.VisualChestMarket;

public class ChatListener implements Listener {

    private final VisualChestMarket plugin;

    public ChatListener(VisualChestMarket plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        var player = event.getPlayer();
        var uuid = player.getUniqueId();

        // Eğer oyuncu girdi bekleme listesindeyse (Sayı veya Fiyat giriyorsa)
        if (plugin.getInputManager().isWaiting(uuid)) {
            event.setCancelled(true); // Mesajın sohbete gitmesini engelle

            // Mesajı String formatına çevir
            String input = PlainTextComponentSerializer.plainText().serialize(event.message());

            if (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("iptal")) {
                plugin.getInputManager().cancelInput(uuid);
                player.sendMessage(plugin.getConfigManager().getMessage("cancel"));
                return;
            }

            // --- KRİTİK DÜZELTME: ANA THREAD'E GEÇİŞ ---
            // EntityRemoveEvent veya UpdateDisplay işlemleri için asenkron thread'den çıkmalıyız.
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Bu kısım artık güvenli bir şekilde ana thread'de çalışıyor.
                plugin.getInputManager().processInput(uuid, input);
            });
        }
    }
}