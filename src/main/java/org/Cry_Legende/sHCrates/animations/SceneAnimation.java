package org.Cry_Legende.sHCrates.animations;

import org.Cry_Legende.sHCrates.SHCrates;
import org.Cry_Legende.sHCrates.crates.Crate;
import org.Cry_Legende.sHCrates.crates.Reward;
import org.Cry_Legende.sHCrates.scenes.Placement;
import org.Cry_Legende.sHCrates.scenes.Scene;
import org.Cry_Legende.sHCrates.scenes.SceneKind;
import org.Cry_Legende.sHCrates.scenes.SceneListener;
import org.Cry_Legende.sHCrates.scenes.SceneManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;

/**
 * Animation jouée dans le monde (COFFRE_CELESTE, MIROIR) : une {@link Scene} devant le joueur. Si elle ne trouve pas de
 * place correcte, ou pour un joueur Bedrock, l'ouverture prend l'animation de repli de la caisse (Animation.Repli).
 * Le lot est donné quand il sort de la scène (ou quand le joueur choisit dans le dialogue de relance).
 */
public class SceneAnimation implements Animation {

    private final SHCrates plugin;
    private final SceneKind kind;

    public SceneAnimation(SHCrates plugin, SceneKind kind) {
        this.plugin = plugin;
        this.kind = kind;
    }

    @Override
    public String getName() {
        return kind.type();
    }

    @Override
    public boolean usesGUI() {
        return false;
    }

    @Override
    public boolean isWorld() {
        return true;
    }

    @Override
    public boolean prepare(Player player, Crate crate, Location crateLocation) {
        SceneManager scenes = plugin.getSceneManager();
        if (scenes == null) return false;
        if (scenes.settings().bedrockFallback() && scenes.isBedrock(player)) return false;
        return scenes.prepare(player, kind) != null;
    }

    @Override
    public void play(Player player, Crate crate, Location crateLocation, Reward chosenReward, Runnable onAnimationFinish) {
        SceneManager scenes = plugin.getSceneManager();
        AnimationManager animations = plugin.getAnimationManager();
        Placement placement = scenes.placementFor(player, kind);
        // un menu ouvert (/box) se referme : la scène se joue dans le monde
        if (player.getOpenInventory().getType() != InventoryType.CRAFTING) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.getOpenInventory().getType() != InventoryType.CRAFTING
                    && !plugin.getGuiManager().isAnimationInventory(player.getOpenInventory().getTopInventory())) {
                    player.closeInventory();
                }
            });
        }
        if (placement == null) {
            // plus de place (relance) : le lot sort tout de suite
            if (onAnimationFinish != null) onAnimationFinish.run();
            animations.onSceneReveal(player, null);
            animations.onSceneEnd(player, null);
            return;
        }
        scenes.play(player, crate, chosenReward, placement, kind, false, new SceneListener() {
            @Override
            public void onReveal(Scene scene) {
                if (onAnimationFinish != null) onAnimationFinish.run();
                animations.onSceneReveal(player, scene);
            }

            @Override
            public void onEnd(Scene scene) {
                animations.onSceneEnd(player, scene);
            }

            @Override
            public void onAbandon(Scene scene, boolean wasRevealed) {
                if (!wasRevealed && onAnimationFinish != null) onAnimationFinish.run();
                animations.onSceneAbandon(player, scene, wasRevealed);
            }
        });
    }

    @Override
    public void stop(Player player, boolean giveReward) {
        SceneManager scenes = plugin.getSceneManager();
        if (scenes != null) scenes.abort(player);
    }
}
