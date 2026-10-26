package org.Cry_Legende.sHCrates.animations;

import org.Cry_Legende.sHCrates.SHCrates;
import org.Cry_Legende.sHCrates.animations.AnimationManager.ActiveAnimationInfo;
import org.Cry_Legende.sHCrates.config.MessageManager;
import org.Cry_Legende.sHCrates.crates.Crate;
import org.Cry_Legende.sHCrates.crates.Reward;
import org.Cry_Legende.sHCrates.crates.config.AnimationConfig;
import org.Cry_Legende.sHCrates.gui.GUIManager;
import org.Cry_Legende.sHCrates.utils.ChatUtils;
import org.Cry_Legende.sHCrates.utils.ItemBuilder;
import org.Cry_Legende.sHCrates.utils.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class CSGOAnimation implements Animation, InventoryHolder {

    private final SHCrates plugin;
    private final GUIManager guiManager;
    private final Random random = new Random();
    private final Map<UUID, BukkitTask> activePlayerMasterTasks = new HashMap<>();
    private final Map<UUID, Inventory> activeInventories = new HashMap<>();

    private final int GUI_SIZE = 27;
    private final int[] REWARD_DISPLAY_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private final int MIDDLE_DISPLAY_SLOT_INDEX = 3;

    public CSGOAnimation(SHCrates plugin) {
        this.plugin = plugin;
        this.guiManager = plugin.getGuiManager();
    }

    @Override
    public String getName() {
        return "CSGO";
    }

    @Override
    public boolean usesGUI() {
        return true;
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        throw new UnsupportedOperationException("CSGOAnimation is a per-player InventoryHolder.");
    }

    @Override
    public void play(Player player, Crate crate, Location crateLocation, Reward chosenReward, Runnable onAnimationFinish) {
        BukkitTask existingTask = activePlayerMasterTasks.remove(player.getUniqueId());
        if (existingTask != null) {
            existingTask.cancel();
        }

        AnimationConfig animConfig = crate.getAnimationConfig();
        String guiTitle = ChatUtils.colorize(animConfig.getGuiTitle().replace("%crate_display_name%", crate.getDisplayName()));

        Inventory gui = activeInventories.get(player.getUniqueId());
        if (gui == null) {
            gui = Bukkit.createInventory(this, GUI_SIZE, guiTitle);
            activeInventories.put(player.getUniqueId(), gui);
            guiManager.addAnimationGUI(player, gui);
            player.openInventory(gui);
        }

        List<Reward> eligibleRewards = crate.getEligibleRewards(player);
        if (eligibleRewards.isEmpty()) {
            player.closeInventory();
            plugin.getMessageManager().sendMessage(player, "error_no_eligible_reward", MessageManager.ph("%crate_display_name%", crate.getDisplayName()));
            if (onAnimationFinish != null) onAnimationFinish.run();
            return;
        }

        List<ItemStack> spinItems = generateSpinItems(eligibleRewards, chosenReward);

        // Calculer le bon index d'arrêt du défilement. C'est l'index dans la liste `spinItems`
        // qui doit se retrouver dans le premier slot visible (REWARD_DISPLAY_SLOTS[0]) pour que
        // la récompense finale soit au milieu.
        int finalStopIndex = spinItems.size() - REWARD_DISPLAY_SLOTS.length;

        setupInitialGUI(gui);
        playSound(player, crateLocation, animConfig.getSoundOpen());

        MasterAnimationTick masterTick = new MasterAnimationTick(player, crate, gui, spinItems, chosenReward, finalStopIndex, onAnimationFinish, animConfig, crateLocation);
        BukkitTask task = masterTick.runTaskTimer(plugin, 5L, 1L);
        activePlayerMasterTasks.put(player.getUniqueId(), task);
    }

    private void setupInitialGUI(Inventory gui) {
        gui.clear();
        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).setName(" ").build();
        ItemStack picker = new ItemBuilder(Material.RED_STAINED_GLASS_PANE).setName(" ").build();
        for (int i = 0; i < GUI_SIZE; i++) {
            gui.setItem(i, filler);
        }
        gui.setItem(REWARD_DISPLAY_SLOTS[MIDDLE_DISPLAY_SLOT_INDEX] - 9, picker);
        gui.setItem(REWARD_DISPLAY_SLOTS[MIDDLE_DISPLAY_SLOT_INDEX] + 9, picker);
    }

    private List<ItemStack> generateSpinItems(List<Reward> eligibleRewards, Reward finalReward) {
        List<ItemStack> items = new ArrayList<>();
        int listLength = 70;
        for (int i = 0; i < listLength; i++) {
            items.add(eligibleRewards.get(random.nextInt(eligibleRewards.size())).getDisplayItemStack().clone());
        }
        items.set(listLength - REWARD_DISPLAY_SLOTS.length + MIDDLE_DISPLAY_SLOT_INDEX, finalReward.getDisplayItemStack().clone());
        return items;
    }

    @Override
    public void stop(Player player, boolean giveReward) {
        UUID playerId = player.getUniqueId();
        BukkitTask task = activePlayerMasterTasks.remove(playerId);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        activeInventories.remove(playerId);
        guiManager.removeAnimationGUI(player);
    }

    private class MasterAnimationTick extends BukkitRunnable {
        private final Player player;
        private final Crate crate;
        private final Inventory gui;
        private final List<ItemStack> spinItems;
        private final Reward chosenReward;
        private final int finalStopIndex;
        private final Runnable onAnimationFinishCallback;
        private final AnimationConfig animConfig;
        private final Location physicalCrateLocation;

        private int spinIndex = 0;
        private long ticksElapsed = 0;
        private double currentDelay = 1.0;
        private long nextTick = 0;
        private final long totalDuration;
        private final double slowdownPoint;

        MasterAnimationTick(Player p, Crate c, Inventory inv, List<ItemStack> items, Reward reward, int stopIndex, Runnable onFinish, AnimationConfig config, Location crateLoc) {
            this.player = p;
            this.crate = c;
            this.gui = inv;
            this.spinItems = items;
            this.chosenReward = reward;
            this.finalStopIndex = stopIndex;
            this.onAnimationFinishCallback = onFinish;
            this.animConfig = config;
            this.physicalCrateLocation = crateLoc;
            this.totalDuration = config.getSpinDurationTicks();
            this.slowdownPoint = totalDuration * 0.8;
        }

        @Override
        public void run() {
            if (!player.isOnline() || !activeInventories.containsKey(player.getUniqueId())) {
                this.cancel();
                return;
            }

            ticksElapsed++;
            if (ticksElapsed < nextTick) return;

            if (ticksElapsed < slowdownPoint) {
                currentDelay *= 1.025;
            } else {
                double ticksRemaining = totalDuration - ticksElapsed;
                double itemsRemaining = finalStopIndex - spinIndex;
                if (itemsRemaining > 0 && ticksRemaining > 1) {
                    currentDelay = Math.max(1.0, ticksRemaining / itemsRemaining);
                } else {
                    currentDelay = 5.0;
                }
            }

            nextTick = ticksElapsed + (long) Math.max(1.0, currentDelay);
            spinIndex++;
            updateGUISpin();
            playSound(player, player.getLocation(), animConfig.getSoundSpin());

            if (spinIndex >= finalStopIndex || ticksElapsed >= totalDuration) {
                finishAnimation();
            }
        }

        private void updateGUISpin() {
            for (int i = 0; i < REWARD_DISPLAY_SLOTS.length; i++) {
                int itemIndex = spinIndex + i;
                if (itemIndex < spinItems.size()) {
                    gui.setItem(REWARD_DISPLAY_SLOTS[i], spinItems.get(itemIndex));
                }
            }
        }

        private void finishAnimation() {
            this.cancel();
            activePlayerMasterTasks.remove(player.getUniqueId());

            // Assurer que la récompense finale est bien affichée au centre
            gui.setItem(REWARD_DISPLAY_SLOTS[MIDDLE_DISPLAY_SLOT_INDEX], chosenReward.getDisplayItemStack().clone());

            playSound(player, player.getLocation(), animConfig.getSoundWin());
            if (chosenReward.hasFireworkOnWin() && physicalCrateLocation != null) {
                plugin.getAnimationManager().spawnFirework(physicalCrateLocation.clone().add(0.5, 1.5, 0.5));
            }

            if (plugin.getConfigManager().isRerollEnabled()) {
                int rerollSlot = animConfig.getRerollButtonSlot();
                if (rerollSlot >= 0 && rerollSlot < gui.getSize()) {
                    ActiveAnimationInfo animInfo = plugin.getAnimationManager().getActiveAnimationInfo(player);
                    boolean canReroll = player.hasPermission("shcrates.reroll." + crate.getId()) || player.hasPermission("shcrates.reroll.*");
                    if (animInfo != null && canReroll) {
                        gui.setItem(rerollSlot, animInfo.hasRerollUsed() ?
                            plugin.getConfigManager().getRerollUsedButtonItem() :
                            plugin.getConfigManager().getRerollButtonItem()
                        );
                    }
                }
            }

            if (onAnimationFinishCallback != null) {
                onAnimationFinishCallback.run();
            }

            int autoCloseDelay = plugin.getConfigManager().getAutoCloseGUIDelaySeconds();
            if (autoCloseDelay >= 0) { // >= 0 pour permettre la fermeture instantanée si 0
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (player.isOnline() && player.getOpenInventory().getTopInventory() == gui) {
                            player.closeInventory();
                        }
                    }
                }.runTaskLater(plugin, autoCloseDelay * 20L);
            }
        }
    }

    private void playSound(Player player, Location location, String soundName) {
        if (soundName == null || soundName.isEmpty()) return;
        // Ancien nom ou clé du son ; son inconnu ignoré en silence, comme avant
        Sound sound = SoundUtils.sound(soundName);
        if (sound == null) return;
        player.playSound(location, sound, 1f, 1f);
    }
}
