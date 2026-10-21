package org.Cry_Legende.sHCrates.crates;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class Reward {

    private final String id; // ID unique de la récompense DANS SA CRATE
    private final boolean displayInPreview;
    private final String displayName;
    private final Material displayItemMaterial;
    private final String playerSkinTexture; // Pour PLAYER_HEAD
    private final int displayAmount;
    private final List<String> lore;
    private double chance; // Peut être modifié par le CrateManager pour normalisation
    private final boolean fireworkOnWin;
    private final boolean glowingDisplayItem;
    private final boolean hideItemFlags;
    private final List<String> commands;
    private final List<String> messages;
    private final List<String> requiredPermissions;
    private final List<String> blacklistedPermissions;

    private ItemStack displayItemStack; // ItemStack réel pour l'affichage, généré

    public Reward(String id, boolean displayInPreview, String displayName, Material displayItemMaterial, String playerSkinTexture,
                  int displayAmount, List<String> lore, double chance, boolean fireworkOnWin,
                  boolean glowingDisplayItem, boolean hideItemFlags, List<String> commands,
                  List<String> messages, List<String> requiredPermissions, List<String> blacklistedPermissions) {
        this.id = id;
        this.displayInPreview = displayInPreview;
        this.displayName = displayName;
        this.displayItemMaterial = displayItemMaterial;
        this.playerSkinTexture = playerSkinTexture;
        this.displayAmount = displayAmount;
        this.lore = lore;
        this.chance = chance;
        this.fireworkOnWin = fireworkOnWin;
        this.glowingDisplayItem = glowingDisplayItem;
        this.hideItemFlags = hideItemFlags;
        this.commands = commands;
        this.messages = messages;
        this.requiredPermissions = requiredPermissions != null ? requiredPermissions : List.of();
        this.blacklistedPermissions = blacklistedPermissions != null ? blacklistedPermissions : List.of();
    }

    // Getters
    public String getId() {
        return id;
    }

    public boolean isDisplayInPreview() {
        return displayInPreview;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getDisplayItemMaterial() {
        return displayItemMaterial;
    }

    public String getPlayerSkinTexture() {
        return playerSkinTexture;
    }

    public int getDisplayAmount() {
        return displayAmount;
    }

    public List<String> getLore() {
        return lore;
    }

    public double getChance() {
        return chance;
    }

    public void setChance(double chance) { // Utilisé par CrateManager pour la normalisation si nécessaire
        this.chance = chance;
    }

    public boolean hasFireworkOnWin() {
        return fireworkOnWin;
    }

    public boolean isGlowingDisplayItem() {
        return glowingDisplayItem;
    }

    public boolean shoudHideItemFlags() {
        return hideItemFlags;
    }

    public List<String> getCommands() {
        return commands;
    }

    public List<String> getMessages() {
        return messages;
    }

    public List<String> getRequiredPermissions() {
        return requiredPermissions;
    }

    public List<String> getBlacklistedPermissions() {
        return blacklistedPermissions;
    }

    public ItemStack getDisplayItemStack() {
        return displayItemStack; // Sera initialisé par ItemBuilder
    }

    public void setDisplayItemStack(ItemStack displayItemStack) {
        this.displayItemStack = displayItemStack;
    }
}