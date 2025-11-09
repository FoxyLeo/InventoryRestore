package com.foxy.inventoryRestore.listener;

import com.foxy.inventoryRestore.database.DeathInventoryRepository;
import com.foxy.inventoryRestore.database.record.DeathRecord;
import com.foxy.inventoryRestore.inventory.InventorySerializer;
import com.foxy.inventoryRestore.inventory.SerializedInventory;
import com.foxy.inventoryRestore.util.AsyncTaskQueue;
import com.foxy.inventoryRestore.util.DateFormats;
import com.foxy.inventoryRestore.util.LocationFormats;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

public final class DeathListener implements Listener {

    private final DeathInventoryRepository repository;
    private final AsyncTaskQueue taskQueue;

    public DeathListener(DeathInventoryRepository repository, AsyncTaskQueue taskQueue) {
        this.repository = repository;
        this.taskQueue = taskQueue;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (InventorySerializer.isEmpty(player.getInventory())) {
            return;
        }

        SerializedInventory snapshot = InventorySerializer.capture(player.getInventory());
        String deathType = resolveDeathType(player);
        String deathDate = LocalDateTime.now().format(DateFormats.RECORD_DATE_TIME);
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        Location location = player.getLocation();
        String coords = LocationFormats.coordinates(location);
        String world = LocationFormats.worldName(location);

        taskQueue.execute("death record for " + playerName, () -> {
            String serializedInventory = InventorySerializer.serialize(snapshot);
            DeathRecord record = new DeathRecord(
                    deathType,
                    deathDate,
                    playerId.toString(),
                    playerName,
                    serializedInventory,
                    coords,
                    world
            );
            repository.save(record);
        });
    }

    private String resolveDeathType(Player player) {
        Player killer = player.getKiller();
        if (killer != null) {
            return killer.getName();
        }

        EntityDamageEvent damageEvent = player.getLastDamageCause();
        if (damageEvent instanceof EntityDamageByEntityEvent entityDamage) {
            Entity damager = entityDamage.getDamager();
            if (damager instanceof Projectile projectile) {
                ProjectileSource shooter = projectile.getShooter();
                if (shooter instanceof Player shooterPlayer) {
                    return shooterPlayer.getName();
                }
                if (shooter instanceof LivingEntity livingShooter) {
                    return formatName(livingShooter.getType().name());
                }
            }

            if (damager instanceof LivingEntity livingDamager) {
                return formatName(livingDamager.getType().name());
            }
        }

        if (damageEvent != null) {
            return formatName(damageEvent.getCause().name());
        }

        return "Unknown";
    }

    private String formatName(String name) {
        if (name == null || name.isEmpty()) {
            return "Unknown";
        }

        String[] parts = name.toLowerCase(Locale.ENGLISH).split("_");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(parts[i].charAt(0)))
                    .append(parts[i].substring(1));
        }
        return builder.length() > 0 ? builder.toString() : "Unknown";
    }
}
