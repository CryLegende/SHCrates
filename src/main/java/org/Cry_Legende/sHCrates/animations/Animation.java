package org.Cry_Legende.sHCrates.animations;

import org.Cry_Legende.sHCrates.crates.Crate;
import org.Cry_Legende.sHCrates.crates.Reward;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface Animation {

    /**
     * Démarre l'animation pour un joueur.
     *
     * @param player            Le joueur qui ouvre la caisse.
     * @param crate             La caisse en cours d'ouverture.
     * @param crateLocation     L'emplacement physique de la caisse (peut être null si ouvert par commande sans bloc).
     * @param chosenReward      La récompense qui a déjà été choisie par le système de chance.
     * @param onAnimationFinish Callback à exécuter une fois l'animation visuelle terminée.
     */
    void play(Player player, Crate crate, Location crateLocation, Reward chosenReward, Runnable onAnimationFinish);

    /**
     * @return Le nom unique de cette animation (ex: "ROULETTE", "CSGO").
     */
    String getName();

    /**
     * Indique si cette animation utilise un inventaire GUI.
     *
     * @return true si un GUI est utilisé, false sinon.
     */
    boolean usesGUI();

    /**
     * Méthode appelée pour arrêter proprement l'animation si le joueur se déconnecte
     * ou si une autre action l'interrompt.
     *
     * @param player     Le joueur concerné.
     * @param giveReward Si la récompense doit quand même être donnée (par exemple, si l'animation était sur le point de finir).
     */
    void stop(Player player, boolean giveReward);

    /**
     * Animation jouée dans le monde (scène), sans menu : l'ouverture est suivie jusqu'à la sortie du lot.
     */
    default boolean isWorld() {
        return false;
    }

    /**
     * Appelée avant l'ouverture : faux si l'animation ne peut pas se jouer ici (une scène sans place correcte), l'ouverture
     * prend alors l'animation de repli de la caisse.
     */
    default boolean prepare(Player player, Crate crate, Location crateLocation) {
        return true;
    }
}
