package com.foxy.inventoryRestore.database;

import com.foxy.inventoryRestore.database.record.StoredPlayerInventory;
import com.foxy.inventoryRestore.inventory.menu.InventoryRecordType;
import com.foxy.inventoryRestore.util.DateFormats;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

public final class PlayerSnapshotRepository {

    private static final EnumSet<InventoryRecordType> SUPPORTED_TYPES = EnumSet.of(
            InventoryRecordType.CONNECTION,
            InventoryRecordType.DISCONNECTION
    );

    private final DatabaseManager databaseManager;

    public PlayerSnapshotRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void save(InventoryRecordType type,
                     String timestamp,
                     String uuid,
                     String nickname,
                     String inventory,
                     String location,
                     String world) {
        ensureSupported(type);

        String sql = "INSERT INTO " + type.tableName() + " (event_date, uuid, nickname, inventory, location, world, returned) VALUES (?, ?, ?, ?, ?, ?, 0)";
        databaseManager.execute("Failed to store player snapshot for table " + type.tableName(), connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, timestamp);
                statement.setString(2, uuid);
                statement.setString(3, nickname);
                statement.setString(4, inventory);
                statement.setString(5, location);
                statement.setString(6, world);
                statement.executeUpdate();
            }
        });
    }

    public Optional<StoredPlayerInventory> findLatest(String nickname, InventoryRecordType type) {
        ensureSupported(type);

        String sql = "SELECT id, event_date, uuid, nickname, inventory, location, world, returned FROM " + type.tableName()
                + " WHERE nickname = ? COLLATE NOCASE ORDER BY id DESC LIMIT 1";
        return databaseManager.query("Failed to fetch latest snapshot for table " + type.tableName(), connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, nickname);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(mapRecord(resultSet, type));
                }
            }
        });
    }

    public List<StoredPlayerInventory> findByNickname(String nickname, InventoryRecordType type, int limit, int offset) {
        ensureSupported(type);

        String sql = "SELECT id, event_date, uuid, nickname, inventory, location, world, returned FROM " + type.tableName()
                + " WHERE nickname = ? COLLATE NOCASE ORDER BY id DESC LIMIT ? OFFSET ?";

        int safeLimit = Math.max(0, limit);
        int safeOffset = Math.max(0, offset);

        return databaseManager.query("Failed to fetch snapshots for table " + type.tableName(), connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, nickname);
                statement.setInt(2, safeLimit);
                statement.setInt(3, safeOffset);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<StoredPlayerInventory> records = new ArrayList<>();
                    while (resultSet.next()) {
                        records.add(mapRecord(resultSet, type));
                    }
                    return records;
                }
            }
        });
    }

    public void delete(long id, InventoryRecordType type) {
        ensureSupported(type);

        String sql = "DELETE FROM " + type.tableName() + " WHERE id = ?";
        databaseManager.execute("Failed to delete snapshot from table " + type.tableName(), connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, id);
                statement.executeUpdate();
            }
        });
    }

    public void markInventoryReturned(long id, InventoryRecordType type) {
        ensureSupported(type);

        String sql = "UPDATE " + type.tableName() + " SET returned = 1 WHERE id = ?";
        databaseManager.execute("Failed to mark snapshot as returned in table " + type.tableName(), connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, id);
                statement.executeUpdate();
            }
        });
    }

    public Optional<StoredPlayerInventory> findById(long id, InventoryRecordType type) {
        ensureSupported(type);

        String sql = "SELECT id, event_date, uuid, nickname, inventory, location, world, returned FROM " + type.tableName() + " WHERE id = ?";
        return databaseManager.query("Failed to fetch snapshot by id from table " + type.tableName(), connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(mapRecord(resultSet, type));
                }
            }
        });
    }

    public boolean hasRecords(String nickname, InventoryRecordType type) {
        ensureSupported(type);

        String sql = "SELECT 1 FROM " + type.tableName() + " WHERE nickname = ? COLLATE NOCASE LIMIT 1";
        return databaseManager.query("Failed to determine if snapshots exist for table " + type.tableName(), connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, nickname);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next();
                }
            }
        });
    }

    public int countByNickname(String nickname, InventoryRecordType type) {
        ensureSupported(type);

        String sql = "SELECT COUNT(*) FROM " + type.tableName() + " WHERE nickname = ? COLLATE NOCASE";

        return databaseManager.query("Failed to count snapshots for table " + type.tableName(), connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, nickname);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return 0;
                    }
                    return resultSet.getInt(1);
                }
            }
        });
    }

    public List<String> findAllNicknames(InventoryRecordType type) {
        ensureSupported(type);

        String sql = "SELECT DISTINCT nickname FROM " + type.tableName()
                + " WHERE nickname IS NOT NULL AND TRIM(nickname) <> '' ORDER BY nickname COLLATE NOCASE";
        return databaseManager.query("Failed to fetch player nicknames from table " + type.tableName(), connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                List<String> names = new ArrayList<>();
                while (resultSet.next()) {
                    names.add(resultSet.getString("nickname"));
                }
                return names;
            }
        });
    }

    public int deleteOlderThan(LocalDateTime threshold, InventoryRecordType type) {
        ensureSupported(type);

        String whereClause = buildCleanupClause("event_date");
        String sql = "DELETE FROM " + type.tableName() + " WHERE " + whereClause;
        String thresholdValue = threshold.format(DateFormats.SQLITE_DATE_TIME);

        return databaseManager.query("Failed to delete expired snapshots from table " + type.tableName(), connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, thresholdValue);
                return statement.executeUpdate();
            }
        });
    }

    private static String buildCleanupClause(String column) {
        String normalized = normalizeDate(column);
        return column + " IS NULL OR TRIM(" + column + ") = '' OR "
                + "strftime('%s', " + normalized + ") IS NULL OR "
                + "strftime('%s', " + normalized + ") < strftime('%s', ?)";
    }

    private static String normalizeDate(String column) {
        return "'20' || substr(" + column + ", 7, 2) || '-' || substr(" + column + ", 4, 2) || '-' || substr(" + column + ", 1, 2) || ' ' || substr(" + column + ", 10)";
    }

    private StoredPlayerInventory mapRecord(ResultSet resultSet, InventoryRecordType type) throws SQLException {
        long id = resultSet.getLong("id");
        String eventDate = resultSet.getString("event_date");
        String uuid = resultSet.getString("uuid");
        String nickname = resultSet.getString("nickname");
        String inventory = resultSet.getString("inventory");
        String location = resultSet.getString("location");
        String world = resultSet.getString("world");
        boolean returned = resultSet.getInt("returned") == 1 || "returned".equalsIgnoreCase(inventory);
        if (returned && "returned".equalsIgnoreCase(inventory)) {
            inventory = "returned";
        }
        return new StoredPlayerInventory(id, type, eventDate, uuid, nickname, inventory, returned, location, world);
    }

    private void ensureSupported(InventoryRecordType type) {
        if (!SUPPORTED_TYPES.contains(type)) {
            throw new IllegalArgumentException("Unsupported inventory record type: " + type);
        }
    }
}
