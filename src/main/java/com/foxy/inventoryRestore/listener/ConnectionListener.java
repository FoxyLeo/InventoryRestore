package com.foxy.inventoryRestore.listener;

import com.foxy.inventoryRestore.database.PendingInventoryRepository;
import com.foxy.inventoryRestore.database.PlayerSnapshotRepository;
import com.foxy.inventoryRestore.inventory.InventorySerializer;
import com.foxy.inventoryRestore.inventory.SerializedInventory;
import com.foxy.inventoryRestore.inventory.menu.InventoryRecordType;
import com.foxy.inventoryRestore.database.record.StoredPendingInventory;
import com.foxy.inventoryRestore.util.AsyncTaskQueue;
import com.foxy.inventoryRestore.util.DateFormats;
import com.foxy.inventoryRestore.util.LocationFormats;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.Map;

public final class ConnectionListener implements Listener {

    private final PlayerSnapshotRepository repository;
    private final PendingInventoryRepository pendingRepository;
    private final AsyncTaskQueue taskQueue;

    public ConnectionListener(PlayerSnapshotRepository repository,
                              PendingInventoryRepository pendingRepository,
                              AsyncTaskQueue taskQueue) {
        this.repository = repository;
        this.pendingRepository = pendingRepository;
        this.taskQueue = taskQueue;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        applyPendingInventory(event.getPlayer());
        saveSnapshot(event.getPlayer(), InventoryRecordType.CONNECTION);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        saveSnapshot(event.getPlayer(), InventoryRecordType.DISCONNECTION);
    }

    private void saveSnapshot(Player player, InventoryRecordType type) {
        if (InventorySerializer.isEmpty(player.getInventory())) {
            return;
        }

        SerializedInventory snapshot = InventorySerializer.capture(player.getInventory());
        String timestamp = LocalDateTime.now().format(DateFormats.RECORD_DATE_TIME);
        String uuid = player.getUniqueId().toString();
        String nickname = player.getName();
        Location location = player.getLocation();
        String coords = LocationFormats.coordinates(location);
        String world = LocationFormats.worldName(location);

        taskQueue.execute(type.name().toLowerCase(Locale.ENGLISH) + " snapshot for " + nickname, () -> {
            String serializedInventory = InventorySerializer.serialize(snapshot);
            repository.save(type, timestamp, uuid, nickname, serializedInventory, coords, world);
        });
    }

    private void applyPendingInventory(Player player) {
        String uuid = player.getUniqueId().toString();
        Optional<StoredPendingInventory> pending = pendingRepository.findByUuid(uuid);
        if (pending.isEmpty()) {
            pending = pendingRepository.findByNickname(player.getName());
        }
        if (pending.isEmpty()) {
            return;
        }

        StoredPendingInventory stored = pending.get();

        SerializedInventory inventory;
        try {
            inventory = InventorySerializer.deserialize(stored.inventory());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            Bukkit.getLogger().log(Level.WARNING,
                    "Failed to deserialize pending inventory for {0} ({1})",
                    new Object[]{stored.nickname(), stored.uuid()});
            pendingRepository.deleteByUuid(stored.uuid());
            return;
        }

        player.getInventory().setStorageContents(cloneContents(inventory.contents(), 36));
        player.getInventory().setArmorContents(cloneContents(inventory.armor(), 4));

        ItemStack[] extra = inventory.extra();
        ItemStack offhand = extra.length > 0 ? cloneItem(extra[0]) : null;
        player.getInventory().setItemInOffHand(offhand);

        List<ItemStack> overflow = new ArrayList<>();
        for (int i = 1; i < extra.length; i++) {
            ItemStack item = cloneItem(extra[i]);
            if (item != null) {
                overflow.add(item);
            }
        }

        if (!overflow.isEmpty()) {
            Map<Integer, ItemStack> remaining = player.getInventory().addItem(overflow.toArray(new ItemStack[0]));
            if (!remaining.isEmpty()) {
                remaining.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            }
        }

        player.updateInventory();
        pendingRepository.deleteByUuid(stored.uuid());
    }

    private ItemStack[] cloneContents(ItemStack[] source, int length) {
        ItemStack[] copy = new ItemStack[length];
        for (int i = 0; i < length; i++) {
            copy[i] = cloneItem(i < source.length ? source[i] : null);
        }
        return copy;
    }

    private ItemStack cloneItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        return item.clone();
    }
}
