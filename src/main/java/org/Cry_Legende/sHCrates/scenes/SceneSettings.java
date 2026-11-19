package org.Cry_Legende.sHCrates.scenes;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Réglages des scènes d'ouverture (section {@code scenes} de config.yml).
 *
 * @param visibleRadius     distance (blocs) à laquelle les autres joueurs voient la scène, 0 = seulement celui qui ouvre
 * @param maxPerSpectator   nombre de scènes qu'un spectateur voit à la fois (les plus proches)
 * @param skipBySneak       s'accroupir passe directement au lot
 * @param abandonDistance   au-delà de cette distance (ou dans un autre monde), le lot est donné tout de suite
 * @param fixedSpotWorlds   mondes où une scène ne se joue que sur un emplacement posé avec /shcrates scene add
 * @param spotRadius        distance maximale entre le joueur et un emplacement
 * @param turnCamera        tourner la caméra du joueur vers un emplacement qu'il ne regarde pas
 * @param bedrockFallback   joueurs Bedrock : animation de repli en menu (Geyser n'affiche pas ces entités)
 * @param avoidPlugins      plugins d'hologrammes ou de PNJ (par paquets) dont la scène s'écarte
 * @param sounds            sons des scènes, par clé (voir {@link SceneSounds})
 */
public record SceneSettings(int visibleRadius, int maxPerSpectator, boolean skipBySneak, double abandonDistance,
                            Set<String> fixedSpotWorlds, double spotRadius, boolean turnCamera, boolean bedrockFallback,
                            List<String> avoidPlugins, Map<String, String> sounds) {

    public static SceneSettings defaults() {
        return new SceneSettings(24, 2, true, 20, Set.of("spawn"), 24, true, true,
            List.of("FancyHolograms", "DecentHolograms", "FancyNpcs"), Collections.emptyMap());
    }

    public static SceneSettings load(ConfigurationSection section, Logger logger) {
        SceneSettings d = defaults();
        if (section == null) {
            return d;
        }
        Set<String> worlds = new LinkedHashSet<>();
        List<String> listed = section.isList("mondes-emplacements") ? section.getStringList("mondes-emplacements") : List.copyOf(d.fixedSpotWorlds());
        for (String w : listed) {
            if (!w.isBlank()) worlds.add(w.toLowerCase(Locale.ROOT));
        }
        Map<String, String> sounds = new LinkedHashMap<>();
        ConfigurationSection soundSection = section.getConfigurationSection("sons");
        if (soundSection != null) {
            for (String key : soundSection.getKeys(false)) {
                sounds.put(key.toLowerCase(Locale.ROOT), soundSection.getString(key, ""));
            }
        }
        return new SceneSettings(
            Math.max(0, section.getInt("visible-autour", d.visibleRadius())),
            Math.max(1, section.getInt("max-par-spectateur", d.maxPerSpectator())),
            section.getBoolean("passer-en-accroupi", d.skipBySneak()),
            Math.max(4, section.getDouble("distance-abandon", d.abandonDistance())),
            Collections.unmodifiableSet(worlds),
            Math.max(4, section.getDouble("rayon-emplacements", d.spotRadius())),
            section.getBoolean("tourner-camera", d.turnCamera()),
            section.getBoolean("repli-bedrock", d.bedrockFallback()),
            section.isList("eviter-plugins") ? List.copyOf(section.getStringList("eviter-plugins")) : d.avoidPlugins(),
            Collections.unmodifiableMap(sounds));
    }

    /** Son configuré pour une clé, ou le son par défaut de la scène. "" coupe le son. */
    public String sound(String key, String fallback) {
        String configured = sounds.get(key);
        return configured == null ? fallback : configured;
    }

    public boolean isFixedSpotWorld(String world) {
        return world != null && fixedSpotWorlds.contains(world.toLowerCase(Locale.ROOT));
    }
}
