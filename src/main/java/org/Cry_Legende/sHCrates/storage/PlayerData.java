package org.Cry_Legende.sHCrates.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Clés virtuelles d'un joueur et compteurs de progression (jauge, ouvertures, première clé, meilleur palier).
 * Les compteurs sont rangés dans la même table que les clés, sous un identifiant qui commence par
 * {@link #COUNTER_PREFIX} : en mode réseau, ils suivent donc le joueur d'un serveur à l'autre comme les clés.
 * Les méthodes sont synchronisées : en mode réseau, le fil principal et le fil d'écriture vers la base y accèdent tous les deux.
 */
public class PlayerData {

    /** Préfixe des compteurs dans la table des clés : aucun identifiant de caisse ne commence ainsi. */
    public static final String COUNTER_PREFIX = "#";

    private final UUID playerUUID;
    // Key: Crate InternalID, Value: Amount of virtual keys
    private final Map<String, Integer> virtualKeys;
    // Faux seulement en mode réseau, tant que les clés de la base ne sont pas chargées
    private volatile boolean ready = true;

    public PlayerData(UUID playerUUID) {
        this.playerUUID = playerUUID;
        this.virtualKeys = new HashMap<>();
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    /** Vrai quand les clés sont connues (toujours le cas sur un serveur seul). */
    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public synchronized int getVirtualKeys(String crateInternalId) {
        return virtualKeys.getOrDefault(crateInternalId.toLowerCase(), 0);
    }

    public synchronized void setVirtualKeys(String crateInternalId, int amount) {
        if (amount <= 0) {
            virtualKeys.remove(crateInternalId.toLowerCase());
        } else {
            virtualKeys.put(crateInternalId.toLowerCase(), amount);
        }
    }

    public synchronized void addVirtualKeys(String crateInternalId, int amount) {
        setVirtualKeys(crateInternalId, getVirtualKeys(crateInternalId) + amount);
    }

    /**
     * Tries to consume a virtual key for the given crate.
     *
     * @param crateInternalId The internal ID of the crate.
     * @return true if a key was consumed, false otherwise.
     */
    public synchronized boolean consumeVirtualKey(String crateInternalId) {
        int currentKeys = getVirtualKeys(crateInternalId);
        if (currentKeys > 0) {
            setVirtualKeys(crateInternalId, currentKeys - 1);
            return true;
        }
        return false;
    }

    /** Clés virtuelles seulement, sans les compteurs. */
    public synchronized Map<String, Integer> getAllVirtualKeys() {
        Map<String, Integer> keys = new HashMap<>();
        virtualKeys.forEach((id, amount) -> {
            if (!id.startsWith(COUNTER_PREFIX)) keys.put(id, amount);
        });
        return keys;
    }

    /** Compteurs de progression, sans le préfixe. */
    public synchronized Map<String, Integer> getAllCounters() {
        Map<String, Integer> counters = new HashMap<>();
        virtualKeys.forEach((id, amount) -> {
            if (id.startsWith(COUNTER_PREFIX)) counters.put(id.substring(COUNTER_PREFIX.length()), amount);
        });
        return counters;
    }

    public synchronized int getCounter(String counterId) {
        return getVirtualKeys(COUNTER_PREFIX + counterId);
    }

    public synchronized void setCounter(String counterId, int value) {
        setVirtualKeys(COUNTER_PREFIX + counterId, value);
    }

    public synchronized void loadFromMap(Map<String, Integer> keysMap) {
        this.virtualKeys.clear();
        if (keysMap != null) {
            keysMap.forEach((crateId, amount) -> this.virtualKeys.put(crateId.toLowerCase(), amount));
        }
    }
}
