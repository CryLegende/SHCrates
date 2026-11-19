package org.Cry_Legende.sHCrates.scenes;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Hologrammes, PNJ et décors envoyés par paquets (sans entité côté serveur), que la recherche de place ne voit pas autrement.
 * Lus à la demande, au moment d'une ouverture : par l'API publique de chaque plugin, appelée par réflexion (SHCrates ne
 * dépend d'aucun d'eux), ou, pour les décors de BlockDisplayCreator, par les ancres qu'il range dans les données des chunks
 * (clés blockdisplaycreator:x&lt;x&gt;y&lt;y&gt;z&lt;z&gt;, x et z dans le chunk). Un plugin absent est ignoré ; une API qui a
 * changé est signalée une fois puis ignorée.
 */
final class Obstacles {

    /** Un obstacle : point d'ancrage, rayon horizontal, et hauteur au-dessous et au-dessus du point. */
    record Obstacle(double x, double y, double z, double radius, double below, double above) {
    }

    private final Logger logger;
    private final Set<String> broken = new HashSet<>();

    Obstacles(Logger logger) {
        this.logger = logger;
    }

    /** Obstacles des plugins listés, dans ce monde et à moins de {@code range} blocs du point. */
    List<Obstacle> near(World world, Location around, double range, List<String> plugins) {
        List<Obstacle> found = new ArrayList<>();
        double r2 = range * range;
        for (String name : plugins) {
            if (broken.contains(name)) continue;
            Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
            if (plugin == null || !plugin.isEnabled()) continue;
            try {
                if (name.equalsIgnoreCase("BlockDisplayCreator")) {
                    found.addAll(decorAnchors(world, around, range));
                    continue;
                }
                for (Location loc : locations(name, plugin)) {
                    if (loc == null || loc.getWorld() == null || !loc.getWorld().equals(world)) continue;
                    if (loc.distanceSquared(around) > r2) continue;
                    found.add(obstacleFor(name, loc));
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                broken.add(name);
                logger.warning("Scènes : positions de " + name + " illisibles (" + e.getClass().getSimpleName() + " " + e.getMessage()
                    + "), ses hologrammes ou PNJ ne seront pas évités.");
            }
        }
        return found;
    }

    private static final java.util.regex.Pattern BDC_ANCHOR = java.util.regex.Pattern.compile("x(\\d+)y(-?\\d+)z(\\d+)");

    /**
     * Décors de BlockDisplayCreator des chunks chargés à moins de {@code range} blocs : une ancre par décor, gardée par le
     * plugin dans les données du chunk. Le modèle déborde souvent de son bloc : un cylindre large autour de l'ancre.
     */
    private static List<Obstacle> decorAnchors(World world, Location around, double range) {
        List<Obstacle> out = new ArrayList<>();
        int minX = (int) Math.floor((around.getX() - range) / 16.0);
        int maxX = (int) Math.floor((around.getX() + range) / 16.0);
        int minZ = (int) Math.floor((around.getZ() - range) / 16.0);
        int maxZ = (int) Math.floor((around.getZ() + range) / 16.0);
        double r2 = range * range;
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                if (!world.isChunkLoaded(cx, cz)) continue;
                for (org.bukkit.NamespacedKey key : world.getChunkAt(cx, cz).getPersistentDataContainer().getKeys()) {
                    if (!key.getNamespace().equals("blockdisplaycreator")) continue;
                    java.util.regex.Matcher m = BDC_ANCHOR.matcher(key.getKey());
                    if (!m.matches()) continue;
                    double x = cx * 16 + Integer.parseInt(m.group(1)) + 0.5;
                    double y = Integer.parseInt(m.group(2));
                    double z = cz * 16 + Integer.parseInt(m.group(3)) + 0.5;
                    double dx = x - around.getX();
                    double dy = y - around.getY();
                    double dz = z - around.getZ();
                    if (dx * dx + dy * dy + dz * dz > r2) continue;
                    out.add(new Obstacle(x, y, z, 1.4, 0.5, 3.0));
                }
            }
        }
        return out;
    }

    private static Obstacle obstacleFor(String plugin, Location loc) {
        if (plugin.equalsIgnoreCase("FancyNpcs")) {
            // PNJ debout : de ses pieds à au-dessus de sa tête (pseudo)
            return new Obstacle(loc.getX(), loc.getY(), loc.getZ(), 0.7, 0.2, 2.4);
        }
        // hologramme : quelques lignes autour du point, vers le bas pour DecentHolograms
        return new Obstacle(loc.getX(), loc.getY(), loc.getZ(), 1.3, 2.6, 0.8);
    }

    private static Collection<Location> locations(String name, Plugin plugin) throws ReflectiveOperationException {
        ClassLoader loader = plugin.getClass().getClassLoader();
        List<Location> out = new ArrayList<>();
        switch (name.toLowerCase()) {
            case "fancyholograms" -> {
                Object api = Class.forName("de.oliver.fancyholograms.api.FancyHologramsPlugin", true, loader).getMethod("get").invoke(null);
                Object manager = call(api, "getHologramManager");
                for (Object hologram : iterable(call(manager, "getHolograms"))) {
                    out.add((Location) call(call(hologram, "getData"), "getLocation"));
                }
            }
            case "fancynpcs" -> {
                Object api = Class.forName("de.oliver.fancynpcs.api.FancyNpcsPlugin", true, loader).getMethod("get").invoke(null);
                Object manager = call(api, "getNpcManager");
                for (Object npc : iterable(call(manager, "getAllNpcs"))) {
                    out.add((Location) call(call(npc, "getData"), "getLocation"));
                }
            }
            case "decentholograms" -> {
                Object api = Class.forName("eu.decentsoftware.holograms.api.DecentHologramsAPI", true, loader).getMethod("get").invoke(null);
                Object manager = call(api, "getHologramManager");
                for (Object hologram : iterable(call(manager, "getHolograms"))) {
                    Object enabled = callOrNull(hologram, "isEnabled");
                    if (Boolean.FALSE.equals(enabled)) continue;
                    out.add((Location) call(hologram, "getLocation"));
                }
            }
            default -> {
                // plugin sans lecteur de positions
            }
        }
        return out;
    }

    private static Object call(Object target, String method) throws ReflectiveOperationException {
        Method m = target.getClass().getMethod(method);
        m.setAccessible(true);
        return m.invoke(target);
    }

    private static Object callOrNull(Object target, String method) {
        try {
            return call(target, method);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Iterable<?> iterable(Object value) {
        if (value instanceof Iterable<?> it) return it;
        if (value instanceof Map<?, ?> map) return map.values();
        if (value instanceof Object[] array) return List.of(array);
        return List.of();
    }
}
