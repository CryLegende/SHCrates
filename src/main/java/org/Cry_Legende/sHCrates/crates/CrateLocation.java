package org.Cry_Legende.sHCrates.crates;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Locale;
import java.util.Objects;

public class CrateLocation {
    // Emplacement gardé par nom de monde : il reste valable même si le monde n'est pas encore chargé
    // (mondes PhantomWorlds chargés après onEnable) ou s'il est déchargé puis rechargé.
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;
    private final String crateId; // ID du type de crate (nom du fichier .yml)

    public CrateLocation(Location location, String crateId) {
        this(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ(), crateId);
    }

    public CrateLocation(String worldName, int x, int y, int z, String crateId) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.crateId = crateId.toLowerCase();
    }

    /**
     * @return l'emplacement du bloc, ou null si son monde n'est pas chargé.
     */
    public Location getLocation() {
        World world = Bukkit.getWorld(worldName);
        return world == null ? null : new Location(world, x, y, z);
    }

    public String getWorldName() {
        return worldName;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public String getCrateId() {
        return crateId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CrateLocation that)) return false;
        return x == that.x && y == that.y && z == that.z
            && worldName.equalsIgnoreCase(that.worldName) && Objects.equals(crateId, that.crateId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldName.toLowerCase(Locale.ROOT), x, y, z, crateId);
    }
}
