package org.Cry_Legende.sHCrates.listeners;

import org.Cry_Legende.sHCrates.SHCrates;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * Mondes chargés après onEnable (PhantomWorlds) : leurs caisses, gardées en mémoire par nom de monde,
 * deviennent actives à ce moment-là.
 */
public class WorldLoadListener implements Listener {

    private final SHCrates plugin;

    public WorldLoadListener(SHCrates plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        plugin.getCrateManager().onWorldLoaded(event.getWorld());
    }
}
