package org.Cry_Legende.sHCrates.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerData {

    private final UUID playerUUID;
    // Key: Crate InternalID, Value: Amount of virtual keys
    private final Map<String, Integer> virtualKeys;

    public PlayerData(UUID playerUUID) {
        this.playerUUID = playerUUID;
        this.virtualKeys = new HashMap<>();
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public int getVirtualKeys(String crateInternalId) {
        return virtualKeys.getOrDefault(crateInternalId.toLowerCase(), 0);
    }

    public void setVirtualKeys(String crateInternalId, int amount) {
        if (amount <= 0) {
            virtualKeys.remove(crateInternalId.toLowerCase());
        } else {
            virtualKeys.put(crateInternalId.toLowerCase(), amount);
        }
    }

    public void addVirtualKeys(String crateInternalId, int amount) {
        setVirtualKeys(crateInternalId, getVirtualKeys(crateInternalId) + amount);
    }

    /**
     * Tries to consume a virtual key for the given crate.
     *
     * @param crateInternalId The internal ID of the crate.
     * @return true if a key was consumed, false otherwise.
     */
    public boolean consumeVirtualKey(String crateInternalId) {
        int currentKeys = getVirtualKeys(crateInternalId);
        if (currentKeys > 0) {
            setVirtualKeys(crateInternalId, currentKeys - 1);
            return true;
        }
        return false;
    }

    public Map<String, Integer> getAllVirtualKeys() {
        return new HashMap<>(virtualKeys); // Return a copy to prevent external modification
    }

    public void loadFromMap(Map<String, Integer> keysMap) {
        this.virtualKeys.clear();
        if (keysMap != null) {
            keysMap.forEach((crateId, amount) -> this.virtualKeys.put(crateId.toLowerCase(), amount));
        }
    }
}
