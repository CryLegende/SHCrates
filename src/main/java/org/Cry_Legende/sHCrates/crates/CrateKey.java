package org.Cry_Legende.sHCrates.crates;

import org.bukkit.Material;

import java.util.List;

public class CrateKey {
    private final Material material;
    private final String name;
    private final List<String> lore;
    private final int customModelData;
    private final boolean enchanted;

    public CrateKey(Material material, String name, List<String> lore, int customModelData, boolean enchanted) {
        this.material = material;
        this.name = name;
        this.lore = lore;
        this.customModelData = customModelData;
        this.enchanted = enchanted;
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

    public boolean isEnchanted() {
        return enchanted;
    }
}
