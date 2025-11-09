package com.foxy.inventoryRestore.message;

import com.foxy.inventoryRestore.InventoryRestore;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MessageService {

    private static final String DEFAULT_LANGUAGE = "en";
    private static final String PREFIX_PATH = "prefix";
    private static final String MESSAGE_ROOT = "messages.";

    private final InventoryRestore plugin;

    private FileConfiguration configuration;
    private String prefix;
    private String language;

    public MessageService(InventoryRestore plugin) {
        this.plugin = plugin;
    }

    public void load(String language) {
        this.language = normalizeLanguage(language);
        loadConfiguration();
    }

    public void reload() {
        loadConfiguration();
    }

    private void loadConfiguration() {
        if (language == null || language.isBlank()) {
            language = DEFAULT_LANGUAGE;
        }

        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Unable to create plugin data folder: " + dataFolder.getAbsolutePath());
        }

        File messagesFolder = new File(dataFolder, "messages");
        if (!messagesFolder.exists() && !messagesFolder.mkdirs()) {
            throw new IllegalStateException("Unable to create messages folder: " + messagesFolder.getAbsolutePath());
        }

        File languageFile = resolveLanguageFile(messagesFolder);
        configuration = YamlConfiguration.loadConfiguration(languageFile);
        String rawPrefix = configuration.getString(PREFIX_PATH, "");
        prefix = ChatColor.translateAlternateColorCodes('&', rawPrefix == null ? "" : rawPrefix);
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Collections.emptyMap());
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(formatMessage(key, placeholders, true));
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders, boolean includePrefix) {
        sender.sendMessage(formatMessage(key, placeholders, includePrefix));
    }

    public void sendList(CommandSender sender, String key) {
        sendList(sender, key, Collections.emptyMap(), true);
    }

    public void sendList(CommandSender sender, String key, Map<String, String> placeholders, boolean includePrefix) {
        for (String line : formatList(key, placeholders, includePrefix)) {
            sender.sendMessage(line);
        }
    }

    public List<String> formatList(String key, Map<String, String> placeholders, boolean includePrefix) {
        ensureConfiguration();

        List<String> lines = configuration.getStringList(resolvePath(key));
        if (lines.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> formatted = new ArrayList<>(lines.size());
        for (String line : lines) {
            formatted.add(applyFormatting(line, placeholders, includePrefix));
        }
        return formatted;
    }

    public String formatMessage(String key, Map<String, String> placeholders, boolean includePrefix) {
        ensureConfiguration();

        String raw = configuration.getString(resolvePath(key));
        if (raw == null) {
            String missing = prefix.isEmpty()
                    ? ChatColor.RED + "Missing message: " + key
                    : prefix + ChatColor.RED + " Missing message: " + key;
            return missing;
        }

        return applyFormatting(raw, placeholders, includePrefix);
    }

    public String getPrefix() {
        ensureConfiguration();
        return prefix;
    }

    private String applyFormatting(String message, Map<String, String> placeholders, boolean includePrefix) {
        Map<String, String> replacements = new HashMap<>();
        if (placeholders != null) {
            replacements.putAll(placeholders);
        }
        replacements.putIfAbsent("prefix", prefix);

        String formatted = message;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            String lowerKey = entry.getKey().toLowerCase(Locale.ENGLISH);
            String value = entry.getValue();
            formatted = formatted.replace("%" + lowerKey + "%", value);
            formatted = formatted.replace("{" + lowerKey + "}", value);
        }

        if (includePrefix && !prefix.isEmpty() && !formatted.startsWith(prefix)) {
            formatted = prefix + " " + formatted;
        }

        return ChatColor.translateAlternateColorCodes('&', formatted);
    }

    private void ensureConfiguration() {
        if (configuration == null) {
            throw new IllegalStateException("Message configuration has not been loaded yet.");
        }
    }

    private String resolvePath(String key) {
        return MESSAGE_ROOT + key;
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return DEFAULT_LANGUAGE;
        }
        return language.trim().toLowerCase(Locale.ENGLISH);
    }

    private File resolveLanguageFile(File messagesFolder) {
        String fileName = language + ".yml";
        File languageFile = new File(messagesFolder, fileName);
        if (!languageFile.exists()) {
            String resourcePath = "messages/" + fileName;
            if (plugin.getResource(resourcePath) != null) {
                plugin.saveResource(resourcePath, false);
            } else {
                language = DEFAULT_LANGUAGE;
                File defaultFile = new File(messagesFolder, DEFAULT_LANGUAGE + ".yml");
                if (!defaultFile.exists()) {
                    plugin.saveResource("messages/" + DEFAULT_LANGUAGE + ".yml", false);
                }
                return defaultFile;
            }
        }
        return languageFile;
    }
}

