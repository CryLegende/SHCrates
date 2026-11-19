package org.Cry_Legende.sHCrates.scenes;

/**
 * Les scènes d'ouverture et la place qu'elles occupent à l'échelle 1 : un cylindre vertical posé sur la base.
 *
 * @param type      nom de l'animation dans la config des caisses (Animation.Type)
 * @param radius    rayon du cylindre (blocs)
 * @param height    hauteur du cylindre (blocs)
 * @param distance  distance préférée entre le joueur et le centre de la scène
 */
public enum SceneKind {
    /** Box Légendaire : un coffre-fort sinistre tombe du ciel, s'éveille, monte de palier en palier puis éjecte le lot. */
    COFFRE_CELESTE("COFFRE_CELESTE", 1.7, 3.2, 4.2),
    /** Box Cosmétique : le reflet du joueur essaie le cosmétique dans un miroir. */
    MIROIR("MIROIR", 1.25, 3.75, 3.8);

    private final String type;
    private final double radius;
    private final double height;
    private final double distance;

    SceneKind(String type, double radius, double height, double distance) {
        this.type = type;
        this.radius = radius;
        this.height = height;
        this.distance = distance;
    }

    public String type() {
        return type;
    }

    public double radius() {
        return radius;
    }

    public double height() {
        return height;
    }

    public double distance() {
        return distance;
    }
}
