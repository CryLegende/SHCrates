package org.Cry_Legende.sHCrates.scenes;

import org.Cry_Legende.sHCrates.utils.ItemBuilder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modèle de scène fait dans Blockbench (têtes de joueur), lu depuis {@code scenes/<nom>.yml} du jar : les pièces avec leur
 * pose de repos, et les animations en images clés (fichier écrit par {@code _tools/voteparty/construire.mjs}).
 * Repère du fichier : le modèle regarde vers +z, pieds à y = 0, en blocs ; une pose = translation, rotation gauche, échelle.
 */
final class SceneModel {

    record Pose(Vector3f t, Quaternionf l, Vector3f s) {
    }

    record Frame(int at, int over, int part, Pose pose) {
    }

    record Anim(int length, boolean loop, Map<Integer, List<Frame>> byTick) {
    }

    private static final Map<String, SceneModel> CACHE = new ConcurrentHashMap<>();

    final List<ItemStack> heads = new ArrayList<>();
    final List<Pose> rest = new ArrayList<>();
    final Map<String, Anim> anims = new HashMap<>();

    /** Modèle du jar, lu une fois. */
    static SceneModel get(Plugin plugin, String name) {
        return CACHE.computeIfAbsent(name, n -> read(plugin, n));
    }

    private static SceneModel read(Plugin plugin, String name) {
        SceneModel model = new SceneModel();
        YamlConfiguration yaml;
        try (InputStream in = plugin.getResource("scenes/" + name + ".yml")) {
            if (in == null) throw new IllegalStateException("modèle de scène absent du jar : scenes/" + name + ".yml");
            yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("modèle de scène illisible : " + name, e);
        }
        Map<String, ItemStack> byTexture = new HashMap<>();
        for (Map<?, ?> part : yaml.getMapList("parts")) {
            String texture = String.valueOf(part.get("head"));
            model.heads.add(byTexture.computeIfAbsent(texture, v -> new ItemBuilder(Material.PLAYER_HEAD).setSkullTexture(v).build()));
            model.rest.add(pose((List<?>) part.get("rest"), 0));
        }
        var section = yaml.getConfigurationSection("animations");
        if (section != null) {
            for (String anim : section.getKeys(false)) {
                Map<Integer, List<Frame>> byTick = new HashMap<>();
                for (Object raw : section.getList(anim + ".frames", List.of())) {
                    List<?> f = (List<?>) raw;
                    Frame frame = new Frame(num(f, 0).intValue(), num(f, 1).intValue(), num(f, 2).intValue(), pose(f, 3));
                    byTick.computeIfAbsent(frame.at(), k -> new ArrayList<>()).add(frame);
                }
                model.anims.put(anim, new Anim(section.getInt(anim + ".length"), section.getBoolean(anim + ".loop"), byTick));
            }
        }
        return model;
    }

    private static Number num(List<?> list, int i) {
        return (Number) list.get(i);
    }

    private static Pose pose(List<?> v, int o) {
        return new Pose(
            new Vector3f(num(v, o).floatValue(), num(v, o + 1).floatValue(), num(v, o + 2).floatValue()),
            new Quaternionf(num(v, o + 3).floatValue(), num(v, o + 4).floatValue(), num(v, o + 5).floatValue(), num(v, o + 6).floatValue()),
            new Vector3f(num(v, o + 7).floatValue(), num(v, o + 8).floatValue(), num(v, o + 9).floatValue()));
    }

    /**
     * Le modèle posé dans une scène : une entité par pièce, toutes au même point, orientées et mises à l'échelle ensemble.
     * Une seule animation joue à la fois ; en changer part de la pose où en est chaque pièce.
     */
    static final class Instance {
        private final Scene scene;
        private final SceneModel model;
        private final List<ItemDisplay> parts = new ArrayList<>();
        private final Pose[] target;
        private final Quaternionf orient;
        private float scale;
        private Anim current;
        private String currentName;
        private int start;

        Instance(Scene scene, SceneModel model, Location at, Quaternionf orient, float scale, Scene.Audience audience) {
            this.scene = scene;
            this.model = model;
            this.orient = new Quaternionf(orient);
            this.scale = scale;
            this.target = model.rest.toArray(new Pose[0]);
            for (int i = 0; i < model.heads.size(); i++) {
                ItemStack head = model.heads.get(i);
                Transformation tf = compose(target[i]);
                parts.add(scene.spawn(at, ItemDisplay.class, audience, d -> {
                    d.setItemStack(head);
                    d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                    d.setTransformation(tf);
                }));
            }
        }

        private Transformation compose(Pose p) {
            Vector3f t = orient.transform(new Vector3f(p.t()).mul(scale));
            return new Transformation(t, new Quaternionf(orient).mul(p.l()), new Vector3f(p.s()).mul(scale), new Quaternionf());
        }

        /** Lance une animation au tick {@code now} de la scène (ses images du tick 0 partent tout de suite). */
        void play(String name, int now) {
            current = model.anims.get(name);
            currentName = current == null ? null : name;
            start = now;
            tick(now);
        }

        boolean isPlaying(String name) {
            return current != null && name.equals(currentName);
        }

        void tick(int now) {
            if (current == null) return;
            int local = now - start;
            if (current.loop()) {
                local %= Math.max(1, current.length());
            } else if (local >= current.length()) {
                current = null;
                currentName = null;
                return;
            }
            List<Frame> frames = current.byTick().get(local);
            if (frames == null) return;
            for (Frame f : frames) {
                target[f.part()] = f.pose();
                Scene.move(parts.get(f.part()), compose(f.pose()), f.over());
            }
        }

        /** Déplace tout le modèle, glissé par le client sur {@code ticks} (59 au plus). */
        void moveTo(Location to, int ticks) {
            for (ItemDisplay d : parts) {
                if (!d.isValid()) continue;
                d.setTeleportDuration(Math.max(0, Math.min(59, ticks)));
                d.teleport(to);
            }
        }

        /** Change l'échelle de tout le modèle, sur la pose visée par chaque pièce. */
        void scaleTo(float newScale, int ticks) {
            this.scale = newScale;
            for (int i = 0; i < parts.size(); i++) Scene.move(parts.get(i), compose(target[i]), ticks);
        }

        void remove() {
            for (ItemDisplay d : parts) scene.remove(d);
            parts.clear();
            current = null;
        }
    }
}
