package org.Cry_Legende.sHCrates.scenes;

import io.papermc.paper.entity.LookAnchor;
import org.Cry_Legende.sHCrates.SHCrates;
import org.Cry_Legende.sHCrates.crates.Crate;
import org.Cry_Legende.sHCrates.crates.Reward;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Scènes en cours : leur déroulé (un seul tick pour toutes), leurs spectateurs, leur place réservée, et la protection de
 * leurs entités. Règles de lisibilité quand plusieurs joueurs ouvrent en même temps : les scènes ne se chevauchent jamais
 * (place réservée), un joueur qui ouvre ne voit que la sienne, un spectateur ne voit que les plus proches
 * ({@code scenes.max-par-spectateur}), et n'entend que les grands moments, moins fort.
 */
public final class SceneManager implements Listener {

    /** Placement trouvé à l'ouverture, gardé le temps que l'animation démarre (délai d'un bloc animé). */
    private record Prepared(Placement placement, SceneKind kind, long at) {
    }

    private static final long PREPARED_TTL_MS = 10_000;
    private static final int SKIP_AFTER_TICKS = 8;

    private final SHCrates plugin;
    private final NamespacedKey sceneKey;
    private final NamespacedKey fireworkKey;
    private final SceneSpots spots;
    private final ScenePlacer placer;
    // lus depuis les événements des mondes (tick des mondes en parallèle sur DivineMC) : collections sûres entre threads
    private final List<Scene> scenes = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final Map<UUID, Scene> byOwner = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Prepared> prepared = new java.util.concurrent.ConcurrentHashMap<>();
    private BukkitTask ticker;
    private long tickCount;

    public SceneManager(SHCrates plugin) {
        this.plugin = plugin;
        this.sceneKey = new NamespacedKey(plugin, "scene");
        this.fireworkKey = new NamespacedKey(plugin, "feu");
        this.spots = new SceneSpots(plugin);
        this.placer = new ScenePlacer(this, new Obstacles(plugin.getLogger()));
    }

    public void start() {
        spots.load();
        // modèle de la piñata lu tout de suite, pas pendant la première ouverture
        try {
            SceneModel.get(plugin, "pinata");
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Modèle de scène pinata illisible : " + e.getMessage());
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 1L, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, this::warmUp, 100L);
    }

    /**
     * Fait passer une fois, sans joueur pour le voir, le chemin d'une scène (bloc de lumière, têtes du modèle, texte) :
     * sinon la première scène après un démarrage coûte ~150 ms d'un coup (mesuré sur un serveur neuf), un tick perdu.
     */
    private void warmUp() {
        try {
            // la lumière côté client d'une scène (bloc LIGHT) : ~115 ms la première fois qu'un BlockData est créé
            org.bukkit.Material.LIGHT.createBlockData();
            Bukkit.createBlockData("minecraft:vault[facing=south,ominous=true,vault_state=inactive]");
            // entités invisibles posées là où se trouve un joueur (chunk chargé à coup sûr), retirées dans le même tick ;
            // personne en ligne : on réessaie plus tard
            Player any = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
            if (any == null) {
                if (ticker != null && !ticker.isCancelled()) Bukkit.getScheduler().runTaskLater(plugin, this::warmUp, 200L);
                return;
            }
            Location at = any.getLocation();
            World world = at.getWorld();
            SceneModel model = SceneModel.get(plugin, "pinata");
            List<Entity> spawned = new ArrayList<>();
            for (org.bukkit.inventory.ItemStack head : model.heads) {
                spawned.add(world.spawn(at, org.bukkit.entity.ItemDisplay.class, d -> {
                    d.setVisibleByDefault(false);
                    d.setPersistent(false);
                    d.setItemStack(head);
                    d.setItemDisplayTransform(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.NONE);
                    d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.Quaternionf(),
                        new org.joml.Vector3f(0.5f), new org.joml.Quaternionf()));
                    d.setInterpolationDuration(2);
                    d.setTeleportDuration(2);
                }));
            }
            spawned.add(world.spawn(at, org.bukkit.entity.TextDisplay.class, td -> {
                td.setVisibleByDefault(false);
                td.setPersistent(false);
                td.text(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize("§a×1"));
                td.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            }));
            for (Entity e : spawned) e.remove();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Préchauffage des scènes impossible : " + e.getMessage());
        }
    }

    /** Arrêt du plugin : toutes les scènes sont retirées (les lots ont été soldés avant). */
    public void shutdown() {
        for (Scene scene : new ArrayList<>(scenes)) scene.abort();
        scenes.clear();
        byOwner.clear();
        prepared.clear();
        if (ticker != null) ticker.cancel();
        HandlerList.unregisterAll(this);
    }

    public SHCrates plugin() {
        return plugin;
    }

    public SceneSettings settings() {
        return plugin.getConfigManager().getSceneSettings();
    }

    public SceneSpots spots() {
        return spots;
    }

    // ------------------------------------------------------------------ placement

    /** Joueur Bedrock (Floodgate) : Geyser n'affiche pas ces entités. */
    public boolean isBedrock(Player player) {
        return player.getUniqueId().getMostSignificantBits() == 0 || player.getName().startsWith(".");
    }

    /** Cherche une place et la garde pour l'ouverture qui suit ; null s'il n'y en a pas. */
    public Placement prepare(Player player, SceneKind kind) {
        Placement placement = placer.find(player, kind);
        if (placement != null) {
            prepared.put(player.getUniqueId(), new Prepared(placement, kind, System.currentTimeMillis()));
        } else {
            prepared.remove(player.getUniqueId());
        }
        return placement;
    }

    /** Place préparée pour ce joueur, ou une nouvelle recherche si elle est trop ancienne. */
    public Placement placementFor(Player player, SceneKind kind) {
        Prepared p = prepared.remove(player.getUniqueId());
        if (p != null && p.kind() == kind && System.currentTimeMillis() - p.at() < PREPARED_TTL_MS) {
            return p.placement();
        }
        return placer.find(player, kind);
    }

    /** Une scène (ou une place préparée) occupe déjà ce volume, marge comprise. */
    boolean isReserved(World world, double x, double y, double z, double r, double h, Scene except) {
        for (Scene scene : scenes) {
            if (scene == except || scene.isEnded()) continue;
            Placement p = scene.placement();
            if (overlaps(world, x, y, z, r, h, p, scene.kind())) return true;
        }
        long now = System.currentTimeMillis();
        for (Prepared pr : prepared.values()) {
            if (now - pr.at() >= PREPARED_TTL_MS) continue;
            if (overlaps(world, x, y, z, r, h, pr.placement(), pr.kind())) return true;
        }
        return false;
    }

    private static boolean overlaps(World world, double x, double y, double z, double r, double h, Placement p, SceneKind kind) {
        if (!p.world().equals(world)) return false;
        double pr = kind.radius() * p.scale();
        double ph = kind.height() * p.scale() + p.skyHeight();
        double dx = p.x() - x;
        double dz = p.z() - z;
        boolean horizontal = Math.sqrt(dx * dx + dz * dz) < r + pr + 1.0;
        boolean vertical = y < p.y() + ph + 0.5 && y + h > p.y() - 0.5;
        return horizontal && vertical;
    }

    // ------------------------------------------------------------------ scènes

    /** Démarre une scène pour ce joueur (celle qu'il avait encore est retirée). */
    public Scene play(Player owner, Crate crate, Reward reward, Placement placement, SceneKind kind, boolean test, SceneListener listener) {
        abort(owner);
        Scene scene = switch (kind) {
            case COFFRE_CELESTE -> new VaultScene(this, owner, crate, reward, placement, test, listener);
            case MIROIR -> new MirrorScene(this, owner, crate, reward, placement, test, listener);
            case PINATA -> new PinataScene(this, owner, crate, reward == null ? List.of() : List.of(reward), placement, test, listener);
        };
        return begin(owner, scene, placement, kind);
    }

    /**
     * Démarre la scène d'une ouverture à plusieurs tirages ({@link SceneKind#multi()}) : tous les lots dans la même scène,
     * dans l'ordre de la liste (du plus courant au plus rare). Chaque lot est donné par {@link MultiSceneListener#onCollect}.
     */
    public Scene playMulti(Player owner, Crate crate, List<Reward> rewards, Placement placement, SceneKind kind, boolean test, MultiSceneListener listener) {
        abort(owner);
        return begin(owner, new PinataScene(this, owner, crate, rewards, placement, test, listener), placement, kind);
    }

    private Scene begin(Player owner, Scene scene, Placement placement, SceneKind kind) {
        scenes.add(scene);
        byOwner.put(owner.getUniqueId(), scene);
        if (placement.turnCamera() && settings().turnCamera()) {
            Location c = scene.center();
            owner.lookAt(c.getX(), c.getY(), c.getZ(), LookAnchor.EYES);
        }
        try {
            scene.start();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Scène " + kind + " impossible à poser pour " + owner.getName(), e);
            scene.abandon();
            return null;
        }
        updateViewers();
        return scene;
    }

    public Scene sceneOf(Player owner) {
        return byOwner.get(owner.getUniqueId());
    }

    /** Retire la scène du joueur, sans suite. */
    public void abort(Player owner) {
        Scene scene = byOwner.get(owner.getUniqueId());
        if (scene != null) scene.abort();
    }

    void forget(Scene scene) {
        scenes.remove(scene);
        byOwner.remove(scene.owner().getUniqueId(), scene);
    }

    /** Rappel dans la barre d'action : s'accroupir passe l'animation. */
    void hint(Player owner) {
        if (!settings().skipBySneak()) return;
        String text = plugin.getMessageManager().getRawPrefixedMessage("scene_skip_hint", org.Cry_Legende.sHCrates.config.MessageManager.ph());
        if (!text.isEmpty()) {
            owner.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(text));
        }
    }

    void tag(Entity entity) {
        entity.getPersistentDataContainer().set(sceneKey, PersistentDataType.BYTE, (byte) 1);
    }

    public boolean isSceneEntity(Entity entity) {
        return entity.getPersistentDataContainer().has(sceneKey, PersistentDataType.BYTE);
    }

    /** Feu d'artifice d'un palier : il éclate sans blesser personne. */
    public void tagFirework(Firework firework) {
        firework.getPersistentDataContainer().set(fireworkKey, PersistentDataType.BYTE, (byte) 1);
    }

    private void tickAll() {
        tickCount++;
        double abandon = settings().abandonDistance();
        for (Scene scene : new ArrayList<>(scenes)) {
            if (scene.isEnded()) {
                scenes.remove(scene);
                continue;
            }
            Player owner = scene.owner();
            if (!owner.isOnline()) {
                scene.abort();
                continue;
            }
            if (!owner.getWorld().equals(scene.placement().world())
                || owner.getLocation().distanceSquared(scene.center()) > abandon * abandon) {
                scene.abandon();
                continue;
            }
            try {
                scene.tick();
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Scène " + scene.kind() + " de " + owner.getName() + " interrompue", e);
                scene.abandon();
            }
        }
        if (tickCount % 10 == 0 && !scenes.isEmpty()) {
            updateViewers();
        }
    }

    /**
     * Spectateurs : joueurs du même monde à moins de {@code visible-autour} blocs, sauf ceux qui ouvrent eux-mêmes (ils ne
     * voient que leur scène), chacun limité aux {@code max-par-spectateur} scènes les plus proches.
     */
    private void updateViewers() {
        SceneSettings settings = settings();
        Map<Scene, List<Player>> desired = new HashMap<>();
        for (Scene scene : scenes) desired.put(scene, new ArrayList<>());
        double radius = settings.visibleRadius();
        if (radius > 0) {
            double r2 = radius * radius;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (byOwner.containsKey(p.getUniqueId())) continue;
                List<Scene> near = new ArrayList<>();
                for (Scene scene : scenes) {
                    if (scene.isEnded() || !scene.placement().world().equals(p.getWorld())) continue;
                    if (p.getLocation().distanceSquared(scene.center()) <= r2) near.add(scene);
                }
                near.sort(Comparator.comparingDouble(sc -> p.getLocation().distanceSquared(sc.center())));
                for (int i = 0; i < Math.min(settings.maxPerSpectator(), near.size()); i++) {
                    desired.get(near.get(i)).add(p);
                }
            }
        }
        for (Map.Entry<Scene, List<Player>> e : desired.entrySet()) {
            if (!e.getKey().isEnded()) e.getKey().updateViewers(e.getValue());
        }
    }

    // ------------------------------------------------------------------ événements

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (byOwner.isEmpty() || !event.isSneaking() || !settings().skipBySneak()) return;
        Scene scene = byOwner.get(event.getPlayer().getUniqueId());
        if (scene != null && !scene.isRevealed() && scene.t >= SKIP_AFTER_TICKS) {
            scene.skip();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent event) {
        if (byOwner.isEmpty() || event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Scene scene = byOwner.get(event.getPlayer().getUniqueId());
        if (scene != null) scene.onOwnerSwing();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (scenes.isEmpty()) return;
        if (isSceneEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFirework(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework fw && fw.getPersistentDataContainer().has(fireworkKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!scenes.isEmpty() && isSceneEntity(event.getRightClicked())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        if (!scenes.isEmpty() && isSceneEntity(event.getRightClicked())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTarget(EntityTargetEvent event) {
        if (!scenes.isEmpty() && event.getTarget() != null && isSceneEntity(event.getTarget())) event.setCancelled(true);
    }

    /** Une scène de test n'a pas d'ouverture derrière elle : elle part avec son joueur. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        prepared.remove(event.getPlayer().getUniqueId());
        Scene scene = byOwner.get(event.getPlayer().getUniqueId());
        if (scene != null && scene.isTest()) scene.abort();
    }
}
