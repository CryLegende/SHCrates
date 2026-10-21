package org.Cry_Legende.sHCrates.crates.config;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PreviewGUIConfig {
    private final String title;
    private final int size;
    private final DefaultFillerConfig defaultFiller;
    private final Map<String, FillerEntryConfig> fillerItems;
    private final int itemsPerPage;
    private final List<Integer> layout;
    private final Map<String, ButtonConfig> buttons;

    public PreviewGUIConfig(String title, int size, DefaultFillerConfig defaultFiller, Map<String, FillerEntryConfig> fillerItems, int itemsPerPage, List<Integer> layout, Map<String, ButtonConfig> buttons) {
        this.title = title;
        this.size = size;
        this.defaultFiller = defaultFiller;
        this.fillerItems = fillerItems != null ? Collections.unmodifiableMap(fillerItems) : Collections.emptyMap();
        this.itemsPerPage = itemsPerPage;
        this.layout = layout != null ? Collections.unmodifiableList(layout) : Collections.emptyList();
        this.buttons = buttons != null ? Collections.unmodifiableMap(buttons) : Collections.emptyMap();
    }

    public String getTitle() {
        return title;
    }

    public int getSize() {
        return size;
    }

    public DefaultFillerConfig getDefaultFiller() {
        return defaultFiller;
    }

    public Map<String, FillerEntryConfig> getFillerItems() {
        return fillerItems;
    }

    public int getItemsPerPage() {
        return itemsPerPage;
    }

    public List<Integer> getLayout() {
        return layout;
    }

    public Map<String, ButtonConfig> getButtons() {
        return buttons;
    }

    public ButtonConfig getButton(String buttonName) {
        if (buttonName == null || buttons == null) return null;
        return buttons.get(buttonName.toLowerCase());
    }

    public static class DefaultFillerConfig {
        private final boolean enabled;
        private final Material material;
        private final String name;

        public DefaultFillerConfig(Material material, String name) {
            this.material = material;
            this.name = name;
            this.enabled = (material != null && material != Material.AIR);
        }

        public boolean isEnabled() {
            return enabled;
        }

        public Material getMaterial() {
            return material;
        }

        public String getName() {
            return name;
        }
    }

    public static class FillerEntryConfig {
        private final Material material;
        private final String name;
        private final List<String> lore;
        private final List<Integer> slots;
        private final List<String> commandsOnClick;
        private final int customModelData;
        private final boolean closeOnClick; // NOUVEAU

        public FillerEntryConfig(Material material, String name, List<String> lore, List<Integer> slots, List<String> commands, int cmd, boolean close) { // NOUVEAU paramètre
            this.material = material;
            this.name = name;
            this.lore = lore != null ? Collections.unmodifiableList(new ArrayList<>(lore)) : Collections.emptyList();
            this.slots = slots != null ? Collections.unmodifiableList(new ArrayList<>(slots)) : Collections.emptyList();
            this.commandsOnClick = commands != null ? Collections.unmodifiableList(new ArrayList<>(commands)) : Collections.emptyList();
            this.customModelData = cmd;
            this.closeOnClick = close; // NOUVEAU
        }

        public Material getMaterial() {
            return material;
        }

        public String getName() {
            return name;
        }

        public List<String> getLore() {
            return lore;
        }

        public List<Integer> getSlots() {
            return slots;
        }

        public List<String> getCommandsOnClick() {
            return commandsOnClick;
        }

        public int getCustomModelData() {
            return customModelData;
        }

        public boolean shouldCloseOnClick() {
            return closeOnClick;
        } // NOUVEAU getter
    }

    public static class ButtonConfig {
        private final int slot;
        private final ItemConfig itemConfig;
        private final List<String> commands;
        private final boolean closeOnClick; // Peut être utile pour les boutons aussi

        public ButtonConfig(int slot, ItemConfig itemConfig, List<String> commands, boolean close) {
            this.slot = slot;
            this.itemConfig = itemConfig;
            this.commands = commands != null ? Collections.unmodifiableList(new ArrayList<>(commands)) : Collections.emptyList();
            this.closeOnClick = close;
        }

        public int getSlot() {
            return slot;
        }

        public ItemConfig getItemConfig() {
            return itemConfig;
        }

        public List<String> getCommands() {
            return commands;
        }

        public boolean shouldCloseOnClick() {
            return closeOnClick;
        }
    }

    public static class ItemConfig {
        private final Material material;
        private final String name;
        private final List<String> lore;
        private final int customModelData;

        public ItemConfig(Material material, String name, List<String> lore, int customModelData) {
            this.material = material;
            this.name = name;
            this.lore = lore != null ? Collections.unmodifiableList(new ArrayList<>(lore)) : Collections.emptyList();
            this.customModelData = customModelData;
        }

        public Material getMaterial() {
            return material;
        }

        public String getName() {
            return name;
        }

        public List<String> getLore() {
            return lore;
        }

        public int getCustomModelData() {
            return customModelData;
        }
    }
}
