package com.foxy.inventoryRestore.inventory.menu;

import com.foxy.inventoryRestore.InventoryRestore;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public final class MenuConfiguration {

    private static final int DEFAULT_MAIN_MENU_SIZE = 18;
    private static final int DEFAULT_DEATH_MENU_SIZE = 54;
    private static final String MENU_FILE = "invconfig.yml";

    private final InventoryRestore plugin;
    private FileConfiguration configuration;

    public MenuConfiguration(InventoryRestore plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Unable to create plugin data folder: " + dataFolder.getAbsolutePath());
        }

        File file = new File(dataFolder, MENU_FILE);
        if (!file.exists()) {
            plugin.saveResource(MENU_FILE, false);
        }

        configuration = YamlConfiguration.loadConfiguration(file);
    }

    public int getMainMenuSize() {
        ensureConfiguration();
        return configuration.getInt("menus.main.size", DEFAULT_MAIN_MENU_SIZE);
    }

    public MenuItem getMainMenuItem(String key, int defaultSlot, Material defaultMaterial) {
        ensureConfiguration();
        String path = "menus.main.items." + key;
        int slot = configuration.getInt(path + ".slot", defaultSlot);
        Material material = getMaterial(path + ".material", defaultMaterial);
        return new MenuItem(slot, material);
    }

    public int getDeathListSize() {
        ensureConfiguration();
        return configuration.getInt("menus.death-list.size", DEFAULT_DEATH_MENU_SIZE);
    }

    public Material getDeathListRecordMaterial(Material defaultMaterial) {
        ensureConfiguration();
        return getMaterial("menus.death-list.record-item.material", defaultMaterial);
    }

    public MenuItem getDeathListNavigationItem(String key, int defaultSlot, Material defaultMaterial) {
        ensureConfiguration();
        String path = "menus.death-list.navigation." + key;
        int slot = configuration.getInt(path + ".slot", defaultSlot);
        Material material = getMaterial(path + ".material", defaultMaterial);
        return new MenuItem(slot, material);
    }

    public List<Integer> getDeathListSlots() {
        ensureConfiguration();
        List<Integer> configured = configuration.getIntegerList("menus.death-list.slots");
        if (!configured.isEmpty()) {
            return Collections.unmodifiableList(new ArrayList<>(configured));
        }

        int startSlot = Math.max(0, configuration.getInt("menus.death-list.start-slot", 0));
        int size = getDeathListSize();
        List<Integer> defaults = new ArrayList<>(Math.max(0, size - startSlot));
        for (int i = startSlot; i < size; i++) {
            defaults.add(i);
        }
        if (defaults.isEmpty()) {
            for (int i = 0; i < size; i++) {
                defaults.add(i);
            }
        }
        return Collections.unmodifiableList(defaults);
    }

    public int getDeathDetailSize() {
        ensureConfiguration();
        return configuration.getInt("menus.death-detail.size", DEFAULT_DEATH_MENU_SIZE);
    }

    public MenuItem getDeathDetailAction(String key, int defaultSlot, Material defaultMaterial) {
        ensureConfiguration();
        String path = "menus.death-detail.actions." + key;
        int slot = configuration.getInt(path + ".slot", defaultSlot);
        Material material = getMaterial(path + ".material", defaultMaterial);
        return new MenuItem(slot, material);
    }

    public int getEquipmentSlot(String key, int defaultSlot) {
        ensureConfiguration();
        return configuration.getInt("menus.death-detail.equipment-slots." + key, defaultSlot);
    }

    public List<Integer> getContentSlots() {
        ensureConfiguration();
        List<Integer> rawSlots = configuration.getIntegerList("menus.death-detail.content-slots");
        if (rawSlots.isEmpty()) {
            List<Integer> defaults = new ArrayList<>(36);
            for (int i = 0; i < 36; i++) {
                defaults.add(i);
            }
            return Collections.unmodifiableList(defaults);
        }
        return Collections.unmodifiableList(new ArrayList<>(rawSlots));
    }

    private Material getMaterial(String path, Material fallback) {
        ensureConfiguration();
        String configured = configuration.getString(path);
        if (configured == null || configured.isEmpty()) {
            return fallback;
        }

        Material material = Material.matchMaterial(configured, false);
        if (material != null) {
            return material;
        }

        Logger logger = plugin.getLogger();
        logger.warning("Invalid material '" + configured + "' at path '" + path + "'. Using " + fallback + " instead.");
        return fallback;
    }

    private void ensureConfiguration() {
        if (configuration == null) {
            throw new IllegalStateException("Menu configuration has not been loaded yet.");
        }
    }

    public record MenuItem(int slot, Material material) {
    }
}

