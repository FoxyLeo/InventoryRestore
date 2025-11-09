package com.foxy.inventoryRestore.listener;

import com.foxy.inventoryRestore.database.TeleportRepository;
import com.foxy.inventoryRestore.database.record.TeleportRecord;
import com.foxy.inventoryRestore.inventory.InventorySerializer;
import com.foxy.inventoryRestore.inventory.SerializedInventory;
import com.foxy.inventoryRestore.util.AsyncTaskQueue;
import com.foxy.inventoryRestore.util.DateFormats;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.time.LocalDateTime;

public final class TeleportListener implements Listener {

    private final TeleportRepository repository;
    private final AsyncTaskQueue taskQueue;

    public TeleportListener(TeleportRepository repository, AsyncTaskQueue taskQueue) {
        this.repository = repository;
        this.taskQueue = taskQueue;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (InventorySerializer.isEmpty(player.getInventory())) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || isSameBlock(from, to)) {
            return;
        }

        if (from != null && from.getWorld() != null && to.getWorld() != null && !from.getWorld().equals(to.getWorld())) {
            return;
        }

        SerializedInventory snapshot = InventorySerializer.capture(player.getInventory());
        String fromLocation = formatLocation(from);
        String toLocation = formatLocation(to);
        String timestamp = LocalDateTime.now().format(DateFormats.RECORD_DATE_TIME);
        String uuid = player.getUniqueId().toString();
        String nickname = player.getName();

        taskQueue.execute("teleport record for " + nickname, () -> {
            String serializedInventory = InventorySerializer.serialize(snapshot);
            TeleportRecord record = new TeleportRecord(
                    fromLocation,
                    toLocation,
                    timestamp,
                    uuid,
                    nickname,
                    serializedInventory
            );
            repository.save(record);
        });
    }

    private boolean isSameBlock(Location first, Location second) {
        if (first == null || second == null) {
            return false;
        }
        return first.getWorld() == second.getWorld()
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }

    private String formatLocation(Location location) {
        if (location == null) {
            return "Unknown";
        }
        String worldName = location.getWorld() != null ? location.getWorld().getName() : "Unknown";
        return worldName + ": " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
    }
}
