package org.Cry_Legende.sHCrates.animations;

import org.Cry_Legende.sHCrates.SHCrates;
import org.Cry_Legende.sHCrates.crates.Crate;
import org.Cry_Legende.sHCrates.crates.Reward;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class NoneAnimation implements Animation {

    private final SHCrates plugin;

    public NoneAnimation(SHCrates plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "NONE";
    }

    @Override
    public boolean usesGUI() {
        return false;
    }

    @Override
    public void play(Player player, Crate crate, Location crateLocation, Reward chosenReward, Runnable onAnimationFinish) {
        // Pas d'effet visuel, on appelle directement le callback de fin.
        if (onAnimationFinish != null) {
            onAnimationFinish.run();
        }
    }

    @Override
    public void stop(Player player, boolean giveReward) {
        // Rien à stopper spécifiquement pour cette animation
    }
}