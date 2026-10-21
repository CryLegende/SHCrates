package org.Cry_Legende.sHCrates.listeners;

import org.Cry_Legende.sHCrates.SHCrates;
import org.Cry_Legende.sHCrates.config.MessageManager;
import org.Cry_Legende.sHCrates.crates.Crate;
import org.Cry_Legende.sHCrates.crates.CrateLocation;
import org.Cry_Legende.sHCrates.crates.CrateManager;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Optional;

public class BlockBreakListener implements Listener {

    private final SHCrates plugin;
    private final CrateManager crateManager;
    private final MessageManager messageManager;

    public BlockBreakListener(SHCrates plugin) {
        this.plugin = plugin;
        this.crateManager = plugin.getCrateManager();
        this.messageManager = plugin.getMessageManager();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        Optional<CrateLocation> crateLocationOpt = crateManager.getCrateLocation(block.getLocation());

        if (crateLocationOpt.isPresent()) {
            CrateLocation crateLocation = crateLocationOpt.get();
            Optional<Crate> crateOpt = crateManager.getCrate(crateLocation.getCrateId());
            String crateDisplayName = crateOpt.map(Crate::getDisplayName).orElse(crateLocation.getCrateId());

            // Vérifier la permission de supprimer cette crate spécifique ou toutes les crates
            // La permission pourrait être shcrates.admin.removeblock ou shcrates.removeblock.<cratename>
            if (!player.hasPermission("shcrates.admin.removeblock") &&
                !player.hasPermission("shcrates.admin") &&
                !player.hasPermission("shcrates.removeblock." + crateLocation.getCrateId())) {

                messageManager.sendMessage(player, "no_permission_break_crate", MessageManager.ph(
                    "%crate_display_name%", crateDisplayName
                ));
                event.setCancelled(true);
                return;
            }

            // En mode survie, si on casse la crate, on pourrait vouloir "rendre" la clé (ou pas, selon la config)
            // Pour l'instant, on la supprime simplement. Le bloc se cassera normalement si l'event n'est pas annulé.
            // Si le joueur est en créatif, le bloc se casse sans drop, ce qui est bien.

            boolean removed = crateManager.removeCrateLocation(block.getLocation());
            if (removed) {
                messageManager.sendMessage(player, "crate_block_removed_by_break", MessageManager.ph(
                    "%crate_display_name%", crateDisplayName
                ));
                plugin.getLogger().info("Crate '" + crateDisplayName + "' at " + block.getLocation().toString() + " was removed by " + player.getName() + " breaking the block.");
            } else {
                // Devrait pas arriver si crateLocationOpt était présent, mais sécurité
                plugin.getLogger().warning("Failed to remove crate location that was supposedly present at " + block.getLocation().toString());
            }
        }
    }
}
