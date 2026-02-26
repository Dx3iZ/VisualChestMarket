package org.dxeiz.visualchestmarket;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.dxeiz.visualchestmarket.command.MarketCommand;
import org.dxeiz.visualchestmarket.core.*;
import org.dxeiz.visualchestmarket.listener.*;
import org.dxeiz.visualchestmarket.storage.DatabaseManager;

import java.util.logging.Logger;

public class VisualChestMarket extends JavaPlugin {

    private static VisualChestMarket instance;
    public static final Logger LOGGER = Logger.getLogger("VisualChestMarket");

    private DatabaseManager databaseManager;
    private ShopManager shopManager;
    private DisplayManager displayManager;
    private InputManager inputManager;
    private EconomyManager economyManager;
    private TransactionManager transactionManager;
    private ConfigManager configManager;
    private Economy economy;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Bağımlılık Kontrolleri
        if (!setupEconomy()) {
            LOGGER.severe("Vault veya Ekonomi eklentisi bulunamadi! Plugin devre disi kaliyor.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        if (getServer().getPluginManager().getPlugin("DecentHolograms") == null) {
            LOGGER.severe("DecentHolograms bulunamadi! Lutfen yukleyin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 1. Initialize Managers
        this.configManager = new ConfigManager(this);
        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.initialize();

        this.economyManager = new EconomyManager(this);
        this.transactionManager = new TransactionManager(this);
        this.inputManager = new InputManager();
        this.displayManager = new DisplayManager(this);
        this.shopManager = new ShopManager(this);

        // 2. Register Listeners
        getServer().getPluginManager().registerEvents(new InteractionListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockListener(this), this);
        getServer().getPluginManager().registerEvents(new ChunkListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);

        // 3. Register Commands
        if (getCommand("vcm") != null) {
            getCommand("vcm").setExecutor(new MarketCommand(this));
            getCommand("vcm").setTabCompleter(new MarketCommand(this));
        }

        // 4. Post-Enable Logic
        this.displayManager.cleanupAllWorldDisplays();
        this.shopManager.loadShops();

        LOGGER.info("VisualChestMarket 1.0.0 basariyla aktif edildi!");
    }

    @Override
    public void onDisable() {
        if (displayManager != null) {
            displayManager.cleanupAllWorldDisplays();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        LOGGER.info("VisualChestMarket devre disi birakildi.");
    }

    public void reload() {
        reloadConfig();
        configManager.reload();
        economyManager.reload(); // Ekonomi ayarlarını yenile
        shopManager.reload(); // Tüm marketleri güncelle
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    // Getters
    public static VisualChestMarket getInstance() { return instance; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public ShopManager getShopManager() { return shopManager; }
    public DisplayManager getDisplayManager() { return displayManager; }
    public InputManager getInputManager() { return inputManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public TransactionManager getTransactionManager() { return transactionManager; }
    public ConfigManager getConfigManager() { return configManager; }
    public Economy getEconomy() { return economy; }
}