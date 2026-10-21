package org.Cry_Legende.sHCrates.config;

import org.Cry_Legende.sHCrates.SHCrates;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class CrateConfigManager {

    private final SHCrates plugin;
    private final File cratesFolder;

    public CrateConfigManager(SHCrates plugin) {
        this.plugin = plugin;
        this.cratesFolder = new File(plugin.getDataFolder(), "crates");
        setupCratesFolder();
    }

    private void setupCratesFolder() {
        if (!cratesFolder.exists()) {
            if (cratesFolder.mkdirs()) {
                plugin.getLogger().info("Crates folder created at: " + cratesFolder.getPath());
                // Vous pouvez copier un exemple de fichier de crate ici si vous le souhaitez
                plugin.saveResource("crates/ordinaire.yml", false);
                plugin.saveResource("crates/rare.yml", false); // Exemple
            } else {
                plugin.getLogger().severe("Could not create crates folder!");
            }
        }
    }

    public File getCratesFolder() {
        return cratesFolder;
    }

    public List<File> getCrateConfigFiles() {
        List<File> crateFiles = new ArrayList<>();
        File[] files = cratesFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                crateFiles.add(file);
            }
        }
        return crateFiles;
    }

    public FileConfiguration loadCrateConfig(File crateFile) {
        if (!crateFile.exists()) {
            plugin.getLogger().warning("Crate file " + crateFile.getName() + " does not exist!");
            return null;
        }
        YamlConfiguration crateConfig = new YamlConfiguration();
        try {
            crateConfig.load(crateFile);
            return crateConfig;
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load crate configuration from " + crateFile.getName(), e);
            return null;
        }
    }

    // Appelé par CrateManager pour chaque crate lors du reload/load
    public void saveCrateConfig(String crateName, FileConfiguration config) {
        File crateFile = new File(cratesFolder, crateName.toLowerCase() + ".yml");
        try {
            config.save(crateFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save crate configuration to " + crateFile.getName(), e);
        }
    }
}