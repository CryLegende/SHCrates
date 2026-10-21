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
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class WheelAnimation implements Animation, InventoryHolder {

    private final SHCrates plugin;
    private final GUIManager guiManager;
    private final Random random = new Random();
    private final Map<UUID, BukkitTask> activePlayerMasterTasks = new HashMap<>();
    private final Map<UUID, Inventory> activeInventories = new HashMap<>();

    private final int GUI_SIZE = 45;
    private final int[] WHEEL_SLOTS = {
        0, 1, 2, 3, 4, 5, 6, 7, 8,
        17, 26, 35,
        44, 43, 42, 41, 40, 39, 38, 37, 36,
        27, 18, 9
    };
    private final int CURSOR_SLOT = 22;
    private final Material FILLER_MATERIAL_WHEEL = Material.GRAY_STAINED_GLASS_PANE;
    private final Material HIGHLIGHT_MATERIAL_WHEEL = Material.LIGHT_BLUE_STAINED_GLASS_PANE;

    public WheelAnimation(SHCrates plugin) {
        this.plugin = plugin;
        this.guiManager = plugin.getGuiManager();
    }

    @Override
    public String getName() {
        return "WHEEL";
    }

    @Override
    public boolean usesGUI() {
        return true;
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        throw new UnsupportedOperationException("WheelAnimation is a per-player InventoryHolder.");
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

        // CORRECTION: Utiliser la liste des récompenses que le joueur peut réellement gagner
        List<Reward> eligibleRewards = crate.getEligibleRewards(player);
        if (eligibleRewards.isEmpty()) {
            player.closeInventory();
            plugin.getMessageManager().sendMessage(player, "error_no_eligible_reward", MessageManager.ph("%crate_display_name%", crate.getDisplayName()));
            if (onAnimationFinish != null) onAnimationFinish.run();
            return;
        }

        List<ItemStack> wheelDisplayItems = prepareWheelItems(eligibleRewards, WHEEL_SLOTS.length);
        int finalStopIndexTarget = findRewardIndexOnWheel(wheelDisplayItems, chosenReward);

        setupInitialGUI(gui, wheelDisplayItems);
        playSound(player, crateLocation, animConfig.getSoundOpen());

        MasterAnimationTick masterTick = new MasterAnimationTick(player, crate, gui, wheelDisplayItems, chosenReward, finalStopIndexTarget, onAnimationFinish, animConfig, crateLocation);
        BukkitTask task = masterTick.runTaskTimer(plugin, 5L, 1L);
        activePlayerMasterTasks.put(player.getUniqueId(), task);
    }

    private void setupInitialGUI(Inventory gui, List<ItemStack> wheelItems) {
        gui.clear();
        ItemStack filler = new ItemBuilder(FILLER_MATERIAL_WHEEL).setName(" ").build();
        for (int i = 0; i < GUI_SIZE; i++) {
            boolean isWheelSlot = false;
            for (int slot : WHEEL_SLOTS) {
                if (i == slot) {
                    isWheelSlot = true;
                    break;
                }
            }
            if (!isWheelSlot) {
                gui.setItem(i, filler);
            }
        }
        for (int i = 0; i < WHEEL_SLOTS.length; i++) {
            if (i < wheelItems.size()) {
                gui.setItem(WHEEL_SLOTS[i], wheelItems.get(i));
            }
        }
        gui.setItem(CURSOR_SLOT, new ItemBuilder(Material.BEACON).setName("&eSpinning...").build());
    }

    private List<ItemStack> prepareWheelItems(List<Reward> eligibleRewards, int wheelSize) {
        List<ItemStack> items = new ArrayList<>();
        Collections.shuffle(eligibleRewards);

        for (int i = 0; i < wheelSize; i++) {
            items.add(eligibleRewards.get(i % eligibleRewards.size()).getDisplayItemStack().clone());
        }
        return items;
    }

    private int findRewardIndexOnWheel(List<ItemStack> wheelItems, Reward targetReward) {
        for (int i = 0; i < wheelItems.size(); i++) {
            if (wheelItems.get(i).isSimilar(targetReward.getDisplayItemStack())) {
                return i;
            }
        }
        // Si la récompense gagnée n'a pas été placée sur la roue (très improbable), en choisir une au hasard
        return random.nextInt(Math.max(1, wheelItems.size()));
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
        private final List<ItemStack> wheelItems;
        private final Reward chosenReward;
        private final int finalStopIndex;
        private final Runnable onAnimationFinishCallback;
        private final AnimationConfig animConfig;
        private final Location physicalCrateLocation;

        private final int totalSpins;
        private int currentSpinCount = 0;
        private long ticksElapsed = 0;
        private double currentDelay = 1.0;
        private long nextTick = 0;

        MasterAnimationTick(Player p, Crate c, Inventory inv, List<ItemStack> items, Reward reward, int stopIndex, Runnable onFinish, AnimationConfig config, Location crateLoc) {
            this.player = p;
            this.crate = c;
            this.gui = inv;
            this.wheelItems = items;
            this.chosenReward = reward;
            this.finalStopIndex = stopIndex;
            this.onAnimationFinishCallback = onFinish;
            this.animConfig = config;
            this.physicalCrateLocation = crateLoc;
            this.totalSpins = (WHEEL_SLOTS.length * 2) + stopIndex;
        }

        @Override
        public void run() {
            if (!player.isOnline() || !activeInventories.containsKey(player.getUniqueId())) {
                this.cancel();
                return;
            }

            ticksElapsed++;
            if (ticksElapsed < nextTick) return;

            double progress = (double) currentSpinCount / totalSpins;
            currentDelay = 1.0 + (progress * progress * 5);
            nextTick = ticksElapsed + (long) currentDelay;

            currentSpinCount++;
            updateGUISpin();
            playSound(player, player.getLocation(), animConfig.getSoundSpin());

            if (currentSpinCount >= totalSpins) {
                finishAnimation();
            }
        }

        private void updateGUISpin() {
            int currentSlotIndex = currentSpinCount % WHEEL_SLOTS.length;
            for (int i = 0; i < WHEEL_SLOTS.length; i++) {
                ItemStack item = wheelItems.get(i);
                if (i == currentSlotIndex) {
                    ItemStack highlighted = new ItemBuilder(HIGHLIGHT_MATERIAL_WHEEL)
                        .setName(item.hasItemMeta() ? item.getItemMeta().getDisplayName() : "")
                        .setLore(item.hasItemMeta() ? item.getItemMeta().getLore() : null)
                        .build();
                    gui.setItem(WHEEL_SLOTS[i], highlighted);
                } else {
                    gui.setItem(WHEEL_SLOTS[i], item);
                }
            }
            gui.setItem(CURSOR_SLOT, wheelItems.get(currentSlotIndex));
        }

        private void finishAnimation() {
            this.cancel();
            activePlayerMasterTasks.remove(player.getUniqueId());

            for (int i = 0; i < WHEEL_SLOTS.length; i++) {
                gui.setItem(WHEEL_SLOTS[i], wheelItems.get(i));
            }
            ItemStack winningItem = chosenReward.getDisplayItemStack().clone();
            ItemMeta meta = winningItem.getItemMeta();
            if (meta != null) {
                meta.addEnchant(Enchantment.LURE, 1, false);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                winningItem.setItemMeta(meta);
            }
            gui.setItem(WHEEL_SLOTS[finalStopIndex], winningItem);
            gui.setItem(CURSOR_SLOT, winningItem.clone());

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
            if (autoCloseDelay > 0) {
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
        try {
            player.playSound(location, Sound.valueOf(soundName.toUpperCase()), 0.8f, 1.0f + (random.nextFloat() * 0.4f - 0.2f));
        } catch (IllegalArgumentException e) {
            // Silence
        }
    }
}
