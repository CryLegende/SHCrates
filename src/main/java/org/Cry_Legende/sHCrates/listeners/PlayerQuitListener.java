package org.Cry_Legende.sHCrates.listeners;

import org.Cry_Legende.sHCrates.SHCrates;
import org.Cry_Legende.sHCrates.animations.AnimationManager;
import org.Cry_Legende.sHCrates.gui.GUIManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final GUIManager guiManager;
    private final AnimationManager animationManager;

    public PlayerQuitListener(SHCrates plugin) {
        this.guiManager = plugin.getGuiManager();
        this.animationManager = plugin.getAnimationManager();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Nettoyer les GUIs actifs
        guiManager.playerQuitCleanup(player);

        // Arrêter toute animation en cours et donner la récompense si elle n'a pas été donnée.
        // C'est crucial pour que le joueur ne perde pas sa récompense en se déconnectant.
        if (animationManager.isPlayerInAnimation(player)) {
            animationManager.stopAnimationForPlayer(player, true);
        }
    }
}