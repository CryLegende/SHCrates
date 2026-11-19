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
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Dialogue « Équiper maintenant / Plus tard », ouvert par la commande {@code /shcrates equiper <joueur> <commande>} placée
 * dans les commandes d'une récompense : le bouton « Équiper maintenant » lance la commande donnée (par la console, ou par
 * le joueur avec « [joueur] » devant). Lancée pendant la remise d'un lot, le dialogue montre ce lot.
 * <p>
 * Le dialogue attend que le menu d'animation de la caisse soit fermé (le joueur voit d'abord son lot), et ne s'ouvre pas
 * pendant une ouverture multiple. Style des dialogues SkyHand : titre en première ligne, joueur aveuglé, choix principal
 * en premier.
 */
public final class EquipDialog implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final Key EQUIP = Key.key("shcrates", "equiper");
    private static final int BODY_WIDTH = 260;
    /** Une proposition jamais montrée (menu resté ouvert) est oubliée au bout de ce délai. */
    private static final long PENDING_MS = 120_000;

    /** Proposition : commande du bouton, lot à montrer (ou null), heure. */
    private record Offer(String command, Reward reward, long at) {
    }

    private final SHCrates plugin;
    private final DialogBlindness blindness;
    /** Propositions en attente que le menu d'animation se ferme. */
    private final Map<UUID, Offer> waiting = new ConcurrentHashMap<>();
    /** Proposition affichée, dont le bouton n'a pas encore été cliqué. */
    private final Map<UUID, Offer> shown = new ConcurrentHashMap<>();

    public EquipDialog(SHCrates plugin, boolean blind) {
        this.plugin = plugin;
        this.blindness = new DialogBlindness(plugin, blind);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void setBlind(boolean blind) {
        blindness.setEnabled(blind);
    }

    public void shutdown() {
        waiting.clear();
        shown.clear();
        HandlerList.unregisterAll(this);
        blindness.shutdown();
    }

    /**
     * Propose au joueur d'équiper le lot qu'il vient de recevoir ({@code reward}, null hors remise d'un lot) avec
     * {@code command}. Rien pendant une ouverture multiple.
     */
    public void offer(Player player, String command, Reward reward) {
        if (!player.isOnline() || plugin.getAnimationManager().isBulkOpening(player)) return;
        Offer offer = new Offer(command, reward, System.currentTimeMillis());
        // un tick plus tard : la récompense finit d'être donnée et un menu en cours de fermeture est bien fermé
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (plugin.getGuiManager().isAnimationInventory(player.getOpenInventory().getTopInventory())) {
                waiting.put(player.getUniqueId(), offer);
            } else {
                show(player, offer);
            }
        });
    }

    private void show(Player player, Offer offer) {
        MessageManager m = plugin.getMessageManager();
        String rewardName = offer.reward() == null ? "" : offer.reward().getDisplayName();
        Map<String, String> ph = MessageManager.ph("%reward_display_name%", rewardName);
        List<DialogBody> body = new ArrayList<>();
        String first = m.getRawPrefixedMessage("dialog_title", MessageManager.ph());
        body.add(DialogBody.plainMessage(LEGACY.deserialize(first + "\n" + m.getRawPrefixedMessage("dialog_equip_title", ph)), BODY_WIDTH));
        if (offer.reward() != null) {
            ItemStack item = offer.reward().getDisplayItemStack().clone();
            item.setAmount(1);
            body.add(DialogBody.item(item).showTooltip(true).showDecorations(false).width(32).height(32).build());
        }
        String text = m.getRawPrefixedMessage(offer.reward() == null ? "dialog_equip_text_generic" : "dialog_equip_text", ph);
        if (!text.isEmpty()) {
            body.add(DialogBody.plainMessage(LEGACY.deserialize(ChatUtils.colorize(text)), BODY_WIDTH));
        }
        List<ActionButton> buttons = List.of(
            button(m.getRawPrefixedMessage("dialog_equip_now", ph), DialogAction.customClick(EQUIP, null)),
            button(m.getRawPrefixedMessage("dialog_equip_later", ph), DialogBlindness.closeAction()));
        DialogBase base = DialogBase.builder(Component.empty()).canCloseWithEscape(true).pause(false).body(body).build();
        Dialog dialog = Dialog.create(f -> f.empty().base(base).type(DialogType.multiAction(buttons).columns(1).build()));
        shown.put(player.getUniqueId(), offer);
        try {
            blindness.show(player, dialog);
        } catch (RuntimeException e) {
            shown.remove(player.getUniqueId());
            plugin.getLogger().log(Level.WARNING, "Dialogue « Équiper » impossible pour " + player.getName(), e);
        }
    }

    private static ActionButton button(String label, DialogAction action) {
        return ActionButton.builder(LEGACY.deserialize(ChatUtils.colorize(label))).width(200).action(action).build();
    }

    /** Le menu d'animation de la caisse se ferme : la proposition en attente s'affiche. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player) || waiting.isEmpty()) return;
        Offer offer = waiting.remove(player.getUniqueId());
        if (offer == null || event.getReason() == InventoryCloseEvent.Reason.DISCONNECT) return;
        if (System.currentTimeMillis() - offer.at() > PENDING_MS) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) show(player, offer);
        });
    }

    @EventHandler
    public void onClick(PlayerCustomClickEvent event) {
        if (!EQUIP.equals(event.getIdentifier()) || !(event.getCommonConnection() instanceof PlayerGameConnection connection)) return;
        Player player = connection.getPlayer();
        Offer offer = shown.remove(player.getUniqueId());
        if (offer == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> run(player, offer.command()));
    }

    /** Commande du bouton : par la console, ou par le joueur avec « [joueur] » devant. */
    private void run(Player player, String raw) {
        if (!player.isOnline()) return;
        String line = Papi.apply(player, raw.replace("%player_name%", player.getName()).replace("%player%", player.getName())).trim();
        try {
            if (line.regionMatches(true, 0, "[joueur]", 0, 8)) {
                String cmd = line.substring(8).trim();
                player.performCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
            } else {
                String cmd = line.regionMatches(true, 0, "[console]", 0, 9) ? line.substring(9).trim() : line;
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.startsWith("/") ? cmd.substring(1) : cmd);
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Commande du bouton « Équiper » en échec : " + line, e);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        waiting.remove(event.getPlayer().getUniqueId());
        shown.remove(event.getPlayer().getUniqueId());
    }
}
