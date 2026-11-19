package org.Cry_Legende.sHCrates.scenes;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.Cry_Legende.sHCrates.crates.Crate;
import org.Cry_Legende.sHCrates.crates.Reward;
import org.Cry_Legende.sHCrates.crates.Tier;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Box Légendaire, « le Coffre Céleste » (environ 8 à 9 s, s'accroupir passe au lot) :
 * <ol>
 *   <li>un coffre-fort sinistre (vault ominous) tombe du ciel dans une traînée de feu d'âme, ou apparaît sur place sous un
 *   plafond ; impact sourd, poussière, léger choc de caméra pour le joueur ;</li>
 *   <li>il s'éveille : six lots de la box tournent autour de lui comme une machine à sous qui ralentit ;</li>
 *   <li>les lots sont aspirés, le coffre tremble et son contour monte de palier en palier jusqu'à celui du lot ;</li>
 *   <li>il s'ouvre et éjecte le lot, qui flotte en grand avec son nom et son palier ;</li>
 *   <li>tout rétrécit et disparaît.</li>
 * </ol>
 */
final class VaultScene extends Scene {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final int ORBIT_COUNT = 6;
    private static final double ORBIT_RADIUS = 1.25;
    private static final double VAULT_SIZE = 1.15;
    private static final double VAULT_CENTER = VAULT_SIZE / 2;
    private static final double ORBIT_Y = 1.1;
    private static final double REWARD_Y = 1.85;
    private static final Color OMINOUS = Color.fromRGB(0x3FD7E0);

    private final double fallFrom;
    private final boolean falls;
    private final List<ItemStack> pool = new ArrayList<>();

    private BlockDisplay vault;
    private TextDisplay label;
    private final List<ItemDisplay> orbit = new ArrayList<>();
    private ItemDisplay prize;
    private TextDisplay nameText;
    private TextDisplay tierText;

    // moments de la scène (ticks)
    private int impactAt;
    private int awakeAt;
    private int orbitAt;
    private int collapseAt;
    private int climbAt;
    private int unlockAt;
    private int revealAt;
    private int outroAt;
    private int endAt;
    private boolean skipped;
    private double orbitAngle;
    private int nextSwap;
    private int swapInterval;
    private int swapCount;
    private int swapIndex;
    private int climbStep = -1;

    VaultScene(SceneManager manager, Player owner, Crate crate, Reward reward, Placement place, boolean test, SceneListener listener) {
        super(manager, owner, crate, reward, place, SceneKind.COFFRE_CELESTE, test, listener);
        this.falls = place.skyHeight() >= 4;
        this.fallFrom = falls ? Math.min(place.skyHeight(), 9) + 1.5 : 0;
        pool.addAll(showcase(24));
    }

    // ------------------------------------------------------------------ déroulé

    @Override
    protected void onStart() {
        impactAt = falls ? SETTLE + 14 : SETTLE + 10;
        awakeAt = impactAt + 8;
        orbitAt = awakeAt + 9;
        collapseAt = orbitAt + 46;
        climbAt = collapseAt + 8;
        int steps = Math.max(1, ladder.size());
        unlockAt = climbAt + steps * 10;
        revealAt = unlockAt + 6;
        outroAt = revealAt + 50;
        endAt = outroAt + 7;

        BlockData inactive = vaultData("inactive");
        double startY = falls ? fallFrom : 0;
        float startScale = falls ? 1f : 0.02f;
        vault = spawn(at(0, 0, 0), BlockDisplay.class, Audience.ALL, d -> {
            d.setBlock(inactive);
            d.setTransformation(vaultTf(startY, startScale, 0f));
            d.setShadowRadius(0.6f);
            d.setShadowStrength(0.6f);
        });
        String labelText = plugin.getMessageManager().getRawPrefixedMessage("scene_spectator_label",
            org.Cry_Legende.sHCrates.config.MessageManager.ph("%player_name%", owner.getName(), "%crate_display_name%", crate.getDisplayName()));
        if (!labelText.isEmpty()) {
            label = spawn(at(0, 2.95, 0), TextDisplay.class, Audience.SPECTATORS, td -> {
                td.text(LEGACY.deserialize(labelText));
                td.setBillboard(Display.Billboard.CENTER);
                td.setShadowed(true);
                td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                td.setTransformation(itemTf(new Vector3f(), new Quaternionf(), (float) (0.7 * s)));
            });
        }
        if (falls) {
            sound("coffre-chute", "entity.phantom.swoop", 1f, 0.6f, 0.5f);
        } else {
            sound("coffre-apparition", "block.trial_spawner.ominous_activate", 1f, 1f, 0.5f);
            particle(false, Particle.OMINOUS_SPAWNING, at(0, VAULT_CENTER, 0), 30, 0.5 * s, 0.05, null);
        }
        manager.hint(owner);
    }

    @Override
    protected void onTick(int t) {
        // 1. chute ou apparition
        if (t == SETTLE) {
            move(vault, vaultTf(0, 1f, 0f), impactAt - SETTLE);
        }
        if (!skipped && falls && t > SETTLE && t < impactAt) {
            double progress = (t - SETTLE) / (double) (impactAt - SETTLE);
            Location trail = at(0, VAULT_CENTER + fallFrom * (1 - progress), 0);
            particle(true, Particle.SOUL_FIRE_FLAME, trail, 4, 0.18 * s, 0.01, null);
            particle(true, Particle.LARGE_SMOKE, trail, 1, 0.2 * s, 0.01, null);
            particle(false, Particle.SOUL_FIRE_FLAME, trail, 1, 0.1 * s, 0.0, null);
        }
        if (t == impactAt && !skipped) impact();
        if (t == impactAt + 2 && falls && !skipped) move(vault, vaultTf(0, 1f, 0f), 3);

        // 2. éveil et ronde des lots
        if (!skipped && t == awakeAt) awake();
        if (!skipped && t == awakeAt + SETTLE) spreadOrbit();
        if (!skipped && t >= orbitAt && t < collapseAt) orbitTick(t);
        if (!skipped && t == collapseAt) collapse();
        if (!skipped && t == collapseAt + 7) clearOrbit();

        // 3. montée de palier en palier
        if (!skipped && t >= climbAt && t < unlockAt) climbTick(t);

        // 4. ouverture et lot
        if (t == unlockAt) unlock();
        if (t == revealAt) eject();
        if (t == revealAt + 8) showTexts();
        if (t > revealAt + 8 && t < outroAt) hold(t);
        if (t == revealAt + 18) vault.setBlock(vaultData("active"));

        // 5. fin
        if (t == outroAt) outro();
        if (t >= endAt) finish();
    }

    @Override
    public void skip() {
        if (skipped || isRevealed() || t >= unlockAt) return;
        skipped = true;
        clearOrbit();
        vault.setGlowing(false);
        move(vault, vaultTf(0, 1f, 0f), 0);
        unlockAt = t + 1;
        revealAt = unlockAt + 3;
        outroAt = revealAt + 44;
        endAt = outroAt + 7;
    }

    @Override
    public Location focus() {
        return at(0, REWARD_Y, 0);
    }

    // ------------------------------------------------------------------ étapes

    private void impact() {
        if (falls) {
            sound("coffre-impact", "item.mace.smash_ground_heavy", 1f, 0.9f, 0.7f);
            // écrasement à l'impact, rebond deux ticks plus tard
            move(vault, new Transformation(vaultTf(0, 1f, 0f).getTranslation(), facing(),
                new Vector3f((float) (VAULT_SIZE * s * 1.12), (float) (VAULT_SIZE * s * 0.82), (float) (VAULT_SIZE * s * 1.12)), new Quaternionf()), 2);
            owner.sendHurtAnimation(0f);
        } else {
            sound("coffre-pose", "block.heavy_core.place", 1f, 0.8f, 0.5f);
        }
        BlockData ground = at(0, -0.5, 0).getBlock().getBlockData();
        boolean onGround = !ground.getMaterial().isAir();
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16;
            Location ring = at(Math.cos(a) * 0.9, 0.05, Math.sin(a) * 0.9);
            if (onGround) particle(false, Particle.DUST_PILLAR, ring, 2, 0.05, 0.0, ground);
            else particle(false, Particle.POOF, ring, 1, 0.05, 0.02, null);
        }
        particle(false, Particle.GUST_EMITTER_SMALL, at(0, 0.2, 0), 1, 0, 0, null);
    }

    private void awake() {
        vault.setBlock(vaultData("active"));
        sound("coffre-eveil", "block.vault.activate", 1f, 1f, 0.6f);
        particle(false, Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, at(0, VAULT_SIZE, 0), 12, 0.35 * s, 0.01, null);
        Location centerLoc = at(0, ORBIT_Y, 0);
        for (int i = 0; i < ORBIT_COUNT; i++) {
            ItemStack item = pool.get(i % pool.size());
            ItemDisplay d = spawn(centerLoc, ItemDisplay.class, Audience.ALL, id -> {
                id.setItemStack(item);
                id.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                id.setTransformation(itemTf(new Vector3f(), facing(), 0.01f));
            });
            orbit.add(d);
        }
        orbitAngle = 0;
        swapInterval = 3;
        nextSwap = orbitAt;
        swapIndex = 0;
    }

    private void spreadOrbit() {
        for (int i = 0; i < orbit.size(); i++) {
            move(orbit.get(i), orbitTf(i, 0), 8);
        }
    }

    private Transformation orbitTf(int i, double bob) {
        double a = orbitAngle + Math.PI * 2 * i / ORBIT_COUNT;
        Vector3f pos = offset(Math.cos(a) * ORBIT_RADIUS, bob, Math.sin(a) * ORBIT_RADIUS);
        return itemTf(pos, facing(), (float) (0.68 * s));
    }

    private void orbitTick(int t) {
        if ((t - orbitAt) % 3 == 0) {
            orbitAngle += Math.toRadians(16);
            for (int i = 0; i < orbit.size(); i++) {
                double bob = Math.sin(orbitAngle * 2 + i) * 0.06;
                move(orbit.get(i), orbitTf(i, bob), 3);
            }
        }
        if (t >= nextSwap && t < collapseAt - 4) {
            // machine à sous : un lot change à chaque cran, de plus en plus lentement
            ItemDisplay d = orbit.get(swapIndex % orbit.size());
            d.setItemStack(pool.get((ORBIT_COUNT + swapCount) % pool.size()));
            swapIndex += 5;
            swapCount++;
            sound("coffre-cran", "block.note_block.chime", 0.6f, (float) Math.min(2.0, 0.7 + swapCount * 0.07), 0f);
            swapInterval = (int) Math.min(9, Math.round(swapInterval * 1.16 + 0.4));
            nextSwap = t + swapInterval;
        }
        if ((t - orbitAt) % 5 == 0) {
            Location target = at(0, VAULT_CENTER, 0);
            for (ItemDisplay d : orbit) {
                Location from = d.getLocation().add(d.getTransformation().getTranslation().x(),
                    d.getTransformation().getTranslation().y(), d.getTransformation().getTranslation().z());
                particle(true, Particle.TRAIL, from, 1, 0, 0, new Particle.Trail(target, OMINOUS, 14));
            }
        }
    }

    private void collapse() {
        sound("coffre-aspiration", "block.trial_spawner.about_to_spawn_item", 1f, 1f, 0.6f);
        for (ItemDisplay d : orbit) {
            move(d, itemTf(offset(0, VAULT_CENTER - ORBIT_Y, 0), facing(), 0.01f), 7);
        }
    }

    private void clearOrbit() {
        for (ItemDisplay d : orbit) remove(d);
        orbit.clear();
    }

    private void climbTick(int t) {
        int step = (t - climbAt) / 10;
        int within = (t - climbAt) % 10;
        if (step != climbStep) {
            climbStep = step;
            Tier stepTier = ladder.isEmpty() ? null : ladder.get(Math.min(step, ladder.size() - 1));
            vault.setGlowing(true);
            vault.setGlowColorOverride(colorOf(stepTier));
            sound("coffre-palier", "block.respawn_anchor.charge", 0.9f, (float) (0.7 + 0.18 * step), 0f);
            particle(false, Particle.DUST, at(0, VAULT_SIZE + 0.1, 0), 14, 0.45 * s, 0, dust(colorOf(stepTier), 1.4f));
        }
        if (t % 2 == 0) {
            // tremblement, plus fort à chaque palier, et petite pulsation au début de chaque palier
            float jitter = (float) Math.toRadians((t % 4 == 0 ? 1 : -1) * (2.5 + 1.5 * step));
            float pulse = within < 3 ? 1.07f : 1f;
            move(vault, vaultTf(0, pulse, jitter), 2);
        }
    }

    private void unlock() {
        vault.setGlowing(true);
        vault.setGlowColorOverride(colorOf(tier));
        vault.setBlock(vaultData("unlocking"));
        move(vault, vaultTf(0, 1f, 0f), 2);
        sound("coffre-ouverture", "block.vault.open_shutter", 1f, 1f, 0.7f);
    }

    private void eject() {
        vault.setBlock(vaultData("ejecting"));
        sound("coffre-ejection", "block.vault.eject_item", 1f, 1f, 0.8f);
        ItemStack shown = reward.getDisplayItemStack().clone();
        shown.setAmount(1);
        boolean head = shown.getType() == Material.PLAYER_HEAD;
        float finalScale = (float) ((head ? 1.15 : 1.6) * s);
        prize = spawn(at(0, VAULT_CENTER, 0), ItemDisplay.class, Audience.ALL, d -> {
            d.setItemStack(shown);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTransformation(itemTf(new Vector3f(), facing(), 0.2f * (float) s));
            if (tier != null && tier.rank() >= 2) {
                d.setGlowing(true);
                d.setGlowColorOverride(colorOf(tier));
            }
        });
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (prize != null && prize.isValid()) {
                move(prize, itemTf(offset(0, REWARD_Y - VAULT_CENTER, 0), facing(), finalScale), 8);
            }
        }, SETTLE);
        Color c = colorOf(tier);
        particle(false, Particle.DUST, at(0, VAULT_SIZE, 0), 16, 0.35 * s, 0, dust(c, 1.4f));
        if (tier != null && tier.totem()) {
            // plus haut palier : rayon de lumière vers le ciel
            for (int i = 0; i < 24; i++) {
                particle(false, Particle.END_ROD, at(0, VAULT_SIZE + i * 0.35, 0), 1, 0.03, 0.0, null);
            }
        }
        reveal();
    }

    private void showTexts() {
        String name = reward.getDisplayName();
        nameText = spawn(at(0, 2.62, 0), TextDisplay.class, Audience.ALL, td -> {
            td.text(LEGACY.deserialize(name));
            td.setBillboard(Display.Billboard.CENTER);
            td.setShadowed(true);
            td.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
            td.setTransformation(itemTf(new Vector3f(), new Quaternionf(), 0.01f));
        });
        if (tier != null) {
            tierText = spawn(at(0, 2.38, 0), TextDisplay.class, Audience.ALL, td -> {
                td.text(LEGACY.deserialize(tier.name()));
                td.setBillboard(Display.Billboard.CENTER);
                td.setShadowed(true);
                td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                td.setTransformation(itemTf(new Vector3f(), new Quaternionf(), 0.01f));
            });
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            move(nameText, itemTf(new Vector3f(), new Quaternionf(), (float) (1.15 * s)), 4);
            move(tierText, itemTf(new Vector3f(), new Quaternionf(), (float) (0.85 * s)), 4);
        }, SETTLE);
    }

    private void hold(int t) {
        if (prize != null && (t - revealAt) % 10 == 0) {
            // tour sur lui-même par crans de 120° (le client prend toujours le chemin le plus court)
            double turns = (t - revealAt) / 10.0;
            Quaternionf spin = facing().rotateY((float) Math.toRadians(120 * turns));
            Transformation cur = prize.getTransformation();
            move(prize, new Transformation(cur.getTranslation(), spin, cur.getScale(), new Quaternionf()), 10);
        }
        if ((t - revealAt) % 6 == 0) {
            particle(true, Particle.END_ROD, focus(), 2, 0.4 * s, 0.01, null);
            particle(false, Particle.DUST, focus(), 3, 0.5 * s, 0, dust(colorOf(tier), 1.0f));
        }
    }

    private void outro() {
        sound("coffre-fin", "block.vault.deactivate", 0.8f, 1f, 0f);
        // le titre du palier, qui aurait caché le lot pendant la scène, arrive quand elle s'efface
        plugin.getAnimationManager().showTierTitle(owner, reward);
        vault.setGlowing(false);
        vault.setBlock(vaultData("inactive"));
        move(vault, vaultTf(0, 0.01f, 0f), 6);
        if (prize != null) {
            Transformation cur = prize.getTransformation();
            move(prize, new Transformation(cur.getTranslation(), cur.getLeftRotation(), new Vector3f(0.01f), new Quaternionf()), 6);
        }
        move(nameText, itemTf(new Vector3f(), new Quaternionf(), 0.01f), 6);
        move(tierText, itemTf(new Vector3f(), new Quaternionf(), 0.01f), 6);
        move(label, itemTf(new Vector3f(), new Quaternionf(), 0.01f), 6);
        particle(false, Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, at(0, VAULT_CENTER, 0), 10, 0.4 * s, 0.01, null);
    }

    // ------------------------------------------------------------------ outils

    /** Coffre à la hauteur {@code lift} au-dessus de sa place, à l'échelle {@code scale}, tourné de {@code yawJitter}. */
    private Transformation vaultTf(double lift, float scale, float yawJitter) {
        double size = VAULT_SIZE * s * scale;
        Quaternionf rot = facing().rotateY(yawJitter);
        Vector3f center = new Vector3f(0f, (float) ((VAULT_CENTER + lift) * s), 0f);
        // la base reste au sol quand le coffre grossit ou rapetisse
        center.y = (float) (lift * s + size / 2);
        return blockTf(center, rot, new Vector3f((float) size, (float) size, (float) size));
    }

    private static BlockData vaultData(String state) {
        return Bukkit.createBlockData("minecraft:vault[facing=south,ominous=true,vault_state=" + state + "]");
    }
}
