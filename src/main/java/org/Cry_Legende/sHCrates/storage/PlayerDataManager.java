package org.Cry_Legende.sHCrates.storage;

import org.Cry_Legende.sHCrates.SHCrates;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
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

    // --- Event Handlers for loading/saving data ---
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        loadPlayerData(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        savePlayerData(event.getPlayer().getUniqueId());
        playerDataCache.remove(event.getPlayer().getUniqueId()); // Retirer du cache
    }

    // --- Core Data Management Methods ---

    public PlayerData getPlayerData(UUID playerUUID) {
        return playerDataCache.computeIfAbsent(playerUUID, this::loadPlayerDataFromFile);
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
        PlayerData data = loadPlayerDataFromFile(playerUUID);
        playerDataCache.put(playerUUID, data);
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[DEBUG] Loaded data for player " + playerUUID);
        }
    }

    private PlayerData loadPlayerDataFromFile(UUID playerUUID) {
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
        getPlayerData(playerUUID).addVirtualKeys(crateInternalId, amount);
        // Sauvegarde immédiate ou périodique ? Pour l'instant, la sauvegarde se fait à la déconnexion ou à l'arrêt.
        // Si vous voulez une sauvegarde plus fréquente, vous pouvez appeler savePlayerData(playerUUID) ici.
    }

    public boolean consumeVirtualKey(UUID playerUUID, String crateInternalId) {
        return getPlayerData(playerUUID).consumeVirtualKey(crateInternalId);
    }

    public Map<String, Integer> getAllVirtualKeysForPlayer(UUID playerUUID) {
        return getPlayerData(playerUUID).getAllVirtualKeys();
    }

    public int takeVirtualKeys(UUID playerUUID, String crateInternalId, int amountToTake) {
        if (amountToTake <= 0) return 0;
        PlayerData data = getPlayerData(playerUUID);
        int currentKeys = data.getVirtualKeys(crateInternalId);
        int actualAmountTaken = Math.min(amountToTake, currentKeys);

        if (actualAmountTaken > 0) {
            data.setVirtualKeys(crateInternalId, currentKeys - actualAmountTaken);
            savePlayerData(playerUUID);
        }
        return actualAmountTaken;
    }
}