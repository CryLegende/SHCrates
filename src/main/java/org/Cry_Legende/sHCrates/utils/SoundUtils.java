package org.Cry_Legende.sHCrates.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class SoundUtils {

    // Les sons des animations sont relus à chaque cran : on garde la résolution en mémoire
    private static final Map<String, Optional<Sound>> CACHE = new ConcurrentHashMap<>();

    private SoundUtils() {
    }

    /**
     * Son lu en config : ancien nom (ENTITY_PLAYER_LEVELUP) ou clé (minecraft:entity.player.levelup).
     *
     * @return le son, ou null s'il est inconnu.
     */
    public static Sound sound(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return CACHE.computeIfAbsent(raw, SoundUtils::resolve).orElse(null);
    }

    private static Optional<Sound> resolve(String raw) {
        String s = raw.trim();
        if (s.indexOf(':') >= 0 || s.indexOf('.') >= 0) {
            NamespacedKey key = NamespacedKey.fromString(s.toLowerCase(Locale.ROOT));
            return Optional.ofNullable(key == null ? null : Registry.SOUND_EVENT.get(key));
        }
        try {
            return Optional.ofNullable((Sound) Sound.class.getField(s.toUpperCase(Locale.ROOT)).get(null));
        } catch (ReflectiveOperationException | ClassCastException e) {
            return Optional.empty();
        }
    }
}
