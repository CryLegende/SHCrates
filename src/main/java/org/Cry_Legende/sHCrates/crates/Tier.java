package org.Cry_Legende.sHCrates.crates;

import org.bukkit.Color;
import org.bukkit.Material;

/**
 * Palier de rareté d'une récompense (section {@code paliers} de config.yml). Le rang ordonne les paliers :
 * plus il est haut, plus le lot est rare et plus son ouverture se voit et s'entend.
 *
 * @param id        identifiant en minuscules, celui qu'on écrit dans {@code Palier:} d'une récompense
 * @param rank      ordre du palier, 1 pour le plus courant
 * @param name      nom affiché, avec sa couleur
 * @param glass     vitre qui encadre la roulette quand elle ralentit sur un lot de ce palier (null : aucune)
 * @param sound     son joué au joueur quand le lot apparaît
 * @param volume    volume du son
 * @param pitch     hauteur du son
 * @param firework  feu d'artifice au-dessus du bloc de la caisse
 * @param totem     le lot vole à l'écran du joueur comme un totem d'immortalité
 * @param title     titre affiché au joueur ("" : aucun), %reward% = nom du lot
 * @param subtitle  sous-titre ("" : aucun), %reward% = nom du lot
 * @param announce  annonce du gain à tout le serveur
 * @param color     couleur du palier dans les scènes (contour lumineux, rayon), lue dans {@code couleur} ou tirée du nom
 */
public record Tier(String id, int rank, String name, Material glass, String sound, float volume, float pitch,
                   boolean firework, boolean totem, String title, String subtitle, boolean announce, Color color) {
}
