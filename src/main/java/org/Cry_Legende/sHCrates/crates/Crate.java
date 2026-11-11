package org.Cry_Legende.sHCrates.crates;

import org.Cry_Legende.sHCrates.crates.config.AnimationConfig;
import org.Cry_Legende.sHCrates.crates.config.PreviewGUIConfig;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
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
    private String logFile;

    /**
     * Jauge de garantie : après {@code threshold - 1} clés sans lot d'un des paliers visés, la clé suivante tire
     * seulement parmi ces paliers. Elle repart à zéro dès qu'un lot visé sort, garanti ou non.
     */
    public record Gauge(int threshold, Set<String> tierIds, String name) {
    }

    private Gauge gauge;
    // Palier minimal de la toute première clé ouverte par un joueur (null : pas de garantie)
    private String firstOpenMinTier;
    // Ouverture de toutes les clés d'un coup permise (/crate openall, Shift+clic sur une QuickCrate)
    private boolean openAllAllowed = true;
    // Message et son quand le joueur reçoit des clés de cette caisse
    private boolean announceReception = false;
    // Lots en rotation : un seul est gagnable chaque mois, à tour de rôle depuis rotationStart
    private List<String> rotation = Collections.emptyList();
    private YearMonth rotationStart = YearMonth.of(2026, 1);

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
        this.logFile = id;
    }

    public void addReward(Reward reward) {
        this.rewards.add(reward);
    }

    public void calculateTotalChanceWeight() {
        this.totalChanceWeight = rewards.stream().mapToDouble(Reward::getChance).sum();
    }

    public String getLogFile() {
        return logFile;
    }

    public void setLogFile(String logFile) {
        this.logFile = logFile;
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
            if (!isInCurrentRotation(reward)) {
                return false;
            }
            if (reward.getCondition() != null && !Conditions.test(player, reward.getCondition())) {
                return false;
            }
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

        return pick(eligibleRewards);
    }

    /** Tirage pondéré par Chance parmi les lots donnés (null si la liste est vide). */
    public static Reward pick(List<Reward> pool) {
        if (pool.isEmpty()) {
            return null;
        }
        double total = pool.stream().mapToDouble(Reward::getChance).sum();
        if (total <= 0) {
            return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        }
        double randomValue = ThreadLocalRandom.current().nextDouble() * total;
        double cumulativeChance = 0;
        for (Reward reward : pool) {
            cumulativeChance += reward.getChance();
            if (randomValue <= cumulativeChance) {
                return reward;
            }
        }
        return pool.get(pool.size() - 1);
    }

    // --- Jauge, première clé, ouverture multiple, rotation ---

    public Gauge getGauge() {
        return gauge;
    }

    public void setGauge(Gauge gauge) {
        this.gauge = gauge;
    }

    public String getFirstOpenMinTier() {
        return firstOpenMinTier;
    }

    public void setFirstOpenMinTier(String firstOpenMinTier) {
        this.firstOpenMinTier = firstOpenMinTier;
    }

    public boolean isOpenAllAllowed() {
        return openAllAllowed;
    }

    public void setOpenAllAllowed(boolean openAllAllowed) {
        this.openAllAllowed = openAllAllowed;
    }

    public boolean isAnnounceReception() {
        return announceReception;
    }

    public void setAnnounceReception(boolean announceReception) {
        this.announceReception = announceReception;
    }

    public List<String> getRotation() {
        return rotation;
    }

    public void setRotation(List<String> rotation, YearMonth start) {
        this.rotation = rotation == null ? Collections.emptyList() : List.copyOf(rotation);
        if (start != null) this.rotationStart = start;
    }

    /** Identifiant du lot en rotation ce mois-ci, ou null sans rotation. */
    public String getCurrentRotationId() {
        if (rotation.isEmpty()) {
            return null;
        }
        long months = ChronoUnit.MONTHS.between(rotationStart, YearMonth.now());
        int index = (int) Math.floorMod(months, (long) rotation.size());
        return rotation.get(index);
    }

    /** Dernier jour où le lot en rotation actuel reste gagnable. */
    public LocalDate getRotationEnd() {
        return YearMonth.now().atEndOfMonth();
    }

    /** Faux pour un lot en rotation qui n'est pas celui du mois. */
    public boolean isInCurrentRotation(Reward reward) {
        if (rotation.isEmpty() || !rotation.contains(reward.getId())) {
            return true;
        }
        return reward.getId().equals(getCurrentRotationId());
    }
}
