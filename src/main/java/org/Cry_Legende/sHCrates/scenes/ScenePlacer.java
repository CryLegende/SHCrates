package org.Cry_Legende.sHCrates.scenes;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ArmorStand;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Cherche où jouer une scène pour qu'elle se voie bien : rien dans son volume (blocs, entités, hologrammes et PNJ par
 * paquets, autres scènes, autres joueurs), rien entre les yeux du joueur et elle, et dans son champ de vision.
 * <p>
 * Dans un monde de {@code scenes.mondes-emplacements}, seuls les emplacements posés par un administrateur sont essayés : le
 * décor y est souvent rendu par paquets (modèles DEU/BDC), invisible pour le serveur. Ailleurs, des places devant le joueur
 * sont essayées à plusieurs distances et angles, à l'échelle 1 puis plus petite. Sans place, l'ouverture garde l'animation
 * de repli en menu : une scène n'est jamais jouée là où elle serait gâchée.
 */
final class ScenePlacer {

    private static final double[] ANGLES = {0, -20, 20, -40, 40};
    private static final double MIN_SKY = 4.0;
    private static final double MAX_SKY = 10.0;

    private final SceneManager manager;
    private final Obstacles obstacles;

    ScenePlacer(SceneManager manager, Obstacles obstacles) {
        this.manager = manager;
        this.obstacles = obstacles;
    }

    /** Place de la scène, ou null s'il n'y en a aucune de correcte. */
    Placement find(Player player, SceneKind kind) {
        SceneSettings settings = manager.settings();
        World world = player.getWorld();
        List<Obstacles.Obstacle> packetObstacles = obstacles.near(world, player.getLocation(), 32, settings.avoidPlugins());
        if (settings.isFixedSpotWorld(world.getName())) {
            return fromSpots(player, kind, packetObstacles);
        }
        for (double scale : new double[]{1.0, 0.75}) {
            Placement found = around(player, kind, scale, packetObstacles);
            if (found != null) return found;
        }
        return null;
    }

    // ------------------------------------------------------------------ emplacements posés

    private Placement fromSpots(Player player, SceneKind kind, List<Obstacles.Obstacle> packetObstacles) {
        SceneSettings settings = manager.settings();
        Location eye = player.getEyeLocation();
        Vector look = horizontal(eye.getDirection());
        double maxD2 = settings.spotRadius() * settings.spotRadius();
        List<SceneSpots.Spot> spots = new ArrayList<>(manager.spots().inWorld(player.getWorld().getName()));
        spots.removeIf(s -> s.distanceSquared(player.getLocation()) > maxD2);
        // d'abord ceux qu'il regarde déjà, puis les plus proches
        spots.sort(Comparator.comparingDouble((SceneSpots.Spot s) -> angleTo(look, eye, s.x(), s.z()) > 60 ? 1 : 0)
            .thenComparingDouble(s -> s.distanceSquared(player.getLocation())));
        for (SceneSpots.Spot spot : spots) {
            double scale = spot.scale();
            double r = kind.radius() * scale;
            double h = kind.height() * scale;
            if (manager.isReserved(player.getWorld(), spot.x(), spot.y(), spot.z(), r, h, null)) continue;
            Location center = new Location(player.getWorld(), spot.x(), spot.y() + h * 0.45, spot.z());
            if (!clearLine(eye, center)) continue;
            Vector front = horizontal(player.getLocation().toVector().subtract(new Vector(spot.x(), spot.y(), spot.z())));
            boolean turn = angleTo(look, eye, spot.x(), spot.z()) > 45;
            Placement p = new Placement(player.getWorld(), spot.x(), spot.y(), spot.z(), front, scale, 0, true, turn);
            return p.withSky(skyAbove(p, kind));
        }
        return null;
    }

    // ------------------------------------------------------------------ recherche devant le joueur

    private Placement around(Player player, SceneKind kind, double scale, List<Obstacles.Obstacle> packetObstacles) {
        Location feet = player.getLocation();
        Location eye = player.getEyeLocation();
        Vector forward = horizontal(eye.getDirection());
        double preferred = kind.distance() * (scale < 1 ? 0.85 : 1.0);
        double[] distances = {preferred, preferred - 0.5, preferred + 0.6, preferred + 1.2};
        Placement best = null;
        double bestScore = Double.MAX_VALUE;
        for (double d : distances) {
            for (double angle : ANGLES) {
                Vector dir = rotateY(forward, Math.toRadians(angle));
                double cx = feet.getX() + dir.getX() * d;
                double cz = feet.getZ() + dir.getZ() * d;
                Ground ground = ground(player.getWorld(), cx, feet.getY(), cz);
                if (ground == null) continue;
                Vector front = horizontal(new Vector(feet.getX() - cx, 0, feet.getZ() - cz));
                // décalée de plus de 25° : la caméra du joueur se tourne vers elle au départ, sinon elle serait au bord
                Placement p = new Placement(player.getWorld(), cx, ground.y(), cz, front, scale, 0, false, Math.abs(angle) > 25);
                double score = Math.abs(angle) / 22.0 + Math.abs(d - preferred) * 1.2 + (ground.solid() ? 0 : 0.9);
                if (score >= bestScore) continue;
                if (!fits(player, kind, p, eye, packetObstacles)) continue;
                best = p;
                bestScore = score;
            }
        }
        return best == null ? null : best.withSky(skyAbove(best, kind));
    }

    /** Sol sous le centre : dessus du premier bloc plein entre un bloc au-dessus et deux au-dessous des pieds. */
    private record Ground(double y, boolean solid) {
    }

    private static Ground ground(World world, double x, double feetY, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int top = (int) Math.floor(feetY) + 1;
        for (int y = top; y >= top - 3; y--) {
            Block block = world.getBlockAt(bx, y, bz);
            if (block.isCollidable() && !block.isPassable()) {
                double surface = block.getBoundingBox().getMaxY();
                if (surface <= y) surface = y + 1;
                if (Math.abs(surface - feetY) <= 1.25) return new Ground(surface, true);
                return surface > feetY ? null : new Ground(feetY, false);
            }
        }
        // rien dessous (vide, falaise) : la scène flotte à la hauteur des pieds
        return new Ground(feetY, false);
    }

    private boolean fits(Player player, SceneKind kind, Placement p, Location eye, List<Obstacles.Obstacle> packetObstacles) {
        double r = kind.radius() * p.scale();
        double h = kind.height() * p.scale();
        World world = p.world();
        if (manager.isReserved(world, p.x(), p.y(), p.z(), r, h, null)) return false;
        if (!blocksFree(world, p.x(), p.y(), p.z(), r, h)) return false;
        // vue dégagée sur le bas, le milieu et le haut de la scène
        for (double f : new double[]{0.25, 0.55, 0.9}) {
            if (!clearLine(eye, new Location(world, p.x(), p.y() + h * f, p.z()))) return false;
        }
        if (!entitiesFree(player, world, p, r, h, eye)) return false;
        Vector a = eye.toVector();
        Vector b = new Vector(p.x(), p.y() + h * 0.5, p.z());
        for (Obstacles.Obstacle o : packetObstacles) {
            double dx = o.x() - p.x();
            double dz = o.z() - p.z();
            boolean overlapsY = o.y() + o.above() > p.y() && o.y() - o.below() < p.y() + h;
            if (overlapsY && Math.sqrt(dx * dx + dz * dz) < r + o.radius()) return false;
            if (distanceToSegment(new Vector(o.x(), o.y(), o.z()), a, b) < 0.6 + o.radius() * 0.5) return false;
        }
        return true;
    }

    /** Aucun bloc visible dans le cylindre ; au ras du sol, les petites plantes et tapis sont tolérés. */
    private static boolean blocksFree(World world, double cx, double baseY, double cz, double r, double h) {
        int y0 = (int) Math.floor(baseY + 0.01);
        int y1 = (int) Math.floor(baseY + h - 0.01);
        int x0 = (int) Math.floor(cx - r);
        int x1 = (int) Math.floor(cx + r);
        int z0 = (int) Math.floor(cz - r);
        int z1 = (int) Math.floor(cz + r);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                double nx = Math.max(x, Math.min(cx, x + 1));
                double nz = Math.max(z, Math.min(cz, z + 1));
                if ((nx - cx) * (nx - cx) + (nz - cz) * (nz - cz) > r * r) continue;
                for (int y = y0; y <= y1; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (isEmpty(block)) continue;
                    if (y == y0 && !block.isCollidable() && !block.isLiquid()) continue;
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isEmpty(Block block) {
        Material type = block.getType();
        return type.isAir() || type == Material.LIGHT || type == Material.STRUCTURE_VOID;
    }

    /** Ni entité dans le volume, ni décor (hologramme, cadre, PNJ...) entre les yeux du joueur et la scène. */
    private boolean entitiesFree(Player player, World world, Placement p, double r, double h, Location eye) {
        BoundingBox volume = new BoundingBox(p.x() - r - 0.5, p.y() - 0.2, p.z() - r - 0.5, p.x() + r + 0.5, p.y() + h + 0.5, p.z() + r + 0.5);
        for (Entity e : world.getNearbyEntities(volume)) {
            if (ignored(player, e)) continue;
            double dx = e.getX() - p.x();
            double dz = e.getZ() - p.z();
            if (Math.sqrt(dx * dx + dz * dz) < r + Math.max(0.4, e.getWidth() / 2)) return false;
        }
        Vector a = eye.toVector();
        Vector b = new Vector(p.x(), p.y() + h * 0.5, p.z());
        BoundingBox corridor = BoundingBox.of(a, b).expand(0.8);
        for (Entity e : world.getNearbyEntities(corridor)) {
            if (ignored(player, e) || !decorative(e)) continue;
            Vector c = e.getLocation().toVector().add(new Vector(0, e.getHeight() / 2, 0));
            if (distanceToSegment(c, a, b) < 0.6) return false;
        }
        return true;
    }

    private boolean ignored(Player owner, Entity e) {
        return e.equals(owner) || manager.isSceneEntity(e) || e instanceof Item || e instanceof ExperienceOrb
            || e instanceof Projectile || e instanceof FallingBlock || e instanceof AreaEffectCloud
            || owner.getPassengers().contains(e) || e.getVehicle() != null && e.getVehicle().equals(owner);
    }

    /** Décor immobile : hologrammes, modèles, cadres, zones de clic, et PNJ (joueurs marqués NPC). */
    private static boolean decorative(Entity e) {
        return e instanceof Display || e instanceof ArmorStand || e instanceof Hanging || e instanceof Interaction
            || (e instanceof Player && e.hasMetadata("NPC"));
    }

    private static boolean clearLine(Location from, Location to) {
        Vector dir = to.toVector().subtract(from.toVector());
        double dist = dir.length();
        if (dist < 0.01) return true;
        RayTraceResult hit = from.getWorld().rayTraceBlocks(from, dir.normalize(), dist, FluidCollisionMode.NEVER, true);
        return hit == null || hit.getHitPosition().distance(from.toVector()) >= dist - 0.35;
    }

    /** Blocs libres au-dessus de la scène, jusqu'à 10 : à partir de 4, le coffre tombe du ciel. */
    private static double skyAbove(Placement p, SceneKind kind) {
        double top = p.y() + kind.height() * p.scale();
        int x = (int) Math.floor(p.x());
        int z = (int) Math.floor(p.z());
        int start = (int) Math.floor(top);
        int free = 0;
        for (int y = start; y < start + MAX_SKY; y++) {
            boolean clear = true;
            for (int[] o : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                if (!isEmpty(p.world().getBlockAt(x + o[0], y, z + o[1]))) {
                    clear = false;
                    break;
                }
            }
            if (!clear) break;
            free++;
        }
        return free >= MIN_SKY ? free : 0;
    }

    // ------------------------------------------------------------------ géométrie

    static Vector horizontal(Vector v) {
        Vector h = new Vector(v.getX(), 0, v.getZ());
        if (h.lengthSquared() < 1e-6) return new Vector(0, 0, 1);
        return h.normalize();
    }

    private static Vector rotateY(Vector v, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vector(v.getX() * cos - v.getZ() * sin, 0, v.getX() * sin + v.getZ() * cos);
    }

    /** Angle (degrés) entre le regard horizontal et la direction de l'œil vers le point. */
    private static double angleTo(Vector look, Location eye, double x, double z) {
        Vector to = horizontal(new Vector(x - eye.getX(), 0, z - eye.getZ()));
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, look.dot(to)))));
    }

    private static double distanceToSegment(Vector p, Vector a, Vector b) {
        Vector ab = b.clone().subtract(a);
        double len2 = ab.lengthSquared();
        if (len2 < 1e-9) return p.distance(a);
        double t = Math.max(0.15, Math.min(0.95, p.clone().subtract(a).dot(ab) / len2));
        return p.distance(a.clone().add(ab.multiply(t)));
    }
}
