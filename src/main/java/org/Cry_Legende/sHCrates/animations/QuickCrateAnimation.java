package org.Cry_Legende.sHCrates.animations;

import org.Cry_Legende.sHCrates.SHCrates;
import org.Cry_Legende.sHCrates.crates.Crate;
import org.Cry_Legende.sHCrates.crates.Reward;
import org.Cry_Legende.sHCrates.crates.config.AnimationConfig;
import org.Cry_Legende.sHCrates.utils.SoundUtils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class QuickCrateAnimation implements Animation {

    private final SHCrates plugin;
    private final Map<Location, BukkitTask> activeAnimationTasks = new HashMap<>();
    private static final boolean isDisplayApiAvailable;

    static {
        boolean available;
        try {
            Class.forName("org.bukkit.entity.TextDisplay");
            available = true;
        } catch (ClassNotFoundException e) {
            available = false;
        }
        isDisplayApiAvailable = available;
    }

    public QuickCrateAnimation(SHCrates plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "QUICKCRATE";
    }

    @Override
    public boolean usesGUI() {
        return false;
    }

    @Override
    public void play(Player player, Crate crate, Location crateLocation, Reward chosenReward, Runnable onAnimationFinish) {
        AnimationConfig.QuickCrateAnimConfig quickConfig = crate.getAnimationConfig().getQuickCrateAnimConfig();

        if (!isDisplayApiAvailable) {
            plugin.getLogger().log(Level.SEVERE, "QuickCrate animation failed: Display Entities are not available on this server version (1.19.4+ required).");
            if (onAnimationFinish != null) onAnimationFinish.run();
            return;
        }

        if (crateLocation == null || !quickConfig.isEnabled()) {
            if (onAnimationFinish != null) onAnimationFinish.run();
            return;
        }

        final Location blockKeyLocation = crateLocation.getBlock().getLocation();

        plugin.getAnimationManager().clearDisplayEntities(blockKeyLocation);
        BukkitTask oldTask = activeAnimationTasks.remove(blockKeyLocation);
        if (oldTask != null) oldTask.cancel();

        playSound(player, crateLocation, crate.getAnimationConfig().getSoundOpen());

        final Location spawnAnchor = crateLocation.clone().add(0.5, 0.0, 0.5);
        final ItemStack displayItem = chosenReward.getDisplayItemStack().clone();
        // Le lot apparaît après DelaiApparition ticks (le couvercle d'un coffre animé qui s'ouvre) et peut monter
        // depuis HauteurDepart jusqu'à sa hauteur en DureeMontee ticks ; son nom s'affiche une fois arrivé.
        final int appearAt = quickConfig.getAppearDelayTicks();
        final int riseTicks = quickConfig.rises() ? quickConfig.getRiseTicks() : 0;
        final double toY = quickConfig.getItemHeightOffset();
        final double fromY = quickConfig.rises() ? quickConfig.getRiseFromHeight() : toY;
        final long durationTicks = quickConfig.getDurationSeconds() * 20L;

        BukkitRunnable animationRunnable = new BukkitRunnable() {
            private int tick = 0;
            private ItemDisplay itemDisplay;
            private TextDisplay nameDisplay;
            private double currentYaw = 0;

            @Override
            public void run() {
                BukkitTask currentTask = activeAnimationTasks.get(blockKeyLocation);
                // Une autre ouverture a pris ce bloc : cette tâche est obsolète, elle retire SES entités et s'arrête.
                if (currentTask == null || currentTask.getTaskId() != this.getTaskId()) {
                    removeOwn();
                    this.cancel();
                    return;
                }

                int shownFor = tick - appearAt;
                boolean shouldEnd = !player.isOnline() || shownFor >= durationTicks
                    || itemDisplay != null && !itemDisplay.isValid() || nameDisplay != null && !nameDisplay.isValid();
                if (shouldEnd) {
                    plugin.getAnimationManager().clearDisplayEntities(blockKeyLocation);
                    activeAnimationTasks.remove(blockKeyLocation);
                    if (player.isOnline()) {
                        plugin.getAnimationManager().revealQuick(player, crate, chosenReward, crateLocation);
                    }
                    if (onAnimationFinish != null) {
                        onAnimationFinish.run();
                    }
                    this.cancel();
                    return;
                }

                if (shownFor == 0) {
                    itemDisplay = crateLocation.getWorld().spawn(spawnAnchor, ItemDisplay.class, id -> {
                        id.setItemStack(displayItem);
                        id.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
                        id.setBillboard(Display.Billboard.FIXED);
                        id.setPersistent(false);
                        // vu du seul joueur qui ouvre, comme l'animation de son coffre
                        id.setVisibleByDefault(false);
                        // le client glisse d'une pose à la suivante : rotation et montée sans à-coups
                        id.setInterpolationDuration(1);
                        id.setTransformation(pose(fromY, 0));
                    });
                    player.showEntity(plugin, itemDisplay);
                    plugin.getAnimationManager().addDisplayEntity(blockKeyLocation, itemDisplay);
                }
                if (shownFor == riseTicks && quickConfig.isDisplayHologram()) {
                    nameDisplay = crateLocation.getWorld().spawn(spawnAnchor, TextDisplay.class, td -> {
                        td.setText(chosenReward.getDisplayName());
                        td.setBillboard(Display.Billboard.VERTICAL);
                        td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                        td.setShadowed(true);
                        td.setPersistent(false);
                        td.setVisibleByDefault(false);
                        td.setTransformation(new Transformation(
                            new Vector3f(0, (float) (toY + quickConfig.getHologramHeightOffset()), 0),
                            new Quaternionf(), new Vector3f(1.0f, 1.0f, 1.0f), new Quaternionf()));
                    });
                    player.showEntity(plugin, nameDisplay);
                    plugin.getAnimationManager().addDisplayEntity(blockKeyLocation, nameDisplay);
                }
                if (shownFor > 0 && itemDisplay != null) {
                    currentYaw += 5;
                    if (currentYaw >= 360) currentYaw -= 360;
                    double y = toY;
                    if (shownFor < riseTicks) {
                        double t = shownFor / (double) riseTicks;
                        y = fromY + (toY - fromY) * (1 - (1 - t) * (1 - t)); // ralentit en arrivant
                    }
                    itemDisplay.setTransformation(pose(y, currentYaw));
                    itemDisplay.setInterpolationDelay(0);
                }
                tick++;
            }

            private void removeOwn() {
                if (itemDisplay != null && itemDisplay.isValid()) {
                    itemDisplay.remove();
                }
                if (nameDisplay != null && nameDisplay.isValid()) {
                    nameDisplay.remove();
                }
            }
        };
        activeAnimationTasks.put(blockKeyLocation, animationRunnable.runTaskTimer(plugin, 0L, 1L));
    }

    private static Transformation pose(double y, double yawDegrees) {
        return new Transformation(
            new Vector3f(0, (float) y, 0),
            new Quaternionf().rotateY((float) Math.toRadians(yawDegrees)),
            new Vector3f(1.0f, 1.0f, 1.0f),
            new Quaternionf());
    }

    @Override
    public void stop(Player player, boolean giveReward) {
        // --- CORRECTION 2 : GESTION DE LA DÉCONNEXION ---
        // Ne rien faire ici. La précédente implémentation était boguée car elle
        // annulait toutes les animations QuickCrate actives, pas seulement celle du joueur.
        // La tâche BukkitRunnable gère déjà sa propre annulation si le joueur se déconnecte
        // (via la vérification `!player.isOnline()`), ce qui est la principale raison pour laquelle
        // cette méthode `stop` serait appelée. Laisser le runnable se terminer proprement
        // garantit que les entités sont correctement supprimées.
    }

    private void playSound(Player player, Location location, String soundName) {
        if (soundName == null || soundName.isEmpty() || location == null) return;
        // Ancien nom ou clé du son ; son inconnu ignoré en silence, comme avant
        Sound sound = SoundUtils.sound(soundName);
        if (sound == null) return;
        player.playSound(location, sound, 1f, 1f);
    }
}
