package org.Cry_Legende.sHCrates.scenes;

import org.Cry_Legende.sHCrates.config.ConfigManager;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;

/**
 * Particule lue en config : « HEART », « DUST purple 1.2 », « DUST #FF5500 », « DUST rainbow », « NOTE random »,
 * « DUST_COLOR_TRANSITION #C80000 #460000 », « ENTITY_EFFECT #FFFFFF ». Les couleurs nommées sont celles de
 * PlayerParticles (red, orange, yellow, lime, green, light_blue, cyan, blue, purple, magenta, pink, white, gray, black, brown).
 *
 * @param particle  particule vanilla
 * @param color     couleur, ou null (aléatoire ou arc-en-ciel selon {@code mode})
 * @param color2    seconde couleur d'une transition, ou null
 * @param size      taille des particules de poussière
 * @param mode      FIXED, RANDOM ou RAINBOW
 */
public record ParticleSpec(Particle particle, Color color, Color color2, float size, Mode mode) {

    public enum Mode { FIXED, RANDOM, RAINBOW }

    private static final Map<String, Color> NAMED = Map.ofEntries(
        Map.entry("red", Color.fromRGB(255, 0, 0)), Map.entry("orange", Color.fromRGB(255, 140, 0)),
        Map.entry("yellow", Color.fromRGB(255, 255, 0)), Map.entry("lime", Color.fromRGB(50, 205, 50)),
        Map.entry("green", Color.fromRGB(0, 128, 0)), Map.entry("light_blue", Color.fromRGB(173, 216, 230)),
        Map.entry("cyan", Color.fromRGB(0, 139, 139)), Map.entry("blue", Color.fromRGB(0, 0, 255)),
        Map.entry("purple", Color.fromRGB(138, 43, 226)), Map.entry("magenta", Color.fromRGB(202, 31, 123)),
        Map.entry("pink", Color.fromRGB(255, 182, 193)), Map.entry("white", Color.fromRGB(255, 255, 255)),
        Map.entry("gray", Color.fromRGB(128, 128, 128)), Map.entry("light_gray", Color.fromRGB(192, 192, 192)),
        Map.entry("black", Color.fromRGB(0, 0, 0)), Map.entry("brown", Color.fromRGB(139, 69, 19)));

    /** Lit une particule, null si le nom est inconnu. */
    public static ParticleSpec parse(String text) {
        if (text == null || text.isBlank()) return null;
        String[] parts = text.trim().split("\\s+");
        Particle particle = particle(parts[0]);
        if (particle == null) return null;
        Color c1 = null;
        Color c2 = null;
        float size = 1.0f;
        Mode mode = Mode.FIXED;
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i].toLowerCase(Locale.ROOT);
            if (p.equals("random")) {
                mode = Mode.RANDOM;
            } else if (p.equals("rainbow")) {
                mode = Mode.RAINBOW;
            } else if (NAMED.containsKey(p) || p.startsWith("#")) {
                Color c = NAMED.containsKey(p) ? NAMED.get(p) : ConfigManager.parseHexColor(p);
                if (c1 == null) c1 = c;
                else c2 = c;
            } else {
                try {
                    size = Math.max(0.1f, Math.min(4f, Float.parseFloat(p)));
                } catch (NumberFormatException ignored) {
                    // mot inconnu : ignoré
                }
            }
        }
        if (c1 == null && mode == Mode.FIXED && needsColor(particle)) {
            mode = Mode.RANDOM;
        }
        return new ParticleSpec(particle, c1, c2, size, mode);
    }

    private static Particle particle(String name) {
        String n = name.trim().toLowerCase(Locale.ROOT);
        NamespacedKey key = NamespacedKey.fromString(n.contains(":") ? n : "minecraft:" + n);
        Particle found = key == null ? null : Registry.PARTICLE_TYPE.get(key);
        if (found != null) return found;
        try {
            return Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean needsColor(Particle particle) {
        Class<?> type = particle.getDataType();
        return type == Particle.DustOptions.class || type == Particle.DustTransition.class || type == Color.class;
    }

    /** Couleur au tick donné selon le mode. */
    private Color colorAt(int tick, int index) {
        return switch (mode) {
            case RAINBOW -> hsb(((tick * 6 + index * 40) % 360) / 360f);
            case RANDOM -> hsb((float) Math.random());
            default -> color == null ? Color.WHITE : color;
        };
    }

    private static Color hsb(float hue) {
        int rgb = java.awt.Color.HSBtoRGB(hue, 0.85f, 1f) & 0xFFFFFF;
        return Color.fromRGB(rgb);
    }

    /** Envoie la particule au joueur ; {@code index} décale la couleur des modes aléatoire et arc-en-ciel. */
    public void spawn(Player viewer, Location at, int count, double spread, double speed, int tick, int index) {
        Class<?> type = particle.getDataType();
        try {
            if (particle == Particle.NOTE && mode != Mode.FIXED) {
                // la note tire sa couleur de l'écart en x quand le nombre vaut 0
                viewer.spawnParticle(particle, at, 0, ((tick + index * 5) % 25) / 24.0, 0, 0, 1);
            } else if (type == Particle.DustOptions.class) {
                viewer.spawnParticle(particle, at, count, spread, spread, spread, 0, new Particle.DustOptions(colorAt(tick, index), size));
            } else if (type == Particle.DustTransition.class) {
                Color from = colorAt(tick, index);
                viewer.spawnParticle(particle, at, count, spread, spread, spread, 0,
                    new Particle.DustTransition(from, color2 == null ? from : color2, size));
            } else if (type == Color.class) {
                viewer.spawnParticle(particle, at, count, spread, spread, spread, 0, colorAt(tick, index));
            } else if (type == Void.class) {
                viewer.spawnParticle(particle, at, count, spread, spread, spread, speed);
            }
            // particules à donnée d'un autre type (bloc, objet) : non prises en charge ici
        } catch (IllegalArgumentException ignored) {
            // donnée refusée par cette version : la particule est simplement sautée
        }
    }
}
