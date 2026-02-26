package org.dxeiz.visualchestmarket.listener;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.dxeiz.visualchestmarket.VisualChestMarket;
import org.dxeiz.visualchestmarket.model.Shop;

public class ChunkListener implements Listener {
    private final VisualChestMarket plugin;

    public ChunkListener(VisualChestMarket plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onUnload(ChunkUnloadEvent event) {
        // Despawn entities in this chunk to prevent ghost entities or unnecessary load
        for (BlockState state : event.getChunk().getTileEntities()) {
            if (state instanceof Chest) {
                plugin.getDisplayManager().removeDisplay(state.getLocation());
            }
        }
    }

    @EventHandler
    public void onLoad(ChunkLoadEvent event) {
        // Respawn displays for shops in this chunk
        for (BlockState state : event.getChunk().getTileEntities()) {
            if (state instanceof Chest) {
                Shop shop = plugin.getShopManager().getShop(state.getLocation());
                if (shop != null) {
                    plugin.getDisplayManager().updateDisplay(shop);
                    plugin.getShopManager().updateShopSign(shop);
                }
            }
        }
    }
}