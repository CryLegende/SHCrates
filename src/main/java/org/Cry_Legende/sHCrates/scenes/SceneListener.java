package org.Cry_Legende.sHCrates.scenes;

/** Suite donnée à une scène : remise du lot, dialogue de fin. */
public interface SceneListener {

    /** Le lot sort (une seule fois par scène). */
    void onReveal(Scene scene);

    /** La scène est finie, ses entités retirées. */
    void onEnd(Scene scene);

    /**
     * Le joueur est parti trop loin ou a changé de monde : la scène est retirée sans sa fin.
     *
     * @param wasRevealed vrai si le lot était déjà sorti
     */
    void onAbandon(Scene scene, boolean wasRevealed);
}
