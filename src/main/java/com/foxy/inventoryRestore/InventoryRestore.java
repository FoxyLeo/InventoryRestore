package com.foxy.inventoryRestore;

import com.foxy.inventoryRestore.bootstrap.PluginBootstrap;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

public final class InventoryRestore extends JavaPlugin {

    private static final int BSTATS_PLUGIN_ID = 24815;
    private PluginBootstrap bootstrap;

    @Override
    public void onEnable() {
        String pluginName = getDescription().getName();
        String version = getDescription().getVersion();
        String author = getDescription().getAuthors().isEmpty()
                ? "Unknown"
                : String.join(", ", getDescription().getAuthors());

        String line = "----------------------------------------------";
        getServer().getConsoleSender().sendMessage(line);
        getServer().getConsoleSender().sendMessage("[" + pluginName + "] Version: " + version);
        getServer().getConsoleSender().sendMessage("[" + pluginName + "] Author: " + author);
        getServer().getConsoleSender().sendMessage("[" + pluginName + "] Enjoy the plugin! :)");
        getServer().getConsoleSender().sendMessage(line);

        bootstrap = new PluginBootstrap(this);
        bootstrap.enable();

        new Metrics(this, BSTATS_PLUGIN_ID);
    }

    @Override
    public void onDisable() {
        if (bootstrap != null) {
            bootstrap.disable();
        }
    }
}
