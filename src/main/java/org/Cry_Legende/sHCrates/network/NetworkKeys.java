package org.Cry_Legende.sHCrates.network;

import com.google.gson.JsonObject;
import org.Cry_Legende.sHCrates.SHCrates;
import org.Cry_Legende.sHCrates.storage.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.io.File;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Mode réseau : les clés virtuelles vivent dans la base partagée au lieu de {@code playerdata/<uuid>.yml}.
 *
 * <ul>
 *   <li>à la connexion : une fois le départ de l'autre serveur confirmé, ses clés sont lues en base et gardées en
 *       mémoire, comme avant ; en attendant, elles ne sont pas prêtes et aucune ne peut être ouverte ;</li>
 *   <li>à chaque changement (clé ajoutée, ouverte, retirée) : la mémoire change tout de suite et la base reçoit
 *       l'écart (+n ou -n), jamais le total, sur un fil à part ;</li>
 *   <li>au départ : la barrière de SHNetwork attend que ces écritures soient finies ;</li>
 *   <li>un joueur n'est que sur un serveur à la fois, donc sa mémoire fait foi pendant son séjour ; les commandes
 *       d'administration visant un joueur d'un autre serveur lui sont transmises (voir CrateCommand).</li>
 * </ul>
 */
public final class NetworkKeys implements Listener {

    private static final String CHANNEL = "shcrates";
    private static final int WRITE_ATTEMPTS = 3;
    private static final int LOAD_RETRY_SECONDS = 5;
    private static final int LOAD_RETRY_MAX = 60;
    private static final long PRELOAD_KEEP_MS = 120_000L;

    private record Preloaded(PlayerData data, long at) {
    }

    private final SHCrates plugin;
    private final KeyDatabase db;
    private final NetworkLink link;
    private final Duration waitMax;
    private final ScheduledThreadPoolExecutor io = new ScheduledThreadPoolExecutor(1, runnable -> {
        Thread thread = new Thread(runnable, "SHCrates-reseau");
        thread.setDaemon(true);
        return thread;
    });
    /** Joueurs chargés pendant leur pré-connexion, repris à leur arrivée. */
    private final Map<UUID, Preloaded> preloaded = new ConcurrentHashMap<>();

    public NetworkKeys(SHCrates plugin, KeyDatabase db, NetworkLink link, Duration waitMax) {
        this.plugin = plugin;
        this.db = db;
        this.link = link;
        this.waitMax = waitMax;
        io.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        io.setRemoveOnCancelPolicy(true);
    }

    public NetworkLink link() {
        return link;
    }

    /** Branche le mode réseau : chargement à la connexion, sauvegarde au départ, messages des autres serveurs. */
    public void start() {
        link.registerSaveTask(player -> flush());
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        link.listen(CHANNEL, this::onRemote);
    }

    /** Termine les écritures en cours puis ferme la base. */
    public void stop() {
        io.shutdown();
        try {
            if (!io.awaitTermination(5, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Des écritures de clés n'ont pas pu être terminées à l'arrêt.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        db.close();
    }

    // ------------------------------------------------------------------ chargement

    /**
     * Lance la lecture des clés dès la pré-connexion, sans l'attendre : le serveur quitté ne sauvegarde le joueur
     * qu'une fois cette connexion terminée (le proxy ferme l'ancienne connexion après), attendre ici bloquerait la
     * connexion jusqu'au délai maximal. Les clés restent « pas prêtes » jusqu'à la lecture, faite sur le fil de la base
     * une fois le départ confirmé.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID uuid = event.getUniqueId();
        String name = event.getName();
        long now = System.currentTimeMillis();
        preloaded.values().removeIf(p -> now - p.at() > PRELOAD_KEEP_MS);
        PlayerData data = notReady(uuid);
        preloaded.put(uuid, new Preloaded(data, now));
        link.awaitSaved(uuid, waitMax).whenComplete((saved, error) -> {
            if (error != null || !Boolean.TRUE.equals(saved)) {
                plugin.getLogger().warning("Départ de " + name + " non confirmé par l'autre serveur"
                    + (error != null ? " (" + error + ")" : "") + " : clés relues quand même.");
            }
            submit(() -> loadOnce(data, 0));
        });
    }

    /** Données du joueur qui entre : celles lues à sa pré-connexion, sinon (rechargement à chaud) lues maintenant. */
    public PlayerData adopt(UUID uuid) {
        Preloaded loaded = preloaded.remove(uuid);
        return loaded != null ? loaded.data() : startLoading(uuid);
    }

    /** Données vides en attente de la base, pour un joueur dont la connexion n'est pas passée par ici. */
    public PlayerData startLoading(UUID uuid) {
        PlayerData data = notReady(uuid);
        submit(() -> loadOnce(data, 0));
        return data;
    }

    private static PlayerData notReady(UUID uuid) {
        PlayerData data = new PlayerData(uuid);
        data.setReady(false);
        return data;
    }

    private void loadOnce(PlayerData data, int attempt) {
        try {
            data.loadFromMap(db.load(data.getPlayerUUID()));
            data.setReady(true);
        } catch (SQLException e) {
            plugin.getLogger().warning("Clés de " + data.getPlayerUUID() + " non lues en base : " + e.getMessage());
            retryLoad(data, attempt + 1);
        }
    }

    private void retryLoad(PlayerData data, int attempt) {
        if (attempt > LOAD_RETRY_MAX) {
            plugin.getLogger().severe("Clés de " + data.getPlayerUUID() + " jamais lues en base, abandon.");
            return;
        }
        try {
            io.schedule(() -> loadOnce(data, attempt), LOAD_RETRY_SECONDS, TimeUnit.SECONDS);
        } catch (RejectedExecutionException stopped) {
            // plugin arrêté
        }
    }

    // ------------------------------------------------------------------ changements

    /**
     * Ajoute des clés. Joueur chargé : la mémoire change tout de suite, la base reçoit l'écart. Pas encore chargé :
     * l'écriture et la mise à jour de la mémoire se font l'une après l'autre sur le fil de la base, de sorte que
     * le chargement en cours ne les compte ni deux fois ni jamais.
     */
    public void add(PlayerData data, String crateId, int amount) {
        String crate = crateId.toLowerCase();
        UUID uuid = data.getPlayerUUID();
        if (data.isReady()) {
            data.addVirtualKeys(crate, amount);
            submit(() -> write(uuid, crate, amount));
        } else {
            submit(() -> {
                write(uuid, crate, amount);
                if (data.isReady()) {
                    data.addVirtualKeys(crate, amount);
                }
            });
        }
    }

    /** Écrit en base des clés déjà retirées de la mémoire (ouverture, retrait par un administrateur). */
    public void removed(UUID uuid, String crateId, int amount) {
        String crate = crateId.toLowerCase();
        submit(() -> write(uuid, crate, -amount));
    }

    private void write(UUID uuid, String crate, int delta) {
        for (int attempt = 1; attempt <= WRITE_ATTEMPTS; attempt++) {
            try {
                db.add(uuid, crate, delta);
                return;
            } catch (SQLException e) {
                if (attempt == WRITE_ATTEMPTS) {
                    plugin.getLogger().log(Level.SEVERE, "Clés non écrites en base (joueur " + uuid + ", caisse " + crate
                        + ", écart " + delta + ") : " + e.getMessage());
                    return;
                }
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /** Futur terminé quand toutes les écritures déjà demandées sont faites (le fil d'écriture les traite dans l'ordre). */
    private CompletableFuture<Void> flush() {
        CompletableFuture<Void> done = new CompletableFuture<>();
        try {
            io.execute(() -> done.complete(null));
        } catch (RejectedExecutionException stopped) {
            done.complete(null);
        }
        return done;
    }

    private void submit(Runnable task) {
        try {
            io.execute(task);
        } catch (RejectedExecutionException stopped) {
            // plugin arrêté : plus rien à écrire
        }
    }

    // ------------------------------------------------------------------ autres serveurs

    /** Donne des clés à tous les joueurs de ce serveur, sur demande d'un autre serveur ({@code giveall}). */
    public void broadcastGiveAll(String crateId, int amount, boolean physical) {
        JsonObject message = new JsonObject();
        message.addProperty("type", "giveall");
        message.addProperty("crate", crateId);
        message.addProperty("amount", amount);
        message.addProperty("physical", physical);
        link.publish(CHANNEL, message);
    }

    private void onRemote(JsonObject message) {
        try {
            if (!"giveall".equals(message.get("type").getAsString())) {
                return;
            }
            String crate = message.get("crate").getAsString();
            int amount = message.get("amount").getAsInt();
            boolean physical = message.get("physical").getAsBoolean();
            for (Player online : Bukkit.getOnlinePlayers()) {
                plugin.getCrateManager().giveKey(online, crate, amount, physical);
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Message de clés illisible : " + e);
        }
    }

    // ------------------------------------------------------------------ importation

    /**
     * Copie les clés de l'ancien dossier {@code playerdata} dans la base, sans toucher aux lignes déjà présentes.
     * Tourne sur un fil à part ; {@code done} reçoit {importées, déjà présentes}, ou null si la base a refusé.
     */
    public void importFiles(File folder, Consumer<int[]> done) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<UUID, Map<String, Integer>> keys = new HashMap<>();
            File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files != null) {
                for (File file : files) {
                    try {
                        UUID uuid = UUID.fromString(file.getName().substring(0, file.getName().length() - 4));
                        ConfigurationSection section = YamlConfiguration.loadConfiguration(file).getConfigurationSection("virtual-keys");
                        if (section == null) {
                            continue;
                        }
                        Map<String, Integer> byCrate = new HashMap<>();
                        for (String crate : section.getKeys(false)) {
                            byCrate.put(crate, section.getInt(crate));
                        }
                        keys.put(uuid, byCrate);
                    } catch (IllegalArgumentException notAPlayerFile) {
                        // fichier qui n'est pas <uuid>.yml : ignoré
                    }
                }
            }
            int[] result;
            try {
                result = db.importKeys(keys);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Importation des clés impossible : " + e.getMessage());
                result = null;
            }
            int[] finalResult = result;
            Bukkit.getScheduler().runTask(plugin, () -> done.accept(finalResult));
        });
    }
}
