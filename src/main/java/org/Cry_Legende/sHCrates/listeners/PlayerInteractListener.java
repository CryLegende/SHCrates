package org.Cry_Legende.sHCrates.listeners;

import org.Cry_Legende.sHCrates.SHCrates;
import org.Cry_Legende.sHCrates.config.MessageManager;
import org.Cry_Legende.sHCrates.crates.Crate;
import org.Cry_Legende.sHCrates.crates.CrateLocation;
import org.Cry_Legende.sHCrates.crates.CrateManager;
import org.Cry_Legende.sHCrates.gui.GUIManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material; // Vérification du Tripwire Hook
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack; // Ajouté pour l'item en main

import java.util.Optional;

public class PlayerInteractListener implements Listener {

    private final SHCrates plugin;
    private final CrateManager crateManager;
    private final GUIManager guiManager;
    private final MessageManager messageManager;

    public PlayerInteractListener(SHCrates plugin) {
        this.plugin = plugin;
        this.crateManager = plugin.getCrateManager();
        this.guiManager = plugin.getGuiManager();
        this.messageManager = plugin.getMessageManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand(); // Récupérer l'item en main ici

        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (itemInHand != null && itemInHand.getType() == Material.TRIPWIRE_HOOK) {
                boolean isKey = false;
                for (Crate c : crateManager.getAllCrates()) {
                    if (crateManager.isPhysicalKey(itemInHand, c)) {
                        isKey = true;
                        break;
                    }
                }
                if (isKey) {
                    Block currentClickedBlock = event.getClickedBlock();
                    // Annuler si c'est un clic droit dans l'air AVEC une clé,
                    // OU si c'est un clic droit sur un bloc qui N'EST PAS une crate.
                    if (event.getAction() == Action.RIGHT_CLICK_AIR ||
                        (currentClickedBlock != null && crateManager.getCrateLocation(currentClickedBlock.getLocation()).isEmpty())) {
                        event.setCancelled(true);
                        // Si l'event est annulé et que c'était un RIGHT_CLICK_AIR, on ne veut pas continuer la logique de crate
                        if (event.getAction() == Action.RIGHT_CLICK_AIR) return;
                    }
                }
            }
        }

        Block clickedBlock = event.getClickedBlock();

        // Si c'est un clic dans l'air (clickedBlock == null) ou si la main n'est pas la principale,
        // ou si l'event a déjà été annulé par la logique anti-placement de clé, on sort.
        if (clickedBlock == null || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Location blockLocation = clickedBlock.getLocation();
        Optional<CrateLocation> crateLocationOpt = crateManager.getCrateLocation(blockLocation);

        if (crateLocationOpt.isEmpty()) {
            return; // Ce n'est pas un bloc de caisse
        }

        // À partir d'ici, on sait qu'on a cliqué sur un bloc de caisse valide.
        CrateLocation crateLocation = crateLocationOpt.get();
        Optional<Crate> crateOpt = crateManager.getCrate(crateLocation.getCrateId());

        if (crateOpt.isEmpty()) {
            plugin.getLogger().warning("Crate location found at " + blockLocation + " but crate type '" + crateLocation.getCrateId() + "' is not loaded.");
            return;
        }
        Crate crate = crateOpt.get();

        event.setCancelled(true); // Annuler l'interaction par défaut avec le bloc de caisse

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (!player.hasPermission("shcrates.preview." + crate.getId()) && !player.hasPermission("shcrates.preview.*") && !player.hasPermission("shcrates.admin")) {
                messageManager.sendMessage(player, "no_permission");
                return;
            }
            guiManager.openPreviewGUI(player, crate);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (!player.hasPermission("shcrates.open." + crate.getId()) && !player.hasPermission("shcrates.open.*") && !player.hasPermission("shcrates.admin")) {
                messageManager.sendMessage(player, "no_permission");
                return;
            }

            if (player.getGameMode() == GameMode.CREATIVE && !player.hasPermission("shcrates.admin.creativebypass")) {
                messageManager.sendMessage(player, "cannot_open_creative");
                return;
            }

            // Gestion du Shift+Clic pour QuickCrate
            if (player.isSneaking() && crate.getAnimationConfig().getType().equalsIgnoreCase("QUICKCRATE")) {
                plugin.getAnimationManager().attemptShiftOpenQuickCrate(player, crate, blockLocation);
            } else {
                plugin.getAnimationManager().attemptStartCrateOpening(player, crate, blockLocation, false);
            }
        }
    }
}
