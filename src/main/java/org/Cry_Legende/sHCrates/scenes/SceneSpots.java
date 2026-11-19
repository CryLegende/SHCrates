package org.Cry_Legende.sHCrates.scenes;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Emplacements de scène posés par un administrateur (/shcrates scene add), gardés dans scenes.yml. Dans les mondes de
 * {@code scenes.mondes-emplacements}, une scène ne se joue que sur l'un d'eux : le décor (hologrammes, modèles par paquets)
 * y est invisible pour le serveur, seul un humain sait où la scène se voit bien.
 */
public final class SceneSpots {

    /** Emplacement : centre de la base de la scène et son échelle. */
    public record Spot(String world, double x, double y, double z, double scale) {
        public double distanceSquared(Location loc) {
            double dx = loc.getX() - x;
            double dy = loc.getY() - y;
            double dz = loc.getZ() - z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private final Plugin plugin;
    private final File file;
    private List<Spot> spots = new ArrayList<>();

    public SceneSpots(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "scenes.yml");
    }

    public void load() {
        List<Spot> loaded = new ArrayList<>();
        if (file.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection section = yaml.getConfigurationSection("emplacements");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    ConfigurationSection s = section.getConfigurationSection(key);
                    if (s == null) continue;
                    loaded.add(new Spot(s.getString("monde", "world"), s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                        Math.max(0.4, Math.min(1.5, s.getDouble("echelle", 1.0)))));
                }
            }
        }
        spots = loaded;
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
            "Emplacements des scènes d'ouverture (Box Légendaire, Box Cosmétique), posés en jeu avec /shcrates scene add.",
            "Dans les mondes de scenes.mondes-emplacements (config.yml), une scène ne se joue que sur l'un d'eux.",
            "x, y, z : centre de la base de la scène (au sol). echelle : taille de la scène (1 = normale)."));
        int i = 0;
        for (Spot spot : spots) {
            String base = "emplacements." + i++;
            yaml.set(base + ".monde", spot.world());
            yaml.set(base + ".x", round(spot.x()));
            yaml.set(base + ".y", round(spot.y()));
            yaml.set(base + ".z", round(spot.z()));
            yaml.set(base + ".echelle", spot.scale());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "scenes.yml non enregistré : " + e.getMessage());
        }
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    public List<Spot> all() {
        return Collections.unmodifiableList(spots);
    }

    public List<Spot> inWorld(String world) {
        List<Spot> found = new ArrayList<>();
        for (Spot spot : spots) {
            if (spot.world().toLowerCase(Locale.ROOT).equals(world.toLowerCase(Locale.ROOT))) found.add(spot);
        }
        return found;
    }

    public void add(Spot spot) {
        List<Spot> copy = new ArrayList<>(spots);
        copy.add(spot);
        spots = copy;
        save();
    }

    /** Retire l'emplacement le plus proche à moins de {@code radius} blocs ; null s'il n'y en a pas. */
    public Spot removeNearest(Location loc, double radius) {
        Spot nearest = null;
        double best = radius * radius;
        for (Spot spot : inWorld(loc.getWorld().getName())) {
            double d = spot.distanceSquared(loc);
            if (d <= best) {
                best = d;
                nearest = spot;
            }
        }
        if (nearest != null) {
            List<Spot> copy = new ArrayList<>(spots);
            copy.remove(nearest);
            spots = copy;
            save();
        }
        return nearest;
    }
}
