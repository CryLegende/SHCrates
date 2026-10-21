package org.Cry_Legende.sHCrates.gui;

import org.Cry_Legende.sHCrates.SHCrates;
import org.Cry_Legende.sHCrates.animations.CSGOAnimation;
import org.Cry_Legende.sHCrates.animations.RouletteAnimation;
import org.Cry_Legende.sHCrates.animations.WheelAnimation;
import org.Cry_Legende.sHCrates.config.MessageManager;
import org.Cry_Legende.sHCrates.crates.Crate;
import org.Cry_Legende.sHCrates.crates.Reward;
import org.Cry_Legende.sHCrates.crates.config.PreviewGUIConfig;
import org.Cry_Legende.sHCrates.utils.ChatUtils;
import org.Cry_Legende.sHCrates.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class GUIManager {

    private final SHCrates plugin;
    private final MessageManager messageManager;
    private final Map<UUID, PagedGUI> activePreviewGUIs = new HashMap<>();
    private final Map<UUID, Inventory> activeAnimationGUIs = new HashMap<>();

    public GUIManager(SHCrates plugin) {
        this.plugin = plugin;
        this.messageManager = plugin.getMessageManager();
    }

    public void openPreviewGUI(Player player, Crate crate) {
        openPreviewGUIPage(player, crate, 0);
    }

    public void openPreviewGUIPage(Player player, Crate crate, int page) {
        PreviewGUIConfig guiConfig = crate.getPreviewGUIConfig();
        if (guiConfig == null) {
            plugin.getLogger().warning("PreviewGUIConfig is null for crate: " + crate.getId());
            messageManager.sendMessage(player, "error_gui_load", MessageManager.ph("%details%", "Crate preview config not found."));
            return;
        }

        List<ItemStack> rewardItems = crate.getRewards().stream()
            .filter(Reward::isDisplayInPreview) // Filtrer par DisplayInPreview
            .map(reward -> prepareRewardDisplayItem(reward, player, crate)) // Préparer l'item avec lore dynamique
            .collect(Collectors.toList());

        // --- MODIFICATION ICI ---
        // On continue même si rewardItems est vide. PagedGUI gérera une liste vide.
        // if (rewardItems.isEmpty() && crate.getRewards().stream().anyMatch(Reward::isDisplayInPreview)) {
        //     // Ce cas est pour quand il y A des récompenses à afficher, mais le joueur ne peut en voir aucune (ex: permissions)
        //     // On pourrait envoyer un message spécifique, mais on ouvre quand même le GUI vide.
        //     // player.sendMessage("Vous ne pouvez actuellement gagner aucune récompense de cette caisse.");
        // } else if (rewardItems.isEmpty()) {
        //     messageManager.sendMessage(player, "preview_no_rewards_displayable", MessageManager.ph("%crate_display_name%", crate.getDisplayName()));
        //     // NE PAS RETOURNER ICI, on veut ouvrir le GUI quand même
        // }
        // Le message "preview_no_rewards_displayable" pourrait être affiché via un item dans le GUI vide,
        // ou on peut simplement le laisser vide (avec fillers et boutons).

        String title = ChatUtils.colorize(guiConfig.getTitle().replace("%crate_display_name%", crate.getDisplayName()));
        // PagedGUI accepte une liste vide de rewardItems.
        PagedGUI pagedGUI = new PagedGUI(player, crate, title, guiConfig.getSize(), rewardItems, guiConfig.getItemsPerPage(), page);

        populatePreviewGUI(pagedGUI, crate, guiConfig);

        activePreviewGUIs.put(player.getUniqueId(), pagedGUI);
        player.openInventory(pagedGUI.getInventory());

        // Envoyer le message s'il n'y a rien à afficher APRÈS avoir préparé le GUI
        if (rewardItems.isEmpty() && crate.getRewards().stream().anyMatch(Reward::isDisplayInPreview)) {
            // Ce message indique que des récompenses existent mais ne sont pas visibles/gagnables par CE joueur.
            messageManager.sendMessage(player, "preview_no_rewards_eligible_for_player", MessageManager.ph("%crate_display_name%", crate.getDisplayName())); // Ajouter message
        } else if (crate.getRewards().isEmpty()) {
            // Ce message indique que la caisse n'a AUCUNE récompense définie.
            messageManager.sendMessage(player, "preview_crate_is_empty", MessageManager.ph("%crate_display_name%", crate.getDisplayName())); // Ajouter message
        }
    }

    private ItemStack prepareRewardDisplayItem(Reward reward, Player player, Crate crate) {
        ItemStack displayItem = reward.getDisplayItemStack().clone();
        ItemMeta meta = displayItem.getItemMeta();
        if (meta == null) return displayItem;

        List<String> lore = new ArrayList<>(meta.getLore() != null ? meta.getLore() : Collections.emptyList());

        boolean canWin = true;
        if (!reward.getBlacklistedPermissions().isEmpty()) {
            for (String perm : reward.getBlacklistedPermissions()) {
                if (player.hasPermission(perm)) {
                    canWin = false;
                    lore.add(messageManager.getRawMessage("preview_item_lore_already_has"));
                    break;
                }
            }
        }
        if (canWin && !reward.getRequiredPermissions().isEmpty()) {
            boolean hasRequired = false;
            for (String perm : reward.getRequiredPermissions()) {
                if (player.hasPermission(perm)) {
                    hasRequired = true;
                    break;
                }
            }
            if (!hasRequired) {
                lore.add(messageManager.getRawMessage("preview_item_lore_no_permission_to_win"));
            }
        }

        meta.setLore(ChatUtils.colorize(lore));
        displayItem.setItemMeta(meta);
        return displayItem;
    }


    private void populatePreviewGUI(PagedGUI pagedGUI, Crate crate, PreviewGUIConfig guiConfig) {
        Inventory inv = pagedGUI.getInventory();
        inv.clear();
        Set<Integer> occupiedSlots = new HashSet<>();

        if (guiConfig.getFillerItems() != null) {
            for (Map.Entry<String, PreviewGUIConfig.FillerEntryConfig> entry : guiConfig.getFillerItems().entrySet()) {
                PreviewGUIConfig.FillerEntryConfig fillerEntry = entry.getValue();
                if (fillerEntry.getMaterial() == null || fillerEntry.getMaterial() == Material.AIR) continue;

                ItemBuilder fillerBuilder = new ItemBuilder(fillerEntry.getMaterial())
                    .setName(ChatUtils.colorize(fillerEntry.getName()))
                    .setCustomModelData(fillerEntry.getCustomModelData());
                if (fillerEntry.getLore() != null && !fillerEntry.getLore().isEmpty()) {
                    fillerBuilder.setLore(ChatUtils.colorize(fillerEntry.getLore()));
                }
                ItemStack specificFillerStack = fillerBuilder.build();

                for (int slot : fillerEntry.getSlots()) {
                    if (slot >= 0 && slot < inv.getSize()) {
                        inv.setItem(slot, specificFillerStack.clone());
                        occupiedSlots.add(slot);
                    }
                }
            }
        }

        PreviewGUIConfig.ButtonConfig nextButtonConfig = guiConfig.getButton("nextpage");
        if (nextButtonConfig != null) {
            occupiedSlots.add(nextButtonConfig.getSlot());
            if (pagedGUI.hasNextPage()) {
                inv.setItem(nextButtonConfig.getSlot(), createButton(nextButtonConfig.getItemConfig()));
            }
        }
        PreviewGUIConfig.ButtonConfig prevButtonConfig = guiConfig.getButton("previouspage");
        if (prevButtonConfig != null) {
            occupiedSlots.add(prevButtonConfig.getSlot());
            if (pagedGUI.hasPreviousPage()) {
                inv.setItem(prevButtonConfig.getSlot(), createButton(prevButtonConfig.getItemConfig()));
            }
        }
        if (guiConfig.getButtons() != null) {
            for (Map.Entry<String, PreviewGUIConfig.ButtonConfig> entry : guiConfig.getButtons().entrySet()) {
                String key = entry.getKey().toLowerCase();
                if (key.equals("nextpage") || key.equals("previouspage")) continue;
                PreviewGUIConfig.ButtonConfig customButtonConf = entry.getValue();
                if (customButtonConf.getSlot() >= 0 && customButtonConf.getSlot() < inv.getSize()) {
                    inv.setItem(customButtonConf.getSlot(), createButton(customButtonConf.getItemConfig()));
                    occupiedSlots.add(customButtonConf.getSlot());
                }
            }
        }

        PreviewGUIConfig.DefaultFillerConfig defaultFiller = guiConfig.getDefaultFiller();
        if (defaultFiller != null && defaultFiller.isEnabled()) {
            ItemStack defaultFillerStack = new ItemBuilder(defaultFiller.getMaterial())
                .setName(ChatUtils.colorize(defaultFiller.getName()))
                .build();
            for (int i = 0; i < inv.getSize(); i++) {
                if (!occupiedSlots.contains(i)) {
                    inv.setItem(i, defaultFillerStack.clone());
                }
            }
        }
        if (nextButtonConfig != null && !pagedGUI.hasNextPage() && defaultFiller != null && defaultFiller.isEnabled()) {
            Material fillerMat = defaultFiller.getMaterial() != null ? defaultFiller.getMaterial() : Material.AIR;
            if (fillerMat != Material.AIR)
                inv.setItem(nextButtonConfig.getSlot(), new ItemBuilder(fillerMat).setName(defaultFiller.getName()).build());
            else inv.setItem(nextButtonConfig.getSlot(), null);
        }
        if (prevButtonConfig != null && !pagedGUI.hasPreviousPage() && defaultFiller != null && defaultFiller.isEnabled()) {
            Material fillerMat = defaultFiller.getMaterial() != null ? defaultFiller.getMaterial() : Material.AIR;
            if (fillerMat != Material.AIR)
                inv.setItem(prevButtonConfig.getSlot(), new ItemBuilder(fillerMat).setName(defaultFiller.getName()).build());
            else inv.setItem(prevButtonConfig.getSlot(), null);
        }

        List<ItemStack> itemsForPage = pagedGUI.getItemsForCurrentPage();
        List<Integer> layoutSlots = guiConfig.getLayout();

        if (layoutSlots != null && !layoutSlots.isEmpty()) {
            for (int i = 0; i < itemsForPage.size(); i++) {
                if (i < layoutSlots.size()) {
                    int slot = layoutSlots.get(i);
                    if (slot >= 0 && slot < inv.getSize()) {
                        if (occupiedSlots.contains(slot)) {
                            plugin.getLogger().warning("Layout slot " + slot + " for reward in crate " + crate.getId() + " is already occupied. Skipping reward.");
                            continue;
                        }
                        inv.setItem(slot, itemsForPage.get(i));
                        occupiedSlots.add(slot);
                    }
                } else break;
            }
        } else {
            int currentRewardSlot = 0;
            for (ItemStack itemStack : itemsForPage) {
                boolean placed = false;
                while (currentRewardSlot < inv.getSize()) {
                    if (!occupiedSlots.contains(currentRewardSlot)) {
                        inv.setItem(currentRewardSlot, itemStack);
                        occupiedSlots.add(currentRewardSlot);
                        placed = true;
                        currentRewardSlot++;
                        break;
                    }
                    currentRewardSlot++;
                }
                if (!placed) {
                    plugin.getLogger().warning("Ran out of GUI space for reward items in crate " + crate.getId());
                    break;
                }
            }
        }
    }

    private boolean isDefaultFiller(ItemStack item, PreviewGUIConfig.DefaultFillerConfig defaultFillerConfig) {
        if (defaultFillerConfig == null || !defaultFillerConfig.isEnabled() || item == null || item.getItemMeta() == null) {
            return false;
        }
        String configFillerName = defaultFillerConfig.getName();
        String itemDisplayName = item.getItemMeta().getDisplayName();
        Material configFillerMaterial = defaultFillerConfig.getMaterial();
        Material actualConfigFillerMaterial = (configFillerMaterial == null) ? Material.AIR : configFillerMaterial;

        if (configFillerName == null || configFillerName.trim().isEmpty()) {
            return item.getType() == actualConfigFillerMaterial;
        }
        if (itemDisplayName == null || itemDisplayName.isEmpty()) {
            return false;
        }
        return item.getType() == actualConfigFillerMaterial &&
            ChatUtils.colorize(configFillerName).equals(itemDisplayName);
    }

    private ItemStack createButton(PreviewGUIConfig.ItemConfig itemConfig) {
        Material mat = itemConfig.getMaterial() != null ? itemConfig.getMaterial() : Material.STONE;
        ItemBuilder builder = new ItemBuilder(mat)
            .setName(ChatUtils.colorize(itemConfig.getName()));
        if (itemConfig.getLore() != null && !itemConfig.getLore().isEmpty()) {
            builder.setLore(ChatUtils.colorize(itemConfig.getLore()));
        }
        if (itemConfig.getCustomModelData() != 0) {
            builder.setCustomModelData(itemConfig.getCustomModelData());
        }
        return builder.build();
    }

    public void removeActiveGUI(Player player, PagedGUI gui) {
        if (activePreviewGUIs.get(player.getUniqueId()) == gui) {
            activePreviewGUIs.remove(player.getUniqueId());
        }
    }

    public void playerQuitCleanup(Player player) {
        activePreviewGUIs.remove(player.getUniqueId());
        activeAnimationGUIs.remove(player.getUniqueId());
    }

    public void closeAllPluginInventories() {
        List<UUID> playersToClosePreview = new ArrayList<>(activePreviewGUIs.keySet());
        playersToClosePreview.forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.getOpenInventory().getTopInventory().getHolder() instanceof PagedGUI) {
                p.closeInventory();
            }
        });
        activePreviewGUIs.clear();

        List<UUID> playersToCloseAnimation = new ArrayList<>(activeAnimationGUIs.keySet());
        playersToCloseAnimation.forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && isAnimationInventory(p.getOpenInventory().getTopInventory())) {
                p.closeInventory();
            }
        });
        activeAnimationGUIs.clear();
    }

    public void addAnimationGUI(Player player, Inventory inventory) {
        activeAnimationGUIs.put(player.getUniqueId(), inventory);
    }

    public void removeAnimationGUI(Player player) {
        activeAnimationGUIs.remove(player.getUniqueId());
    }

    public boolean isAnimationInventory(Inventory inventory) {
        if (inventory == null) return false;
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof RouletteAnimation ||
            holder instanceof CSGOAnimation ||
            holder instanceof WheelAnimation;
    }
}
