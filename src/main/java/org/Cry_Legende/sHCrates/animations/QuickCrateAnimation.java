package org.Cry_Legende.sHCrates.animations;

import org.Cry_Legende.sHCrates.SHCrates;
import org.Cry_Legende.sHCrates.crates.Crate;
import org.Cry_Legende.sHCrates.crates.Reward;
import org.Cry_Legende.sHCrates.crates.config.AnimationConfig;
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

        Location spawnAnchor = crateLocation.clone().add(0.5, 0.0, 0.5);
        ItemStack displayItem = chosenReward.getDisplayItemStack().clone();

        ItemDisplay itemDisplay = crateLocation.getWorld().spawn(spawnAnchor, ItemDisplay.class, id -> {
            id.setItemStack(displayItem);
            id.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            id.setBillboard(Display.Billboard.FIXED);

            Transformation transform = new Transformation(
                new Vector3f(0, (float) quickConfig.getItemHeightOffset(), 0),
                new Quaternionf(),
                new Vector3f(1.0f, 1.0f, 1.0f),
                new Quaternionf()
            );
            id.setTransformation(transform);
        });
        plugin.getAnimationManager().addDisplayEntity(blockKeyLocation, itemDisplay);

        TextDisplay nameDisplay = null;
        if (quickConfig.isDisplayHologram()) {
            nameDisplay = crateLocation.getWorld().spawn(spawnAnchor, TextDisplay.class, td -> {
                td.setText(chosenReward.getDisplayName());
                td.setBillboard(Display.Billboard.VERTICAL);
                td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                td.setShadowed(true);

                Transformation transform = new Transformation(
                    new Vector3f(0, (float) (quickConfig.getItemHeightOffset() + quickConfig.getHologramHeightOffset()), 0),
                    new Quaternionf(),
                    new Vector3f(1.0f, 1.0f, 1.0f),
                    new Quaternionf()
                );
                td.setTransformation(transform);
            });
            plugin.getAnimationManager().addDisplayEntity(blockKeyLocation, nameDisplay);
        }
        final TextDisplay finalNameDisplay = nameDisplay;

        BukkitRunnable animationRunnable = new BukkitRunnable() {
            private final long startTime = System.currentTimeMillis();
            private final long durationMillis = quickConfig.getDurationSeconds() * 1000L;
            private double currentYaw = 0;

            @Override
            public void run() {
                BukkitTask currentTask = activeAnimationTasks.get(blockKeyLocation);
                // --- CORRECTION 1 : GESTION DE LA CONCURRENCE ---
                // Si cette tâche n'est plus la tâche active pour cet emplacement (parce qu'une autre
                // animation a commencé), elle est obsolète. Elle doit nettoyer SES PROPRES entités
                // avant de s'arrêter pour ne pas les laisser derrière.
                if (currentTask == null || currentTask.getTaskId() != this.getTaskId()) {
                    if (itemDisplay != null && itemDisplay.isValid()) {
                        itemDisplay.remove();
                    }
                    if (finalNameDisplay != null && finalNameDisplay.isValid()) {
                        finalNameDisplay.remove();
                    }
                    this.cancel();
                    return;
                }

                long elapsed = System.currentTimeMillis() - startTime;
                boolean shouldEnd = elapsed >= durationMillis || !player.isOnline() || !itemDisplay.isValid() || (finalNameDisplay != null && !finalNameDisplay.isValid());

                if (shouldEnd) {
                    plugin.getAnimationManager().clearDisplayEntities(blockKeyLocation);
                    activeAnimationTasks.remove(blockKeyLocation);
                    if (onAnimationFinish != null) {
                        onAnimationFinish.run();
                    }
                    this.cancel();
                    return;
                }

                currentYaw += 5;
                if (currentYaw >= 360) currentYaw -= 360;

                Transformation currentTransform = itemDisplay.getTransformation();
                Quaternionf newRotation = new Quaternionf().rotateY((float) Math.toRadians(currentYaw));

                itemDisplay.setTransformation(new Transformation(
                    currentTransform.getTranslation(),
                    newRotation,
                    currentTransform.getScale(),
                    currentTransform.getRightRotation()
                ));
            }
        };
        activeAnimationTasks.put(blockKeyLocation, animationRunnable.runTaskTimer(plugin, 0L, 1L));
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
        try {
            player.playSound(location, Sound.valueOf(soundName.toUpperCase()), 1f, 1f);
        } catch (IllegalArgumentException e) {
            // Silence
        }
    }
}
