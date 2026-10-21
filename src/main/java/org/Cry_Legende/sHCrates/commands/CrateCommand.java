package org.Cry_Legende.sHCrates.commands;

import org.Cry_Legende.sHCrates.SHCrates;
import org.Cry_Legende.sHCrates.config.MessageManager;
import org.Cry_Legende.sHCrates.crates.Crate;
import org.Cry_Legende.sHCrates.crates.CrateManager;
import org.Cry_Legende.sHCrates.gui.GUIManager;
import org.Cry_Legende.sHCrates.storage.PlayerDataManager;
import org.Cry_Legende.sHCrates.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays; // Assurez-vous que cet import est là
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CrateCommand implements CommandExecutor, TabCompleter {

    private final SHCrates plugin;
    private final CrateManager crateManager;
    private final PlayerDataManager playerDataManager;
    private final GUIManager guiManager;
    private final MessageManager messageManager;

    private final List<String> adminSubCommands = List.of("givekey", "takekey", "giveall", "reload", "setblock", "removeblock");
    private final List<String> playerSubCommands = List.of("help", "list", "preview", "open", "openall", "keys");


    public CrateCommand(SHCrates plugin) {
        this.plugin = plugin;
        this.crateManager = plugin.getCrateManager();
        this.playerDataManager = plugin.getPlayerDataManager();
        this.guiManager = plugin.getGuiManager();
        this.messageManager = plugin.getMessageManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "help":
                sendHelpMessage(sender);
                break;
            case "list":
                handleListCrates(sender);
                break;
            case "preview":
                handlePreviewCommand(sender, args);
                break;
            case "open":
                handleOpenCommand(sender, args, false);
                break;
            case "openall":
                handleOpenCommand(sender, args, true);
                break;
            case "keys":
                handleKeysCommand(sender, args);
                break;
            case "givekey":
                handleGiveKeyCommand(sender, args);
                break;
            case "takekey":
                handleTakeKeyCommand(sender, args);
                break;
            case "giveall":
                handleGiveAllKeyCommand(sender, args);
                break;
            case "setblock":
                handleSetBlockCommand(sender, args);
                break;
            case "removeblock":
                handleRemoveBlockCommand(sender, args);
                break;
            case "reload":
                handleReloadCommand(sender);
                break;
            default:
                messageManager.sendMessage(sender, "unknown_command", MessageManager.ph("%command%", subCommand));
                sendHelpMessage(sender);
                break;
        }
        return true;
    }

    private void sendHelpMessage(CommandSender sender) {
        messageManager.sendRawMessage(sender, "command_usage_help_header", Collections.emptyMap());
        for (String cmd : playerSubCommands) {
            String usage = plugin.getCommand("shcrates").getUsage().lines()
                .filter(line -> line.contains("/shcrates " + cmd) || line.contains("/shc " + cmd)) // Gérer les alias
                .map(line -> line.split(" - ", 2)[0]) // Prendre seulement la partie commande
                .findFirst().orElse("/shcrates " + cmd); // Fallback
            messageManager.sendRawMessage(sender, "command_usage_line", MessageManager.ph(
                "%command%", ChatUtils.colorize(usage.replace("§e", "&e").replace("§7", "&7").replace("§c", "&c")),
                "%description%", getCommandDescription(cmd)
            ));
        }
        if (sender.hasPermission("shcrates.admin")) {
            messageManager.sendRawMessage(sender, "command_usage_admin_header", Collections.emptyMap());
            for (String cmd : adminSubCommands) {
                String usage = plugin.getCommand("shcrates").getUsage().lines()
                    .filter(line -> line.contains("/shcrates " + cmd) || line.contains("/shc " + cmd))
                    .map(line -> line.split(" - ", 2)[0])
                    .findFirst().orElse("/shcrates " + cmd);
                messageManager.sendRawMessage(sender, "command_usage_line", MessageManager.ph(
                    "%command%", ChatUtils.colorize(usage.replace("§e", "&e").replace("§7", "&7").replace("§c", "&c")),
                    "%description%", getCommandDescription(cmd)
                ));
            }
        }
    }

    private String getCommandDescription(String subCommand) {
        String usageMessage = plugin.getCommand("shcrates").getUsage();
        for (String line : usageMessage.split("\n")) {
            // Adapter pour mieux matcher la sous-commande
            if (line.matches(".*(/shcrates|/shc) " + Pattern.quote(subCommand) + "(\\s.*|\\s*)-.*")) {
                String[] parts = line.split(" - ", 2);
                if (parts.length > 1) {
                    return ChatUtils.colorize(parts[1].replace("§7", "&7").replace("§e", "&e").replace("§c", "&c"));
                }
            }
        }
        return "Description non trouvée.";
    }


    private void handleListCrates(CommandSender sender) {
        if (!sender.hasPermission("shcrates.command.list") && !sender.hasPermission("shcrates.admin")) {
            messageManager.sendMessage(sender, "no_permission");
            return;
        }
        Collection<Crate> crates = crateManager.getAllCrates();
        if (crates.isEmpty()) {
            messageManager.sendMessage(sender, "no_crates_defined");
            return;
        }
        messageManager.sendRawMessage(sender, "crate_list_header", Collections.emptyMap());
        for (Crate crate : crates) {
            messageManager.sendRawMessage(sender, "crate_list_entry", MessageManager.ph(
                "%crate_id%", crate.getId(),
                "%crate_display_name%", crate.getDisplayName()
            ));
        }
    }

    private void handlePreviewCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messageManager.sendMessage(sender, "command_usage_preview");
            return;
        }
        String crateName = args[1];
        Optional<Crate> crateOpt = crateManager.getCrate(crateName);
        if (crateOpt.isEmpty()) {
            messageManager.sendMessage(sender, "crate_not_exist", MessageManager.ph("%crate_name%", crateName));
            return;
        }
        Crate crate = crateOpt.get();
        Player targetPlayer;
        if (args.length >= 3) {
            if (!sender.hasPermission("shcrates.admin.previewothers") && !sender.hasPermission("shcrates.admin")) {
                messageManager.sendMessage(sender, "no_permission");
                return;
            }
            targetPlayer = Bukkit.getPlayer(args[2]);
            if (targetPlayer == null) {
                messageManager.sendMessage(sender, "player_not_found", MessageManager.ph("%player%", args[2]));
                return;
            }
        } else {
            if (!(sender instanceof Player)) {
                messageManager.sendMessage(sender, "console_cannot_execute_personal");
                return;
            }
            targetPlayer = (Player) sender;
        }
        if (!targetPlayer.hasPermission("shcrates.preview." + crate.getId()) && !targetPlayer.hasPermission("shcrates.preview.*") && !targetPlayer.hasPermission("shcrates.admin")) {
            messageManager.sendMessage(sender, "no_permission_for_target", MessageManager.ph("%player%", targetPlayer.getName()));
            return;
        }
        guiManager.openPreviewGUI(targetPlayer, crate);
    }

    private void handleOpenCommand(CommandSender sender, String[] args, boolean openAll) {
        if (args.length < 2) {
            String usageKey = openAll ? "command_usage_openall" : "command_usage_open";
            messageManager.sendMessage(sender, usageKey);
            return;
        }
        String crateName = args[1];
        Optional<Crate> crateOpt = crateManager.getCrate(crateName);
        if (crateOpt.isEmpty()) {
            messageManager.sendMessage(sender, "crate_not_exist", MessageManager.ph("%crate_name%", crateName));
            return;
        }
        Crate crate = crateOpt.get();
        Player targetPlayer;
        if (args.length >= 3) {
            if (!sender.hasPermission("shcrates.admin.openothers") && !sender.hasPermission("shcrates.admin")) {
                messageManager.sendMessage(sender, "no_permission");
                return;
            }
            targetPlayer = Bukkit.getPlayer(args[2]);
            if (targetPlayer == null) {
                messageManager.sendMessage(sender, "player_not_found", MessageManager.ph("%player%", args[2]));
                return;
            }
        } else {
            if (!(sender instanceof Player)) {
                messageManager.sendMessage(sender, "console_cannot_execute_personal");
                return;
            }
            targetPlayer = (Player) sender;
        }
        if (!targetPlayer.hasPermission("shcrates.open." + crate.getId()) && !targetPlayer.hasPermission("shcrates.open.*") && !targetPlayer.hasPermission("shcrates.admin")) {
            messageManager.sendMessage(sender, "no_permission_for_target", MessageManager.ph("%player%", targetPlayer.getName()));
            return;
        }
        if (openAll) {
            plugin.getAnimationManager().openAllCrates(targetPlayer, crate);
        } else {
            plugin.getAnimationManager().attemptStartCrateOpening(targetPlayer, crate, null, false);
        }
    }

    private void handleKeysCommand(CommandSender sender, String[] args) {
        Player targetPlayer;
        if (args.length >= 2) {
            if (!sender.hasPermission("shcrates.admin.keysothers") && !sender.hasPermission("shcrates.admin")) {
                messageManager.sendMessage(sender, "no_permission");
                return;
            }
            OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(args[1]);
            if (!offlineTarget.hasPlayedBefore() && !offlineTarget.isOnline()) {
                messageManager.sendMessage(sender, "player_never_played", MessageManager.ph("%player%", args[1]));
                return;
            }
            targetPlayer = offlineTarget.getPlayer();
            if (targetPlayer == null && offlineTarget.isOnline()) {
                messageManager.sendMessage(sender, "player_not_found", MessageManager.ph("%player%", args[1]));
                return;
            }
            if (targetPlayer == null && !offlineTarget.isOnline()) {
                messageManager.sendMessage(sender, "keys_offline_player_not_supported_yet");
                return;
            }
        } else {
            if (!(sender instanceof Player)) {
                messageManager.sendMessage(sender, "console_cannot_execute_personal");
                return;
            }
            targetPlayer = (Player) sender;
        }
        if (!sender.equals(targetPlayer) && !sender.hasPermission("shcrates.command.keys.others") && !sender.hasPermission("shcrates.admin")) {
            messageManager.sendMessage(sender, "no_permission");
            return;
        }
        if (!targetPlayer.hasPermission("shcrates.command.keys") && !targetPlayer.hasPermission("shcrates.admin")) {
            messageManager.sendMessage(sender, "no_permission_for_target", MessageManager.ph("%player%", targetPlayer.getName()));
            return;
        }

        Map<String, Integer> virtualKeys = playerDataManager.getAllVirtualKeysForPlayer(targetPlayer.getUniqueId());
        List<String> keyLines = new ArrayList<>();
        virtualKeys.forEach((crateInternalId, amount) -> {
            crateManager.getAllCrates().stream()
                .filter(c -> c.getInternalID().equals(crateInternalId))
                .findFirst()
                .ifPresent(crate -> keyLines.add(messageManager.getRawPrefixedMessage("player_keys_virtual_format", MessageManager.ph(
                    "%crate_name%", crate.getDisplayName(),
                    "%amount%", String.valueOf(amount)
                ))));
        });
        for (Crate crate : crateManager.getAllCrates()) {
            int physicalAmount = 0;
            for (ItemStack item : targetPlayer.getInventory().getContents()) {
                if (crateManager.isPhysicalKey(item, crate)) {
                    physicalAmount += item.getAmount();
                }
            }
            if (physicalAmount > 0) {
                keyLines.add(messageManager.getRawPrefixedMessage("player_keys_physical_format", MessageManager.ph(
                    "%crate_name%", crate.getDisplayName(),
                    "%amount%", String.valueOf(physicalAmount)
                )));
            }
        }
        if (keyLines.isEmpty()) {
            messageManager.sendMessage(targetPlayer, "player_keys_no_keys");
            if (!sender.equals(targetPlayer)) {
                messageManager.sendMessage(sender, "target_has_no_keys", MessageManager.ph("%player%", targetPlayer.getName()));
            }
        } else {
            messageManager.sendMessage(targetPlayer, "player_keys_header");
            keyLines.forEach(targetPlayer::sendMessage);
            if (!sender.equals(targetPlayer)) {
                messageManager.sendMessage(sender, "target_keys_header", MessageManager.ph("%player%", targetPlayer.getName()));
                keyLines.forEach(sender::sendMessage);
            }
        }
    }

    private void handleGiveKeyCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shcrates.admin.givekey") && !sender.hasPermission("shcrates.admin")) {
            messageManager.sendMessage(sender, "no_permission");
            return;
        }
        if (args.length < 4) {
            messageManager.sendMessage(sender, "command_usage_givekey");
            return;
        }
        Player targetPlayer = Bukkit.getPlayer(args[1]);
        if (targetPlayer == null) {
            messageManager.sendMessage(sender, "player_not_found", MessageManager.ph("%player%", args[1]));
            return;
        }
        String crateName = args[2];
        Optional<Crate> crateOpt = crateManager.getCrate(crateName);
        if (crateOpt.isEmpty()) {
            messageManager.sendMessage(sender, "crate_not_exist", MessageManager.ph("%crate_name%", crateName));
            return;
        }
        Crate crate = crateOpt.get();
        int amount;
        try {
            amount = Integer.parseInt(args[3]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            messageManager.sendMessage(sender, "invalid_amount", MessageManager.ph("%amount%", args[3]));
            return;
        }
        boolean physical = true;
        if (args.length >= 5) {
            if (args[4].equalsIgnoreCase("virtual") || args[4].equalsIgnoreCase("v")) {
                physical = false;
            } else if (!args[4].equalsIgnoreCase("physical") && !args[4].equalsIgnoreCase("p")) {
                messageManager.sendMessage(sender, "invalid_key_type", MessageManager.ph("%type%", args[4]));
                return;
            }
        }
        crateManager.giveKey(targetPlayer, crate.getId(), amount, physical);
        String messageKey = physical ? "key_given_physical_sender" : "key_given_virtual_sender";
        messageManager.sendMessage(sender, messageKey, MessageManager.ph(
            "%amount%", String.valueOf(amount),
            "%crate_name%", crate.getDisplayName(),
            "%target_player%", targetPlayer.getName()
        ));
    }

    private void handleTakeKeyCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shcrates.admin.takekey") && !sender.hasPermission("shcrates.admin")) {
            messageManager.sendMessage(sender, "no_permission");
            return;
        }
        if (args.length < 4) {
            messageManager.sendMessage(sender, "command_usage_takekey");
            return;
        }

        Player targetPlayer = Bukkit.getPlayer(args[1]);
        if (targetPlayer == null) {
            messageManager.sendMessage(sender, "player_not_found", MessageManager.ph("%player%", args[1]));
            return;
        }

        String crateName = args[2];
        Optional<Crate> crateOpt = crateManager.getCrate(crateName);
        if (crateOpt.isEmpty()) {
            messageManager.sendMessage(sender, "crate_not_exist", MessageManager.ph("%crate_name%", crateName));
            return;
        }
        Crate crate = crateOpt.get();

        int amountToRemove;
        try {
            amountToRemove = Integer.parseInt(args[3]);
            if (amountToRemove <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            messageManager.sendMessage(sender, "invalid_amount", MessageManager.ph("%amount%", args[3]));
            return;
        }

        boolean physical = true;
        if (args.length >= 5) {
            if (args[4].equalsIgnoreCase("virtual") || args[4].equalsIgnoreCase("v")) {
                physical = false;
            } else if (!args[4].equalsIgnoreCase("physical") && !args[4].equalsIgnoreCase("p")) {
                messageManager.sendMessage(sender, "invalid_key_type", MessageManager.ph("%type%", args[4]));
                return;
            }
        }

        int actualAmountTaken;
        if (physical) {
            actualAmountTaken = crateManager.takePhysicalKeys(targetPlayer, crate, amountToRemove);
        } else {
            actualAmountTaken = playerDataManager.takeVirtualKeys(targetPlayer.getUniqueId(), crate.getInternalID(), amountToRemove);
        }

        if (actualAmountTaken > 0) {
            String msgKeySender = physical ? "key_taken_physical_sender" : "key_taken_virtual_sender";
            messageManager.sendMessage(sender, msgKeySender, MessageManager.ph(
                "%amount%", String.valueOf(actualAmountTaken),
                "%crate_name%", crate.getDisplayName(),
                "%target_player%", targetPlayer.getName()
            ));
            String msgKeyTarget = physical ? "key_taken_physical_target" : "key_taken_virtual_target";
            messageManager.sendMessage(targetPlayer, msgKeyTarget, MessageManager.ph(
                "%amount%", String.valueOf(actualAmountTaken),
                "%crate_name%", crate.getDisplayName()
            ));
        } else {
            messageManager.sendMessage(sender, "not_enough_keys_to_take", MessageManager.ph(
                "%player%", targetPlayer.getName(),
                "%crate_name%", crate.getDisplayName(),
                "%key_type%", physical ? "physique(s)" : "virtuelle(s)"
            ));
        }
    }

    private void handleGiveAllKeyCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shcrates.admin.giveall") && !sender.hasPermission("shcrates.admin")) {
            messageManager.sendMessage(sender, "no_permission");
            return;
        }
        if (args.length < 3) {
            messageManager.sendMessage(sender, "command_usage_giveallkeys");
            return;
        }
        String crateName = args[1];
        Optional<Crate> crateOpt = crateManager.getCrate(crateName);
        if (crateOpt.isEmpty()) {
            messageManager.sendMessage(sender, "crate_not_exist", MessageManager.ph("%crate_name%", crateName));
            return;
        }
        Crate crate = crateOpt.get();
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            messageManager.sendMessage(sender, "invalid_amount", MessageManager.ph("%amount%", args[2]));
            return;
        }
        boolean physical = true;
        if (args.length >= 4) {
            if (args[3].equalsIgnoreCase("virtual") || args[3].equalsIgnoreCase("v")) {
                physical = false;
            } else if (!args[3].equalsIgnoreCase("physical") && !args[3].equalsIgnoreCase("p")) {
                messageManager.sendMessage(sender, "invalid_key_type", MessageManager.ph("%type%", args[3]));
                return;
            }
        }
        int playerCount = 0;
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            crateManager.giveKey(onlinePlayer, crate.getId(), amount, physical);
            playerCount++;
        }
        messageManager.sendMessage(sender, "keys_given_to_all", MessageManager.ph(
            "%amount%", String.valueOf(amount),
            "%key_type%", physical ? "physique(s)" : "virtuelle(s)",
            "%crate_name%", crate.getDisplayName(),
            "%player_count%", String.valueOf(playerCount)
        ));
    }

    private void handleSetBlockCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            messageManager.sendMessage(sender, "console_cannot_execute");
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("shcrates.admin.setblock") && !player.hasPermission("shcrates.admin")) {
            messageManager.sendMessage(player, "no_permission");
            return;
        }
        if (args.length < 2) {
            messageManager.sendMessage(player, "command_usage_setblock");
            return;
        }
        String crateName = args[1];
        Optional<Crate> crateOpt = crateManager.getCrate(crateName);
        if (crateOpt.isEmpty()) {
            messageManager.sendMessage(player, "crate_not_exist", MessageManager.ph("%crate_name%", crateName));
            return;
        }
        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null || targetBlock.getType().isAir()) {
            messageManager.sendMessage(player, "must_look_at_block");
            return;
        }
        if (crateManager.isCrateLocation(targetBlock.getLocation())) {
            Crate existingCrate = crateManager.getCrate(crateManager.getCrateLocation(targetBlock.getLocation()).get().getCrateId()).orElse(null);
            messageManager.sendMessage(player, "block_already_crate", MessageManager.ph(
                "%crate_name%", existingCrate != null ? existingCrate.getDisplayName() : "Inconnue"
            ));
            return;
        }
        Crate crateToSet = crateOpt.get();
        crateManager.addCrateLocation(targetBlock.getLocation(), crateToSet.getId());
        Material newBlockType = crateToSet.getBlockType();
        if (newBlockType != null && targetBlock.getType() != newBlockType) {
            targetBlock.setType(newBlockType);
        }
        messageManager.sendMessage(player, "crate_block_set", MessageManager.ph("%crate_name%", crateToSet.getDisplayName()));
    }

    private void handleRemoveBlockCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            messageManager.sendMessage(sender, "console_cannot_execute");
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("shcrates.admin.removeblock") && !player.hasPermission("shcrates.admin")) {
            messageManager.sendMessage(player, "no_permission");
            return;
        }
        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null || targetBlock.getType().isAir()) {
            messageManager.sendMessage(player, "must_look_at_block");
            return;
        }
        Optional<org.Cry_Legende.sHCrates.crates.CrateLocation> crateLocationOpt = crateManager.getCrateLocation(targetBlock.getLocation());
        if (crateLocationOpt.isEmpty()) {
            messageManager.sendMessage(player, "block_is_not_crate");
            return;
        }
        crateManager.removeCrateLocation(targetBlock.getLocation());
        messageManager.sendMessage(player, "crate_block_removed");
    }

    private void handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("shcrates.admin.reload") && !sender.hasPermission("shcrates.admin")) {
            messageManager.sendMessage(sender, "no_permission");
            return;
        }
        try {
            crateManager.reloadAll();
            messageManager.sendMessage(sender, "reload_success");
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error during SHCrates reload", e);
            messageManager.sendMessage(sender, "reload_failure");
        }
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        String currentArg = args[args.length - 1].toLowerCase();

        if (args.length == 1) {
            suggestions.addAll(playerSubCommands);
            if (sender.hasPermission("shcrates.admin")) {
                suggestions.addAll(adminSubCommands);
            }
        } else {
            String subCommand = args[0].toLowerCase();
            if (adminSubCommands.contains(subCommand) && !sender.hasPermission("shcrates.admin") && !sender.hasPermission("shcrates.admin." + subCommand)) {
                return Collections.emptyList(); // Pas de suggestion si pas la perm admin de base
            }

            switch (subCommand) {
                case "preview":
                case "open":
                case "openall":
                    if (args.length == 2) { // <crateName>
                        crateManager.getAllCrates().stream().map(Crate::getId).forEach(suggestions::add);
                    } else if (args.length == 3) { // [player]
                        if (sender.hasPermission("shcrates.admin.previewothers") || sender.hasPermission("shcrates.admin.openothers")) {
                            Bukkit.getOnlinePlayers().stream().map(Player::getName).forEach(suggestions::add);
                        }
                    }
                    break;
                case "keys":
                    if (args.length == 2 && (sender.hasPermission("shcrates.admin.keysothers") || sender.hasPermission("shcrates.admin"))) {
                        Bukkit.getOnlinePlayers().stream().map(Player::getName).forEach(suggestions::add);
                    }
                    break;
                case "givekey":
                case "takekey":
                    // Permission déjà vérifiée au début du switch pour les commandes admin
                    if (args.length == 2) { // <player>
                        Bukkit.getOnlinePlayers().stream().map(Player::getName).forEach(suggestions::add);
                    } else if (args.length == 3) { // <crateName>
                        crateManager.getAllCrates().stream().map(Crate::getId).forEach(suggestions::add);
                    } else if (args.length == 4) { // <amount>
                        suggestions.addAll(List.of("1", "5", "10", "32", "64"));
                    } else if (args.length == 5) { // [physical|virtual] ou [p|v]
                        suggestions.addAll(List.of("physical", "virtual", "p", "v"));
                    }
                    break;
                case "giveall":
                    if (args.length == 2) { // <crateName>
                        crateManager.getAllCrates().stream().map(Crate::getId).forEach(suggestions::add);
                    } else if (args.length == 3) { // <amount>
                        suggestions.addAll(List.of("1", "5", "10"));
                    } else if (args.length == 4) { // [physical|virtual] ou [p|v]
                        suggestions.addAll(List.of("physical", "virtual", "p", "v"));
                    }
                    break;
                case "setblock":
                    if (args.length == 2) { // <crateName>
                        crateManager.getAllCrates().stream().map(Crate::getId).forEach(suggestions::add);
                    }
                    break;
                // removeblock, reload, list, help n'ont pas besoin de complétion d'arguments spécifique ici
            }
        }
        StringUtil.copyPartialMatches(currentArg, suggestions, completions);
        Collections.sort(completions);
        return completions;
    }
}
