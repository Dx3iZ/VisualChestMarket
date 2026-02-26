package org.dxeiz.visualchestmarket.core;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.dxeiz.visualchestmarket.VisualChestMarket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class InputManager {

    private final Map<UUID, Consumer<String>> waitingInputs = new HashMap<>();

    /**
     * Oyuncuyu girdi listesine ekler.
     * @param callback: Girdi geldiğinde yapılacak işlem.
     */
    public void awaitInput(UUID uuid, Consumer<String> callback) {
        waitingInputs.put(uuid, callback);

        // 5 dakika (300 saniye) sonra zaman aşımı
        Bukkit.getScheduler().runTaskLater(VisualChestMarket.getInstance(), () -> {
            if (waitingInputs.containsKey(uuid)) {
                waitingInputs.remove(uuid);
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    p.sendMessage(VisualChestMarket.getInstance().getConfigManager().getMessage("messages.cancel"));
                }
            }
        }, 20L * 60 * 5);
    }

    public boolean isWaiting(UUID uuid) {
        return waitingInputs.containsKey(uuid);
    }

    /**
     * ChatListener tarafından ana thread'de tetiklenir.
     */
    public void processInput(UUID uuid, String input) {
        Consumer<String> callback = waitingInputs.remove(uuid);
        if (callback != null) {
            callback.accept(input);
        }
    }

    public void cancelInput(UUID uuid) {
        waitingInputs.remove(uuid);
    }
}