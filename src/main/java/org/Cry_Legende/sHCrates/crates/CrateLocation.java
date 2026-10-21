package org.Cry_Legende.sHCrates.crates;

import org.bukkit.Location;

import java.util.Objects;

public class CrateLocation {
    private final Location location; // Doit être un BlockLocation (pas de pitch/yaw)
    private final String crateId; // ID du type de crate (nom du fichier .yml)

    public CrateLocation(Location location, String crateId) {
        // Normalize location to block location
        this.location = new Location(location.getWorld(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ());
        this.crateId = crateId.toLowerCase();
    }

    public Location getLocation() {
        return location;
    }

    public String getCrateId() {
        return crateId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CrateLocation that = (CrateLocation) o;
        return Objects.equals(location, that.location) && Objects.equals(crateId, that.crateId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(location, crateId);
    }
}
