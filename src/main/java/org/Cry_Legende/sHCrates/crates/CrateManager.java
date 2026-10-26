package org.Cry_Legende.sHCrates.crates;

import org.Cry_Legende.sHCrates.SHCrates;
import org.Cry_Legende.sHCrates.config.ConfigManager;
import org.Cry_Legende.sHCrates.config.CrateConfigManager;
import org.Cry_Legende.sHCrates.config.MessageManager;
import org.Cry_Legende.sHCrates.crates.config.AnimationConfig;
import org.Cry_Legende.sHCrates.crates.config.PreviewGUIConfig;
import org.Cry_Legende.sHCrates.utils.ChatUtils;
import org.Cry_Legende.sHCrates.utils.ItemBuilder;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class CrateManager {

    private final SHCrates plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final CrateConfigManager crateConfigManager;

    private final Map<String, Crate> loadedCrates = new HashMap<>();
    // Index des caisses posées, par monde (en minuscules) et coordonnées de bloc
    private final Map<BlockKey, CrateLocation> crateLocations = new LinkedHashMap<>();
    // Entrées de locations.yml inutilisables pour l'instant (type de caisse non chargé, entrée incomplète) :
    // réécrites telles quelles à chaque sauvegarde pour ne jamais les perdre
    private final List<Object> unresolvedLocationEntries = new ArrayList<>();
    // Mondes pas encore chargés au démarrage qui portent des caisses (nom en minuscules -> nombre)
    private final Map<String, Integer> pendingWorlds = new HashMap<>();
    private File locationsFile;
    private FileConfiguration locationsConfig;

    private record BlockKey(String world, int x, int y, int z) {
        static BlockKey of(CrateLocation crateLoc) {
            return new BlockKey(crateLoc.getWorldName().toLowerCase(Locale.ROOT), crateLoc.getX(), crateLoc.getY(), crateLoc.getZ());
        }

        static BlockKey of(Location location) {
            return new BlockKey(location.getWorld().getName().toLowerCase(Locale.ROOT),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }

    public CrateManager(SHCrates plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.messageManager = plugin.getMessageManager();
        this.crateConfigManager = plugin.getCrateConfigManager();
        setupLocationsFile();
    }

    private void setupLocationsFile() {
        String fileName = plugin.getConfigManager().getCrateLocationsFileName();
        if (fileName == null || fileName.isEmpty()) {
            fileName = "locations.yml";
            plugin.getLogger().warning("CrateLocationsFileName not set in config, using default 'locations.yml'");
        }

        locationsFile = new File(plugin.getDataFolder(), fileName);
        if (!locationsFile.exists()) {
            try {
                if (locationsFile.createNewFile()) {
                    plugin.getLogger().info(fileName + " created.");
                    locationsConfig = YamlConfiguration.loadConfiguration(locationsFile);
                    if (!locationsConfig.isConfigurationSection("locations")) {
                        locationsConfig.createSection("locations");
                        locationsConfig.save(locationsFile);
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create " + fileName, e);
            }
        }
        locationsConfig = YamlConfiguration.loadConfiguration(locationsFile);
    }

    public void loadCrates() {
        loadedCrates.clear();
        List<File> crateFiles = crateConfigManager.getCrateConfigFiles();
        if (crateFiles.isEmpty()) {
            plugin.getLogger().warning("No crate configuration files found in '" + crateConfigManager.getCratesFolder().getName() + "' folder.");
        }

        for (File crateFile : crateFiles) {
            String crateId = crateFile.getName().replace(".yml", "");
            FileConfiguration config = crateConfigManager.loadCrateConfig(crateFile);
            if (config == null) {
                plugin.getLogger().severe("Failed to load crate config: " + crateFile.getName());
                continue;
            }
            try {
                Crate crate = parseCrate(crateId, config);
                if (crate != null) {
                    String internalId = config.getString("InternalID");
                    boolean saveConfigBack = false;
                    if (internalId == null || internalId.isEmpty()) {
                        internalId = UUID.randomUUID().toString();
                        config.set("InternalID", internalId);
                        saveConfigBack = true;
                        plugin.getLogger().info("Generated new InternalID for crate: " + crateId);
                    }
                    crate.setInternalID(internalId);
                    ItemStack physicalKey = createPhysicalKeyItem(crate);
                    crate.setPhysicalKeyItem(physicalKey);

                    if (saveConfigBack) {
                        crateConfigManager.saveCrateConfig(crateId, config);
                    }
                    loadedCrates.put(crateId.toLowerCase(), crate);
                    plugin.getLogger().info("Successfully loaded crate: " + crate.getDisplayName() + " (ID: " + crateId + ")");
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error parsing crate file: " + crateFile.getName(), e);
            }
        }
        loadCrateLocations();
        plugin.getLogger().info("Loaded " + loadedCrates.size() + " crate types.");
    }

    private Crate parseCrate(String id, FileConfiguration config) {
        String displayName = ChatUtils.colorize(config.getString("DisplayName", "&cUnnamed Crate"));
        Material blockType = Material.matchMaterial(config.getString("BlockType", "CHEST"));
        if (blockType == null) {
            plugin.getLogger().warning("Invalid BlockType for crate " + id + ". Defaulting to CHEST.");
            blockType = Material.CHEST;
        }
        CrateKey keyConfig = parseCrateKey(config.getConfigurationSection("Key"), id);
        if (keyConfig == null) {
            plugin.getLogger().severe("Critical error: Key configuration is missing or invalid for crate " + id + ". Crate will not be loaded.");
            return null;
        }
        PreviewGUIConfig previewGUIConfig = parsePreviewGUIConfig(config.getConfigurationSection("PreviewGUI"), displayName, id);
        AnimationConfig animationConfig = parseAnimationConfig(config.getConfigurationSection("Animation"), displayName, id);
        Crate crate = new Crate(id, displayName, blockType, keyConfig, previewGUIConfig, animationConfig);
        ConfigurationSection rewardsSection = config.getConfigurationSection("Rewards");
        if (rewardsSection != null) {
            for (String rewardKey : rewardsSection.getKeys(false)) {
                ConfigurationSection rewardConfig = rewardsSection.getConfigurationSection(rewardKey);
                if (rewardConfig != null) {
                    Reward reward = parseReward(rewardKey, rewardConfig, crate.getDisplayName());
                    if (reward != null) {
                        crate.addReward(reward);
                    }
                }
            }
        }
        crate.calculateTotalChanceWeight();
        if (crate.getRewards().isEmpty()) {
            plugin.getLogger().warning("Crate '" + id + "' has no rewards defined or failed to load them.");
        }
        return crate;
    }

    private CrateKey parseCrateKey(ConfigurationSection keySection, String crateId) {
        if (keySection == null) {
            return null;
        }
        String materialName = keySection.getString("Material", "TRIPWIRE_HOOK");
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            plugin.getLogger().warning("Invalid key material '" + materialName + "' for crate: " + crateId + ". Defaulting to TRIPWIRE_HOOK.");
            material = Material.TRIPWIRE_HOOK;
        }
        String name = keySection.getString("Name", "&eCrate Key");
        List<String> lore = keySection.getStringList("Lore");
        int customModelData = keySection.getInt("CustomModelData", 0);
        boolean enchanted = keySection.getBoolean("Enchanted", false);
        return new CrateKey(material, name, lore, customModelData, enchanted);
    }

    private PreviewGUIConfig parsePreviewGUIConfig(ConfigurationSection guiSection, String crateDisplayName, String crateId) {
        if (guiSection == null) {
            guiSection = new YamlConfiguration().createSection("dummy");
        }
        String title = guiSection.getString("Title", "&8Preview: %crate_display_name%").replace("%crate_display_name%", crateDisplayName);
        int size = guiSection.getInt("Size", 54);
        if (size % 9 != 0 || size < 9 || size > 54) {
            plugin.getLogger().warning("PreviewGUI size for crate " + crateId + " (" + size + ") is invalid. Adjusting to 54.");
            size = 54;
        }

        PreviewGUIConfig.DefaultFillerConfig defaultFillerConfig = null;
        ConfigurationSection defaultFillerSec = guiSection.getConfigurationSection("FillerItems.DefaultFiller");
        if (defaultFillerSec != null) {
            String dfMatName = defaultFillerSec.getString("Material", "GRAY_STAINED_GLASS_PANE");
            Material defaultMat = Material.matchMaterial(dfMatName);
            if (defaultMat == null) defaultMat = Material.GRAY_STAINED_GLASS_PANE;
            defaultFillerConfig = new PreviewGUIConfig.DefaultFillerConfig(defaultMat, defaultFillerSec.getString("Name", " "));
        }

        Map<String, PreviewGUIConfig.FillerEntryConfig> fillerEntries = new HashMap<>();
        ConfigurationSection fillerItemsParentSec = guiSection.getConfigurationSection("FillerItems");
        if (fillerItemsParentSec != null) {
            for (String key : fillerItemsParentSec.getKeys(false)) {
                if (key.equalsIgnoreCase("DefaultFiller")) continue;
                ConfigurationSection entrySec = fillerItemsParentSec.getConfigurationSection(key);
                if (entrySec != null) {
                    List<Integer> slots = new ArrayList<>();
                    String slotsStr = entrySec.getString("Slots", "");
                    if (!slotsStr.isEmpty()) {
                        try {
                            Arrays.stream(slotsStr.split(","))
                                .map(String::trim).filter(s -> !s.isEmpty())
                                .map(Integer::parseInt).forEach(slots::add);
                        } catch (NumberFormatException e) {
                            plugin.getLogger().warning("Invalid slot format in FillerItems for crate " + crateId + ", key " + key + ": '" + slotsStr + "'. Error: " + e.getMessage());
                        }
                    }
                    Material entryMat = Material.matchMaterial(entrySec.getString("Material", ""));
                    fillerEntries.put(key, new PreviewGUIConfig.FillerEntryConfig(
                        entryMat, entrySec.getString("Name", " "),
                        entrySec.getStringList("Lore"), slots,
                        entrySec.getStringList("CommandsOnClick"),
                        entrySec.getInt("CustomModelData", 0),
                        entrySec.getBoolean("CloseOnClick", false)
                    ));
                }
            }
        }

        int itemsPerPage = guiSection.getInt("ItemsPerPage", 28);
        List<Integer> layout = guiSection.getIntegerList("Layout");

        Map<String, PreviewGUIConfig.ButtonConfig> buttons = new HashMap<>();
        ConfigurationSection buttonsSection = guiSection.getConfigurationSection("Buttons");
        if (buttonsSection != null) {
            for (String buttonKey : buttonsSection.getKeys(false)) {
                ConfigurationSection btnCfgSec = buttonsSection.getConfigurationSection(buttonKey);
                if (btnCfgSec != null && btnCfgSec.contains("Slot")) {
                    Material btnMat = Material.matchMaterial(btnCfgSec.getString("Item.Material", "STONE"));
                    PreviewGUIConfig.ItemConfig itemConfig = new PreviewGUIConfig.ItemConfig(
                        btnMat, btnCfgSec.getString("Item.Name", "&cButton"),
                        btnCfgSec.getStringList("Item.Lore"),
                        btnCfgSec.getInt("Item.CustomModelData", 0));
                    buttons.put(buttonKey.toLowerCase(), new PreviewGUIConfig.ButtonConfig(
                        btnCfgSec.getInt("Slot"), itemConfig,
                        btnCfgSec.getStringList("Commands"),
                        btnCfgSec.getBoolean("CloseOnClick", false)
                    ));
                }
            }
        }

        return new PreviewGUIConfig(title, size, defaultFillerConfig, fillerEntries, itemsPerPage, layout, buttons);
    }

    private AnimationConfig parseAnimationConfig(ConfigurationSection animSection, String crateDisplayName, String crateId) {
        if (animSection == null) {
            animSection = new YamlConfiguration().createSection("dummy");
        }
        String type = animSection.getString("Type", configManager.getDefaultAnimationType()).toUpperCase();
        String guiTitle = animSection.getString("GUITitle", "&8Opening %crate_display_name%...").replace("%crate_display_name%", crateDisplayName);
        int spinDuration = animSection.getInt("SpinDurationTicks", 100);
        String soundOpen = animSection.getString("SoundOpen", "BLOCK_CHEST_OPEN");
        String soundSpin = animSection.getString("SoundSpin", "ENTITY_EXPERIENCE_ORB_PICKUP");
        String soundWin = animSection.getString("SoundWin", "ENTITY_PLAYER_LEVELUP");
        int rerollButtonSlot = animSection.getInt("RerollButtonSlot", 22);

        ConfigurationSection quickCrateSection = animSection.getConfigurationSection("QuickCrateAnimation");
        if (quickCrateSection == null) {
            quickCrateSection = new YamlConfiguration().createSection("dummy");
        }

        AnimationConfig.QuickCrateAnimConfig quickConfig = new AnimationConfig.QuickCrateAnimConfig(
            quickCrateSection.getBoolean("Enabled", true),
            quickCrateSection.getInt("DurationSeconds", 3),
            quickCrateSection.getBoolean("DisplayHologram", true),
            quickCrateSection.getDouble("ItemHeightOffset", 1.0),
            quickCrateSection.getDouble("HologramHeightOffset", 0.5)
        );
        return new AnimationConfig(type, guiTitle, spinDuration, soundOpen, soundSpin, soundWin, rerollButtonSlot, quickConfig);
    }

    private Reward parseReward(String rewardId, ConfigurationSection rewardConfig, String crateDisplayName) {
        boolean displayInPreview = rewardConfig.getBoolean("DisplayInPreview", true);
        String displayName = ChatUtils.colorize(rewardConfig.getString("DisplayName", "&cUnnamed Reward"));
        Material displayItemMat = Material.matchMaterial(rewardConfig.getString("DisplayItem", "STONE"));
        if (displayItemMat == null) {
            displayItemMat = Material.STONE;
        }
        String playerSkinTexture = rewardConfig.getString("PlayerSkinTexture");
        int displayAmount = Math.max(1, rewardConfig.getInt("DisplayAmount", 1));
        List<String> lore = rewardConfig.getStringList("Lore");
        double chance = rewardConfig.getDouble("Chance", 1.0);
        boolean firework = rewardConfig.getBoolean("FireworkOnWin", true);
        boolean glowing = rewardConfig.getBoolean("GlowingDisplayItem", false);
        boolean hideFlags = rewardConfig.getBoolean("HideItemFlags", true);
        List<String> commands = rewardConfig.getStringList("Commands");
        List<String> messages = rewardConfig.getStringList("Messages");
        List<String> reqPerms = rewardConfig.getStringList("Required-Permissions");
        List<String> blackPerms = rewardConfig.getStringList("Blacklisted-Permissions");
        Reward reward = new Reward(rewardId, displayInPreview, displayName, displayItemMat, playerSkinTexture,
            displayAmount, lore, chance, firework, glowing, hideFlags, commands, messages, reqPerms, blackPerms);
        ItemBuilder itemBuilder = new ItemBuilder(reward.getDisplayItemMaterial(), reward.getDisplayAmount())
            .setName(reward.getDisplayName())
            .setLore(reward.getLore());
        if (reward.getDisplayItemMaterial() == Material.PLAYER_HEAD && reward.getPlayerSkinTexture() != null) {
            itemBuilder.setSkullTexture(reward.getPlayerSkinTexture());
        }
        if (reward.isGlowingDisplayItem()) {
            itemBuilder.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true).addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        if (reward.shoudHideItemFlags()) {
            itemBuilder.hideAllFlags();
        }
        reward.setDisplayItemStack(itemBuilder.build());
        return reward;
    }

    public ItemStack createPhysicalKeyItem(Crate crate) {
        if (crate == null || crate.getKeyConfig() == null) {
            return null;
        }
        CrateKey keyConfig = crate.getKeyConfig();
        ItemBuilder keyBuilder = new ItemBuilder(keyConfig.getMaterial())
            .setName(ChatUtils.colorize(keyConfig.getName()))
            .setLore(ChatUtils.colorize(keyConfig.getLore()))
            .setCustomModelData(keyConfig.getCustomModelData());
        if (keyConfig.isEnchanted()) {
            keyBuilder.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true).addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        keyBuilder.setPersistentData(plugin, configManager.getPhysicalKeyNbtTag(), PersistentDataType.STRING, crate.getInternalID());
        return keyBuilder.build();
    }

    public Optional<Crate> getCrate(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(loadedCrates.get(id.toLowerCase()));
    }

    public Collection<Crate> getAllCrates() {
        return Collections.unmodifiableCollection(loadedCrates.values());
    }

    /**
     * Charge locations.yml. Une caisse dont le monde n'est pas encore chargé reste indexée par nom de monde
     * (elle devient utilisable dès que le monde arrive) ; une caisse dont le type n'est pas chargé est
     * conservée telle quelle. Rien n'est jamais retiré du fichier à cause d'un monde ou d'un type absent.
     */
    public void loadCrateLocations() {
        crateLocations.clear();
        unresolvedLocationEntries.clear();
        pendingWorlds.clear();
        if (locationsFile == null || !locationsFile.exists()) return;

        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(locationsFile);
        } catch (IOException | InvalidConfigurationException e) {
            backupUnreadableLocationsFile(e);
            return;
        }
        locationsConfig = config;

        ConfigurationSection locSection = config.getConfigurationSection("locations");
        if (locSection == null) return;
        for (String key : locSection.getKeys(false)) {
            ConfigurationSection entry = locSection.getConfigurationSection(key);
            String worldName = entry != null ? entry.getString("world") : null;
            String crateId = entry != null ? entry.getString("crateId") : null;
            if (worldName == null || crateId == null) {
                plugin.getLogger().warning("Crate location '" + key + "' in " + locationsFile.getName() + " is incomplete (world or crateId missing), kept as is.");
                unresolvedLocationEntries.add(toRawValue(locSection.get(key)));
                continue;
            }
            if (!loadedCrates.containsKey(crateId.toLowerCase())) {
                plugin.getLogger().warning("Crate location '" + key + "' uses crate type '" + crateId + "' which is not loaded, kept as is.");
                unresolvedLocationEntries.add(toRawValue(entry));
                continue;
            }
            CrateLocation crateLoc = new CrateLocation(worldName,
                (int) Math.floor(entry.getDouble("x")), (int) Math.floor(entry.getDouble("y")), (int) Math.floor(entry.getDouble("z")),
                crateId);
            crateLocations.putIfAbsent(BlockKey.of(crateLoc), crateLoc);
            if (Bukkit.getWorld(worldName) == null) {
                pendingWorlds.merge(worldName.toLowerCase(Locale.ROOT), 1, Integer::sum);
            }
        }
        pendingWorlds.forEach((world, count) -> plugin.getLogger().info(count + " crate(s) waiting for world '" + world + "' to load."));
    }

    /**
     * Appelé au chargement d'un monde (WorldLoadEvent) : les caisses de ce monde, déjà indexées, deviennent actives.
     */
    public void onWorldLoaded(World world) {
        Integer count = pendingWorlds.remove(world.getName().toLowerCase(Locale.ROOT));
        if (count != null) {
            plugin.getLogger().info("World '" + world.getName() + "' loaded: " + count + " crate(s) now active.");
        }
    }

    /**
     * locations.yml illisible : on le copie à côté avant qu'une sauvegarde ne l'écrase.
     */
    private void backupUnreadableLocationsFile(Exception cause) {
        File backup = new File(locationsFile.getParentFile(), locationsFile.getName() + ".illisible-"
            + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()));
        try {
            Files.copy(locationsFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().log(Level.SEVERE, "Could not read " + locationsFile.getName() + ", copy kept as " + backup.getName() + ". No crate location loaded.", cause);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not read " + locationsFile.getName() + " nor back it up.", e);
        }
    }

    /**
     * Copie brute d'une entrée YAML (section -> map) pour pouvoir la réécrire à l'identique.
     */
    private static Object toRawValue(Object value) {
        if (value instanceof ConfigurationSection section) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : section.getValues(false).entrySet()) {
                map.put(e.getKey(), toRawValue(e.getValue()));
            }
            return map;
        }
        return value;
    }

    public void saveCrateLocations() {
        if (locationsFile == null) setupLocationsFile();
        FileConfiguration newConfig = new YamlConfiguration();
        ConfigurationSection locSection = newConfig.createSection("locations");
        int i = 0;
        for (CrateLocation crateLoc : crateLocations.values()) {
            String path = String.valueOf(i++);
            locSection.set(path + ".world", crateLoc.getWorldName());
            locSection.set(path + ".x", (double) crateLoc.getX());
            locSection.set(path + ".y", (double) crateLoc.getY());
            locSection.set(path + ".z", (double) crateLoc.getZ());
            locSection.set(path + ".crateId", crateLoc.getCrateId());
        }
        for (Object raw : unresolvedLocationEntries) {
            locSection.set(String.valueOf(i++), raw);
        }
        try {
            newConfig.save(locationsFile);
            this.locationsConfig = newConfig;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save crate locations to " + locationsFile.getName(), e);
        }
    }

    public boolean addCrateLocation(Location location, String crateId) {
        if (isCrateLocation(location)) return false;
        if (getCrate(crateId).isPresent()) {
            CrateLocation crateLoc = new CrateLocation(location, crateId);
            crateLocations.put(BlockKey.of(crateLoc), crateLoc);
            saveCrateLocations();
            return true;
        }
        return false;
    }

    public boolean removeCrateLocation(Location location) {
        if (location == null || location.getWorld() == null) return false;
        boolean removed = crateLocations.remove(BlockKey.of(location)) != null;
        if (removed) saveCrateLocations();
        return removed;
    }

    public Optional<CrateLocation> getCrateLocation(Location location) {
        if (location == null || location.getWorld() == null) return Optional.empty();
        return Optional.ofNullable(crateLocations.get(BlockKey.of(location)));
    }

    public boolean isCrateLocation(Location location) {
        return getCrateLocation(location).isPresent();
    }

    public boolean isPhysicalKey(ItemStack item, Crate crate) {
        if (item == null || item.getType().isAir() || crate == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        NamespacedKey nbtKey = new NamespacedKey(plugin, configManager.getPhysicalKeyNbtTag());
        if (meta.getPersistentDataContainer().has(nbtKey, PersistentDataType.STRING)) {
            String itemCrateInternalId = meta.getPersistentDataContainer().get(nbtKey, PersistentDataType.STRING);
            return Objects.equals(itemCrateInternalId, crate.getInternalID());
        }
        if (configManager.isCompatibilityModeEnabled()) {
            CrateKey keyConfig = crate.getKeyConfig();
            if (configManager.isCompatibilityMatchMaterial() && item.getType() != keyConfig.getMaterial()) {
                return false;
            }
            if (!meta.hasDisplayName() || !ChatColor.stripColor(meta.getDisplayName()).equals(ChatColor.stripColor(ChatUtils.colorize(keyConfig.getName())))) {
                return false;
            }
            if (keyConfig.getLore() != null && !keyConfig.getLore().isEmpty()) {
                if (!meta.hasLore() || meta.getLore() == null) return false;
                List<String> itemLore = meta.getLore().stream().map(ChatColor::stripColor).collect(Collectors.toList());
                List<String> configLore = keyConfig.getLore().stream().map(ChatUtils::colorize).map(ChatColor::stripColor).collect(Collectors.toList());
                if (configManager.isCompatibilityMatchLoreExactly()) {
                    return itemLore.equals(configLore);
                } else {
                    return new HashSet<>(itemLore).containsAll(configLore);
                }
            }
            return true;
        }
        return false;
    }

    public int takePhysicalKeys(Player player, Crate crate, int amountToTake) {
        if (player == null || crate == null || amountToTake <= 0) {
            return 0;
        }
        int removedCount = 0;
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (removedCount >= amountToTake) break;
            ItemStack item = inventory.getItem(i);
            if (isPhysicalKey(item, crate)) {
                int stackAmount = item.getAmount();
                int toRemove = Math.min(amountToTake - removedCount, stackAmount);
                item.setAmount(stackAmount - toRemove);
                removedCount += toRemove;
            }
        }
        if (removedCount > 0) {
            player.updateInventory();
        }
        return removedCount;
    }

    private boolean canInventoryHoldItems(PlayerInventory playerInventory, ItemStack itemsToAdd) {
        int amountToAdd = itemsToAdd.getAmount();
        for (ItemStack item : playerInventory.getStorageContents()) {
            if (amountToAdd <= 0) break;
            if (item != null && item.isSimilar(itemsToAdd)) {
                int canTake = Math.min(amountToAdd, item.getMaxStackSize() - item.getAmount());
                amountToAdd -= canTake;
            }
        }
        if (amountToAdd <= 0) return true;
        for (ItemStack item : playerInventory.getStorageContents()) {
            if (amountToAdd <= 0) break;
            if (item == null || item.getType() == Material.AIR) {
                amountToAdd -= Math.min(amountToAdd, itemsToAdd.getMaxStackSize());
            }
        }
        return amountToAdd <= 0;
    }

    public void giveKey(Player player, String crateId, int amount, boolean physical) {
        Optional<Crate> crateOpt = getCrate(crateId);
        if (crateOpt.isEmpty()) {
            messageManager.sendMessage(player, "crate_not_exist", MessageManager.ph("%crate_name%", crateId));
            return;
        }
        Crate crate = crateOpt.get();

        if (physical) {
            ItemStack keyItem = createPhysicalKeyItem(crate);
            if (keyItem == null) {
                messageManager.sendMessage(player, "error_occurred", MessageManager.ph("%details%", "Crate key is invalid."));
                return;
            }
            keyItem.setAmount(amount);
            if (!canInventoryHoldItems(player.getInventory(), keyItem)) {
                plugin.getPlayerDataManager().addVirtualKeys(player.getUniqueId(), crate.getInternalID(), amount);
                messageManager.sendMessage(player, "key_received_virtual_inventory_full", MessageManager.ph("%amount%", String.valueOf(amount), "%crate_name%", crate.getDisplayName()));
            } else {
                player.getInventory().addItem(keyItem);
                messageManager.sendMessage(player, "key_received_physical", MessageManager.ph("%amount%", String.valueOf(amount), "%crate_name%", crate.getDisplayName()));
            }
            player.updateInventory();
        } else {
            plugin.getPlayerDataManager().addVirtualKeys(player.getUniqueId(), crate.getInternalID(), amount);
            messageManager.sendMessage(player, "key_received_virtual", MessageManager.ph("%amount%", String.valueOf(amount), "%crate_name%", crate.getDisplayName()));
        }
    }

    public void reloadAll() {
        plugin.getLogger().info("Reloading SHCrates...");
        configManager.reloadConfigs();
        messageManager.reloadMessages();
        setupLocationsFile();
        loadCrates();
        plugin.getPlayerDataManager().saveAndReloadAllPlayerData();
        plugin.getLogger().info("SHCrates reloaded successfully.");
    }
}
