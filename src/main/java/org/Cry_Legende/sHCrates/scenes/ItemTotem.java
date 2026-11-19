package org.Cry_Legende.sHCrates.scenes;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.DeathProtection;
import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * « Totem du lot » : l'animation du totem d'immortalité (l'objet qui jaillit au centre de l'écran) montre le lot gagné au
 * lieu du totem. Le client montre l'objet de sa main qui protège de la mort : on lui fait croire un instant que sa seconde
 * main tient le lot, avec cette protection, puis on lui rend son vrai inventaire au tick suivant. Rien ne change côté
 * serveur, et seul le joueur voit et entend l'animation.
 */
public final class ItemTotem {

    private ItemTotem() {
    }

    public static void play(Plugin plugin, Player player, ItemStack shown) {
        ItemStack fake = shown == null || shown.getType().isAir() ? new ItemStack(Material.TOTEM_OF_UNDYING) : shown.clone();
        fake.setAmount(1);
        try {
            fake.setData(DataComponentTypes.DEATH_PROTECTION, DeathProtection.deathProtection(List.of()));
        } catch (RuntimeException | LinkageError e) {
            // composant indisponible : le totem classique
            player.sendEntityEffect(EntityEffect.PROTECTED_FROM_DEATH, player);
            return;
        }
        ItemStack main = player.getInventory().getItemInMainHand();
        // le client prend la première main protégée en commençant par la main principale
        boolean mainProtected = main.hasData(DataComponentTypes.DEATH_PROTECTION);
        if (mainProtected) {
            player.sendEquipmentChange(player, EquipmentSlot.HAND, fake);
        } else {
            player.sendEquipmentChange(player, EquipmentSlot.OFF_HAND, fake);
        }
        player.sendEntityEffect(EntityEffect.PROTECTED_FROM_DEATH, player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) player.updateInventory();
        }, 2L);
    }
}
