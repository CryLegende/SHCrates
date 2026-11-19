package org.Cry_Legende.sHCrates.scenes;

import org.Cry_Legende.sHCrates.SHCrates;
import org.Cry_Legende.sHCrates.crates.Crate;
import org.Cry_Legende.sHCrates.crates.Reward;
import org.Cry_Legende.sHCrates.crates.Tier;
import org.Cry_Legende.sHCrates.utils.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Scène d'ouverture jouée dans le monde, devant le joueur. Ses entités ne sont pas sauvegardées (rien ne reste après un
 * arrêt brutal) et ne sont envoyées qu'à ceux qui doivent les voir : le joueur qui ouvre, et les spectateurs proches
 * choisis par {@link SceneManager}. Certaines ne sont que pour l'un ou pour les autres ({@link Audience}).
 * <p>
 * Repère local : x vers la droite vue par le joueur, y vers le haut, z vers le joueur ; l'unité est le bloc à l'échelle de
 * la scène. Les mouvements sont interpolés par le client : un paquet par changement, pas un par tick.
 */
public abstract class Scene {

    /** Qui voit une entité de la scène. */
    public enum Audience { ALL, OWNER, SPECTATORS }

    protected final SceneManager manager;
    protected final SHCrates plugin;
    protected final Player owner;
    protected final Crate crate;
    protected final Reward reward;
    protected final Placement place;
    protected final SceneKind kind;
    protected final boolean test;
    protected final World world;
    protected final Vector front;
    protected final Vector right;
    protected final double s;
    /** Palier du lot, null pour un lot sans palier. */
    protected final Tier tier;
    /** Paliers franchis pendant la montée, du plus courant jusqu'à celui du lot. */
    protected final List<Tier> ladder;

    private final Map<Entity, Audience> entities = new LinkedHashMap<>();
    private final Set<UUID> viewers = new LinkedHashSet<>();
    private final List<Location> lights = new ArrayList<>();
    private final SceneListener listener;
    /**
     * Ticks à laisser entre l'apparition d'une entité et son premier mouvement : le serveur l'envoie aux joueurs avec
     * quelques ticks de retard (suivi des entités en parallèle), et un mouvement fait avant arriverait déjà fini.
     */
    protected static final int SETTLE = 4;
    protected int t;
    private boolean revealed;
    private boolean ended;

    protected Scene(SceneManager manager, Player owner, Crate crate, Reward reward, Placement place, SceneKind kind,
                    boolean test, SceneListener listener) {
        this.manager = manager;
        this.plugin = manager.plugin();
        this.owner = owner;
        this.crate = crate;
        this.reward = reward;
        this.place = place;
        this.kind = kind;
        this.test = test;
        this.listener = listener;
        this.world = place.world();
        this.front = place.front().clone();
        this.right = place.right();
        this.s = place.scale();
        this.tier = plugin.getAnimationManager().tierOf(reward);
        List<Tier> steps = new ArrayList<>();
        if (tier != null) {
            plugin.getConfigManager().getTiers().stream()
                .filter(x -> x.rank() <= tier.rank())
                .sorted(Comparator.comparingInt(Tier::rank))
                .forEach(steps::add);
        }
        this.ladder = List.copyOf(steps);
        viewers.add(owner.getUniqueId());
    }

    /**
     * Objets qui défilent avant le lot : tirés selon les chances parmi les lots que le joueur peut gagner, ou parmi tous
     * ceux de l'aperçu quand il en reste trop peu (joueur qui a presque tout) pour que le défilé change d'objet.
     */
    protected List<org.bukkit.inventory.ItemStack> showcase(int count) {
        List<Reward> pool = crate.getEligibleRewards(owner);
        long distinct = pool.stream().map(r -> r.getDisplayItemMaterial()).distinct().count();
        if (distinct < 4) {
            pool = crate.getRewards().stream().filter(Reward::isDisplayInPreview).collect(java.util.stream.Collectors.toList());
        }
        List<org.bukkit.inventory.ItemStack> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Reward r = pool.isEmpty() ? reward : Crate.pick(pool);
            org.bukkit.inventory.ItemStack item = r.getDisplayItemStack().clone();
            item.setAmount(1);
            items.add(item);
        }
        return items;
    }

    // ------------------------------------------------------------------ déroulé

    /** Pose la scène (premières entités) ; appelée une fois. */
    protected abstract void onStart();

    /** Un tick de la scène, {@code t} commence à 0. */
    protected abstract void onTick(int t);

    /** Le joueur s'est accroupi : aller tout de suite au lot. Sans effet une fois le lot sorti. */
    public abstract void skip();

    /** Le joueur qui ouvre a donné un coup de bras (le Miroir l'imite). */
    public void onOwnerSwing() {
    }

    /** Point où le lot apparaît : le feu d'artifice du palier y éclate. */
    public abstract Location focus();

    final void start() {
        onStart();
    }

    final void tick() {
        if (ended) return;
        onTick(t);
        t++;
    }

    /** Le lot sort : récompense et effets du palier (une seule fois). */
    protected final void reveal() {
        if (revealed) return;
        revealed = true;
        listener.onReveal(this);
    }

    /** Fin normale : entités retirées, puis suite (dialogue) côté écouteur. */
    protected final void finish() {
        if (ended) return;
        ended = true;
        cleanup();
        manager.forget(this);
        listener.onEnd(this);
    }

    /** Fin immédiate sans suite (déconnexion, nouvelle ouverture, arrêt du plugin). */
    public final void abort() {
        if (ended) return;
        ended = true;
        cleanup();
        manager.forget(this);
    }

    /** Le joueur s'est éloigné ou a changé de monde : le lot est donné sans la fin de la scène. */
    final void abandon() {
        if (ended) return;
        boolean wasRevealed = revealed;
        revealed = true;
        ended = true;
        cleanup();
        manager.forget(this);
        listener.onAbandon(this, wasRevealed);
    }

    public boolean isRevealed() {
        return revealed;
    }

    public boolean isEnded() {
        return ended;
    }

    public Player owner() {
        return owner;
    }

    public Reward reward() {
        return reward;
    }

    public Crate crate() {
        return crate;
    }

    public boolean isTest() {
        return test;
    }

    public Placement placement() {
        return place;
    }

    public SceneKind kind() {
        return kind;
    }

    // ------------------------------------------------------------------ géométrie

    /** Point du repère local (x droite, y haut, z vers le joueur), en blocs à l'échelle de la scène. */
    protected Location at(double x, double y, double z) {
        return new Location(world,
            place.x() + (right.getX() * x + front.getX() * z) * s,
            place.y() + y * s,
            place.z() + (right.getZ() * x + front.getZ() * z) * s);
    }

    /** Vecteur du repère local en vecteur du monde, à l'échelle de la scène. */
    protected Vector3f offset(double x, double y, double z) {
        return new Vector3f(
            (float) ((right.getX() * x + front.getX() * z) * s),
            (float) (y * s),
            (float) ((right.getZ() * x + front.getZ() * z) * s));
    }

    /** Rotation qui tourne la face +z d'un modèle vers le joueur. */
    protected Quaternionf facing() {
        return new Quaternionf().rotateY((float) Math.atan2(front.getX(), front.getZ()));
    }

    /** Angle de lacet d'une entité qui regarde le joueur. */
    protected float yawTowardOwner() {
        return (float) Math.toDegrees(Math.atan2(-front.getX(), front.getZ()));
    }

    /** Centre de la scène, d'où partent ses sons. */
    public Location center() {
        return new Location(world, place.x(), place.y() + kind.height() * s * 0.45, place.z());
    }

    /**
     * Transformation d'un bloc affiché, pivot au centre du bloc : centre {@code center} (décalage depuis l'entité, déjà à
     * l'échelle), taille {@code size} en blocs, rotation {@code rot}.
     */
    protected static Transformation blockTf(Vector3f center, Quaternionf rot, Vector3f size) {
        Vector3f half = new Vector3f(size).mul(0.5f);
        Vector3f rotatedHalf = new Quaternionf(rot).transform(new Vector3f(half));
        return new Transformation(new Vector3f(center).sub(rotatedHalf), new Quaternionf(rot), new Vector3f(size), new Quaternionf());
    }

    /** Transformation d'un objet ou d'un texte affiché (centré sur son entité). */
    protected static Transformation itemTf(Vector3f translation, Quaternionf rot, float scale) {
        return new Transformation(new Vector3f(translation), new Quaternionf(rot), new Vector3f(scale, scale, scale), new Quaternionf());
    }

    /**
     * Change la transformation, interpolée par le client sur {@code ticks}. Le départ de l'interpolation n'est envoyé au
     * client que s'il change : il alterne donc entre 0 et -1, sinon le client saute directement à l'arrivée.
     */
    protected static void move(Display display, Transformation to, int ticks) {
        if (display == null || !display.isValid()) return;
        display.setInterpolationDelay(display.getInterpolationDelay() == 0 ? -1 : 0);
        display.setInterpolationDuration(Math.max(0, ticks));
        display.setTransformation(to);
    }

    // ------------------------------------------------------------------ entités

    /** Fait apparaître une entité de la scène, invisible sauf pour son public. */
    protected <T extends Entity> T spawn(Location at, Class<T> type, Audience audience, Consumer<T> setup) {
        T entity = world.spawn(at, type, e -> {
            e.setVisibleByDefault(false);
            e.setPersistent(false);
            e.setSilent(true);
            manager.tag(e);
            if (e instanceof Display d) {
                d.setBrightness(new Display.Brightness(15, 15));
                d.setViewRange(1.5f);
            }
            setup.accept(e);
        });
        entities.put(entity, audience);
        for (UUID id : viewers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && allowed(p, audience)) p.showEntity(plugin, entity);
        }
        return entity;
    }

    protected void remove(Entity entity) {
        if (entity == null) return;
        entities.remove(entity);
        if (entity.isValid()) entity.remove();
    }

    private boolean allowed(Player p, Audience audience) {
        boolean isOwner = p.getUniqueId().equals(owner.getUniqueId());
        return switch (audience) {
            case ALL -> true;
            case OWNER -> isOwner;
            case SPECTATORS -> !isOwner;
        };
    }

    // ------------------------------------------------------------------ spectateurs

    /** Joueurs qui voient la scène maintenant (le joueur qui ouvre compris). */
    final Set<UUID> viewerIds() {
        return viewers;
    }

    /** Remplace les spectateurs ; le joueur qui ouvre reste toujours. */
    final void updateViewers(Collection<Player> desired) {
        Set<UUID> next = new LinkedHashSet<>();
        next.add(owner.getUniqueId());
        for (Player p : desired) next.add(p.getUniqueId());
        for (UUID id : new ArrayList<>(viewers)) {
            if (!next.contains(id)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) hideFrom(p);
                viewers.remove(id);
            }
        }
        for (UUID id : next) {
            if (viewers.add(id)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) showTo(p);
            }
        }
    }

    private void showTo(Player p) {
        for (Map.Entry<Entity, Audience> e : entities.entrySet()) {
            if (allowed(p, e.getValue()) && e.getKey().isValid()) p.showEntity(plugin, e.getKey());
        }
        for (Location light : lights) {
            p.sendBlockChange(light, lightData(light));
        }
    }

    private void hideFrom(Player p) {
        for (Entity e : entities.keySet()) {
            if (e.isValid()) p.hideEntity(plugin, e);
        }
        for (Location light : lights) {
            p.sendBlockChange(light, light.getBlock().getBlockData());
        }
    }

    /** Joueurs en ligne qui voient la scène, le joueur qui ouvre compris. */
    public Collection<Player> viewers() {
        return viewersOnline();
    }

    protected Collection<Player> viewersOnline() {
        List<Player> list = new ArrayList<>();
        for (UUID id : viewers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) list.add(p);
        }
        return list;
    }

    // ------------------------------------------------------------------ sons et particules

    /**
     * Son de la scène, joué depuis son centre : au joueur qui ouvre au volume {@code volume}, aux spectateurs au volume
     * {@code spectatorVolume} (0 : ils ne l'entendent pas). La clé se règle dans scenes.sons.
     */
    protected void sound(String key, String fallback, float volume, float pitch, float spectatorVolume) {
        String name = manager.settings().sound(key, fallback);
        Sound sound = SoundUtils.sound(name);
        if (sound == null) return;
        Location from = center();
        for (Player p : viewersOnline()) {
            boolean isOwner = p.equals(owner);
            float v = isOwner ? volume : spectatorVolume;
            if (v <= 0) continue;
            p.playSound(from, sound, SoundCategory.MASTER, v, pitch);
        }
    }

    /** Particules : à tous ceux qui voient la scène, ou seulement au joueur qui ouvre. */
    protected <D> void particle(boolean ownerOnly, Particle particle, Location at, int count, double spread, double speed, D data) {
        for (Player p : ownerOnly ? List.of(owner) : viewersOnline()) {
            try {
                if (data == null) p.spawnParticle(particle, at, count, spread, spread, spread, speed);
                else p.spawnParticle(particle, at, count, spread, spread, spread, speed, data);
            } catch (IllegalArgumentException ignored) {
                // donnée refusée par cette version : particule sautée
            }
        }
    }

    protected static Particle.DustOptions dust(Color color, float size) {
        return new Particle.DustOptions(color, size);
    }

    /** Couleur d'un palier, blanc sans palier. */
    protected static Color colorOf(Tier tier) {
        return tier == null || tier.color() == null ? Color.WHITE : tier.color();
    }

    // ------------------------------------------------------------------ lumière

    /**
     * Lumière envoyée aux seuls joueurs qui voient la scène (bloc de lumière côté client, dans une case vide) : la scène
     * reste lisible la nuit ou dans une grotte. Rendue telle qu'elle était à la fin.
     */
    protected void addLight(Location loc) {
        Location block = loc.getBlock().getLocation();
        if (!block.getBlock().getType().isAir() || lights.contains(block)) return;
        lights.add(block);
        BlockData data = lightData(block);
        for (Player p : viewersOnline()) p.sendBlockChange(block, data);
    }

    private static BlockData lightData(Location loc) {
        BlockData data = Material.LIGHT.createBlockData();
        if (data instanceof Levelled levelled) levelled.setLevel(Math.min(levelled.getMaximumLevel(), 13));
        return data;
    }

    // ------------------------------------------------------------------ nettoyage

    private void cleanup() {
        for (Player p : viewersOnline()) {
            for (Location light : lights) p.sendBlockChange(light, light.getBlock().getBlockData());
        }
        lights.clear();
        for (Entity e : new ArrayList<>(entities.keySet())) {
            if (e.isValid()) e.remove();
        }
        entities.clear();
    }
}
