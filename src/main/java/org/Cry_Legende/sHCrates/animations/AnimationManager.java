package org.Cry_Legende.sHCrates.animations;

import org.Cry_Legende.sHCrates.SHCrates;
import org.Cry_Legende.sHCrates.config.ConfigManager;
import org.Cry_Legende.sHCrates.config.MessageManager;
import org.Cry_Legende.sHCrates.crates.Crate;
import org.Cry_Legende.sHCrates.crates.Reward;
import org.Cry_Legende.sHCrates.crates.CrateManager;
import org.Cry_Legende.sHCrates.gui.GUIManager;
import org.Cry_Legende.sHCrates.storage.PlayerDataManager;
import org.Cry_Legende.sHCrates.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
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

    public ActiveAnimationInfo getActiveAnimationInfo(Player player) {
        return activeAnimations.get(player.getUniqueId());
    }

    public boolean attemptStartCrateOpening(Player player, Crate crate, Location crateLocation, boolean isQuickOpenAll) {
        // Seules les animations en GUI sont suivies ici (récompense pas encore donnée) : on ne les
        // interrompt jamais, sinon la récompense déjà payée par la clé serait perdue.
        if (isPlayerInAnimation(player)) {
            messageManager.sendMessage(player, "error_already_in_animation");
            return false;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        boolean usedPhysicalKey = false;
        boolean usedVirtualKey = false;

        if (crateManager.isPhysicalKey(itemInHand, crate)) {
            itemInHand.setAmount(itemInHand.getAmount() - 1);
            usedPhysicalKey = true;
        } else if (playerDataManager.getVirtualKeys(player.getUniqueId(), crate.getInternalID()) > 0) {
            playerDataManager.consumeVirtualKey(player.getUniqueId(), crate.getInternalID());
            usedVirtualKey = true;
        } else {
            messageManager.sendMessage(player, "no_key_any", MessageManager.ph("%key_name%", crate.getKeyConfig().getName()));
            if (configManager.isKnockbackOnNoKey() && crateLocation != null) {
                Vector knockback = player.getLocation().getDirection().multiply(-1).normalize().multiply(configManager.getKnockbackStrength());
                knockback.setY(0.2);
                player.setVelocity(knockback);
            }
            return false;
        }

        Reward chosenReward = crate.getRandomReward(player);
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
        startCrateOpeningLogic(player, crate, crateLocation, chosenReward, usedPhysicalKey, usedVirtualKey, animationTypeName);
        return true;
    }

    public boolean attemptShiftOpenQuickCrate(Player player, Crate crate, Location crateLocation) {
        // ... (Logique inchangée)
        return true;
    }

    private void startCrateOpeningLogic(Player player, Crate crate, Location crateLocation, Reward chosenReward, boolean usedPhysicalKey, boolean usedVirtualKey, String animationTypeName) {
        Animation animation = getAnimation(animationTypeName).orElseGet(() -> getAnimation(configManager.getDefaultAnimationType()).orElse(getAnimation("NONE").get()));
        ActiveAnimationInfo animationInfo = new ActiveAnimationInfo(animation, crate, chosenReward, crateLocation);
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

        Reward newReward = crate.getRandomReward(player);
        if (newReward == null) {
            messageManager.sendMessage(player, "reroll_failed_no_reward");
            player.closeInventory();
            return;
        }

        // CORRECTION: Mettre à jour l'objet existant au lieu d'en créer un nouveau
        animInfo.setChosenReward(newReward);
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

    public void openAllCrates(Player player, Crate crate) {
        // ... (Logique inchangée)
    }

    public void giveReward(Player player, Crate crate, Reward reward, Location physicalCrateLocation) {
        giveReward(player, crate, reward, physicalCrateLocation, false);
    }

    /**
     * @param immediate true quand le joueur ne sera plus là au tick suivant (déconnexion, arrêt) :
     *                  les commandes de récompense sont alors exécutées tout de suite.
     */
    public void giveReward(Player player, Crate crate, Reward reward, Location physicalCrateLocation, boolean immediate) {
        if (reward == null) return;
        if (!player.isOnline()) {
            plugin.getLogger().warning(String.format("Reward %s from %s NOT given: %s is offline.", reward.getDisplayName(), crate.getDisplayName(), player.getName()));
            return;
        }

        Runnable give = () -> {
            messageManager.sendMessage(player, "crate_opened", MessageManager.ph("%crate_display_name%", crate.getDisplayName(), "%reward_display_name%", reward.getDisplayName()));
            for (String cmd : reward.getCommands()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player_name%", player.getName()));
            }
            for (String msg : reward.getMessages()) {
                player.sendMessage(ChatUtils.colorize(msg.replace("%player_name%", player.getName())));
            }
            if (configManager.isLogRewardsToConsole()) {
                plugin.getLogger().info(String.format("%s won: %s from %s", player.getName(), reward.getDisplayName(), crate.getDisplayName()));
            }
            if (reward.hasFireworkOnWin() && physicalCrateLocation != null) {
                spawnFirework(physicalCrateLocation.clone().add(0.5, 1.5, 0.5));
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
    }
}
