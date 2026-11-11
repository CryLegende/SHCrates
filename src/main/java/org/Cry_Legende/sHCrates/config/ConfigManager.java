package org.Cry_Legende.sHCrates.config;

import org.Cry_Legende.sHCrates.SHCrates;
import org.Cry_Legende.sHCrates.crates.Tier;
import org.Cry_Legende.sHCrates.utils.ChatUtils;
import org.Cry_Legende.sHCrates.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private String rewardLogFormat;

    // Opening settings
    private int openAllDelayTicks;
    private int autoCloseGUIDelaySeconds;
    private int openAllMax;
    private int openAllPerTick;
    private String keyReceivedSound;
    private int gaugeBarSegments;
    // Conditions nommées (section conditions) : nom -> expression
    private Map<String, String> namedConditions = Collections.emptyMap();

    // Paliers de rareté, par identifiant, dans l'ordre du fichier
    private Map<String, Tier> tiers = Collections.emptyMap();

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

        apply(YamlConfiguration.loadConfiguration(configFile));
    }

    /** Lit config.yml sans rien appliquer. */
    public YamlConfiguration parse() throws IOException, InvalidConfigurationException {
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveDefaultConfig();
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(configFile);
        return yaml;
    }

    public YamlConfiguration getYaml() {
        return (YamlConfiguration) config;
    }

    /** Remplace les réglages par ceux d'une lecture réussie de config.yml. */
    public void apply(YamlConfiguration yaml) {
        config = yaml;

        // Charger les valeurs
        debugMode = config.getBoolean("debug_mode", false);
        physicalKeyNbtTag = config.getString("physical_key_nbt_tag", "SHCrates_KeyID");
        defaultAnimationType = config.getString("default_animation_type", "ROULETTE").toUpperCase();

        knockbackOnNoKey = config.getBoolean("knockback_on_no_key.enabled", true);
        knockbackStrength = config.getDouble("knockback_on_no_key.strength", 0.5);

        logRewardsToConsole = config.getBoolean("log_rewards_to_console", true);
        rewardLogFormat = config.getString("reward_log_format", "%player_name% gagne %reward_name%");
        openAllDelayTicks = config.getInt("open_all_delay_ticks", 2);
        autoCloseGUIDelaySeconds = config.getInt("auto_close_gui_delay_seconds", 7);
        openAllMax = Math.max(1, config.getInt("ouverture_multiple.max", 200));
        openAllPerTick = Math.max(1, config.getInt("ouverture_multiple.par_tick", 20));
        keyReceivedSound = config.getString("son_cle_recue", "ENTITY_EXPERIENCE_ORB_PICKUP");
        gaugeBarSegments = Math.max(1, config.getInt("jauge_barre_segments", 20));
        Map<String, String> named = new LinkedHashMap<>();
        ConfigurationSection conditionsSection = config.getConfigurationSection("conditions");
        if (conditionsSection != null) {
            for (String name : conditionsSection.getKeys(false)) {
                named.put(name.toLowerCase(Locale.ROOT), conditionsSection.getString(name, ""));
            }
        }
        namedConditions = named;
        tiers = loadTiers(config.getConfigurationSection("paliers"));

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
            ItemBuilder builder = new ItemBuilder(materialOr(rerollButtonSection.getString("material", "ENDER_EYE"), Material.ENDER_EYE))
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
            rerollUsedButtonItem = new ItemBuilder(materialOr(rerollUsedButtonSection.getString("material", "BARRIER"), Material.BARRIER))
                .setName(rerollUsedButtonSection.getString("name", "&c&lRELANCE UTILISÉE"))
                .setLore(rerollUsedButtonSection.getStringList("lore"))
                .build();
        } else {
            rerollUsedButtonItem = new ItemBuilder(Material.BARRIER).setName("&c&lRELANCE UTILISÉE").build(); // Fallback
        }
    }

    /**
     * Matériau lu en config, ou le repli s'il est invalide (null faisait planter le chargement).
     */
    private Map<String, Tier> loadTiers(ConfigurationSection section) {
        Map<String, Tier> loaded = new LinkedHashMap<>();
        if (section == null) {
            return loaded;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection t = section.getConfigurationSection(id);
            if (t == null) continue;
            String glassName = t.getString("vitre", "");
            Material glass = glassName.isEmpty() ? null : Material.matchMaterial(glassName);
            loaded.put(id.toLowerCase(Locale.ROOT), new Tier(
                id.toLowerCase(Locale.ROOT),
                t.getInt("rang", loaded.size() + 1),
                ChatUtils.colorize(t.getString("nom", id)),
                glass,
                t.getString("son", ""),
                (float) t.getDouble("volume", 1.0),
                (float) t.getDouble("hauteur", 1.0),
                t.getBoolean("feu-artifice", false),
                t.getBoolean("totem", false),
                t.getString("titre", ""),
                t.getString("sous-titre", ""),
                t.getBoolean("annonce", false)));
        }
        return loaded;
    }

    /** Palier par identifiant (sans tenir compte de la casse), ou null s'il n'existe pas. */
    public Tier getTier(String id) {
        return id == null ? null : tiers.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Tier> getTiers() {
        return tiers.values();
    }

    /** Nombre maximal de clés ouvertes par une ouverture multiple. */
    public int getOpenAllMax() {
        return openAllMax;
    }

    /** Expression d'une condition nommée (section conditions), ou null si ce nom n'existe pas. */
    public String getNamedCondition(String name) {
        return name == null ? null : namedConditions.get(name.trim().toLowerCase(Locale.ROOT));
    }

    /** Nombre de segments de %shcrates_jauge_barre_<caisse>%. */
    public int getGaugeBarSegments() {
        return gaugeBarSegments;
    }

    /** Son joué à la réception de clés d'une caisse qui a AnnonceReception. */
    public String getKeyReceivedSound() {
        return keyReceivedSound;
    }

    /** Clés ouvertes par tick pendant une ouverture multiple. */
    public int getOpenAllPerTick() {
        return openAllPerTick;
    }

    private Material materialOr(String name, Material fallback) {
        Material material = name == null ? null : Material.matchMaterial(name);
        if (material == null) {
            plugin.getLogger().warning("Invalid reroll button material '" + name + "', using " + fallback + ".");
            return fallback;
        }
        return material;
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

    public String getRewardLogFormat() {
        return rewardLogFormat;
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
            loadConfigs();
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
