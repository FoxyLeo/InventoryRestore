package com.foxy.inventoryRestore.bootstrap;

import com.foxy.inventoryRestore.InventoryRestore;
import com.foxy.inventoryRestore.command.InventoryRestoreCommand;
import com.foxy.inventoryRestore.database.DatabaseManager;
import com.foxy.inventoryRestore.database.DeathInventoryRepository;
import com.foxy.inventoryRestore.database.PendingInventoryRepository;
import com.foxy.inventoryRestore.database.PlayerSnapshotRepository;
import com.foxy.inventoryRestore.database.TeleportRepository;
import com.foxy.inventoryRestore.database.WorldChangeRepository;
import com.foxy.inventoryRestore.inventory.InventoryLayoutService;
import com.foxy.inventoryRestore.inventory.menu.InventoryRecordType;
import com.foxy.inventoryRestore.inventory.menu.MenuConfiguration;
import com.foxy.inventoryRestore.inventory.menu.RestoreMenuManager;
import com.foxy.inventoryRestore.inventory.view.InventoryViewManager;
import com.foxy.inventoryRestore.listener.ConnectionListener;
import com.foxy.inventoryRestore.listener.DeathListener;
import com.foxy.inventoryRestore.listener.TeleportListener;
import com.foxy.inventoryRestore.listener.WorldChangeListener;
import com.foxy.inventoryRestore.message.MessageService;
import com.foxy.inventoryRestore.util.AsyncTaskQueue;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;

import java.time.LocalDateTime;
import java.util.logging.Logger;

public final class PluginBootstrap {

    private final InventoryRestore plugin;
    private DatabaseManager databaseManager;
    private MessageService messageService;
    private InventoryLayoutService inventoryLayoutService;
    private MenuConfiguration menuConfiguration;
    private DeathInventoryRepository deathRepository;
    private PendingInventoryRepository pendingRepository;
    private PlayerSnapshotRepository snapshotRepository;
    private WorldChangeRepository worldRepository;
    private TeleportRepository teleportRepository;
    private RestoreMenuManager menuManager;
    private InventoryViewManager viewManager;
    private InventoryRestoreCommand commandExecutor;
    private AsyncTaskQueue asyncTaskQueue;

    public PluginBootstrap(InventoryRestore plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        String language = plugin.getConfig().getString("lang");

        messageService = new MessageService(plugin);
        messageService.load(language);

        inventoryLayoutService = new InventoryLayoutService(plugin);
        inventoryLayoutService.load();

        menuConfiguration = new MenuConfiguration(plugin);
        menuConfiguration.load();

        databaseManager = new DatabaseManager(plugin);
        databaseManager.initialize();

        asyncTaskQueue = new AsyncTaskQueue(plugin.getLogger(), plugin.getDescription().getName() + "-Storage");

        deathRepository = new DeathInventoryRepository(databaseManager);
        pendingRepository = new PendingInventoryRepository(databaseManager);
        snapshotRepository = new PlayerSnapshotRepository(databaseManager);
        worldRepository = new WorldChangeRepository(databaseManager);
        teleportRepository = new TeleportRepository(databaseManager);

        Bukkit.getPluginManager().registerEvents(new DeathListener(deathRepository, asyncTaskQueue), plugin);
        Bukkit.getPluginManager().registerEvents(new ConnectionListener(snapshotRepository, pendingRepository, asyncTaskQueue), plugin);
        Bukkit.getPluginManager().registerEvents(new WorldChangeListener(worldRepository, asyncTaskQueue), plugin);
        Bukkit.getPluginManager().registerEvents(new TeleportListener(teleportRepository, asyncTaskQueue), plugin);

        menuManager = new RestoreMenuManager(plugin, messageService, inventoryLayoutService, menuConfiguration, deathRepository, snapshotRepository, worldRepository, teleportRepository);
        Bukkit.getPluginManager().registerEvents(menuManager, plugin);

        viewManager = new InventoryViewManager(plugin, messageService, menuConfiguration, snapshotRepository, pendingRepository);
        Bukkit.getPluginManager().registerEvents(viewManager, plugin);

        commandExecutor = new InventoryRestoreCommand(messageService, deathRepository, snapshotRepository, worldRepository, teleportRepository, menuManager, viewManager, this::reload);
        registerCommand();

        purgeExpiredRecords();
    }

    public void reload() {
        plugin.reloadConfig();

        String language = plugin.getConfig().getString("lang");
        messageService.load(language);
        inventoryLayoutService.load();
        menuConfiguration.load();

        if (menuManager != null) {
            HandlerList.unregisterAll(menuManager);
        }
        if (viewManager != null) {
            HandlerList.unregisterAll(viewManager);
        }

        menuManager = new RestoreMenuManager(plugin, messageService, inventoryLayoutService, menuConfiguration, deathRepository, snapshotRepository, worldRepository, teleportRepository);
        Bukkit.getPluginManager().registerEvents(menuManager, plugin);

        viewManager = new InventoryViewManager(plugin, messageService, menuConfiguration, snapshotRepository, pendingRepository);
        Bukkit.getPluginManager().registerEvents(viewManager, plugin);

        commandExecutor = new InventoryRestoreCommand(messageService, deathRepository, snapshotRepository, worldRepository, teleportRepository, menuManager, viewManager, this::reload);
        registerCommand();

        purgeExpiredRecords();
    }

    public void disable() {
        HandlerList.unregisterAll(plugin);
        if (databaseManager != null) {
            databaseManager.shutdown();
            databaseManager = null;
        }
        if (asyncTaskQueue != null) {
            asyncTaskQueue.shutdown();
            asyncTaskQueue = null;
        }
        messageService = null;
        inventoryLayoutService = null;
        menuConfiguration = null;
        deathRepository = null;
        pendingRepository = null;
        snapshotRepository = null;
        worldRepository = null;
        teleportRepository = null;
        menuManager = null;
        viewManager = null;
        commandExecutor = null;
    }

    private void registerCommand() {
        PluginCommand pluginCommand = plugin.getCommand("invrestore");
        if (pluginCommand != null && commandExecutor != null) {
            pluginCommand.setExecutor(commandExecutor);
            pluginCommand.setTabCompleter(commandExecutor);
        }
    }

    private void purgeExpiredRecords() {
        int days = plugin.getConfig().getInt("time_erase", 0);
        if (days <= 0) {
            return;
        }

        LocalDateTime threshold = LocalDateTime.now().minusDays(days);
        int removedDeaths = deathRepository.deleteOlderThan(threshold);
        int removedConnections = snapshotRepository.deleteOlderThan(threshold, InventoryRecordType.CONNECTION);
        int removedDisconnections = snapshotRepository.deleteOlderThan(threshold, InventoryRecordType.DISCONNECTION);
        int removedWorlds = worldRepository.deleteOlderThan(threshold);
        int removedTeleports = teleportRepository.deleteOlderThan(threshold);

        int totalRemoved = removedDeaths + removedConnections + removedDisconnections + removedWorlds + removedTeleports;
        if (totalRemoved > 0) {
            Logger logger = plugin.getLogger();
            logger.info("Removed " + totalRemoved + " expired inventory records (" +
                    removedDeaths + " deaths, " + removedWorlds + " worlds, " + removedTeleports + " teleports, " + removedConnections + " connections, " + removedDisconnections + " disconnections).");
        }
    }

}
