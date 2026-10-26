package org.Cry_Legende.sHCrates.listeners;

import org.Cry_Legende.sHCrates.SHCrates;
import org.Cry_Legende.sHCrates.animations.AnimationManager;
import org.Cry_Legende.sHCrates.crates.Crate;
import org.Cry_Legende.sHCrates.crates.config.PreviewGUIConfig;
import org.Cry_Legende.sHCrates.gui.GUIManager;
import org.Cry_Legende.sHCrates.gui.PagedGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class InventoryClickListener implements Listener {

    private final SHCrates plugin;
    private final GUIManager guiManager;
    private final AnimationManager animationManager;

    public InventoryClickListener(SHCrates plugin) {
        this.plugin = plugin;
        this.guiManager = plugin.getGuiManager();
        this.animationManager = plugin.getAnimationManager();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory topInventory = event.getView().getTopInventory();

        // Sans instantané : ce listener voit tous les clics du serveur, y compris dans les coffres
        InventoryHolder holder = topInventory.getHolder(false);

        if (holder instanceof PagedGUI pagedGUI) {
            handlePreviewGUIClick(event, player, pagedGUI);
        } else if (guiManager.isAnimationInventory(topInventory)) {
            handleAnimationGUIClick(event, player);
        }
    }

    private void handlePreviewGUIClick(InventoryClickEvent event, Player player, PagedGUI pagedGUI) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(pagedGUI.getInventory())) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir()) {
            return;
        }

        Crate crate = pagedGUI.getAssociatedCrate();
        if (crate == null) return;
        PreviewGUIConfig guiConfig = crate.getPreviewGUIConfig();

        // Gérer les boutons de pagination
        PreviewGUIConfig.ButtonConfig nextPageButton = guiConfig.getButton("nextpage");
        if (nextPageButton != null && event.getSlot() == nextPageButton.getSlot() && pagedGUI.hasNextPage()) {
            guiManager.openPreviewGUIPage(player, crate, pagedGUI.getCurrentPage() + 1);
            return;
        }

        PreviewGUIConfig.ButtonConfig prevPageButton = guiConfig.getButton("previouspage");
        if (prevPageButton != null && event.getSlot() == prevPageButton.getSlot() && pagedGUI.hasPreviousPage()) {
            guiManager.openPreviewGUIPage(player, crate, pagedGUI.getCurrentPage() - 1);
            return;
        }

        // Gérer les fillers et boutons personnalisés
        if (guiConfig.getFillerItems() != null) {
            for (PreviewGUIConfig.FillerEntryConfig fillerConfig : guiConfig.getFillerItems().values()) {
                if (fillerConfig.getSlots().contains(event.getSlot())) {
                    handleCommandsAndClose(player, fillerConfig.getCommandsOnClick(), fillerConfig.shouldCloseOnClick());
                    return;
                }
            }
        }

        if (guiConfig.getButtons() != null) {
            for (Map.Entry<String, PreviewGUIConfig.ButtonConfig> entry : guiConfig.getButtons().entrySet()) {
                String key = entry.getKey().toLowerCase();
                if (key.equals("nextpage") || key.equals("previouspage")) continue;

                PreviewGUIConfig.ButtonConfig buttonConfig = entry.getValue();
                if (event.getSlot() == buttonConfig.getSlot()) {
                    handleCommandsAndClose(player, buttonConfig.getCommands(), buttonConfig.shouldCloseOnClick());
                    return;
                }
            }
        }
    }

    private void handleAnimationGUIClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);

        AnimationManager.ActiveAnimationInfo animInfo = animationManager.getActiveAnimationInfo(player);
        if (animInfo == null) return;

        // Gérer le clic sur le bouton de relance (uniquement dans le GUI, pas dans l'inventaire du joueur)
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (animInfo.isAnimationFinished() && plugin.getConfigManager().isRerollEnabled()) {
            int rerollSlot = animInfo.getCrate().getAnimationConfig().getRerollButtonSlot();
            if (event.getSlot() == rerollSlot) {
                ItemStack clicked = event.getCurrentItem();
                ItemStack rerollButton = plugin.getConfigManager().getRerollButtonItem();

                // Vérifier si c'est bien le bouton de relance (et pas le bouton "utilisé")
                if (clicked != null && clicked.isSimilar(rerollButton)) {
                    animationManager.performReroll(player);
                }
            }
        }
    }

    private void handleCommandsAndClose(Player player, java.util.List<String> commands, boolean closeOnClick) {
        if (commands != null && !commands.isEmpty()) {
            for (String cmd : commands) {
                plugin.getServer().dispatchCommand(
                    plugin.getServer().getConsoleSender(),
                    cmd.replace("%player_name%", player.getName())
                        .replace("%player_uuid%", player.getUniqueId().toString())
                );
            }
        }
        if (closeOnClick) {
            player.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (topInventory.getHolder(false) instanceof PagedGUI || guiManager.isAnimationInventory(topInventory)) {
            // Empêche de glisser des items dans nos GUIs
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory topInventory = event.getView().getTopInventory();

        InventoryHolder holder = topInventory.getHolder(false);

        if (holder instanceof PagedGUI pagedGUI) {
            guiManager.removeActiveGUI(player, pagedGUI);
        } else if (guiManager.isAnimationInventory(topInventory)) {
            AnimationManager.ActiveAnimationInfo animInfo = animationManager.getActiveAnimationInfo(player);

            if (animInfo != null) {
                // Si la récompense n'a pas encore été donnée, on la donne.
                // Cela se produit lorsque le joueur ferme le GUI manuellement après la fin de l'animation,
                // ou si une action force la fermeture (mort, etc.).
                // À la déconnexion, cette fermeture précède PlayerQuitEvent : la récompense est donnée
                // tout de suite, sinon les commandes partiraient au tick suivant, joueur déjà parti.
                if (!animInfo.isRewardGiven()) {
                    animInfo.setRewardGiven(true);
                    animationManager.giveReward(player, animInfo.getCrate(), animInfo.getChosenReward(), animInfo.getCrateLocation(),
                        event.getReason() == InventoryCloseEvent.Reason.DISCONNECT);
                }

                // Nettoie l'état de l'animation pour le joueur (arrête les tâches restantes, etc.).
                // Le second paramètre est 'false' car la récompense a déjà été gérée juste au-dessus.
                animationManager.stopAnimationForPlayer(player, false);
            }
            // Retire le GUI de la liste des GUIs d'animation actifs.
            guiManager.removeAnimationGUI(player);
        }
    }
}
