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
import org.Cry_Legende.sHCrates.listeners.WorldLoadListener;
import org.Cry_Legende.sHCrates.network.KeyDatabase;
import org.Cry_Legende.sHCrates.network.NetworkKeys;
import org.Cry_Legende.sHCrates.network.NetworkLink;
import org.Cry_Legende.sHCrates.network.ShNetworkLink;
import org.Cry_Legende.sHCrates.placeholders.SHCratesExpansion;
import org.Cry_Legende.sHCrates.storage.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
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
    // Non nul seulement avec SHNetwork : clés virtuelles dans la base partagée
    private NetworkKeys network;

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
        enableNetwork();
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
        // Solder les ouvertures en cours AVANT toute sauvegarde : le plugin étant déjà désactivé,
        // aucune tâche ne peut plus être planifiée, les récompenses sont donc données tout de suite.
        if (animationManager != null) {
            animationManager.finishAllAnimations();
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

        // Sauvegarder les données importantes (après les récompenses, qui peuvent donner des clés)
        if (playerDataManager != null) {
            playerDataManager.saveAllPlayerData();
        }
        if (crateManager != null) {
            crateManager.saveCrateLocations(); // Sauvegarder les emplacements physiques des crates
        }
        // Mode réseau : terminer les écritures en base demandées par ce qui précède
        if (network != null) {
            playerDataManager.setNetwork(null);
            network.stop();
        }

        getLogger().info("SHCrates v" + getDescription().getVersion() + " has been disabled.");
    }

    /**
     * Passe en mode réseau si SHNetwork est installé et 'reseau.actif' est vrai. Les classes qui touchent à SHNetwork
     * ne sont chargées qu'ici : sans lui, le plugin se comporte exactement comme avant.
     */
    private void enableNetwork() {
        if (!configManager.getBoolean("reseau.actif", true)) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("SHNetwork")) return;
        KeyDatabase db = null;
        try {
            NetworkLink link = ShNetworkLink.open();
            db = new KeyDatabase(
                configManager.getString("reseau.base-de-donnees.url", ""),
                configManager.getString("reseau.base-de-donnees.utilisateur", ""),
                configManager.getString("reseau.base-de-donnees.mot-de-passe", ""),
                Math.max(250, configManager.getInt("reseau.base-de-donnees.delai-ms", 3000)));
            db.createTable();
            Duration wait = Duration.ofSeconds(Math.max(1, configManager.getInt("reseau.attente-max-secondes", 10)));
            NetworkKeys net = new NetworkKeys(this, db, link, wait);
            net.start();
            playerDataManager.setNetwork(net);
            network = net;
            getLogger().info("SHNetwork détecté : les clés virtuelles sont dans la base partagée.");
        } catch (Exception | LinkageError e) {
            if (db != null) db.close();
            getLogger().log(Level.SEVERE, "SHNetwork présent mais inutilisable, clés virtuelles locales à ce serveur : " + e);
        }
    }

    /** Mode réseau actif, ou null sur un serveur seul. */
    public NetworkKeys getNetwork() {
        return network;
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
        pm.registerEvents(new WorldLoadListener(this), this);
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