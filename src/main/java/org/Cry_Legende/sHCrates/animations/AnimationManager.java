package org.Cry_Legende.sHCrates.animations;

import org.Cry_Legende.sHCrates.SHCrates;
import org.Cry_Legende.sHCrates.config.ConfigManager;
import org.Cry_Legende.sHCrates.config.MessageManager;
import org.Cry_Legende.sHCrates.crates.Crate;
import org.Cry_Legende.sHCrates.crates.Reward;
import org.Cry_Legende.sHCrates.crates.CrateManager;
import org.Cry_Legende.sHCrates.crates.Tier;
import org.Cry_Legende.sHCrates.utils.Papi;
import org.Cry_Legende.sHCrates.utils.SoundUtils;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.Cry_Legende.sHCrates.gui.GUIManager;
import org.Cry_Legende.sHCrates.storage.PlayerDataManager;
import org.Cry_Legende.sHCrates.utils.ActionLog;
import org.Cry_Legende.sHCrates.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.EntityEffect;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.logging.Level;

public class AnimationManager {

    private final SHCrates plugin;
    private final CrateManager crateManager;
    private final PlayerDataManager playerDataManager;
    private final MessageManager messageManager;
    private final ConfigManager configManager;
    private final GUIManager guiManager;

    private final Map<String, Animation> registeredAnimations = new HashMap<>();
    private final Map<UUID, ActiveAnimationInfo> activeAnimations = new HashMap<>();
    private final Random random = new Random();
    private final Map<Location, List<Entity>> activeDisplayEntities = new HashMap<>();
    // Joueurs en pleine ouverture multiple
    private final Set<UUID> bulkOpening = new HashSet<>();
    // Garantie d'une ouverture QuickCrate, dévoilée à la fin de son hologramme
    private final Map<UUID, Guarantee> pendingQuickGuarantee = new HashMap<>();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    /** Garantie appliquée à un tirage : aucune, première clé du joueur, ou jauge pleine. */
    public enum Guarantee { NONE, FIRST_OPEN, GAUGE }

    /** Lot tiré et garantie qui l'a imposé. */
    public record Draw(Reward reward, Guarantee guarantee) {
    }

    public AnimationManager(SHCrates plugin) {
        this.plugin = plugin;
        this.crateManager = plugin.getCrateManager();
        this.playerDataManager = plugin.getPlayerDataManager();
        this.messageManager = plugin.getMessageManager();
        this.configManager = plugin.getConfigManager();
        this.guiManager = plugin.getGuiManager();

        registerAnimation(new NoneAnimation(plugin));
        registerAnimation(new QuickCrateAnimation(plugin));
        registerAnimation(new RouletteAnimation(plugin));
        registerAnimation(new CSGOAnimation(plugin));
        registerAnimation(new WheelAnimation(plugin));
    }

    public void registerAnimation(Animation animation) {
        registeredAnimations.put(animation.getName().toUpperCase(), animation);
        plugin.getLogger().info("Registered animation type: " + animation.getName());
    }

    public Optional<Animation> getAnimation(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(registeredAnimations.get(name.toUpperCase()));
    }

    public boolean isPlayerInAnimation(Player player) {
        return activeAnimations.containsKey(player.getUniqueId());
    }

    /** Vrai pendant une ouverture multiple du joueur. */
    public boolean isBulkOpening(Player player) {
        return bulkOpening.contains(player.getUniqueId());
    }

    public ActiveAnimationInfo getActiveAnimationInfo(Player player) {
        return activeAnimations.get(player.getUniqueId());
    }

    public boolean attemptStartCrateOpening(Player player, Crate crate, Location crateLocation, boolean isQuickOpenAll) {
        // Seules les animations en GUI sont suivies ici (récompense pas encore donnée) : on ne les
        // interrompt jamais, sinon la récompense déjà payée par la clé serait perdue.
        if (isPlayerInAnimation(player) || isBulkOpening(player)) {
            messageManager.sendMessage(player, "error_already_in_animation");
            return false;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        boolean usedPhysicalKey = false;
        boolean usedVirtualKey = false;

        if (crateManager.isPhysicalKey(itemInHand, crate)) {
            itemInHand.setAmount(itemInHand.getAmount() - 1);
            usedPhysicalKey = true;
            // Le clic sur le bloc est annulé : sans resynchronisation, le client garde l'ancien inventaire
            // (clé encore visible, gain invisible) jusqu'au clic suivant.
            Bukkit.getScheduler().runTask(plugin, player::updateInventory);
        } else if (playerDataManager.getVirtualKeys(player.getUniqueId(), crate.getInternalID()) > 0) {
            playerDataManager.consumeVirtualKey(player.getUniqueId(), crate.getInternalID());
            usedVirtualKey = true;
        } else if (!playerDataManager.isReady(player.getUniqueId())) {
            // Mode réseau : les clés virtuelles du joueur ne sont pas encore lues en base
            messageManager.sendMessage(player, "network_loading");
            return false;
        } else {
            messageManager.sendMessage(player, "no_key_any", MessageManager.ph("%key_name%", crate.getKeyConfig().getName()));
            if (configManager.isKnockbackOnNoKey() && crateLocation != null) {
                Vector knockback = player.getLocation().getDirection().multiply(-1).normalize().multiply(configManager.getKnockbackStrength());
                knockback.setY(0.2);
                player.setVelocity(knockback);
            }
            return false;
        }

        Draw draw = drawReward(player, crate);
        Reward chosenReward = draw == null ? null : draw.reward();
        if (chosenReward == null) {
            messageManager.sendMessage(player, "error_no_eligible_reward", MessageManager.ph("%crate_display_name%", crate.getDisplayName()));
            if (usedPhysicalKey) {
                plugin.getPlayerDataManager().addVirtualKeys(player.getUniqueId(), crate.getInternalID(), 1);
                messageManager.sendMessage(player, "key_refunded_virtual");
            } else if (usedVirtualKey) {
                playerDataManager.addVirtualKeys(player.getUniqueId(), crate.getInternalID(), 1);
                messageManager.sendMessage(player, "key_refunded_virtual");
            }
            return false;
        }

        String animationTypeName = isQuickOpenAll ? "NONE" : crate.getAnimationConfig().getType();
        startCrateOpeningLogic(player, crate, crateLocation, draw, usedPhysicalKey, usedVirtualKey, animationTypeName);
        return true;
    }

    /** Shift+clic sur une QuickCrate : ouvre toutes les clés du joueur pour cette caisse. */
    public boolean attemptShiftOpenQuickCrate(Player player, Crate crate, Location crateLocation) {
        openAllCrates(player, crate, crateLocation);
        return true;
    }

    private void startCrateOpeningLogic(Player player, Crate crate, Location crateLocation, Draw draw, boolean usedPhysicalKey, boolean usedVirtualKey, String animationTypeName) {
        Reward chosenReward = draw.reward();
        Animation animation = getAnimation(animationTypeName).orElseGet(() -> getAnimation(configManager.getDefaultAnimationType()).orElse(getAnimation("NONE").get()));
        ActiveAnimationInfo animationInfo = new ActiveAnimationInfo(animation, crate, chosenReward, crateLocation);
        animationInfo.setGuarantee(draw.guarantee());
        boolean isInstantAnimation = !animation.usesGUI();

        if (usedPhysicalKey)
            messageManager.sendMessage(player, "key_used_physical", MessageManager.ph("%crate_display_name%", crate.getDisplayName()));
        if (usedVirtualKey)
            messageManager.sendMessage(player, "key_used_virtual", MessageManager.ph("%crate_display_name%", crate.getDisplayName()));
        if (isInstantAnimation) {
            // Récompense donnée tout de suite : l'ouverture n'a pas besoin d'être suivie. L'enregistrer
            // bloquait le joueur (« déjà en train d'ouvrir ») quand un autre joueur relançait la même
            // QuickCrate avant la fin de l'affichage, car le callback de fin n'était alors jamais appelé.
            giveReward(player, crate, chosenReward, crateLocation);
            animationInfo.setRewardGiven(true);
            // QuickCrate dévoile le lot à la fin de son hologramme ; sans hologramme, tout de suite
            if (!animation.getName().equalsIgnoreCase("QUICKCRATE") || crateLocation == null
                || !crate.getAnimationConfig().getQuickCrateAnimConfig().isEnabled()) {
                reveal(player, crate, chosenReward, crateLocation, draw.guarantee());
            } else {
                pendingQuickGuarantee.put(player.getUniqueId(), draw.guarantee());
            }
        } else {
            activeAnimations.put(player.getUniqueId(), animationInfo);
        }

        // Ne touche que sa propre ouverture, jamais celle qui serait enregistrée entre-temps
        Runnable onAnimationFinish = () -> animationInfo.setAnimationFinished(true);

        try {
            animation.play(player, crate, crateLocation, chosenReward, onAnimationFinish);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error playing animation " + animation.getName(), e);
            activeAnimations.remove(player.getUniqueId(), animationInfo);
            animation.stop(player, false);
            if (!animationInfo.isRewardGiven()) {
                animationInfo.setRewardGiven(true);
                giveReward(player, crate, chosenReward, crateLocation);
            }
            return;
        }

        // GUI refusé (InventoryOpenEvent annulé par un autre plugin) : sans inventaire ouvert, la récompense
        // ne serait donnée qu'à la déconnexion et le joueur resterait bloqué « en ouverture ».
        if (!isInstantAnimation && activeAnimations.get(player.getUniqueId()) == animationInfo
            && !guiManager.isAnimationInventory(player.getOpenInventory().getTopInventory())) {
            plugin.getLogger().warning("Animation GUI for " + player.getName() + " did not open, reward given directly.");
            stopAnimationForPlayer(player, true);
        }
    }

    public void performReroll(Player player) {
        ActiveAnimationInfo animInfo = getActiveAnimationInfo(player);
        if (animInfo == null || !animInfo.isAnimationFinished() || animInfo.isRewardGiven()) return;

        Crate crate = animInfo.getCrate();
        if (!player.hasPermission("shcrates.reroll." + crate.getId()) && !player.hasPermission("shcrates.reroll.*")) {
            messageManager.sendMessage(player, "no_permission_reroll");
            return;
        }

        // CORRECTION: Vérifier le flag de l'objet animInfo existant
        if (animInfo.hasRerollUsed()) {
            messageManager.sendMessage(player, "no_more_rerolls");
            return;
        }

        // CORRECTION: Mettre à jour le flag sur l'objet existant
        animInfo.setRerollUsed(true);
        messageManager.sendMessage(player, "reroll_successful");

        Draw redraw = drawReward(player, crate);
        Reward newReward = redraw == null ? null : redraw.reward();
        if (newReward == null) {
            messageManager.sendMessage(player, "reroll_failed_no_reward");
            player.closeInventory();
            return;
        }

        // CORRECTION: Mettre à jour l'objet existant au lieu d'en créer un nouveau
        animInfo.setChosenReward(newReward);
        animInfo.setGuarantee(redraw.guarantee());
        animInfo.setAnimationFinished(false);

        // Relancer l'animation sans consommer de nouvelle clé
        try {
            Runnable onAnimationFinish = () -> animInfo.setAnimationFinished(true);
            animInfo.getAnimation().play(player, crate, animInfo.getCrateLocation(), newReward, onAnimationFinish);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error during reroll animation for " + player.getName(), e);
            player.closeInventory(); // Ferme pour donner la nouvelle récompense et nettoyer
        }
    }

    // ------------------------------------------------------------------ tirage

    /**
     * Tire un lot pour le joueur : parmi les lots du palier minimal si c'est sa toute première clé de cette caisse,
     * parmi les paliers de la jauge si elle est pleine, sinon parmi tous les lots qu'il peut gagner.
     * Ne change aucun compteur : ils avancent quand le lot est donné.
     */
    public Draw drawReward(Player player, Crate crate) {
        List<Reward> pool = crate.getEligibleRewards(player);
        if (pool.isEmpty()) {
            return null;
        }
        UUID uuid = player.getUniqueId();
        String crateId = crate.getId().toLowerCase();
        if (crate.getFirstOpenMinTier() != null && playerDataManager.getCounter(uuid, "premiere-" + crateId) == 0) {
            Tier min = configManager.getTier(crate.getFirstOpenMinTier());
            if (min != null) {
                List<Reward> best = pool.stream().filter(r -> tierRank(r) >= min.rank()).collect(Collectors.toList());
                if (!best.isEmpty()) {
                    return new Draw(Crate.pick(best), Guarantee.FIRST_OPEN);
                }
            }
        }
        Crate.Gauge gauge = crate.getGauge();
        if (gauge != null && playerDataManager.getCounter(uuid, "jauge-" + crateId) + 1 >= gauge.threshold()) {
            List<Reward> targets = pool.stream()
                .filter(r -> r.getTierId() != null && gauge.tierIds().contains(r.getTierId()))
                .collect(Collectors.toList());
            if (!targets.isEmpty()) {
                return new Draw(Crate.pick(targets), Guarantee.GAUGE);
            }
        }
        return new Draw(Crate.pick(pool), Guarantee.NONE);
    }

    /** Palier d'un lot, ou null pour un lot sans palier. */
    public Tier tierOf(Reward reward) {
        return reward == null ? null : configManager.getTier(reward.getTierId());
    }

    /** Rang du palier d'un lot, 0 sans palier. */
    public int tierRank(Reward reward) {
        Tier tier = tierOf(reward);
        return tier == null ? 0 : tier.rank();
    }

    /** Fait avancer les compteurs du joueur pour un lot donné : ouvertures, première clé, meilleur palier, jauge. */
    private void recordOpening(Player player, Crate crate, Reward reward, boolean quiet) {
        UUID uuid = player.getUniqueId();
        String crateId = crate.getId().toLowerCase();
        playerDataManager.addCounter(uuid, "ouvertures-" + crateId, 1);
        if (playerDataManager.getCounter(uuid, "premiere-" + crateId) == 0) {
            playerDataManager.setCounter(uuid, "premiere-" + crateId, 1);
        }
        int rank = tierRank(reward);
        if (rank > playerDataManager.getCounter(uuid, "meilleur-" + crateId)) {
            playerDataManager.setCounter(uuid, "meilleur-" + crateId, rank);
        }
        Crate.Gauge gauge = crate.getGauge();
        if (gauge != null) {
            String counter = "jauge-" + crateId;
            if (reward.getTierId() != null && gauge.tierIds().contains(reward.getTierId())) {
                playerDataManager.setCounter(uuid, counter, 0);
            } else {
                playerDataManager.addCounter(uuid, counter, 1);
            }
            if (!quiet) {
                sendGaugeStatus(player, crate);
            }
        }
    }

    /** Barre d'action avec l'état de la jauge, et un message quand la prochaine clé est garantie. */
    public void sendGaugeStatus(Player player, Crate crate) {
        Crate.Gauge gauge = crate.getGauge();
        if (gauge == null) return;
        int current = playerDataManager.getCounter(player.getUniqueId(), "jauge-" + crate.getId().toLowerCase());
        Map<String, String> ph = MessageManager.ph(
            "%gauge_name%", gauge.name(),
            "%current%", String.valueOf(current),
            "%threshold%", String.valueOf(gauge.threshold()),
            "%remaining%", String.valueOf(Math.max(1, gauge.threshold() - current)),
            "%crate_display_name%", crate.getDisplayName(),
            "%tiers%", gaugeTierNames(gauge));
        String bar = messageManager.getRawPrefixedMessage("gauge_actionbar", ph);
        if (!bar.isEmpty()) {
            player.sendActionBar(LEGACY.deserialize(bar));
        }
        if (current + 1 >= gauge.threshold()) {
            messageManager.sendRawMessage(player, "gauge_next_guaranteed", ph);
        }
    }

    /** Noms des paliers visés par une jauge, séparés par des virgules. */
    public String gaugeTierNames(Crate.Gauge gauge) {
        return gauge.tierIds().stream().map(configManager::getTier).filter(t -> t != null)
            .map(Tier::name).collect(Collectors.joining(ChatUtils.colorize("&7, ")));
    }

    // ------------------------------------------------------------------ dévoilement

    /**
     * Le lot apparaît : son, feu d'artifice, animation du totem et titre selon son palier, et le message de la
     * garantie qui l'a imposé. Un lot sans palier garde l'ancien comportement (son de la caisse, FireworkOnWin).
     */
    public void reveal(Player player, Crate crate, Reward reward, Location crateLocation, Guarantee guarantee) {
        if (reward == null || !player.isOnline()) return;
        Tier tier = tierOf(reward);
        if (tier == null) {
            playSound(player, crate.getAnimationConfig().getSoundWin(), 1f, 1f);
            if (reward.hasFireworkOnWin() && crateLocation != null) {
                spawnFirework(crateLocation.clone().add(0.5, 1.5, 0.5));
            }
        } else {
            playSound(player, tier.sound(), tier.volume(), tier.pitch());
            if (tier.firework()) {
                spawnFirework(crateLocation != null ? crateLocation.clone().add(0.5, 1.5, 0.5) : player.getLocation().add(0, 2, 0));
            }
            if (tier.totem()) {
                player.playEffect(EntityEffect.PROTECTED_FROM_DEATH);
            }
            if (!tier.title().isEmpty() || !tier.subtitle().isEmpty()) {
                player.showTitle(Title.title(
                    LEGACY.deserialize(ChatUtils.colorize(tier.title().replace("%reward%", reward.getDisplayName()))),
                    LEGACY.deserialize(ChatUtils.colorize(tier.subtitle().replace("%reward%", reward.getDisplayName())))));
            }
        }
        if (guarantee == Guarantee.FIRST_OPEN) {
            messageManager.sendRawMessage(player, "guarantee_first_open", MessageManager.ph("%crate_display_name%", crate.getDisplayName()));
        } else if (guarantee == Guarantee.GAUGE && crate.getGauge() != null) {
            messageManager.sendRawMessage(player, "guarantee_gauge", MessageManager.ph(
                "%gauge_name%", crate.getGauge().name(), "%crate_display_name%", crate.getDisplayName()));
        }
    }

    /** Fin de l'hologramme d'une QuickCrate : dévoilement avec la garantie retenue à l'ouverture. */
    public void revealQuick(Player player, Crate crate, Reward reward, Location crateLocation) {
        Guarantee guarantee = pendingQuickGuarantee.remove(player.getUniqueId());
        reveal(player, crate, reward, crateLocation, guarantee == null ? Guarantee.NONE : guarantee);
    }

    /** Vitre du cadre pendant le ralentissement : celle du palier du lot, aucune pour un lot sans palier ou sans vitre. */
    public org.bukkit.Material suspenseGlass(Reward reward) {
        Tier tier = tierOf(reward);
        return tier == null ? null : tier.glass();
    }

    private void playSound(Player player, String soundName, float volume, float pitch) {
        if (soundName == null || soundName.isEmpty()) return;
        Sound sound = SoundUtils.sound(soundName);
        if (sound != null) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    /** Le joueur peut relancer ce tirage (relance activée et droit sur la caisse). */
    public boolean canReroll(Player player, Crate crate) {
        return configManager.isRerollEnabled()
            && (player.hasPermission("shcrates.reroll." + crate.getId()) || player.hasPermission("shcrates.reroll.*"));
    }

    /**
     * Fin du défilement d'une animation en GUI : le lot est dévoilé, puis donné tout de suite si le joueur ne peut
     * plus le relancer. Sinon il l'est à la fermeture du GUI, une fois le choix fait.
     */
    public void onSpinFinished(Player player) {
        ActiveAnimationInfo info = activeAnimations.get(player.getUniqueId());
        if (info == null) return;
        reveal(player, info.getCrate(), info.getChosenReward(), info.getCrateLocation(), info.getGuarantee());
        if (!info.isRewardGiven() && (info.hasRerollUsed() || !canReroll(player, info.getCrate()))) {
            info.setRewardGiven(true);
            giveReward(player, info.getCrate(), info.getChosenReward(), info.getCrateLocation());
        }
    }

    // ------------------------------------------------------------------ ouverture multiple

    public void openAllCrates(Player player, Crate crate) {
        openAllCrates(player, crate, null);
    }

    /**
     * Ouvre d'un coup les clés du joueur pour une caisse (virtuelles d'abord, puis physiques de l'inventaire),
     * {@code ouverture_multiple.par_tick} par tick et {@code ouverture_multiple.max} au plus. S'arrête quand
     * l'inventaire est plein. Un récapitulatif, un seul son et le dévoilement du meilleur lot à la fin.
     */
    public void openAllCrates(Player player, Crate crate, Location crateLocation) {
        UUID uuid = player.getUniqueId();
        if (!crate.isOpenAllAllowed()) {
            messageManager.sendRawMessage(player, "openall_not_allowed", MessageManager.ph("%crate_display_name%", crate.getDisplayName()));
            return;
        }
        if (isPlayerInAnimation(player) || isBulkOpening(player)) {
            messageManager.sendMessage(player, "error_already_in_animation");
            return;
        }
        if (!playerDataManager.isReady(uuid)) {
            messageManager.sendMessage(player, "network_loading");
            return;
        }
        int available = playerDataManager.getVirtualKeys(uuid, crate.getInternalID()) + countPhysicalKeys(player, crate);
        if (available <= 0) {
            messageManager.sendMessage(player, "no_key_any", MessageManager.ph("%key_name%", crate.getKeyConfig().getName()));
            return;
        }
        int toOpen = Math.min(available, configManager.getOpenAllMax());
        int perTick = configManager.getOpenAllPerTick();
        bulkOpening.add(uuid);
        messageManager.sendRawMessage(player, "openall_starting", MessageManager.ph(
            "%amount%", String.valueOf(toOpen), "%crate_display_name%", crate.getDisplayName()));

        new BukkitRunnable() {
            private int opened = 0;
            private final Map<String, Integer> counts = new LinkedHashMap<>();
            private final Map<String, Reward> byId = new HashMap<>();
            private Reward best = null;
            private boolean inventoryFull = false;

            @Override
            public void run() {
                boolean done = !player.isOnline();
                for (int i = 0; i < perTick && !done; i++) {
                    if (opened >= toOpen) {
                        done = true;
                        break;
                    }
                    if (player.getInventory().firstEmpty() == -1) {
                        inventoryFull = true;
                        done = true;
                        break;
                    }
                    if (!consumeVirtualKey(player, crate) && !consumePhysicalKey(player, crate)) {
                        done = true;
                        break;
                    }
                    Draw draw = drawReward(player, crate);
                    if (draw == null || draw.reward() == null) {
                        // rien de gagnable : la clé est rendue en virtuelle
                        playerDataManager.addVirtualKeys(uuid, crate.getInternalID(), 1);
                        messageManager.sendMessage(player, "error_no_eligible_reward", MessageManager.ph("%crate_display_name%", crate.getDisplayName()));
                        done = true;
                        break;
                    }
                    Reward reward = draw.reward();
                    giveReward(player, crate, reward, crateLocation, true, true);
                    opened++;
                    counts.merge(reward.getId(), 1, Integer::sum);
                    byId.putIfAbsent(reward.getId(), reward);
                    if (best == null || tierRank(reward) > tierRank(best)) {
                        best = reward;
                    }
                }
                if (done || opened >= toOpen) {
                    this.cancel();
                    finish();
                }
            }

            private void finish() {
                bulkOpening.remove(uuid);
                playerDataManager.savePlayerData(uuid);
                if (!player.isOnline()) return;
                Bukkit.getScheduler().runTask(plugin, player::updateInventory);
                if (opened > 0) {
                    messageManager.sendRawMessage(player, "openall_summary_header", MessageManager.ph(
                        "%amount%", String.valueOf(opened), "%crate_display_name%", crate.getDisplayName()));
                    // du plus courant au plus rare : le meilleur lot finit en bas du chat, sous les yeux
                    counts.entrySet().stream()
                        .sorted(Comparator.comparingInt((Map.Entry<String, Integer> e) -> tierRank(byId.get(e.getKey())))
                            .thenComparing(Map.Entry::getValue, Comparator.reverseOrder()))
                        .forEach(e -> {
                            Reward r = byId.get(e.getKey());
                            Tier t = tierOf(r);
                            messageManager.sendRawMessage(player, "openall_summary_line", MessageManager.ph(
                                "%count%", String.valueOf(e.getValue()),
                                "%reward_display_name%", r.getDisplayName(),
                                "%tier%", t == null ? "" : t.name()));
                        });
                    Tier bestTier = tierOf(best);
                    messageManager.sendRawMessage(player, "openall_summary_best", MessageManager.ph(
                        "%reward_display_name%", best.getDisplayName(), "%tier%", bestTier == null ? "" : bestTier.name()));
                    reveal(player, crate, best, crateLocation, Guarantee.NONE);
                    sendGaugeStatus(player, crate);
                }
                if (inventoryFull) {
                    int left = playerDataManager.getVirtualKeys(uuid, crate.getInternalID()) + countPhysicalKeys(player, crate);
                    messageManager.sendRawMessage(player, "openall_inventory_full", MessageManager.ph(
                        "%remaining%", String.valueOf(left), "%crate_display_name%", crate.getDisplayName()));
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private int countPhysicalKeys(Player player, Crate crate) {
        int amount = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (crateManager.isPhysicalKey(item, crate)) amount += item.getAmount();
        }
        return amount;
    }

    /** Consomme une clé virtuelle ; faux s'il n'y en a plus. */
    private boolean consumeVirtualKey(Player player, Crate crate) {
        return playerDataManager.getVirtualKeys(player.getUniqueId(), crate.getInternalID()) > 0
            && playerDataManager.consumeVirtualKey(player.getUniqueId(), crate.getInternalID());
    }

    /** Consomme une clé physique, d'abord celle de la main ; faux s'il n'y en a plus. */
    private boolean consumePhysicalKey(Player player, Crate crate) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (crateManager.isPhysicalKey(hand, crate)) {
            hand.setAmount(hand.getAmount() - 1);
            return true;
        }
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (crateManager.isPhysicalKey(item, crate)) {
                item.setAmount(item.getAmount() - 1);
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ remise du lot

    public void giveReward(Player player, Crate crate, Reward reward, Location physicalCrateLocation) {
        giveReward(player, crate, reward, physicalCrateLocation, false);
    }

    public void giveReward(Player player, Crate crate, Reward reward, Location physicalCrateLocation, boolean immediate) {
        giveReward(player, crate, reward, physicalCrateLocation, immediate, false);
    }

    /**
     * @param immediate true quand le joueur ne sera plus là au tick suivant (déconnexion, arrêt) :
     *                  les commandes de récompense sont alors exécutées tout de suite.
     */
    /**
     * @param quiet vrai pendant une ouverture multiple : pas de message par lot ni de barre de jauge, le
     *              récapitulatif s'en charge ; l'annonce au serveur d'un lot rare reste.
     */
    public void giveReward(Player player, Crate crate, Reward reward, Location physicalCrateLocation, boolean immediate, boolean quiet) {
        if (reward == null) return;
        if (!player.isOnline()) {
            plugin.getLogger().warning(String.format("Reward %s from %s NOT given: %s is offline.", reward.getDisplayName(), crate.getDisplayName(), player.getName()));
            return;
        }

        Runnable give = () -> {
            Tier tier = tierOf(reward);
            if (!quiet) {
                messageManager.sendMessage(player, "crate_opened", MessageManager.ph("%crate_display_name%", crate.getDisplayName(),
                    "%reward_display_name%", reward.getDisplayName(), "%tier%", tier == null ? "" : tier.name()));
            }
            for (String cmd : reward.getCommands()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), Papi.apply(player, cmd.replace("%player_name%", player.getName())));
            }
            if (!quiet) {
                for (String msg : reward.getMessages()) {
                    player.sendMessage(ChatUtils.colorize(msg.replace("%player_name%", player.getName())));
                }
            }
            if (tier != null && tier.announce()) {
                String announce = messageManager.getRawPrefixedMessage("announce_win", MessageManager.ph(
                    "%player_name%", player.getName(), "%reward_display_name%", reward.getDisplayName(),
                    "%crate_display_name%", crate.getDisplayName(), "%tier%", tier.name()));
                if (!announce.isEmpty()) {
                    Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(announce));
                    Bukkit.getConsoleSender().sendMessage(announce);
                }
            }
            recordOpening(player, crate, reward, quiet);
            if (!quiet) {
                playerDataManager.savePlayerData(player.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, player::updateInventory);
            }
            ActionLog.write(plugin, crate.getLogFile(), configManager.getRewardLogFormat()
                    .replace("%player_name%", player.getName())
                    .replace("%reward_name%", reward.getDisplayName()));
            if (configManager.isLogRewardsToConsole()) {
                plugin.getLogger().info(String.format("%s won: %s from %s", player.getName(), reward.getDisplayName(), crate.getDisplayName()));
            }
        };
        // Plugin en cours d'arrêt : runTask lèverait une exception et la récompense serait perdue
        if (immediate || !plugin.isEnabled()) {
            give.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, give);
        }
    }

    public void spawnFirework(Location location) {
        if (location == null || location.getWorld() == null) return;
        location.getWorld().spawn(location, Firework.class, fw -> {
            FireworkMeta fwm = fw.getFireworkMeta();
            fwm.setPower(1);
            fwm.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(Color.ORANGE, Color.YELLOW).withFade(Color.WHITE).build());
            fw.setFireworkMeta(fwm);
            fw.detonate();
        });
    }

    /**
     * @param giveRewardIfApplicable true pour donner la récompense pas encore donnée, immédiatement
     *                               (utilisé à la déconnexion, où le joueur ne sera plus là au tick suivant).
     */
    public void stopAnimationForPlayer(Player player, boolean giveRewardIfApplicable) {
        ActiveAnimationInfo animInfo = activeAnimations.remove(player.getUniqueId());
        if (animInfo != null) {
            animInfo.getAnimation().stop(player, false);
            if (giveRewardIfApplicable && !animInfo.isRewardGiven()) {
                animInfo.setRewardGiven(true);
                giveReward(player, animInfo.getCrate(), animInfo.getChosenReward(), animInfo.getCrateLocation(), true);
            }
        }
    }

    /**
     * Arrêt du plugin : toutes les ouvertures en cours sont soldées, récompense comprise.
     */
    public void finishAllAnimations() {
        for (UUID uuid : new ArrayList<>(activeAnimations.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                stopAnimationForPlayer(player, true);
            } else {
                ActiveAnimationInfo animInfo = activeAnimations.remove(uuid);
                if (animInfo != null && !animInfo.isRewardGiven()) {
                    plugin.getLogger().warning("Reward " + animInfo.getChosenReward().getDisplayName() + " NOT given: player " + uuid + " is offline.");
                }
            }
        }
    }

    public void addDisplayEntity(Location location, Entity entity) {
        activeDisplayEntities.computeIfAbsent(location.getBlock().getLocation(), k -> new ArrayList<>()).add(entity);
    }

    public void clearDisplayEntities(Location location) {
        List<Entity> entities = activeDisplayEntities.remove(location.getBlock().getLocation());
        if (entities != null) {
            entities.forEach(entity -> {
                if (entity != null && entity.isValid()) {
                    entity.remove();
                }
            });
        }
    }

    /**
     * --- CORRECTION 3 : AJOUT D'UNE MÉTHODE DE NETTOYAGE GLOBAL ---
     * Nettoie TOUTES les entités d'affichage actives gérées par ce manager.
     * Doit être appelée lors de la désactivation du plugin pour éviter que des entités
     * ne restent dans le monde après un redémarrage ou un /reload.
     */
    public void cleanupAllDisplayEntities() {
        if (activeDisplayEntities.isEmpty()) {
            return;
        }
        plugin.getLogger().info("Cleaning up all active display entities...");
        int count = 0;
        // Itérer sur une copie des valeurs pour éviter ConcurrentModificationException
        for (List<Entity> entityList : new ArrayList<>(activeDisplayEntities.values())) {
            if (entityList != null) {
                for (Entity entity : entityList) {
                    if (entity != null && entity.isValid()) {
                        entity.remove();
                        count++;
                    }
                }
            }
        }
        activeDisplayEntities.clear();
        if (count > 0) {
            plugin.getLogger().info("Removed " + count + " leftover display entities.");
        }
    }


    public static class ActiveAnimationInfo {
        private final Animation animation;
        private final Crate crate;
        private Reward chosenReward;
        private final Location crateLocation;
        private boolean rewardGiven;
        private boolean animationFinished;
        private boolean rerollUsed;
        private Guarantee guarantee = Guarantee.NONE;

        public ActiveAnimationInfo(Animation animation, Crate crate, Reward chosenReward, Location crateLocation) {
            this.animation = animation;
            this.crate = crate;
            this.chosenReward = chosenReward;
            this.crateLocation = crateLocation;
            this.rewardGiven = false;
            this.animationFinished = false;
            this.rerollUsed = false;
        }

        public Animation getAnimation() {
            return animation;
        }

        public Crate getCrate() {
            return crate;
        }

        public Reward getChosenReward() {
            return chosenReward;
        }

        public void setChosenReward(Reward reward) {
            this.chosenReward = reward;
        }

        public Location getCrateLocation() {
            return crateLocation;
        }

        public boolean isRewardGiven() {
            return rewardGiven;
        }

        public void setRewardGiven(boolean rewardGiven) {
            this.rewardGiven = rewardGiven;
        }

        public boolean isAnimationFinished() {
            return animationFinished;
        }

        public void setAnimationFinished(boolean animationFinished) {
            this.animationFinished = animationFinished;
        }

        public boolean hasRerollUsed() {
            return rerollUsed;
        }

        public void setRerollUsed(boolean rerollUsed) {
            this.rerollUsed = rerollUsed;
        }

        public Guarantee getGuarantee() {
            return guarantee;
        }

        public void setGuarantee(Guarantee guarantee) {
            this.guarantee = guarantee == null ? Guarantee.NONE : guarantee;
        }
    }
}
