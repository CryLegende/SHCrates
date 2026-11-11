package org.Cry_Legende.sHCrates.utils;

import me.clip.placeholderapi.PlaceholderAPI;
import org.Cry_Legende.sHCrates.SHCrates;
import org.bukkit.OfflinePlayer;

/**
 * Remplacement des placeholders PlaceholderAPI. Sans PlaceholderAPI, le texte est rendu tel quel : la classe
 * de PlaceholderAPI n'est chargée que s'il est présent.
 */
public final class Papi {

    private Papi() {
    }

    public static String apply(OfflinePlayer player, String text) {
        if (text == null || text.indexOf('%') < 0) {
            return text;
        }
        SHCrates plugin = SHCrates.getInstance();
        if (plugin == null || !plugin.isPlaceholderApiHooked()) {
            return text;
        }
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
