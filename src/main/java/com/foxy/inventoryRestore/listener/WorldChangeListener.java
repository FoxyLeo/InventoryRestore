package com.foxy.inventoryRestore.listener;

import com.foxy.inventoryRestore.database.WorldChangeRepository;
import com.foxy.inventoryRestore.database.record.WorldChangeRecord;
import com.foxy.inventoryRestore.inventory.InventorySerializer;
import com.foxy.inventoryRestore.inventory.SerializedInventory;
import com.foxy.inventoryRestore.util.AsyncTaskQueue;
import com.foxy.inventoryRestore.util.DateFormats;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import java.time.LocalDateTime;

public final class WorldChangeListener implements Listener {

    private final WorldChangeRepository repository;
    private final AsyncTaskQueue taskQueue;

    public WorldChangeListener(WorldChangeRepository repository, AsyncTaskQueue taskQueue) {
        this.repository = repository;
        this.taskQueue = taskQueue;
    }

    @EventHandler
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (InventorySerializer.isEmpty(player.getInventory())) {
            return;
        }

        SerializedInventory snapshot = InventorySerializer.capture(player.getInventory());
        String fromWorld = event.getFrom() != null ? event.getFrom().getName() : "Unknown";
        String toWorld = player.getWorld().getName();
        String timestamp = LocalDateTime.now().format(DateFormats.RECORD_DATE_TIME);
        String uuid = player.getUniqueId().toString();
        String nickname = player.getName();

        taskQueue.execute("world change record for " + nickname, () -> {
            String serializedInventory = InventorySerializer.serialize(snapshot);
            WorldChangeRecord record = new WorldChangeRecord(
                    fromWorld,
                    toWorld,
                    timestamp,
                    uuid,
                    nickname,
                    serializedInventory
            );
            repository.save(record);
        });
    }
}
