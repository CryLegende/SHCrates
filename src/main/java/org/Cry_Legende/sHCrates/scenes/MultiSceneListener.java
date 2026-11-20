package org.Cry_Legende.sHCrates.scenes;

/**
 * Suite d'une scène qui montre plusieurs lots (ouverture à plusieurs tirages) : chaque lot est donné quand il rejoint le
 * joueur. {@link #onAbandon} et {@link #onEnd} donnent ceux qui ne l'ont pas encore été.
 */
public interface MultiSceneListener extends SceneListener {

    /** Le lot {@code index} (dans l'ordre de la liste donnée à la scène) arrive au joueur. */
    void onCollect(Scene scene, int index);
}
