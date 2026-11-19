package org.Cry_Legende.sHCrates.scenes;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.Cry_Legende.sHCrates.config.MessageManager;
import org.Cry_Legende.sHCrates.crates.Crate;
import org.Cry_Legende.sHCrates.crates.Reward;
import org.Cry_Legende.sHCrates.crates.Tier;
import org.Cry_Legende.sHCrates.crates.TryOn;
import org.Cry_Legende.sHCrates.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MainHand;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Box Cosmétique, « le Miroir » (environ 8 s, s'accroupir passe au lot) :
 * <ol>
 *   <li>un miroir doré se forme devant le joueur ; dedans apparaît son reflet (Mannequin avec son skin et son pseudo), qui
 *   suit son regard et ses coups de bras comme un vrai reflet ;</li>
 *   <li>au-dessus du miroir, des cosmétiques défilent puis ralentissent ; le cadre monte de palier en palier ;</li>
 *   <li>le cosmétique gagné entre dans le miroir et le reflet l'essaie : titre devant son pseudo, compagnon sur l'épaule,
 *   ou particules autour de lui (clé Essayage du lot) ;</li>
 *   <li>le reflet fait signe, puis le miroir éclate en paillettes.</li>
 * </ol>
 */
final class MirrorScene extends Scene {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final double HALF_WIDTH = 0.78;
    private static final double FRAME_TOP = 2.22;
    private static final double BAR = 0.11;
    private static final double REFLECTION_Z = -0.5;
    private static final double REFLECTION_SCALE = 0.88;
    private static final double REEL_Y = 2.95;
    private static final double[] REEL_X = {-0.72, 0, 0.72};
    private static final float REEL_CENTER = 0.62f;
    private static final float REEL_SIDE = 0.38f;

    private final List<BlockDisplay> frame = new ArrayList<>();
    private final List<Transformation> frameFinal = new ArrayList<>();
    private BlockDisplay glass;
    private Transformation glassFinal;
    private Mannequin reflection;
    private TextDisplay label;
    private TextDisplay nameText;
    private TextDisplay tierText;
    private ItemDisplay companion;
    private ItemDisplay floating;
    /** Bobine du défilé : [gauche, centre, droite]. */
    private final ItemDisplay[] reel = new ItemDisplay[3];
    private final List<ItemStack> sequence = new ArrayList<>();
    /** Position locale (x) où chaque cosmétique du défilé est apparu : ses glissements sont des translations depuis là. */
    private final java.util.Map<ItemDisplay, Double> spawnX = new java.util.HashMap<>();
    private int seqIndex;

    private int appearAt;
    private int reelAt;
    private int climbAt;
    private int revealAt;
    private int tryOnAt;
    private int outroAt;
    private int endAt;
    private int nextSwap;
    private int swapInterval;
    private int swaps;
    private int totalSwaps;
    private int climbStep = -1;
    private boolean skipped;
    private boolean leftHanded;

    MirrorScene(SceneManager manager, Player owner, Crate crate, Reward reward, Placement place, boolean test, SceneListener listener) {
        super(manager, owner, crate, reward, place, SceneKind.MIROIR, test, listener);
        // le défilé se termine sur le lot gagné, au centre
        totalSwaps = 12;
        sequence.addAll(showcase(totalSwaps + 2));
        ItemStack won = reward.getDisplayItemStack().clone();
        won.setAmount(1);
        sequence.add(totalSwaps + 1, won);
    }

    // ------------------------------------------------------------------ déroulé

    @Override
    protected void onStart() {
        appearAt = 18;
        reelAt = 24;
        swapInterval = 3;
        nextSwap = reelAt + 6;
        // le défilé dure jusqu'au dernier cran, la montée commence juste après
        int last = nextSwap;
        int interval = swapInterval;
        for (int i = 0; i < totalSwaps; i++) {
            last += interval;
            interval = (int) Math.min(10, Math.round(interval * 1.15 + 0.4));
        }
        climbAt = last + 6;
        int steps = Math.max(1, ladder.size());
        revealAt = climbAt + steps * 10 + 2;
        tryOnAt = revealAt + 6;
        outroAt = revealAt + 58;
        endAt = outroAt + 7;
        leftHanded = owner.getMainHand() == MainHand.LEFT;

        // cadre doré (barres fines) et couronne d'améthyste, qui grandissent depuis le centre
        Quaternionf rot = facing();
        addBar(blockTf(offset(-HALF_WIDTH, FRAME_TOP / 2, 0), rot, sizeOf(BAR, FRAME_TOP + BAR, BAR)), Material.GOLD_BLOCK);
        addBar(blockTf(offset(HALF_WIDTH, FRAME_TOP / 2, 0), rot, sizeOf(BAR, FRAME_TOP + BAR, BAR)), Material.GOLD_BLOCK);
        addBar(blockTf(offset(0, FRAME_TOP, 0), rot, sizeOf(HALF_WIDTH * 2 + BAR, BAR, BAR)), Material.GOLD_BLOCK);
        addBar(blockTf(offset(0, BAR / 2, 0), rot, sizeOf(HALF_WIDTH * 2 + BAR, BAR, BAR)), Material.GOLD_BLOCK);
        addBar(blockTf(offset(0, FRAME_TOP + 0.2, 0), rot, sizeOf(0.42, 0.42, 0.42)), Material.AMETHYST_CLUSTER);
        glassFinal = blockTf(offset(0, FRAME_TOP / 2, 0.0), rot, sizeOf(HALF_WIDTH * 2 - 0.02, FRAME_TOP - 0.06, 0.02));
        glass = spawn(at(0, 0, 0), BlockDisplay.class, Audience.ALL, d -> {
            d.setBlock(Material.LIGHT_BLUE_STAINED_GLASS.createBlockData());
            d.setTransformation(collapsed(glassFinal));
        });

        String labelText = plugin.getMessageManager().getRawPrefixedMessage("scene_spectator_label",
            MessageManager.ph("%player_name%", owner.getName(), "%crate_display_name%", crate.getDisplayName()));
        if (!labelText.isEmpty()) {
            label = spawn(at(0, 3.38, 0), TextDisplay.class, Audience.SPECTATORS, td -> {
                td.text(LEGACY.deserialize(labelText));
                td.setBillboard(Display.Billboard.CENTER);
                td.setShadowed(true);
                td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                td.setTransformation(itemTf(new Vector3f(), new Quaternionf(), (float) (0.7 * s)));
            });
        }
        // lumière devant le miroir : le reflet reste visible la nuit
        addLight(at(0, 1.0, 0.7));
        addLight(at(0, 2.0, 0.7));
        manager.hint(owner);
    }

    @Override
    protected void onTick(int t) {
        if (t == SETTLE) {
            sound("miroir-cadre", "block.amethyst_block.resonate", 1f, 1.2f, 0.5f);
            for (int i = 0; i < frame.size(); i++) move(frame.get(i), frameFinal.get(i), 8);
        }
        if (t > SETTLE && t < 14 && t % 2 == 0) {
            particle(false, Particle.END_ROD, at((Math.random() - 0.5) * 1.6, Math.random() * FRAME_TOP, 0.05), 1, 0.02, 0.0, null);
        }
        if (t == 10) {
            sound("miroir-verre", "block.beacon.power_select", 0.8f, 1.6f, 0.4f);
            move(glass, glassFinal, 6);
        }
        if (t == appearAt && reflection == null) appearReflection();
        if (reflection != null && t % 2 == 0 && t < outroAt) mirrorLook();

        // défilé
        if (!skipped && t == reelAt) spawnReel();
        if (!skipped && t >= nextSwap && swaps < totalSwaps && t < climbAt) swap(t);

        // montée
        if (!skipped && t >= climbAt && t < revealAt) climbTick(t);

        // essayage
        if (t == revealAt) enterMirror();
        if (t == tryOnAt) tryOn();
        if (t > tryOnAt && t < outroAt) tryOnTick(t);
        if (reflection != null && (t == tryOnAt + 10 || t == tryOnAt + 22)) reflection.swingMainHand();

        if (t == outroAt) shatter();
        if (t >= endAt) finish();
    }

    @Override
    public void skip() {
        if (skipped || isRevealed() || t >= revealAt) return;
        skipped = true;
        for (int i = 0; i < frame.size(); i++) move(frame.get(i), frameFinal.get(i), 0);
        move(glass, glassFinal, 0);
        if (reflection == null) appearReflection();
        for (int i = 0; i < reel.length; i++) {
            remove(reel[i]);
            reel[i] = null;
        }
        // le lot gagné apparaît au centre, puis entre dans le miroir
        ItemStack prize = reward.getDisplayItemStack().clone();
        reel[1] = reelItem(prize, 1, REEL_CENTER);
        setFrameGlow(tier);
        revealAt = t + 3;
        tryOnAt = revealAt + 6;
        outroAt = revealAt + 52;
        endAt = outroAt + 7;
    }

    @Override
    public void onOwnerSwing() {
        // un vrai reflet : il lève le bras du même côté, vu par le joueur
        if (reflection != null && reflection.isValid() && t < outroAt) reflection.swingMainHand();
    }

    @Override
    public Location focus() {
        return at(0, 2.4, 0.2);
    }

    // ------------------------------------------------------------------ étapes

    private void addBar(Transformation finalTf, Material material) {
        BlockData data = material == Material.AMETHYST_CLUSTER
            ? Bukkit.createBlockData("minecraft:amethyst_cluster[facing=up]") : material.createBlockData();
        BlockDisplay d = spawn(at(0, 0, 0), BlockDisplay.class, Audience.ALL, bd -> {
            bd.setBlock(data);
            bd.setTransformation(collapsed(finalTf));
        });
        frame.add(d);
        frameFinal.add(finalTf);
    }

    /** Même élément, réduit à presque rien en son centre (point de départ d'une apparition). */
    private static Transformation collapsed(Transformation tf) {
        Vector3f size = new Vector3f(tf.getScale());
        Vector3f half = new Quaternionf(tf.getLeftRotation()).transform(new Vector3f(size).mul(0.5f));
        Vector3f center = new Vector3f(tf.getTranslation()).add(half);
        Vector3f tiny = new Vector3f(size).mul(0.01f);
        Vector3f tinyHalf = new Quaternionf(tf.getLeftRotation()).transform(new Vector3f(tiny).mul(0.5f));
        return new Transformation(center.sub(tinyHalf), new Quaternionf(tf.getLeftRotation()), tiny, new Quaternionf());
    }

    private Vector3f sizeOf(double x, double y, double z) {
        return new Vector3f((float) (x * s), (float) (y * s), (float) (z * s));
    }

    private void appearReflection() {
        Location where = at(0, 0.04, REFLECTION_Z);
        where.setYaw(yawTowardOwner());
        where.setPitch(0);
        reflection = spawn(where, Mannequin.class, Audience.ALL, m -> {
            m.setProfile(profileOf(owner));
            m.setImmovable(true);
            m.setGravity(false);
            m.setInvulnerable(true);
            m.setCollidable(false);
            m.setSilent(true);
            m.setDescription(null);
            m.customName(Component.text(owner.getName()));
            m.setCustomNameVisible(true);
            // dans un miroir, la main droite devient la gauche
            m.setMainHand(leftHanded ? MainHand.RIGHT : MainHand.LEFT);
            try {
                m.setSkinParts(owner.getClientOption(com.destroystokyo.paper.ClientOption.SKIN_PARTS));
            } catch (RuntimeException ignored) {
                // parties du skin non lisibles : celles par défaut
            }
            AttributeInstance scale = m.getAttribute(Attribute.SCALE);
            if (scale != null) scale.setBaseValue(REFLECTION_SCALE * s);
            m.setBodyYaw(yawTowardOwner());
        });
        sound("miroir-reflet", "entity.illusioner.mirror_move", 1f, 1.1f, 0.6f);
        particle(false, Particle.ELECTRIC_SPARK, at(0, 1.1, 0.05), 20, 0.4 * s, 0.05, null);
    }

    /** Skin du joueur ; sans texture connue (compte hors ligne sans skin), seulement son identifiant. */
    private static ResolvableProfile profileOf(Player player) {
        PlayerProfile profile = player.getPlayerProfile();
        boolean hasTextures = profile.getProperties().stream().anyMatch(p -> p.getName().equals("textures"));
        if (hasTextures) return ResolvableProfile.resolvableProfile(profile);
        return ResolvableProfile.resolvableProfile().uuid(player.getUniqueId()).build();
    }

    /** Le reflet regarde dans la direction du regard du joueur, réfléchie par le plan du miroir. */
    private void mirrorLook() {
        if (!reflection.isValid()) return;
        Vector d = owner.getEyeLocation().getDirection();
        Vector n = front;
        Vector r = d.clone().subtract(n.clone().multiply(2 * d.dot(n)));
        float body = yawTowardOwner();
        float yaw = (float) Math.toDegrees(Math.atan2(-r.getX(), r.getZ()));
        float delta = wrap(yaw - body);
        yaw = body + Math.max(-70, Math.min(70, delta));
        float pitch = (float) Math.toDegrees(-Math.asin(Math.max(-1, Math.min(1, r.getY()))));
        reflection.setRotation(yaw, Math.max(-60, Math.min(60, pitch)));
        reflection.setBodyYaw(body);
    }

    private static float wrap(float angle) {
        float a = angle % 360;
        if (a > 180) a -= 360;
        if (a < -180) a += 360;
        return a;
    }

    private ItemDisplay reelItem(ItemStack item, int slot, float scale) {
        ItemDisplay d = spawn(at(REEL_X[slot], REEL_Y, 0.12), ItemDisplay.class, Audience.ALL, id -> {
            id.setItemStack(item);
            id.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            id.setTransformation(itemTf(new Vector3f(), facing(), (float) (scale * s)));
        });
        spawnX.put(d, REEL_X[slot]);
        return d;
    }

    private void spawnReel() {
        for (int i = 0; i < 3; i++) {
            ItemStack item = sequence.get(seqIndex++);
            int slot = i;
            reel[i] = spawn(at(REEL_X[slot], REEL_Y, 0.12), ItemDisplay.class, Audience.ALL, d -> {
                d.setItemStack(item);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setTransformation(itemTf(new Vector3f(), facing(), 0.01f));
            });
            float target = (float) ((slot == 1 ? REEL_CENTER : REEL_SIDE) * s);
            ItemDisplay d = reel[i];
            spawnX.put(d, REEL_X[slot]);
            Bukkit.getScheduler().runTaskLater(plugin, () -> move(d, itemTf(new Vector3f(), facing(), target), 5), SETTLE);
        }
    }

    /** Un cran du défilé : tout glisse d'une place vers la gauche, un nouveau cosmétique arrive à droite. */
    private void swap(int t) {
        if (reel[0] == null) return;
        int dur = Math.min(4, swapInterval);
        ItemDisplay leaving = reel[0];
        slide(leaving, REEL_X[0] - (REEL_X[1] - REEL_X[0]), 0.01f, dur);
        Bukkit.getScheduler().runTaskLater(plugin, () -> remove(leaving), dur + 1L);
        slide(reel[1], REEL_X[0], REEL_SIDE, dur);
        slide(reel[2], REEL_X[1], REEL_CENTER, dur);
        ItemStack next = sequence.get(Math.min(seqIndex++, sequence.size() - 1));
        double entryX = REEL_X[2] + (REEL_X[2] - REEL_X[1]);
        ItemDisplay incoming = spawn(at(entryX, REEL_Y, 0.12), ItemDisplay.class, Audience.ALL, d -> {
            d.setItemStack(next);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTransformation(itemTf(new Vector3f(), facing(), 0.01f));
        });
        spawnX.put(incoming, entryX);
        Bukkit.getScheduler().runTaskLater(plugin, () -> slide(incoming, REEL_X[2], REEL_SIDE, dur), SETTLE);
        reel[0] = reel[1];
        reel[1] = reel[2];
        reel[2] = incoming;
        swaps++;
        sound("miroir-cran", "block.note_block.bell", 0.55f, (float) Math.min(2.0, 0.75 + swaps * 0.06), 0f);
        swapInterval = (int) Math.min(10, Math.round(swapInterval * 1.15 + 0.4));
        nextSwap = t + swapInterval;
    }

    /** Fait glisser un cosmétique du défilé jusqu'à la position locale x, à la taille donnée. */
    private void slide(ItemDisplay d, double targetX, float scale, int ticks) {
        if (d == null || !d.isValid()) return;
        double from = spawnX.getOrDefault(d, targetX);
        move(d, itemTf(offset(targetX - from, 0, 0), facing(), (float) (scale * s)), ticks);
    }

    private void climbTick(int t) {
        int step = (t - climbAt) / 10;
        if (step == climbStep) return;
        climbStep = step;
        Tier stepTier = ladder.isEmpty() ? null : ladder.get(Math.min(step, ladder.size() - 1));
        setFrameGlow(stepTier);
        if (reel[1] != null) {
            reel[1].setGlowing(true);
            reel[1].setGlowColorOverride(colorOf(stepTier));
        }
        sound("miroir-palier", "block.amethyst_block.resonate", 1f, (float) (0.8 + 0.2 * step), 0f);
        particle(false, Particle.DUST, at(0, FRAME_TOP + 0.2, 0.1), 12, 0.5 * s, 0, dust(colorOf(stepTier), 1.3f));
    }

    private void setFrameGlow(Tier stepTier) {
        Color c = colorOf(stepTier);
        for (BlockDisplay bar : frame) {
            bar.setGlowing(true);
            bar.setGlowColorOverride(c);
        }
    }

    /** Le cosmétique gagné entre dans le miroir, vers le reflet. */
    private void enterMirror() {
        setFrameGlow(tier);
        sound("miroir-entree", "entity.illusioner.cast_spell", 0.9f, 1.2f, 0.6f);
        if (reel[1] != null) {
            double fromX = spawnX.getOrDefault(reel[1], REEL_X[1]);
            move(reel[1], itemTf(offset(-fromX, 1.2 - REEL_Y, REFLECTION_Z - 0.12), facing(), 0.01f), 6);
            ItemDisplay entering = reel[1];
            Bukkit.getScheduler().runTaskLater(plugin, () -> remove(entering), 7L);
            reel[1] = null;
        }
        for (int i : new int[]{0, 2}) {
            if (reel[i] != null) {
                double fromX = spawnX.getOrDefault(reel[i], REEL_X[i]);
                move(reel[i], itemTf(offset(REEL_X[i] - fromX, 0, 0), facing(), 0.01f), 4);
                ItemDisplay side = reel[i];
                Bukkit.getScheduler().runTaskLater(plugin, () -> remove(side), 5L);
                reel[i] = null;
            }
        }
        particle(false, Particle.DUST, at(0, 1.2, 0.05), 24, 0.5 * s, 0, dust(colorOf(tier), 1.2f));
        reveal();
    }

    private void tryOn() {
        TryOn tryOn = reward.getTryOn();
        TryOn.Type type = tryOn == null ? null : tryOn.type();
        if (type == TryOn.Type.TITRE && reflection != null && reflection.isValid()) {
            String title = tryOn.title() == null || tryOn.title().isBlank() ? "" : ChatUtils.colorize(tryOn.title()) + " ";
            reflection.customName(LEGACY.deserialize(title + "§f" + owner.getName()));
            sound("miroir-titre", "ui.cartography_table.take_result", 1f, 1.1f, 0.5f);
        } else if (type == TryOn.Type.COMPAGNON) {
            ItemStack head = reward.getDisplayItemStack().clone();
            head.setAmount(1);
            companion = spawn(shoulder(0), ItemDisplay.class, Audience.ALL, d -> {
                d.setItemStack(head);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setTransformation(itemTf(new Vector3f(), facing(), 0.01f));
            });
            Bukkit.getScheduler().runTaskLater(plugin, () -> move(companion, itemTf(new Vector3f(), facing(), (float) (0.6 * s)), 5), SETTLE);
            sound("miroir-compagnon", "entity.allay.item_given", 1f, 1.2f, 0.5f);
        } else if (type == TryOn.Type.PARTICULE) {
            sound("miroir-particule", "block.amethyst_block.chime", 1f, 1.3f, 0.5f);
        } else {
            ItemStack item = reward.getDisplayItemStack().clone();
            item.setAmount(1);
            floating = spawn(at(0, 1.15, REFLECTION_Z + 0.35), ItemDisplay.class, Audience.ALL, d -> {
                d.setItemStack(item);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setTransformation(itemTf(new Vector3f(), facing(), 0.01f));
            });
            Bukkit.getScheduler().runTaskLater(plugin, () -> move(floating, itemTf(new Vector3f(), facing(), (float) (0.6 * s)), 5), SETTLE);
        }
        showTexts();
    }

    /** Épaule du reflet, du côté droit vu par le joueur, un peu au-dessus de sa tête comme un compagnon. */
    private Location shoulder(double bob) {
        return at(0.6, (1.52 + bob) * REFLECTION_SCALE, REFLECTION_Z + 0.05);
    }

    private void tryOnTick(int t) {
        TryOn tryOn = reward.getTryOn();
        if (companion != null && (t - tryOnAt) % 10 == 0) {
            // petit sautillement sur l'épaule
            boolean up = ((t - tryOnAt) / 10) % 2 == 0;
            move(companion, itemTf(new Vector3f(0, up ? (float) (0.08 * s) : 0f, 0), facing(), (float) (0.6 * s)), 5);
        }
        if (floating != null && (t - tryOnAt) % 10 == 0) {
            Quaternionf spin = facing().rotateY((float) Math.toRadians(120 * ((t - tryOnAt) / 10)));
            move(floating, new Transformation(new Vector3f(), spin, new Vector3f((float) (0.6 * s)), new Quaternionf()), 10);
        }
        if (tryOn != null && tryOn.type() == TryOn.Type.PARTICULE && !tryOn.particles().isEmpty() && t % 2 == 0) {
            List<ParticleSpec> specs = tryOn.particles();
            double head = 1.75 * REFLECTION_SCALE;
            double a = (t - tryOnAt) * 0.35;
            // halo qui tourne autour de la tête, et rappel au-dessus d'elle
            Location p1 = at(Math.cos(a) * 0.45, head, REFLECTION_Z + Math.sin(a) * 0.45);
            Location p2 = at(Math.cos(a + Math.PI) * 0.45, head, REFLECTION_Z + Math.sin(a + Math.PI) * 0.45);
            Location over = at(0, head + 0.45, REFLECTION_Z);
            for (Player viewer : viewersOnline()) {
                specs.get(0).spawn(viewer, p1, 1, 0.02, 0, t, 0);
                specs.get(specs.size() > 1 ? 1 : 0).spawn(viewer, p2, 1, 0.02, 0, t, 1);
                if (t % 6 == 0) specs.get(0).spawn(viewer, over, 1, 0.05, 0, t, 2);
            }
        }
    }

    private void showTexts() {
        nameText = spawn(at(0, REEL_Y + 0.1, 0.1), TextDisplay.class, Audience.ALL, td -> {
            td.text(LEGACY.deserialize(reward.getDisplayName()));
            td.setBillboard(Display.Billboard.CENTER);
            td.setShadowed(true);
            td.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
            td.setTransformation(itemTf(new Vector3f(), new Quaternionf(), 0.01f));
        });
        if (tier != null) {
            tierText = spawn(at(0, REEL_Y - 0.17, 0.1), TextDisplay.class, Audience.ALL, td -> {
                td.text(LEGACY.deserialize(tier.name()));
                td.setBillboard(Display.Billboard.CENTER);
                td.setShadowed(true);
                td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                td.setTransformation(itemTf(new Vector3f(), new Quaternionf(), 0.01f));
            });
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            move(nameText, itemTf(new Vector3f(), new Quaternionf(), (float) (1.1 * s)), 4);
            move(tierText, itemTf(new Vector3f(), new Quaternionf(), (float) (0.8 * s)), 4);
        }, SETTLE);
    }

    /** Le miroir éclate : verre, paillettes, le reflet s'efface. */
    private void shatter() {
        sound("miroir-bris", "block.glass.break", 1f, 1.1f, 0.8f);
        BlockData glassData = Material.LIGHT_BLUE_STAINED_GLASS.createBlockData();
        for (int i = 0; i < 10; i++) {
            particle(false, Particle.BLOCK, at((Math.random() - 0.5) * 1.4, Math.random() * FRAME_TOP, 0.05), 3, 0.1, 0.1, glassData);
        }
        particle(false, Particle.END_ROD, at(0, 1.1, 0.1), 16, 0.5 * s, 0.04, null);
        if (reflection != null) {
            particle(false, Particle.POOF, reflection.getLocation().add(0, 0.9 * REFLECTION_SCALE, 0), 12, 0.25, 0.02, null);
            remove(reflection);
            reflection = null;
        }
        for (int i = 0; i < frame.size(); i++) move(frame.get(i), collapsed(frameFinal.get(i)), 5);
        move(glass, collapsed(glassFinal), 4);
        move(nameText, itemTf(new Vector3f(), new Quaternionf(), 0.01f), 5);
        move(tierText, itemTf(new Vector3f(), new Quaternionf(), 0.01f), 5);
        move(label, itemTf(new Vector3f(), new Quaternionf(), 0.01f), 5);
        if (companion != null) move(companion, itemTf(new Vector3f(), facing(), 0.01f), 5);
        if (floating != null) move(floating, itemTf(new Vector3f(), facing(), 0.01f), 5);
    }
}
