package org.Cry_Legende.sHCrates.config;

import org.Cry_Legende.sHCrates.SHCrates;
import org.Cry_Legende.sHCrates.utils.ChatUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class MessageManager {

    private final SHCrates plugin;
    private File messagesFile;
    private FileConfiguration messagesConfig;

    private String prefix;

    public MessageManager(SHCrates plugin) {
        this.plugin = plugin;
        setup();
    }

    private void setup() {
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public void loadMessages() {
        if (!messagesFile.exists()) {
            plugin.getLogger().info("messages.yml not found, creating a new one...");
            plugin.saveResource("messages.yml", true); // Force overwrite to ensure defaults
        } else {
            plugin.getLogger().info("Loading messages.yml...");
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        prefix = messagesConfig.getString("prefix", "&f&l« &d&lSH &f&l» &f");
    }

    public void reloadMessages() {
        if (messagesFile == null) {
            messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        loadMessages(); // Recharge les valeurs
        plugin.getLogger().info("messages.yml has been reloaded.");
    }

    public String getMessage(String key) {
        String message = messagesConfig.getString(key, "&cMessage not found: " + key);
        if (message.isEmpty()) return ""; // Allow disabling messages by setting them to ""
        return ChatUtils.colorize(prefix + message);
    }

    public String getRawMessage(String key) {
        String message = messagesConfig.getString(key, "&cMessage not found: " + key);
        if (message.isEmpty()) return "";
        return ChatUtils.colorize(message); // No prefix
    }

    public String getPrefixedMessage(String key, Map<String, String> placeholders) {
        String message = messagesConfig.getString(key, "&cMessage not found: " + key);
        if (message.isEmpty()) return "";

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace(entry.getKey(), entry.getValue());
        }
        return ChatUtils.colorize(prefix + message);
    }

    public String getRawPrefixedMessage(String key, Map<String, String> placeholders) {
        String message = messagesConfig.getString(key, "&cMessage not found: " + key);
        if (message.isEmpty()) return "";

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace(entry.getKey(), entry.getValue());
        }
        return ChatUtils.colorize(message);
    }

    public List<String> getMessageList(String key, Map<String, String> placeholders) {
        List<String> messages = messagesConfig.getStringList(key);
        if (messages.isEmpty()) return List.of(ChatUtils.colorize(prefix + "&cMessage list not found: " + key));

        return messages.stream()
            .map(line -> {
                String tempLine = line;
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    tempLine = tempLine.replace(entry.getKey(), entry.getValue());
                }
                return ChatUtils.colorize(tempLine); // Prefix is often part of the list lines themselves
            })
            .collect(Collectors.toList());
    }

    public void sendMessage(CommandSender sender, String key) {
        String message = getMessage(key);
        if (!message.isEmpty()) {
            sender.sendMessage(message);
        }
    }

    public void sendMessage(CommandSender sender, String key, Map<String, String> placeholders) {
        String message = getPrefixedMessage(key, placeholders);
        if (!message.isEmpty()) {
            sender.sendMessage(message);
        }
    }

    public void sendRawMessage(CommandSender sender, String key, Map<String, String> placeholders) {
        String message = getRawPrefixedMessage(key, placeholders);
        if (!message.isEmpty()) {
            sender.sendMessage(message);
        }
    }

    // Helper pour créer rapidement une map de placeholders
    public static Map<String, String> ph(String... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("Must provide an even number of arguments for key-value pairs.");
        }
        Map<String, String> placeholders = new HashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            placeholders.put(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return placeholders;
    }
}
