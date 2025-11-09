package com.foxy.inventoryRestore.inventory;

import com.foxy.inventoryRestore.InventoryRestore;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class InventoryLayoutService {

    private static final String INVENTORY_FILE = "inventories.yml";

    private final InventoryRestore plugin;

    private FileConfiguration configuration;

    public InventoryLayoutService(InventoryRestore plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Unable to create plugin data folder: " + dataFolder.getAbsolutePath());
        }

        File inventoryFile = new File(dataFolder, INVENTORY_FILE);
        if (!inventoryFile.exists()) {
            plugin.saveResource(INVENTORY_FILE, false);
        }

        configuration = YamlConfiguration.loadConfiguration(inventoryFile);
    }

    public String format(String path, Map<String, String> placeholders) {
        ensureConfiguration();

        String raw = configuration.getString(path);
        if (raw == null) {
            return "";
        }

        return applyFormatting(raw, placeholders);
    }

    public List<String> formatList(String path, Map<String, String> placeholders) {
        ensureConfiguration();

        List<String> lines = configuration.getStringList(path);
        if (lines.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> formatted = new ArrayList<>(lines.size());
        for (String line : lines) {
            formatted.add(applyFormatting(line, placeholders));
        }
        return formatted;
    }

    public String getString(String path, String def) {
        ensureConfiguration();
        return configuration.getString(path, def);
    }

    private void ensureConfiguration() {
        if (configuration == null) {
            throw new IllegalStateException("Inventory layout configuration has not been loaded yet.");
        }
    }

    private String applyFormatting(String message, Map<String, String> placeholders) {
        String formatted = message;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                String lowerKey = entry.getKey().toLowerCase(Locale.ENGLISH);
                String value = entry.getValue();
                formatted = formatted.replace("%" + lowerKey + "%", value);
                formatted = formatted.replace("{" + lowerKey + "}", value);
            }
        }
        return ChatColor.translateAlternateColorCodes('&', formatted);
    }
}

