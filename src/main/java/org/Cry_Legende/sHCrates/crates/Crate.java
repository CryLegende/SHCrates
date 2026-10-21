package org.Cry_Legende.sHCrates.crates;

import org.Cry_Legende.sHCrates.crates.config.AnimationConfig;
import org.Cry_Legende.sHCrates.crates.config.PreviewGUIConfig;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

public class Crate {

    private final String id;
    private String internalID;
    private final String displayName;
    private final Material blockType;
    private final CrateKey keyConfig;
    private final PreviewGUIConfig previewGUIConfig;
    private final AnimationConfig animationConfig;
    private final List<Reward> rewards;
    private double totalChanceWeight;
    private ItemStack physicalKeyItem;

    public Crate(String id, String displayName, Material blockType, CrateKey keyConfig,
                 PreviewGUIConfig previewGUIConfig, AnimationConfig animationConfig) {
        this.id = id;
        this.displayName = displayName;
        this.blockType = blockType;
        this.keyConfig = keyConfig;
        this.previewGUIConfig = previewGUIConfig;
        this.animationConfig = animationConfig;
        this.rewards = new ArrayList<>();
        this.internalID = UUID.randomUUID().toString();
    }

    public void addReward(Reward reward) {
        this.rewards.add(reward);
    }

    public void calculateTotalChanceWeight() {
        this.totalChanceWeight = rewards.stream().mapToDouble(Reward::getChance).sum();
    }

    public String getId() {
        return id;
    }

    public String getInternalID() {
        return internalID;
    }

    public void setInternalID(String internalID) {
        this.internalID = internalID;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getBlockType() {
        return blockType;
    }

    public CrateKey getKeyConfig() {
        return keyConfig;
    }

    public PreviewGUIConfig getPreviewGUIConfig() {
        return previewGUIConfig;
    }

    public AnimationConfig getAnimationConfig() {
        return animationConfig;
    }

    public List<Reward> getRewards() {
        return rewards;
    }

    public double getTotalChanceWeight() {
        return totalChanceWeight;
    }

    public ItemStack getPhysicalKeyItem() {
        return physicalKeyItem;
    }

    public void setPhysicalKeyItem(ItemStack physicalKeyItem) {
        this.physicalKeyItem = physicalKeyItem;
    }

    /**
     * Renvoie une liste de récompenses que le joueur a la permission de gagner.
     *
     * @param player Le joueur pour lequel vérifier les permissions.
     * @return Une liste filtrée de récompenses.
     */
    public List<Reward> getEligibleRewards(Player player) {
        if (rewards.isEmpty()) {
            return new ArrayList<>();
        }

        return rewards.stream().filter(reward -> {
            if (!reward.getBlacklistedPermissions().isEmpty()) {
                for (String perm : reward.getBlacklistedPermissions()) {
                    if (player.hasPermission(perm)) {
                        return false;
                    }
                }
            }
            if (!reward.getRequiredPermissions().isEmpty()) {
                for (String perm : reward.getRequiredPermissions()) {
                    if (player.hasPermission(perm)) {
                        return true;
                    }
                }
                return false;
            }
            return true;
        }).collect(Collectors.toList());
    }

    /**
     * Sélectionne une récompense aléatoire parmi celles que le joueur est autorisé à gagner.
     *
     * @param player Le joueur.
     * @return Une récompense choisie au hasard, ou null si aucune n'est éligible.
     */
    public Reward getRandomReward(Player player) {
        List<Reward> eligibleRewards = getEligibleRewards(player);
        if (eligibleRewards.isEmpty()) {
            return null;
        }

        double currentTotalChance = eligibleRewards.stream().mapToDouble(Reward::getChance).sum();
        if (currentTotalChance <= 0) {
            // S'il n'y a pas de chance, retourner une récompense au hasard dans la liste éligible
            return eligibleRewards.get(new Random().nextInt(eligibleRewards.size()));
        }

        double randomValue = Math.random() * currentTotalChance;
        double cumulativeChance = 0;

        for (Reward reward : eligibleRewards) {
            cumulativeChance += reward.getChance();
            if (randomValue <= cumulativeChance) {
                return reward;
            }
        }

        return eligibleRewards.get(eligibleRewards.size() - 1);
    }
}
