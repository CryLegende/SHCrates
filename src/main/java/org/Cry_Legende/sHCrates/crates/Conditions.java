package org.Cry_Legende.sHCrates.crates;

import org.Cry_Legende.sHCrates.SHCrates;
import org.Cry_Legende.sHCrates.utils.Papi;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Condition d'une récompense ({@code Condition:} dans crates/), par exemple {@code "%superior_island_level% < 5000"}.
 * Les deux côtés passent par PlaceholderAPI puis sont comparés comme des nombres ; si l'un des deux n'est pas un
 * nombre, seuls == et != comparent le texte. Une condition illisible laisse le lot gagnable et n'est signalée
 * qu'une fois en console.
 */
public final class Conditions {

    private static final Pattern FORMAT = Pattern.compile("^(.+?)\\s*(<=|>=|==|!=|<|>)\\s*(.+)$");
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private Conditions() {
    }

    public static boolean test(Player player, String condition) {
        Matcher m = FORMAT.matcher(condition.trim());
        if (!m.matches()) {
            warn(condition, "format attendu : <valeur> <opérateur> <valeur>");
            return true;
        }
        String left = Papi.apply(player, m.group(1).trim());
        String right = Papi.apply(player, m.group(3).trim());
        String op = m.group(2);
        Double a = number(left);
        Double b = number(right);
        if (a == null || b == null) {
            return switch (op) {
                case "==" -> left.equalsIgnoreCase(right);
                case "!=" -> !left.equalsIgnoreCase(right);
                default -> {
                    warn(condition, "valeur non numérique (" + left + " / " + right + ")");
                    yield true;
                }
            };
        }
        return switch (op) {
            case "<" -> a < b;
            case "<=" -> a <= b;
            case ">" -> a > b;
            case ">=" -> a >= b;
            case "==" -> a.doubleValue() == b.doubleValue();
            default -> a.doubleValue() != b.doubleValue();
        };
    }

    /** Nombre lu dans un texte de placeholder : espaces et séparateurs de milliers ignorés, virgule décimale acceptée. */
    static Double number(String text) {
        String cleaned = text.replace(" ", "").replace(" ", "").replace(" ", "").replace("_", "");
        if (cleaned.indexOf(',') >= 0 && cleaned.indexOf('.') >= 0) {
            cleaned = cleaned.replace(",", "");
        } else if (cleaned.chars().filter(c -> c == ',').count() == 1 && cleaned.length() - cleaned.indexOf(',') != 4) {
            cleaned = cleaned.replace(',', '.');
        } else {
            cleaned = cleaned.replace(",", "");
        }
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void warn(String condition, String why) {
        if (WARNED.add(condition)) {
            SHCrates.getInstance().getLogger().warning("Condition de récompense ignorée « " + condition + " » : " + why + ".");
        }
    }
}
