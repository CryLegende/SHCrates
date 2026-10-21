package org.Cry_Legende.sHCrates;

import org.Cry_Legende.sHCrates.animations.AnimationManager;
import org.Cry_Legende.sHCrates.commands.CrateCommand;
import org.Cry_Legende.sHCrates.config.ConfigManager;
import org.Cry_Legende.sHCrates.config.CrateConfigManager;
import org.Cry_Legende.sHCrates.config.MessageManager;
import org.Cry_Legende.sHCrates.crates.CrateManager;
import org.Cry_Legende.sHCrates.gui.GUIManager;
import org.Cry_Legende.sHCrates.listeners.BlockBreakListener;
import org.Cry_Legende.sHCrates.listeners.InventoryClickListener;
import org.Cry_Legende.sHCrates.listeners.PlayerInteractListener;
import org.Cry_Legende.sHCrates.listeners.PlayerQuitListener;
import org.Cry_Legende.sHCrates.placeholders.SHCratesExpansion;
import org.Cry_Legende.sHCrates.storage.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Level;

public final class SHCrates extends JavaPlugin {

    private static SHCrates instance;

    private ConfigManager configManager;
    private MessageManager messageManager;
    private CrateConfigManager crateConfigManager;
    private CrateManager crateManager;
    private GUIManager guiManager;
    private AnimationManager animationManager;
    private PlayerDataManager playerDataManager;

    private boolean placeholderApiHooked = false;

    @Override
    public void onEnable() {
        instance = this;

        // 1. Initialisation et chargement des configurations
        this.configManager = new ConfigManager(this);
        this.configManager.loadConfigs();

        this.messageManager = new MessageManager(this);
        this.messageManager.loadMessages();

        this.crateConfigManager = new CrateConfigManager(this);
        // Le chargement des crates individuelles sera fait par CrateManager

        // 2. Initialisation des managers principaux
        this.playerDataManager = new PlayerDataManager(this);
        this.crateManager = new CrateManager(this);
        this.guiManager = new GUIManager(this);
        this.animationManager = new AnimationManager(this);

        // Charger les données des crates (y compris les emplacements physiques et les types de crates)
        this.crateManager.loadCrates(); // Cela va charger les .yml de crates/ et les emplacements
        this.playerDataManager.loadAllPlayerData(); // Charger les données des joueurs (clés virtuelles)

        // 3. Enregistrement des commandes
        registerCommands();

        // 4. Enregistrement des listeners
        registerListeners();

        // 5. Hook avec PlaceholderAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new SHCratesExpansion(this).register();
            placeholderApiHooked = true;
            getLogger().info("Successfully hooked into PlaceholderAPI!");
        } else {
            getLogger().warning("PlaceholderAPI not found. Some placeholder features will be disabled.");
        }

        getLogger().info("SHCrates v" + getDescription().getVersion() + " has been enabled successfully!");
        if (configManager.isDebugMode()) {
            getLogger().log(Level.INFO, "[DEBUG] SHCrates is running in debug mode.");
        }
    }

    @Override
    public void onDisable() {
        // Sauvegarder les données importantes
        if (playerDataManager != null) {
            playerDataManager.saveAllPlayerData();
        }
        if (crateManager != null) {
            crateManager.saveCrateLocations(); // Sauvegarder les emplacements physiques des crates
        }

        // --- CORRECTION 4 : APPEL AU NETTOYAGE GLOBAL ---
        // Nettoie toutes les entités d'affichage potentiellement restantes pour
        // éviter qu'elles ne persistent après un arrêt ou un reload.
        if (animationManager != null) {
            animationManager.cleanupAllDisplayEntities();
        }

        // Fermer les inventaires ouverts par le plugin pour éviter les problèmes
        if (guiManager != null) {
            guiManager.closeAllPluginInventories();
        }

        getLogger().info("SHCrates v" + getDescription().getVersion() + " has been disabled.");
    }

    private void registerCommands() {
        Objects.requireNonNull(getCommand("shcrates")).setExecutor(new CrateCommand(this));
        // Le TabCompleter sera ajouté à CrateCommand plus tard si besoin
    }

    private void registerListeners() {
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new PlayerInteractListener(this), this);
        pm.registerEvents(new InventoryClickListener(this), this);
        pm.registerEvents(new BlockBreakListener(this), this);
        pm.registerEvents(new PlayerQuitListener(this), this);
    }

    public static SHCrates getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public CrateConfigManager getCrateConfigManager() {
        return crateConfigManager;
    }

    public CrateManager getCrateManager() {
        return crateManager;
    }

    public GUIManager getGuiManager() {
        return guiManager;
    }

    public AnimationManager getAnimationManager() {
        return animationManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public boolean isPlaceholderApiHooked() {
        return placeholderApiHooked;
    }
}