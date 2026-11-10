package org.Cry_Legende.sHCrates.storage;

import org.Cry_Legende.sHCrates.SHCrates;
import org.Cry_Legende.sHCrates.network.NetworkKeys;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class PlayerDataManager implements Listener { // Implémente Listener pour PlayerJoin/Quit

    private final SHCrates plugin;
    private final File playerDataFolder;
    private final Map<UUID, PlayerData> playerDataCache = new ConcurrentHashMap<>();
    // Mode réseau : les clés virtuelles sont en base au lieu des fichiers. Nul sur un serveur seul.
    private volatile NetworkKeys network;

    public PlayerDataManager(SHCrates plugin) {
        this.plugin = plugin;
        this.playerDataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!playerDataFolder.exists()) {
            if (playerDataFolder.mkdirs()) {
                plugin.getLogger().info("PlayerData folder created.");
            } else {
                plugin.getLogger().severe("Could not create PlayerData folder!");
            }
        }
        // Enregistrer ce manager comme listener
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** Passe en mode réseau (clés virtuelles en base) ou revient aux fichiers avec null. */
    public void setNetwork(NetworkKeys network) {
        this.network = network;
    }

    // --- Event Handlers for loading/saving data ---
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        loadPlayerData(event.getPlayer().getUniqueId());
    }

    // MONITOR : après les autres listeners du quit, dont la récompense d'une ouverture en cours
    // (qui peut donner des clés) ; sinon l'entrée était recréée dans le cache et jamais libérée.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        savePlayerData(event.getPlayer().getUniqueId());
        playerDataCache.remove(event.getPlayer().getUniqueId()); // Retirer du cache
    }

    // --- Core Data Management Methods ---

    public PlayerData getPlayerData(UUID playerUUID) {
        return playerDataCache.computeIfAbsent(playerUUID, this::loadPlayerDataFromFile);
    }

    /** Faux tant que les clés virtuelles du joueur ne sont pas lues en base (mode réseau seulement). */
    public boolean isReady(UUID playerUUID) {
        return getPlayerData(playerUUID).isReady();
    }

    public PlayerData getPlayerData(Player player) {
        return getPlayerData(player.getUniqueId());
    }

    public void loadPlayerData(UUID playerUUID) {
        if (playerDataCache.containsKey(playerUUID)) {
            // Already loaded or being loaded, perhaps from an earlier getPlayerData call
            return;
        }
        // Load asynchronously to prevent lag on join, but cache result synchronously
        // For simplicity here, we'll do it synchronously. Consider async for larger scale.
        NetworkKeys net = network;
        PlayerData data = net != null ? net.adopt(playerUUID) : loadPlayerDataFromFile(playerUUID);
        playerDataCache.put(playerUUID, data);
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[DEBUG] Loaded data for player " + playerUUID);
        }
    }

    private PlayerData loadPlayerDataFromFile(UUID playerUUID) {
        // Mode réseau : jamais de fichier, les clés viennent de la base (sans clés tant qu'elles ne sont pas lues)
        NetworkKeys net = network;
        if (net != null) {
            return net.startLoading(playerUUID);
        }
        File playerFile = new File(playerDataFolder, playerUUID.toString() + ".yml");
        PlayerData data = new PlayerData(playerUUID);

        if (playerFile.exists()) {
            FileConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);
            ConfigurationSection virtualKeysSection = playerConfig.getConfigurationSection("virtual-keys");
            if (virtualKeysSection != null) {
                Map<String, Integer> keysMap = new HashMap<>();
                for (String crateInternalId : virtualKeysSection.getKeys(false)) {
                    keysMap.put(crateInternalId, virtualKeysSection.getInt(crateInternalId));
                }
                data.loadFromMap(keysMap);
            }
        }
        return data;
    }

    public void savePlayerData(UUID playerUUID) {
        if (network != null) {
            return; // mode réseau : la base est écrite à chaque changement
        }
        PlayerData data = playerDataCache.get(playerUUID);
        if (data == null) {
            // Pas de données à sauvegarder (peut-être que le joueur n'a jamais interagi ou que les données ont déjà été déchargées)
            return;
        }

        File playerFile = new File(playerDataFolder, playerUUID.toString() + ".yml");
        FileConfiguration playerConfig = new YamlConfiguration();

        playerConfig.set("player-uuid", playerUUID.toString()); // Juste pour info dans le fichier

        Map<String, Integer> virtualKeys = data.getAllVirtualKeys();
        if (!virtualKeys.isEmpty()) {
            ConfigurationSection virtualKeysSection = playerConfig.createSection("virtual-keys");
            virtualKeys.forEach(virtualKeysSection::set);
        } else {
            playerConfig.set("virtual-keys", null); // Supprimer la section si vide
        }

        try {
            playerConfig.save(playerFile);
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("[DEBUG] Saved data for player " + playerUUID);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save player data for " + playerUUID, e);
        }
    }

    public void saveAllPlayerData() {
        plugin.getLogger().info("Saving all cached player data...");
        // Note: PlayerQuitEvent gère déjà la sauvegarde des joueurs qui se déconnectent.
        // Ceci est pour un arrêt de serveur ou un reload.
        for (UUID uuid : playerDataCache.keySet()) {
            savePlayerData(uuid);
        }
        playerDataCache.clear(); // Vider le cache après sauvegarde
        plugin.getLogger().info("All cached player data saved.");
    }

    public void loadAllPlayerData() {
        // Typiquement appelé au onEnable. Les données des joueurs en ligne seront chargées par PlayerJoinEvent.
        // Ceci peut être utile si des opérations hors-ligne doivent être faites, mais pour les clés virtuelles
        // un chargement à la demande/connexion est généralement suffisant.
        // Pour l'instant, on ne charge que les joueurs en ligne au démarrage.
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadPlayerData(player.getUniqueId());
        }
        plugin.getLogger().info("Initial player data loaded for online players.");
    }

    public void saveAndReloadAllPlayerData() {
        if (network != null) {
            return; // mode réseau : la mémoire est déjà à jour avec la base
        }
        saveAllPlayerData();
        // Recharger les données pour les joueurs actuellement en ligne
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadPlayerData(player.getUniqueId());
        }
    }


    // --- Convenience methods for key management ---

    public int getVirtualKeys(UUID playerUUID, String crateInternalId) {
        return getPlayerData(playerUUID).getVirtualKeys(crateInternalId);
    }

    public void addVirtualKeys(UUID playerUUID, String crateInternalId, int amount) {
        NetworkKeys net = network;
        if (net != null) {
            net.add(getPlayerData(playerUUID), crateInternalId, amount);
            return;
        }
        getPlayerData(playerUUID).addVirtualKeys(crateInternalId, amount);
        // Sauvegarde immédiate (petit fichier) : un crash ne fait plus perdre les clés achetées,
        // ni « rendre » des clés déjà ouvertes.
        savePlayerData(playerUUID);
    }

    public boolean consumeVirtualKey(UUID playerUUID, String crateInternalId) {
        PlayerData data = getPlayerData(playerUUID);
        NetworkKeys net = network;
        if (net != null && !data.isReady()) {
            return false; // clés pas encore lues en base : rien ne se consomme
        }
        boolean consumed = data.consumeVirtualKey(crateInternalId);
        if (consumed) {
            if (net != null) net.removed(playerUUID, crateInternalId, 1);
            else savePlayerData(playerUUID);
        }
        return consumed;
    }

    public Map<String, Integer> getAllVirtualKeysForPlayer(UUID playerUUID) {
        return getPlayerData(playerUUID).getAllVirtualKeys();
    }

    /**
     * Lecture sans mise en cache (placeholders demandés pour un joueur hors ligne) : l'entrée ne serait
     * jamais retirée du cache puisque ce joueur ne déclenchera pas de PlayerQuitEvent.
     */
    public PlayerData peekPlayerData(UUID playerUUID) {
        PlayerData cached = playerDataCache.get(playerUUID);
        if (cached != null) return cached;
        // Mode réseau : pas de lecture en base depuis le fil principal, un joueur d'un autre serveur n'a rien ici
        return network != null ? new PlayerData(playerUUID) : loadPlayerDataFromFile(playerUUID);
    }

    public int takeVirtualKeys(UUID playerUUID, String crateInternalId, int amountToTake) {
        if (amountToTake <= 0) return 0;
        PlayerData data = getPlayerData(playerUUID);
        NetworkKeys net = network;
        if (net != null && !data.isReady()) {
            return 0; // clés pas encore lues en base : rien ne se retire
        }
        int currentKeys = data.getVirtualKeys(crateInternalId);
        int actualAmountTaken = Math.min(amountToTake, currentKeys);

        if (actualAmountTaken > 0) {
            data.setVirtualKeys(crateInternalId, currentKeys - actualAmountTaken);
            if (net != null) net.removed(playerUUID, crateInternalId, actualAmountTaken);
            else savePlayerData(playerUUID);
        }
        return actualAmountTaken;
    }
}