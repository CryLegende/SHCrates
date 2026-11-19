package org.Cry_Legende.sHCrates.commands;

import org.Cry_Legende.sHCrates.SHCrates;
import org.Cry_Legende.sHCrates.config.MessageManager;
import org.Cry_Legende.sHCrates.crates.Crate;
import org.Cry_Legende.sHCrates.crates.Reward;
import org.Cry_Legende.sHCrates.scenes.Placement;
import org.Cry_Legende.sHCrates.scenes.Scene;
import org.Cry_Legende.sHCrates.scenes.SceneKind;
import org.Cry_Legende.sHCrates.scenes.SceneListener;
import org.Cry_Legende.sHCrates.scenes.SceneManager;
import org.Cry_Legende.sHCrates.scenes.SceneSpots;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * /shcrates scene : emplacements des scènes et essai d'une animation sans clé.
 * <ul>
 *   <li>scene add [echelle] : emplacement au sol, à la position du joueur ;</li>
 *   <li>scene remove : retire l'emplacement le plus proche (3 blocs) ;</li>
 *   <li>scene list : emplacements de ce monde ;</li>
 *   <li>scene test &lt;caisse&gt; [lot] : joue la scène de la caisse, sans clé et sans rien donner.</li>
 * </ul>
 */
final class SceneCommand {

    private final SHCrates plugin;
    private final MessageManager messages;

    SceneCommand(SHCrates plugin) {
        this.plugin = plugin;
        this.messages = plugin.getMessageManager();
    }

    void handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.sendMessage(sender, "console_cannot_execute");
            return;
        }
        String action = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
        SceneManager scenes = plugin.getSceneManager();
        switch (action) {
            case "add" -> {
                double scale = 1.0;
                if (args.length >= 3) {
                    try {
                        scale = Math.max(0.4, Math.min(1.5, Double.parseDouble(args[2].replace(',', '.'))));
                    } catch (NumberFormatException e) {
                        messages.sendRawMessage(player, "scene_usage", MessageManager.ph());
                        return;
                    }
                }
                Location l = player.getLocation();
                scenes.spots().add(new SceneSpots.Spot(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), scale));
                messages.sendRawMessage(player, "scene_spot_added", MessageManager.ph(
                    "%x%", fmt(l.getX()), "%y%", fmt(l.getY()), "%z%", fmt(l.getZ()), "%world%", l.getWorld().getName(),
                    "%scale%", fmt(scale)));
            }
            case "remove" -> {
                SceneSpots.Spot removed = scenes.spots().removeNearest(player.getLocation(), 3);
                if (removed == null) {
                    messages.sendRawMessage(player, "scene_spot_none_near", MessageManager.ph());
                } else {
                    messages.sendRawMessage(player, "scene_spot_removed", MessageManager.ph(
                        "%x%", fmt(removed.x()), "%y%", fmt(removed.y()), "%z%", fmt(removed.z())));
                }
            }
            case "list" -> {
                List<SceneSpots.Spot> spots = scenes.spots().inWorld(player.getWorld().getName());
                messages.sendRawMessage(player, "scene_spot_list_header", MessageManager.ph(
                    "%count%", String.valueOf(spots.size()), "%world%", player.getWorld().getName()));
                for (SceneSpots.Spot spot : spots) {
                    messages.sendRawMessage(player, "scene_spot_list_line", MessageManager.ph(
                        "%x%", fmt(spot.x()), "%y%", fmt(spot.y()), "%z%", fmt(spot.z()), "%scale%", fmt(spot.scale()),
                        "%distance%", fmt(Math.sqrt(spot.distanceSquared(player.getLocation())))));
                }
            }
            case "test" -> test(player, args);
            default -> messages.sendRawMessage(player, "scene_usage", MessageManager.ph());
        }
    }

    /** Joue la scène d'une caisse pour essayer : rien n'est consommé ni donné, les compteurs ne bougent pas. */
    private void test(Player player, String[] args) {
        if (args.length < 3) {
            messages.sendRawMessage(player, "scene_usage", MessageManager.ph());
            return;
        }
        Optional<Crate> crateOpt = plugin.getCrateManager().getCrate(args[2]);
        if (crateOpt.isEmpty()) {
            messages.sendMessage(player, "crate_not_exist", MessageManager.ph("%crate_name%", args[2]));
            return;
        }
        Crate crate = crateOpt.get();
        SceneKind kind = kindOf(crate.getAnimationConfig().getType());
        if (kind == null) {
            messages.sendRawMessage(player, "scene_test_not_scene", MessageManager.ph("%crate_display_name%", crate.getDisplayName()));
            return;
        }
        Reward reward = null;
        if (args.length >= 4) {
            reward = crate.getRewards().stream().filter(r -> r.getId().equalsIgnoreCase(args[3])).findFirst().orElse(null);
            if (reward == null) {
                messages.sendRawMessage(player, "scene_test_unknown_reward", MessageManager.ph("%reward%", args[3]));
                return;
            }
        }
        if (reward == null) {
            List<Reward> eligible = crate.getEligibleRewards(player);
            reward = eligible.isEmpty() ? crate.getRewards().get(0) : Crate.pick(eligible);
        }
        SceneManager scenes = plugin.getSceneManager();
        Placement placement = scenes.prepare(player, kind);
        if (placement == null) {
            messages.sendRawMessage(player, "scene_test_no_place", MessageManager.ph());
            return;
        }
        Reward chosen = reward;
        scenes.play(player, crate, chosen, scenes.placementFor(player, kind), kind, true, new SceneListener() {
            @Override
            public void onReveal(Scene scene) {
                plugin.getAnimationManager().reveal(player, crate, chosen, null, org.Cry_Legende.sHCrates.animations.AnimationManager.Guarantee.NONE, scene);
            }

            @Override
            public void onEnd(Scene scene) {
                // essai : pas de dialogue de relance
            }

            @Override
            public void onAbandon(Scene scene, boolean wasRevealed) {
                // essai : rien à donner
            }
        });
        messages.sendRawMessage(player, "scene_test_started", MessageManager.ph(
            "%reward_display_name%", chosen.getDisplayName(), "%crate_display_name%", crate.getDisplayName(),
            "%spot%", placement.fixedSpot() ? "emplacement posé" : "place trouvée devant toi"));
    }

    static SceneKind kindOf(String type) {
        for (SceneKind k : SceneKind.values()) {
            if (k.type().equalsIgnoreCase(type)) return k;
        }
        return null;
    }

    static List<String> complete(SHCrates plugin, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 2) {
            out.addAll(List.of("add", "remove", "list", "test"));
        } else if (args.length == 3 && args[1].equalsIgnoreCase("test")) {
            for (Crate c : plugin.getCrateManager().getAllCrates()) {
                if (kindOf(c.getAnimationConfig().getType()) != null) out.add(c.getId());
            }
        } else if (args.length == 4 && args[1].equalsIgnoreCase("test")) {
            plugin.getCrateManager().getCrate(args[2]).ifPresent(c -> c.getRewards().forEach(r -> out.add(r.getId())));
        } else if (args.length == 3 && args[1].equalsIgnoreCase("add")) {
            out.addAll(List.of("1", "0.8"));
        }
        return out;
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
