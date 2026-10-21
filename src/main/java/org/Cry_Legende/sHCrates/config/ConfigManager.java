package org.Cry_Legende.sHCrates.config;

import org.Cry_Legende.sHCrates.SHCrates;
import org.Cry_Legende.sHCrates.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

public class ConfigManager {

    private final SHCrates plugin;
    private File configFile;
    private FileConfiguration config;

    // Default config values
    private boolean debugMode;
    private String physicalKeyNbtTag;
    private String defaultAnimationType;

    // Knockback settings
    private boolean knockbackOnNoKey;
    private double knockbackStrength;

    // Logging
    private boolean logRewardsToConsole;

    // Opening settings
    private int openAllDelayTicks;
    private int autoCloseGUIDelaySeconds;

    // Reroll Settings
    private boolean rerollEnabled;
    private ItemStack rerollButtonItem;
    private ItemStack rerollUsedButtonItem;

    // Storage settings
    private String crateLocationsFileName;
    private String virtualKeysStorageType;

    // Compatibility Mode settings
    private boolean compatibilityModeEnabled;
    private boolean compatibilityMatchMaterial;
    private boolean compatibilityMatchLoreExactly;


    public ConfigManager(SHCrates plugin) {
        this.plugin = plugin;
    }

    public void loadConfigs() {
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.getLogger().info("config.yml not found, creating a new one...");
            plugin.saveDefaultConfig();
        } else {
            plugin.getLogger().info("Loading config.yml...");
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        // Charger les valeurs
        debugMode = config.getBoolean("debug_mode", false);
        physicalKeyNbtTag = config.getString("physical_key_nbt_tag", "SHCrates_KeyID");
        defaultAnimationType = config.getString("default_animation_type", "ROULETTE").toUpperCase();

        knockbackOnNoKey = config.getBoolean("knockback_on_no_key.enabled", true);
        knockbackStrength = config.getDouble("knockback_on_no_key.strength", 0.5);

        logRewardsToConsole = config.getBoolean("log_rewards_to_console", true);
        openAllDelayTicks = config.getInt("open_all_delay_ticks", 2);
        autoCloseGUIDelaySeconds = config.getInt("auto_close_gui_delay_seconds", 7);

        // Load Reroll Settings
        rerollEnabled = config.getBoolean("reroll.enabled", true);
        loadRerollButtons();

        crateLocationsFileName = config.getString("storage.crate_locations_file", "locations.yml");
        virtualKeysStorageType = config.getString("storage.virtual_keys_storage_type", "YAML").toUpperCase();

        compatibilityModeEnabled = config.getBoolean("compatibility_mode.enabled", false);
        compatibilityMatchMaterial = config.getBoolean("compatibility_mode.match_material", true);
        compatibilityMatchLoreExactly = config.getBoolean("compatibility_mode.match_lore_exactly", true);


        if (debugMode) {
            plugin.getLogger().log(Level.INFO, "[DEBUG] Debug mode is enabled via config.yml.");
        }
        if (compatibilityModeEnabled) {
            plugin.getLogger().log(Level.WARNING, "[COMPATIBILITY] Key compatibility mode is ENABLED. This is less secure and intended for transitioning from other crate plugins. Disable once no longer needed.");
        }
    }

    private void loadRerollButtons() {
        ConfigurationSection rerollButtonSection = config.getConfigurationSection("reroll.reroll_button");
        if (rerollButtonSection != null) {
            ItemBuilder builder = new ItemBuilder(Material.matchMaterial(rerollButtonSection.getString("material", "ENDER_EYE")))
                .setName(rerollButtonSection.getString("name", "&a&lRELANCER"))
                .setLore(rerollButtonSection.getStringList("lore"));
            if (rerollButtonSection.getBoolean("glowing", true)) {
                builder.addEnchant(Enchantment.LURE, 1, true).addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            rerollButtonItem = builder.build();
        } else {
            rerollButtonItem = new ItemBuilder(Material.ENDER_EYE).setName("&a&lRELANCER").build(); // Fallback
        }

        ConfigurationSection rerollUsedButtonSection = config.getConfigurationSection("reroll.reroll_used_button");
        if (rerollUsedButtonSection != null) {
            rerollUsedButtonItem = new ItemBuilder(Material.matchMaterial(rerollUsedButtonSection.getString("material", "BARRIER")))
                .setName(rerollUsedButtonSection.getString("name", "&c&lRELANCE UTILISÉE"))
                .setLore(rerollUsedButtonSection.getStringList("lore"))
                .build();
        } else {
            rerollUsedButtonItem = new ItemBuilder(Material.BARRIER).setName("&c&lRELANCE UTILISÉE").build(); // Fallback
        }
    }

    public void reloadConfigs() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "config.yml");
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        loadConfigs();
        plugin.getLogger().info("config.yml has been reloaded.");
    }

    public void saveConfig() {
        if (config == null || configFile == null) {
            plugin.getLogger().warning("Attempted to save config, but it's not loaded properly.");
            return;
        }
        try {
            config.save(configFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save config to " + configFile, ex);
        }
    }

    // --- Getters ---
    public boolean isDebugMode() {
        return debugMode;
    }

    public String getPhysicalKeyNbtTag() {
        return physicalKeyNbtTag;
    }

    public String getDefaultAnimationType() {
        return defaultAnimationType;
    }

    public boolean isKnockbackOnNoKey() {
        return knockbackOnNoKey;
    }

    public double getKnockbackStrength() {
        return knockbackStrength;
    }

    public boolean isLogRewardsToConsole() {
        return logRewardsToConsole;
    }

    public int getOpenAllDelayTicks() {
        return openAllDelayTicks;
    }

    public int getAutoCloseGUIDelaySeconds() {
        return autoCloseGUIDelaySeconds;
    }

    public String getCrateLocationsFileName() {
        return crateLocationsFileName;
    }

    public String getVirtualKeysStorageType() {
        return virtualKeysStorageType;
    }

    public boolean isCompatibilityModeEnabled() {
        return compatibilityModeEnabled;
    }

    public boolean isCompatibilityMatchMaterial() {
        return compatibilityMatchMaterial;
    }

    public boolean isCompatibilityMatchLoreExactly() {
        return compatibilityMatchLoreExactly;
    }

    public boolean isRerollEnabled() {
        return rerollEnabled;
    }

    public ItemStack getRerollButtonItem() {
        return rerollButtonItem.clone();
    }

    public ItemStack getRerollUsedButtonItem() {
        return rerollUsedButtonItem.clone();
    }

    public FileConfiguration getConfig() {
        if (config == null) {
            reloadConfigs();
        }
        return config;
    }

    public String getString(String path, String def) {
        return getConfig().getString(path, def);
    }

    public int getInt(String path, int def) {
        return getConfig().getInt(path, def);
    }

    public boolean getBoolean(String path, boolean def) {
        return getConfig().getBoolean(path, def);
    }

    public List<String> getStringList(String path) {
        return getConfig().getStringList(path);
    }
}
