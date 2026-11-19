package org.Cry_Legende.sHCrates.scenes;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

/**
 * Place trouvée pour une scène.
 *
 * @param world      monde de la scène
 * @param x          centre de la base
 * @param y          hauteur de la base (dessus du sol, ou pieds du joueur si la scène flotte)
 * @param z          centre de la base
 * @param front      vecteur horizontal unitaire de la scène vers le joueur : la face avant de la scène le regarde
 * @param scale      échelle de la scène (1 ou plus petite quand la place manque)
 * @param skyHeight  blocs libres au-dessus de la scène (le coffre tombe du ciel s'il y en a assez)
 * @param fixedSpot  vrai pour un emplacement posé par un administrateur
 * @param turnCamera vrai quand le joueur ne regarde pas l'emplacement : sa caméra est tournée vers la scène au départ
 */
public record Placement(World world, double x, double y, double z, Vector front, double scale, double skyHeight,
                        boolean fixedSpot, boolean turnCamera) {

    public Location base() {
        return new Location(world, x, y, z);
    }

    /** Vecteur horizontal unitaire vers la droite de la scène, vue par le joueur. */
    public Vector right() {
        return new Vector(front.getZ(), 0, -front.getX());
    }

    public Placement withSky(double sky) {
        return new Placement(world, x, y, z, front, scale, sky, fixedSpot, turnCamera);
    }
}
