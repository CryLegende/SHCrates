package org.Cry_Legende.sHCrates.dialogs;

import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.Cry_Legende.sHCrates.SHCrates;
import org.Cry_Legende.sHCrates.config.MessageManager;
import org.Cry_Legende.sHCrates.crates.Reward;
import org.Cry_Legende.sHCrates.utils.ChatUtils;
import org.Cry_Legende.sHCrates.utils.Papi;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Dialogues de fin de scène, au style des dialogues SkyHand (titre en première ligne, joueur aveuglé, choix principal en
 * premier) :
 * <ul>
 *   <li>relance : « Garder ce lot » ou « Relancer », pour les joueurs qui ont le droit de relancer ;</li>
 *   <li>cosmétique : « Équiper maintenant » (commandes Equiper du lot) ou « Plus tard ».</li>
 * </ul>
 */
public final class RewardDialogs implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final Key KEEP = Key.key("shcrates", "garder");
    private static final Key REROLL = Key.key("shcrates", "relancer");
    private static final Key EQUIP = Key.key("shcrates", "equiper");
    private static final int BODY_WIDTH = 260;
    /** Sans réponse au dialogue de relance, le lot est gardé au bout de ce délai. */
    private static final long REROLL_TIMEOUT_TICKS = 20L * 30;

    private record Equip(Reward reward, boolean test) {
    }

    private final SHCrates plugin;
    private final DialogBlindness blindness;
    private final Map<UUID, Equip> pendingEquip = new HashMap<>();
    private final Map<UUID, BukkitTask> rerollTimeouts = new HashMap<>();

    public RewardDialogs(SHCrates plugin, boolean blind) {
        this.plugin = plugin;
        this.blindness = new DialogBlindness(plugin, blind);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void setBlind(boolean blind) {
        blindness.setEnabled(blind);
    }

    public void shutdown() {
        for (BukkitTask task : rerollTimeouts.values()) task.cancel();
        rerollTimeouts.clear();
        pendingEquip.clear();
        HandlerList.unregisterAll(this);
        blindness.shutdown();
    }

    // ------------------------------------------------------------------ relance

    /** Propose de garder ou de relancer le lot ; sans réponse, il est gardé au bout de 30 s. */
    public void showReroll(Player player, Reward reward) {
        MessageManager m = plugin.getMessageManager();
        Map<String, String> ph = MessageManager.ph("%reward_display_name%", reward.getDisplayName());
        List<DialogBody> body = new ArrayList<>();
        body.add(title(m.getRawPrefixedMessage("dialog_reroll_title", ph)));
        body.add(item(reward));
        body.add(DialogBody.plainMessage(text(m.getRawPrefixedMessage("dialog_reroll_text", ph)), BODY_WIDTH));
        List<ActionButton> buttons = List.of(
            button(m.getRawPrefixedMessage("dialog_reroll_keep", ph), DialogAction.customClick(KEEP, null)),
            button(m.getRawPrefixedMessage("dialog_reroll_again", ph), DialogAction.customClick(REROLL, null)));
        ActionButton exit = button(m.getRawPrefixedMessage("dialog_reroll_close", ph), DialogAction.customClick(KEEP, null));
        // gardé ouvert au clic : « Garder » peut ouvrir le dialogue « Équiper » sans clignoter
        DialogBase base = DialogBlindness.stayOpen(DialogBase.builder(Component.empty()).canCloseWithEscape(true)).body(body).build();
        Dialog dialog = Dialog.create(f -> f.empty().base(base).type(DialogType.multiAction(buttons).columns(1).exitAction(exit).build()));
        cancelTimeout(player.getUniqueId());
        rerollTimeouts.put(player.getUniqueId(), Bukkit.getScheduler().runTaskLater(plugin, () -> {
            rerollTimeouts.remove(player.getUniqueId());
            if (player.isOnline()) {
                blindness.close(player);
                plugin.getAnimationManager().keepReward(player);
            }
        }, REROLL_TIMEOUT_TICKS));
        try {
            blindness.show(player, dialog);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Dialogue de relance impossible pour " + player.getName(), e);
            cancelTimeout(player.getUniqueId());
            plugin.getAnimationManager().keepReward(player);
        }
    }

    // ------------------------------------------------------------------ équiper

    /** Propose d'équiper tout de suite le cosmétique gagné (lot avec Equiper). En test, le bouton ne fait rien. */
    public void showEquip(Player player, Reward reward, boolean test) {
        if (reward.getEquipCommands().isEmpty() || !player.isOnline()) return;
        MessageManager m = plugin.getMessageManager();
        Map<String, String> ph = MessageManager.ph("%reward_display_name%", reward.getDisplayName());
        List<DialogBody> body = new ArrayList<>();
        body.add(title(m.getRawPrefixedMessage("dialog_equip_title", ph)));
        body.add(item(reward));
        body.add(DialogBody.plainMessage(text(m.getRawPrefixedMessage("dialog_equip_text", ph)), BODY_WIDTH));
        List<ActionButton> buttons = List.of(
            button(m.getRawPrefixedMessage("dialog_equip_now", ph), DialogAction.customClick(EQUIP, null)),
            button(m.getRawPrefixedMessage("dialog_equip_later", ph), DialogBlindness.closeAction()));
        DialogBase base = DialogBase.builder(Component.empty()).canCloseWithEscape(true).pause(false).body(body).build();
        Dialog dialog = Dialog.create(f -> f.empty().base(base).type(DialogType.multiAction(buttons).columns(1).build()));
        pendingEquip.put(player.getUniqueId(), new Equip(reward, test));
        try {
            blindness.show(player, dialog);
        } catch (RuntimeException e) {
            pendingEquip.remove(player.getUniqueId());
            plugin.getLogger().log(Level.WARNING, "Dialogue « Équiper » impossible pour " + player.getName(), e);
        }
    }

    /** Nouvelle ouverture : le dialogue « Équiper » encore à l'écran de la précédente est fermé. */
    public void dismiss(Player player) {
        if (pendingEquip.remove(player.getUniqueId()) != null) {
            blindness.close(player);
        }
    }

    // ------------------------------------------------------------------ clics

    @EventHandler
    public void onClick(PlayerCustomClickEvent event) {
        if (!(event.getCommonConnection() instanceof PlayerGameConnection connection)) return;
        Player player = connection.getPlayer();
        Key key = event.getIdentifier();
        if (KEEP.equals(key)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (cancelTimeout(player.getUniqueId())) plugin.getAnimationManager().keepReward(player);
            });
        } else if (REROLL.equals(key)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (cancelTimeout(player.getUniqueId())) plugin.getAnimationManager().performReroll(player);
            });
        } else if (EQUIP.equals(key)) {
            Bukkit.getScheduler().runTask(plugin, () -> equip(player));
        }
    }

    private void equip(Player player) {
        Equip pending = pendingEquip.remove(player.getUniqueId());
        if (pending == null || !player.isOnline()) return;
        if (pending.test()) {
            plugin.getMessageManager().sendRawMessage(player, "scene_test_equip", MessageManager.ph());
            return;
        }
        for (String raw : pending.reward().getEquipCommands()) {
            String line = raw.replace("%player_name%", player.getName()).replace("%player%", player.getName());
            line = Papi.apply(player, line);
            try {
                if (line.regionMatches(true, 0, "[joueur]", 0, 8)) {
                    String cmd = line.substring(8).trim();
                    player.performCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
                } else {
                    String cmd = line.regionMatches(true, 0, "[console]", 0, 9) ? line.substring(9).trim() : line;
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.startsWith("/") ? cmd.substring(1) : cmd);
                }
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Commande Equiper en échec : " + line, e);
            }
        }
    }

    private boolean cancelTimeout(UUID id) {
        BukkitTask task = rerollTimeouts.remove(id);
        if (task == null) return false;
        task.cancel();
        return true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // le lot d'une relance en attente est donné par stopAnimationForPlayer
        cancelTimeout(event.getPlayer().getUniqueId());
        pendingEquip.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ construction

    private DialogBody title(String secondLine) {
        String first = plugin.getMessageManager().getRawPrefixedMessage("dialog_title", MessageManager.ph());
        return DialogBody.plainMessage(LEGACY.deserialize(first + "\n" + secondLine), BODY_WIDTH);
    }

    private static DialogBody item(Reward reward) {
        ItemStack shown = reward.getDisplayItemStack().clone();
        shown.setAmount(1);
        return DialogBody.item(shown).showTooltip(true).showDecorations(false).width(32).height(32).build();
    }

    private static Component text(String legacy) {
        return LEGACY.deserialize(ChatUtils.colorize(legacy));
    }

    private static ActionButton button(String label, DialogAction action) {
        return ActionButton.builder(LEGACY.deserialize(ChatUtils.colorize(label))).width(200).action(action).build();
    }
}
