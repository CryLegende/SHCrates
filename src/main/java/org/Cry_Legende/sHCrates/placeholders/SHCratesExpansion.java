package org.Cry_Legende.sHCrates.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.Cry_Legende.sHCrates.SHCrates;
import org.Cry_Legende.sHCrates.crates.Crate;
import org.Cry_Legende.sHCrates.crates.CrateManager;
import org.Cry_Legende.sHCrates.storage.PlayerDataManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

public class SHCratesExpansion extends PlaceholderExpansion {

    private final SHCrates plugin;
    private final CrateManager crateManager;
    private final PlayerDataManager playerDataManager;

    public SHCratesExpansion(SHCrates plugin) {
        this.plugin = plugin;
        this.crateManager = plugin.getCrateManager();
        this.playerDataManager = plugin.getPlayerDataManager();
    }

    @Override
    public @NotNull String getIdentifier() {
        return "shcrates"; // Ce sera le préfixe, ex: %shcrates_...%
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // Les placeholders peuvent être mis à jour sans /papi reload
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null) {
            return null;
        }
        Player player = offlinePlayer.getPlayer(); // Peut être null si le joueur est hors ligne

        // Les totaux sont testés avant virtual_<crate>/physical_<crate>, qui les capturaient
        // (ils renvoyaient « Crate_Not_Found »).
        // Placeholder pour le nombre total de clés virtuelles pour toutes les caisses
        // %shcrates_virtual_total%
        if (params.equalsIgnoreCase("virtual_total")) {
            int totalVirtual = 0;
            Map<String, Integer> allVirtualKeys = playerDataManager.peekPlayerData(offlinePlayer.getUniqueId()).getAllVirtualKeys();
            for (int count : allVirtualKeys.values()) {
                totalVirtual += count;
            }
            return String.valueOf(totalVirtual);
        }

        // Placeholder pour le nombre total de clés physiques pour toutes les caisses
        // %shcrates_physical_total%
        if (params.equalsIgnoreCase("physical_total")) {
            if (player == null) {
                return "Player_Offline";
            }
            int totalPhysical = 0;
            for (Crate crate : crateManager.getAllCrates()) {
                for (ItemStack item : player.getInventory().getContents()) {
                    if (crateManager.isPhysicalKey(item, crate)) {
                        totalPhysical += item.getAmount();
                    }
                }
            }
            return String.valueOf(totalPhysical);
        }

        // Format: %shcrates_virtual_<cratename>%
        if (params.startsWith("virtual_")) {
            String crateName = params.substring("virtual_".length());
            Optional<Crate> crateOpt = crateManager.getCrate(crateName);
            if (crateOpt.isPresent()) {
                Crate crate = crateOpt.get();
                int keyCount = playerDataManager.peekPlayerData(offlinePlayer.getUniqueId()).getVirtualKeys(crate.getInternalID());
                return String.valueOf(keyCount);
            } else {
                return "Crate_Not_Found";
            }
        }

        // Format: %shcrates_physical_<cratename>%
        if (params.startsWith("physical_")) {
            if (player == null) { // Nécessite que le joueur soit en ligne pour scanner l'inventaire
                return "Player_Offline";
            }
            String crateName = params.substring("physical_".length());
            Optional<Crate> crateOpt = crateManager.getCrate(crateName);
            if (crateOpt.isPresent()) {
                Crate crate = crateOpt.get();
                int physicalAmount = 0;
                for (ItemStack item : player.getInventory().getContents()) {
                    if (crateManager.isPhysicalKey(item, crate)) {
                        physicalAmount += item.getAmount();
                    }
                }
                return String.valueOf(physicalAmount);
            } else {
                return "Crate_Not_Found";
            }
        }

        return null; // Placeholder non reconnu
    }
}
