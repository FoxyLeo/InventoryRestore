package com.foxy.inventoryRestore.util;

import org.bukkit.Location;
import org.bukkit.World;

public final class LocationFormats {

    private static final String UNKNOWN = "Unknown";

    private LocationFormats() {
    }

    public static String worldName(Location location) {
        if (location == null) {
            return UNKNOWN;
        }
        World world = location.getWorld();
        return world != null ? world.getName() : UNKNOWN;
    }

    public static String coordinates(Location location) {
        if (location == null) {
            return UNKNOWN;
        }
        return location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
    }

    public static String sanitize(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
    }
}
