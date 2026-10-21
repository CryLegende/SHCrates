package org.Cry_Legende.sHCrates.utils;

import com.mojang.authlib.GameProfile; // Peut être retiré si plus utilisé ailleurs
import com.mojang.authlib.properties.Property; // Peut être retiré si plus utilisé ailleurs

// Imports pour la méthode moderne (Paper API)
import org.bukkit.Bukkit; // Nécessaire pour Bukkit.createProfile
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import org.Cry_Legende.sHCrates.SHCrates;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

// import java.lang.reflect.Field; // N'est plus nécessaire pour setSkullTexture
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class ItemBuilder {

    private ItemStack itemStack;
    private ItemMeta itemMeta;

    public ItemBuilder(Material material) {
        this(material, 1);
    }

    public ItemBuilder(Material material, int amount) {
        this.itemStack = new ItemStack(material, amount);
        this.itemMeta = this.itemStack.getItemMeta();
    }

    public ItemBuilder(ItemStack itemStack) {
        this.itemStack = itemStack.clone();
        this.itemMeta = this.itemStack.getItemMeta();
    }

    public ItemBuilder setMaterial(Material material) {
        this.itemStack.setType(material);
        this.itemMeta = this.itemStack.getItemMeta(); // Récupérer la meta car le changement de matériel peut l'invalider
        return this;
    }

    public ItemBuilder setAmount(int amount) {
        this.itemStack.setAmount(amount);
        return this;
    }

    public ItemBuilder setName(String name) {
        if (this.itemMeta != null && name != null) {
            this.itemMeta.setDisplayName(ChatUtils.colorize(name));
        }
        return this;
    }

    public ItemBuilder setLore(List<String> lore) {
        if (this.itemMeta != null && lore != null && !lore.isEmpty()) {
            this.itemMeta.setLore(ChatUtils.colorize(lore));
        }
        return this;
    }

    public ItemBuilder setLore(String... lore) {
        if (this.itemMeta != null && lore != null && lore.length > 0) {
            this.itemMeta.setLore(ChatUtils.colorize(Arrays.asList(lore)));
        }
        return this;
    }

    public ItemBuilder addLoreLine(String line) {
        if (this.itemMeta != null && line != null) {
            List<String> currentLore = this.itemMeta.getLore();
            if (currentLore == null) {
                currentLore = new java.util.ArrayList<>();
            }
            currentLore.add(ChatUtils.colorize(line));
            this.itemMeta.setLore(currentLore);
        }
        return this;
    }

    public ItemBuilder clearLore() {
        if (this.itemMeta != null) {
            this.itemMeta.setLore(null);
        }
        return this;
    }

    public ItemBuilder addEnchant(Enchantment enchantment, int level, boolean ignoreRestrictions) {
        if (this.itemMeta != null) {
            this.itemMeta.addEnchant(enchantment, level, ignoreRestrictions);
        }
        return this;
    }

    public ItemBuilder removeEnchant(Enchantment enchantment) {
        if (this.itemMeta != null) {
            this.itemMeta.removeEnchant(enchantment);
        }
        return this;
    }

    public ItemBuilder setUnbreakable(boolean unbreakable) {
        if (this.itemMeta != null) {
            this.itemMeta.setUnbreakable(unbreakable);
        }
        return this;
    }

    public ItemBuilder addItemFlags(ItemFlag... flags) {
        if (this.itemMeta != null) {
            this.itemMeta.addItemFlags(flags);
        }
        return this;
    }

    public ItemBuilder removeItemFlags(ItemFlag... flags) {
        if (this.itemMeta != null) {
            this.itemMeta.removeItemFlags(flags);
        }
        return this;
    }

    public ItemBuilder hideAllFlags() {
        if (this.itemMeta != null) {
            this.itemMeta.addItemFlags(ItemFlag.values());
        }
        return this;
    }

    public ItemBuilder setCustomModelData(Integer data) {
        if (this.itemMeta != null && data != null && data != 0) {
            this.itemMeta.setCustomModelData(data);
        }
        return this;
    }

    public ItemBuilder setSkullOwner(OfflinePlayer owner) {
        // Assurez-vous que le matériel est PLAYER_HEAD pour que setOwningPlayer fonctionne.
        if (this.itemStack.getType() != Material.PLAYER_HEAD) {
            this.setMaterial(Material.PLAYER_HEAD);
        }
        // itemMeta peut avoir changé après setMaterial, donc vérifiez à nouveau.
        if (this.itemMeta instanceof SkullMeta && owner != null) {
            SkullMeta skullMeta = (SkullMeta) this.itemMeta;
            skullMeta.setOwningPlayer(owner);
        } else if (!(this.itemMeta instanceof SkullMeta)) {
            SHCrates.getInstance().getLogger().warning("Attempted to set skull owner, but ItemMeta is not SkullMeta for: " + this.itemStack.getType());
        }
        return this;
    }

    /**
     * Sets the skull texture using a Base64 encoded string using modern Paper API.
     *
     * @param base64Texture The Base64 encoded texture value.
     * @return This ItemBuilder instance.
     */
    public ItemBuilder setSkullTexture(String base64Texture) {
        // 1. Assurer que le Material est PLAYER_HEAD
        if (this.itemStack.getType() != Material.PLAYER_HEAD) {
            this.setMaterial(Material.PLAYER_HEAD); // setMaterial mettra à jour this.itemMeta
        }

        // 2. Vérifier que itemMeta est bien SkullMeta (devrait l'être après setMaterial(PLAYER_HEAD))
        if (!(this.itemMeta instanceof SkullMeta)) {
            // Si ce n'est toujours pas une SkullMeta, il y a un problème plus profond ou l'itemMeta n'a pas été mis à jour.
            // Tentative de récupération forcée.
            this.itemMeta = this.itemStack.getItemMeta();
            if (!(this.itemMeta instanceof SkullMeta)) {
                SHCrates.getInstance().getLogger().severe("Cannot set skull texture: ItemMeta is not SkullMeta even after setting Material to PLAYER_HEAD. Current type: " + this.itemStack.getType());
                return this; // Impossible de continuer
            }
        }

        SkullMeta skullMeta = (SkullMeta) this.itemMeta;

        if (base64Texture == null || base64Texture.isEmpty()) {
            // Si la texture est nulle ou vide, on pourrait vouloir effacer le profil existant.
            skullMeta.setPlayerProfile(null); // Efface toute skin/texture personnalisée précédente
            return this;
        }

        // 3. Créer et appliquer le PlayerProfile avec la texture
        // Bukkit.createProfile(uuid, name) -> name peut être null pour les têtes purement texturées.
        // Un UUID aléatoire est généralement suffisant pour les têtes qui ne représentent pas un joueur spécifique.
        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID()); // Le nom sera null par défaut

        // Nettoyer les anciennes propriétés de texture au cas où (bien que createProfile crée un nouveau profil)
        profile.getProperties().clear();

        // Ajouter la nouvelle propriété de texture
        // La signature (troisième argument de ProfileProperty) n'est généralement pas nécessaire pour l'affichage client des textures.
        profile.getProperties().add(new ProfileProperty("textures", base64Texture));

        // Appliquer le profil à la SkullMeta
        skullMeta.setPlayerProfile(profile);

        return this;
    }

    public ItemBuilder setLeatherArmorColor(Color color) {
        if (this.itemMeta instanceof LeatherArmorMeta && color != null) {
            LeatherArmorMeta leatherMeta = (LeatherArmorMeta) this.itemMeta;
            leatherMeta.setColor(color);
        }
        return this;
    }

    public ItemBuilder addPotionEffect(PotionEffect effect) {
        if (this.itemMeta instanceof PotionMeta && effect != null) {
            PotionMeta potionMeta = (PotionMeta) this.itemMeta;
            potionMeta.addCustomEffect(effect, true);
        }
        return this;
    }

    public ItemBuilder setPotionColor(Color color) {
        if (this.itemMeta instanceof PotionMeta && color != null) {
            PotionMeta potionMeta = (PotionMeta) this.itemMeta;
            potionMeta.setColor(color);
        }
        return this;
    }

    public <T, Z> ItemBuilder setPersistentData(NamespacedKey key, PersistentDataType<T, Z> type, Z value) {
        if (this.itemMeta != null && key != null && type != null && value != null) {
            this.itemMeta.getPersistentDataContainer().set(key, type, value);
        }
        return this;
    }

    public <T, Z> ItemBuilder setPersistentData(SHCrates plugin, String keyString, PersistentDataType<T, Z> type, Z value) {
        if (this.itemMeta != null && plugin != null && keyString != null && type != null && value != null) {
            NamespacedKey key = new NamespacedKey(plugin, keyString);
            this.itemMeta.getPersistentDataContainer().set(key, type, value);
        }
        return this;
    }


    public ItemStack build() {
        if (this.itemMeta != null) {
            this.itemStack.setItemMeta(this.itemMeta);
        }
        return this.itemStack;
    }
}
