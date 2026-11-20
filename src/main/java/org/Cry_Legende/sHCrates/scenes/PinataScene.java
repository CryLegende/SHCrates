package org.Cry_Legende.sHCrates.scenes;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.Cry_Legende.sHCrates.config.MessageManager;
import org.Cry_Legende.sHCrates.crates.Crate;
import org.Cry_Legende.sHCrates.crates.Reward;
import org.Cry_Legende.sHCrates.crates.Tier;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Coffre de VoteParty, « la piñata » (une seule scène pour tous les tirages de l'ouverture, option Tirages) :
 * <ol>
 *   <li>un lama-piñata en papier crépon descend du ciel sous trois ballons (ou apparaît sous un plafond) et se balance ;</li>
 *   <li>le joueur le frappe (clic gauche en le visant) : il part en arrière et revient ; au troisième coup, ou seul au bout
 *   de quelques secondes, ou en s'accroupissant, il éclate en morceaux sous une pluie de confettis, les ballons s'envolent ;</li>
 *   <li>les lots en jaillissent l'un après l'autre et retombent en arc de cercle devant le joueur, le meilleur au milieu et
 *   en dernier ; le nom du lot regardé s'affiche au-dessus (pour le joueur seul) ;</li>
 *   <li>ils filent vers le joueur un par un, chacun donné à son arrivée, le meilleur en dernier.</li>
 * </ol>
 * Le modèle vient de Blockbench ({@code scenes/pinata.yml}, voir {@link SceneModel}).
 */
final class PinataScene extends Scene {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    /** Pieds du lama au-dessus du sol, centre de son corps (blocs, échelle 1). */
    private static final double HANG = 0.6;
    private static final double BODY = HANG + 0.66;
    /** Le lama est vu de trois quarts. */
    private static final float YAW = 38f;
    private static final int HITS = 3;
    private static final int HIT_COOLDOWN = 5;
    /** Ticks d'attente des coups avant que la piñata éclate seule. */
    private static final int WAIT = 120;
    /** Lots posés en arc devant le joueur. */
    private static final double SLOT_R = 1.8;
    private static final double ITEM_Y = 0.5;
    private static final double SPILL_GAP = 4;
    private static final int HOLD = 60;
    private static final int COLLECT_GAP = 4;
    private static final int COLLECT_FLIGHT = 6;
    private static final Color[] CONFETTI = {Color.fromRGB(0xFF5FB4), Color.fromRGB(0xFFD640), Color.fromRGB(0x3FD3C8), Color.fromRGB(0xFFF1DC)};
    private static final Material[] CONFETTI_DUST = {Material.PINK_CONCRETE_POWDER, Material.YELLOW_CONCRETE_POWDER, Material.CYAN_CONCRETE_POWDER, Material.WHITE_CONCRETE_POWDER};

    /** Un lot de la scène : son objet, sa place dans l'arc, ses mouvements à venir. */
    private static final class Drop {
        final Reward reward;
        final int index;
        ItemDisplay item;
        TextDisplay amount;
        double angle;
        final List<Object[]> track = new ArrayList<>();
        boolean landed;
        boolean collected;

        Drop(Reward reward, int index) {
            this.reward = reward;
            this.index = index;
        }
    }

    private final List<Drop> drops = new ArrayList<>();
    private final SceneListener follow;
    private final boolean falls;
    private final double lift;
    private SceneModel.Instance model;
    private TextDisplay label;
    private TextDisplay tip;
    private Drop tipOn;

    private int readyAt;
    private int burstAt = -1;
    private int landedAt;
    private int collectAt = -1;
    private int endAt = Integer.MAX_VALUE;
    private int removeModelAt = -1;
    private int hits;
    private int nextHitAt;
    private int confettiUntil;
    private boolean holdCut;

    PinataScene(SceneManager manager, Player owner, Crate crate, List<Reward> rewards, Placement place, boolean test, SceneListener listener) {
        super(manager, owner, crate, best(manager, rewards), place, SceneKind.PINATA, test, listener);
        this.follow = listener;
        for (int i = 0; i < rewards.size(); i++) drops.add(new Drop(rewards.get(i), i));
        this.falls = place.skyHeight() >= 2.5;
        this.lift = falls ? Math.min(place.skyHeight(), 6) : 0;
        placeDrops();
    }

    private static Reward best(SceneManager manager, List<Reward> rewards) {
        return rewards.stream().max(Comparator.comparingInt(r -> manager.plugin().getAnimationManager().tierRank(r))).orElse(null);
    }

    /**
     * Places dans l'arc : du bord vers le milieu, en alternant gauche et droite, dans l'ordre de la liste (du plus courant au
     * plus rare) : le meilleur lot finit au milieu.
     */
    private void placeDrops() {
        int n = drops.size();
        if (n == 0) return;
        double spread = n == 1 ? 0 : Math.min(Math.toRadians(75), Math.toRadians(16) * (n - 1));
        List<Double> angles = new ArrayList<>();
        for (int i = 0; i < n; i++) angles.add(n == 1 ? 0 : -spread + 2 * spread * i / (n - 1));
        angles.sort(Comparator.comparingDouble((Double a) -> -Math.abs(a)).thenComparingDouble(a -> a));
        for (int i = 0; i < n; i++) drops.get(i).angle = angles.get(i);
    }

    // ------------------------------------------------------------------ déroulé

    @Override
    protected void onStart() {
        readyAt = SETTLE + (falls ? 28 : 10);
        Quaternionf orient = facing().rotateY((float) Math.toRadians(YAW));
        SceneModel shape = SceneModel.get(plugin, "pinata");
        model = new SceneModel.Instance(this, shape, at(0, HANG + lift, 0), orient, falls ? (float) s : 0.02f, Audience.ALL);

        String labelText = plugin.getMessageManager().getRawPrefixedMessage("scene_spectator_label",
            MessageManager.ph("%player_name%", owner.getName(), "%crate_display_name%", crate.getDisplayName()));
        if (!labelText.isEmpty()) {
            label = spawn(at(0, 3.9, 0), TextDisplay.class, Audience.SPECTATORS, td -> {
                td.text(LEGACY.deserialize(labelText));
                td.setBillboard(Display.Billboard.CENTER);
                td.setShadowed(true);
                td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                td.setTransformation(itemTf(new Vector3f(), new Quaternionf(), (float) (0.7 * s)));
            });
        }
        if (!falls) {
            particle(false, Particle.CLOUD, at(0, BODY, 0), 14, 0.4 * s, 0.02, null);
        }
        addLight(at(0, 1.5, 0));
        chime(0);
    }

    @Override
    protected void onTick(int t) {
        if (model != null) model.tick(t);

        // 1. arrivée : descente sous les ballons (ou la piñata grandit sur place), puis elle se balance
        if (t == SETTLE) {
            model.play("vol", t);
            if (falls) {
                // deux glissements : vite d'abord, puis elle ralentit en arrivant
                model.moveTo(at(0, HANG + lift * 0.22, 0), 18);
            } else {
                model.scaleTo((float) s, 8);
            }
        }
        if (falls && t == SETTLE + 18) model.moveTo(at(0, HANG, 0), 10);
        if (t == SETTLE + 3 || t == SETTLE + 6) chime(t == SETTLE + 3 ? 1 : 2);
        if (t == readyAt - 2) spawnDrops();
        if (burstAt < 0 && t >= readyAt) {
            // après un coup, elle reprend son balancement
            if (!model.isPlaying("coup") && !model.isPlaying("vol")) model.play("vol", t);
            if ((t - readyAt) % 20 == 0) hint();
            if (t >= readyAt + WAIT) burst(t);
        }

        // 2. éclatement : morceaux, ballons, confettis ; les lots jaillissent
        if (burstAt >= 0) {
            for (Drop d : drops) runTrack(d, t);
            if (t < confettiUntil && t % 2 == 0) confettiRain();
            if (t == removeModelAt && model != null) {
                model.remove();
                model = null;
            }
            if (collectAt < 0 && t >= landedAt + (holdCut ? 0 : HOLD)) startCollect(t);
            if (collectAt < 0 && t >= landedAt && t % 2 == 0) lookTip();
            if (collectAt < 0 && t > burstAt && t % 10 == 0) spin(t);
        }

        // 3. les lots rejoignent le joueur, puis fin
        if (collectAt >= 0) collectTick(t);
        if (t >= endAt) {
            ending();
            finish();
        }
    }

    @Override
    public void skip() {
        if (burstAt < 0) {
            if (t >= SETTLE) {
                if (drops.stream().anyMatch(d -> d.item == null)) spawnDrops();
                burst(t);
            }
        } else if (collectAt < 0) {
            // les lots finissent leur vol, puis filent tout de suite vers le joueur
            if (t >= landedAt) startCollect(t);
            else holdCut = true;
        }
    }

    @Override
    public void onOwnerSwing() {
        if (burstAt >= 0 || t < readyAt || t < nextHitAt || model == null) return;
        Location eye = owner.getEyeLocation();
        Vector dir = eye.getDirection();
        Vector to = at(0, BODY, 0).toVector().subtract(eye.toVector());
        double along = to.dot(dir);
        if (along < 0.5 || along > 7.5) return;
        double off = to.clone().subtract(dir.clone().multiply(along)).length();
        if (off > 0.95 * s) return;
        hits++;
        nextHitAt = t + HIT_COOLDOWN;
        sound("pinata-coup", "entity.player.attack.strong", 1f, 1.1f + 0.1f * hits, 0.5f);
        sound("pinata-papier", "item.book.page_turn", 1f, 0.8f, 0.4f);
        Location body = at(0, BODY, 0);
        for (Color c : CONFETTI) particle(false, Particle.DUST, body, 4, 0.35 * s, 0, dust(c, 1.1f));
        particle(false, Particle.CRIT, body, 6, 0.3 * s, 0.25, null);
        if (hits >= HITS) {
            burst(t);
        } else {
            model.play("coup", t);
            hint();
        }
    }

    @Override
    public Location focus() {
        return at(0, ITEM_Y + 0.6, SLOT_R);
    }

    // ------------------------------------------------------------------ étapes

    private void chime(int note) {
        float[] pitch = {1.0f, 1.26f, 1.5f};
        sound("pinata-arrivee", "block.note_block.chime", 0.8f, pitch[note], 0.4f);
    }

    private void hint() {
        String text = plugin.getMessageManager().getRawPrefixedMessage("scene_pinata_hint", MessageManager.ph(
            "%hits%", String.valueOf(hits), "%needed%", String.valueOf(HITS)));
        if (!text.isEmpty()) owner.sendActionBar(LEGACY.deserialize(text));
    }

    /** Les lots attendent, minuscules, dans le ventre de la piñata : ils pourront bouger dès l'éclatement. */
    private void spawnDrops() {
        Location body = at(0, BODY, 0);
        for (Drop d : drops) {
            if (d.item != null) continue;
            ItemStack shown = d.reward.getDisplayItemStack().clone();
            shown.setAmount(1);
            Tier tier = plugin.getAnimationManager().tierOf(d.reward);
            d.item = spawn(body, ItemDisplay.class, Audience.ALL, id -> {
                id.setItemStack(shown);
                id.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                id.setTransformation(itemTf(new Vector3f(), facing(), 0.001f));
                if (tier != null && tier.rank() >= 2) {
                    id.setGlowing(true);
                    id.setGlowColorOverride(colorOf(tier));
                }
            });
        }
    }

    private void burst(int t) {
        if (burstAt >= 0) return;
        burstAt = t;
        if (model != null) model.play("eclate", t);
        removeModelAt = t + 34;
        confettiUntil = t + 44;
        sound("pinata-eclate", "entity.firework_rocket.large_blast", 1f, 1f, 0.7f);
        sound("pinata-fete", "entity.firework_rocket.twinkle", 0.9f, 1f, 0.5f);
        Location body = at(0, BODY, 0);
        for (Color c : CONFETTI) particle(false, Particle.DUST, body, 18, 0.55 * s, 0, dust(c, 1.4f));
        particle(false, Particle.FIREWORK, body, 26, 0.2 * s, 0.16, null);
        particle(false, Particle.POOF, body, 3, 0.25 * s, 0.02, null);
        owner.sendActionBar(net.kyori.adventure.text.Component.empty());
        owner.sendHurtAnimation(0f);

        // chaque lot jaillit à son tour et retombe à sa place en rebondissant
        int start = t + 4;
        for (Drop d : drops) {
            int at = (int) (start + d.index * SPILL_GAP);
            planSpill(d, at);
        }
        landedAt = (int) (start + Math.max(0, drops.size() - 1) * SPILL_GAP + 16);
        if (drops.isEmpty()) landedAt = t + 30;
        tip = spawn(at(0, ITEM_Y, 0), TextDisplay.class, Audience.OWNER, td -> {
            td.text(net.kyori.adventure.text.Component.empty());
            td.setBillboard(Display.Billboard.CENTER);
            td.setShadowed(true);
            td.setBackgroundColor(Color.fromARGB(120, 0, 0, 0));
            td.setTransformation(itemTf(new Vector3f(), new Quaternionf(), 0.001f));
        });
    }

    /** Vol d'un lot : il sort du ventre, monte, retombe à sa place, rebondit une fois. Translations depuis le ventre. */
    private void planSpill(Drop d, int at) {
        double sx = Math.sin(d.angle) * SLOT_R, sz = Math.cos(d.angle) * SLOT_R;
        double dy = ITEM_Y - BODY;
        float size = (float) (itemSize(d) * s);
        float spin = 0;
        double[][] path = {
            {sx * 0.35, 0.75, sz * 0.35, 3},
            {sx * 0.7, 0.7, sz * 0.7, 3},
            {sx, dy - 0.12, sz, 4},
            {sx, dy + 0.22, sz, 3},
            {sx, dy, sz, 3},
        };
        int when = at;
        d.track.add(new Object[]{when, null, 0, "pop"});
        for (double[] step : path) {
            spin += 120;
            boolean squash = step == path[2];
            Vector3f scale = squash ? new Vector3f(size * 1.12f, size * 0.85f, size * 1.12f) : new Vector3f(size);
            Transformation tf = new Transformation(offset(step[0], step[1], step[2]),
                facing().rotateY((float) Math.toRadians(spin)), scale, new Quaternionf());
            d.track.add(new Object[]{when, tf, (int) step[3], squash ? "land" : null});
            when += (int) step[3];
        }
        d.track.add(new Object[]{when, null, 0, "landed"});
    }

    private double itemSize(Drop d) {
        return d.reward.getDisplayItemMaterial() == Material.PLAYER_HEAD ? 0.68 : 0.62;
    }

    private void runTrack(Drop d, int t) {
        while (!d.track.isEmpty() && (int) d.track.get(0)[0] <= t) {
            Object[] step = d.track.remove(0);
            Transformation tf = (Transformation) step[1];
            if (tf != null) move(d.item, tf, (int) step[2]);
            String event = (String) step[3];
            if (event == null) continue;
            switch (event) {
                case "pop" -> {
                    float pitch = (float) Math.min(2.0, 0.9 + d.index * 0.12);
                    sound("pinata-lot", "entity.chicken.egg", 0.9f, pitch, 0.4f);
                    particle(false, Particle.DUST, at(0, BODY, 0), 5, 0.2 * s, 0, dust(CONFETTI[d.index % CONFETTI.length], 1.0f));
                }
                case "land" -> {
                    Location slot = slot(d, 0.1);
                    particle(false, Particle.DUST, slot, 4, 0.18 * s, 0, dust(CONFETTI[d.index % CONFETTI.length], 0.8f));
                    sound("pinata-pose", "block.wool.place", 0.6f, 1.3f, 0f);
                }
                case "landed" -> landed(d);
                default -> {
                }
            }
        }
    }

    private Location slot(Drop d, double y) {
        return at(Math.sin(d.angle) * SLOT_R, y, Math.cos(d.angle) * SLOT_R);
    }

    private void landed(Drop d) {
        d.landed = true;
        Tier tier = plugin.getAnimationManager().tierOf(d.reward);
        if (tier != null && tier.rank() >= 2) {
            Color c = colorOf(tier);
            for (int i = 0; i < 8; i++) particle(false, Particle.DUST, slot(d, ITEM_Y + 0.25 + i * 0.22), 1, 0.03, 0, dust(c, 0.9f));
            if (tier.firework()) plugin.getAnimationManager().spawnFirework(slot(d, ITEM_Y + 1.2), this);
        }
        int amount = d.reward.getDisplayAmount();
        if (amount > 1) {
            d.amount = spawn(slot(d, ITEM_Y - 0.5), TextDisplay.class, Audience.ALL, td -> {
                td.text(LEGACY.deserialize("§f×" + amount));
                td.setBillboard(Display.Billboard.CENTER);
                td.setShadowed(true);
                td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                td.setTransformation(itemTf(new Vector3f(), new Quaternionf(), (float) (0.55 * s)));
            });
        }
        // le nom du dernier lot posé s'affiche ; une fois tous posés, celui que le joueur regarde
        showTip(d);
    }

    private void showTip(Drop d) {
        if (tip == null || d == tipOn) return;
        tipOn = d;
        if (d == null) {
            move(tip, itemTf(new Vector3f(), new Quaternionf(), 0.001f), 2);
            return;
        }
        tip.text(LEGACY.deserialize(d.reward.getDisplayName()));
        double x = Math.sin(d.angle) * SLOT_R, z = Math.cos(d.angle) * SLOT_R;
        move(tip, itemTf(offset(x, 0.5, z), new Quaternionf(), (float) (0.62 * s)), 2);
    }

    /** Lot que le joueur vise (le plus proche de son regard) ; sinon le meilleur, au milieu. */
    private void lookTip() {
        Location eye = owner.getEyeLocation();
        Vector dir = eye.getDirection();
        Drop best = null;
        double bestOff = 0.45 * s;
        for (Drop d : drops) {
            if (!d.landed || d.collected) continue;
            Vector to = slot(d, ITEM_Y).toVector().subtract(eye.toVector());
            double along = to.dot(dir);
            if (along < 0.5) continue;
            double off = to.clone().subtract(dir.clone().multiply(along)).length();
            if (off < bestOff) {
                bestOff = off;
                best = d;
            }
        }
        if (best == null && drops.stream().allMatch(d -> d.landed)) best = drops.get(drops.size() - 1);
        if (best != null) showTip(best);
    }

    private void spin(int t) {
        for (Drop d : drops) {
            if (!d.landed || d.collected || d.item == null || !d.item.isValid()) continue;
            Transformation cur = d.item.getTransformation();
            Quaternionf rot = facing().rotateY((float) Math.toRadians(120 * (t / 10 + d.index)));
            move(d.item, new Transformation(cur.getTranslation(), rot, cur.getScale(), new Quaternionf()), 10);
        }
    }

    private void confettiRain() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 4; i++) {
            double a = r.nextDouble(Math.PI * 2), d = r.nextDouble(1.8);
            BlockData data = CONFETTI_DUST[r.nextInt(CONFETTI_DUST.length)].createBlockData();
            particle(false, Particle.FALLING_DUST, at(Math.cos(a) * d, 2.3 + r.nextDouble(1.2), Math.sin(a) * d), 1, 0, 0, data);
        }
    }

    private void startCollect(int t) {
        if (collectAt >= 0) return;
        collectAt = t;
        showTip(null);
        reveal();
        endAt = t + Math.max(0, drops.size() - 1) * COLLECT_GAP + COLLECT_FLIGHT + 12;
    }

    private void collectTick(int t) {
        for (Drop d : drops) {
            if (d.collected) continue;
            int leave = collectAt + d.index * COLLECT_GAP;
            if (t == leave && d.item != null && d.item.isValid()) {
                Location from = d.item.getLocation();
                Location to = owner.getLocation().add(0, 1.0, 0);
                Vector3f delta = new Vector3f((float) (to.getX() - from.getX()), (float) (to.getY() - from.getY()), (float) (to.getZ() - from.getZ()));
                move(d.item, new Transformation(delta, d.item.getTransformation().getLeftRotation(), new Vector3f(0.12f * (float) s), new Quaternionf()), COLLECT_FLIGHT);
                if (d.amount != null) remove(d.amount);
                sound("pinata-envol", "entity.breeze.jump", 0.35f, 1.6f, 0f);
            }
            if (t == leave + COLLECT_FLIGHT) {
                d.collected = true;
                remove(d.item);
                sound("pinata-ramasse", "entity.item.pickup", 0.9f, (float) Math.min(2.0, 0.9 + d.index * 0.1), 0f);
                if (follow instanceof MultiSceneListener multi) multi.onCollect(this, d.index);
            }
        }
    }

    private void ending() {
        Tier tier = plugin.getAnimationManager().tierOf(reward);
        if (tier != null && tier.rank() >= 2) {
            sound("pinata-fin", "entity.player.levelup", 0.6f, 1.2f, 0f);
        }
        if (model != null) model.remove();
        move(label, itemTf(new Vector3f(), new Quaternionf(), 0.001f), 4);
    }
}
