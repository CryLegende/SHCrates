package org.Cry_Legende.sHCrates.utils;

// L'import de net.md_5.bungee.api.ChatColor est nécessaire pour le support des couleurs hexadécimales via la méthode .of().

import net.md_5.bungee.api.ChatColor;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ChatUtils {

    // Pattern compilé pour trouver les codes hexadécimaux du type &#RRGGBB.
    // Il est déclaré en tant que constante car il sera réutilisé sans jamais changer.
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    /**
     * Colore une chaîne de caractères en interprétant les codes de couleur classiques (&a, &l, etc.)
     * et les codes de couleur hexadécimaux modernes (ex: &#FF5733).
     *
     * @param message Le message à colorer.
     * @return Le message formaté avec les codes de couleur Minecraft.
     */
    public static String colorize(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        // Étape 1: Traduire les codes hexadécimaux (&#RRGGBB)
        // On utilise un Matcher pour trouver toutes les occurrences du pattern.
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer(); // StringBuffer est requis par la méthode appendReplacement.

        while (matcher.find()) {
            // Pour chaque occurrence trouvée, on la remplace par le code de couleur ChatColor correspondant.
            // ChatColor.of("#" + matcher.group(1)) crée l'objet couleur.
            // Le .toString() le convertit en séquence de code que le client comprend.
            matcher.appendReplacement(buffer, ChatColor.of("#" + matcher.group(1)).toString());
        }
        // appendTail ajoute le reste de la chaîne qui se trouve après la dernière occurrence trouvée.
        matcher.appendTail(buffer);

        // Étape 2: Traduire les codes de couleur standards (&a, &c, &l, etc.) dans la chaîne résultante.
        // Cela garantit que les deux systèmes de couleurs peuvent coexister.
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    /**
     * Applique la méthode colorize à chaque chaîne d'une liste.
     *
     * @param messages La liste de messages à colorer.
     * @return Une nouvelle liste contenant les messages colorés.
     */
    public static List<String> colorize(List<String> messages) {
        if (messages == null) {
            return List.of();
        }
        // On utilise un stream pour appliquer la fonction de coloration à chaque élément de la liste.
        return messages.stream().map(ChatUtils::colorize).collect(Collectors.toList());
    }
}
