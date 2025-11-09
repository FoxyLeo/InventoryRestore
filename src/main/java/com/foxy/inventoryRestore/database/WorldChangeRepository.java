package com.foxy.inventoryRestore.database;

import com.foxy.inventoryRestore.database.record.StoredWorldInventory;
import com.foxy.inventoryRestore.database.record.WorldChangeRecord;
import com.foxy.inventoryRestore.util.DateFormats;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class WorldChangeRepository {

    private final DatabaseManager databaseManager;

    public WorldChangeRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void save(WorldChangeRecord record) {
        String sql = "INSERT INTO world (from_world, to_world, event_date, uuid, nickname, inventory, returned) VALUES (?, ?, ?, ?, ?, ?, 0)";

        databaseManager.execute("Failed to store world change record", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, record.fromWorld());
                statement.setString(2, record.toWorld());
                statement.setString(3, record.eventDate());
                statement.setString(4, record.uuid());
                statement.setString(5, record.nickname());
                statement.setString(6, record.inventory());
                statement.executeUpdate();
            }
        });
    }

    public List<StoredWorldInventory> findByNickname(String nickname, int limit, int offset) {
        String sql = "SELECT id, from_world, to_world, event_date, uuid, nickname, inventory, returned FROM world " +
                "WHERE nickname = ? COLLATE NOCASE ORDER BY id DESC LIMIT ? OFFSET ?";

        int safeLimit = Math.max(0, limit);
        int safeOffset = Math.max(0, offset);

        return databaseManager.query("Failed to fetch world change records", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, nickname);
                statement.setInt(2, safeLimit);
                statement.setInt(3, safeOffset);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<StoredWorldInventory> records = new ArrayList<>();
                    while (resultSet.next()) {
                        records.add(mapRecord(resultSet));
                    }
                    return records;
                }
            }
        });
    }

    public Optional<StoredWorldInventory> findLatest(String nickname) {
        String sql = "SELECT id, from_world, to_world, event_date, uuid, nickname, inventory, returned FROM world " +
                "WHERE nickname = ? COLLATE NOCASE ORDER BY id DESC LIMIT 1";

        return databaseManager.query("Failed to fetch latest world change record", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, nickname);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(mapRecord(resultSet));
                }
            }
        });
    }

    public Optional<StoredWorldInventory> findById(long id) {
        String sql = "SELECT id, from_world, to_world, event_date, uuid, nickname, inventory, returned FROM world WHERE id = ?";

        return databaseManager.query("Failed to fetch world change record", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(mapRecord(resultSet));
                }
            }
        });
    }

    public void delete(long id) {
        String sql = "DELETE FROM world WHERE id = ?";

        databaseManager.execute("Failed to delete world change record", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, id);
                statement.executeUpdate();
            }
        });
    }

    public void markInventoryReturned(long id) {
        String sql = "UPDATE world SET returned = 1 WHERE id = ?";

        databaseManager.execute("Failed to mark world change record as returned", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, id);
                statement.executeUpdate();
            }
        });
    }

    public boolean hasRecords(String nickname) {
        String sql = "SELECT 1 FROM world WHERE nickname = ? COLLATE NOCASE LIMIT 1";

        return databaseManager.query("Failed to check if world change records exist", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, nickname);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next();
                }
            }
        });
    }

    public int countByNickname(String nickname) {
        String sql = "SELECT COUNT(*) FROM world WHERE nickname = ? COLLATE NOCASE";

        return databaseManager.query("Failed to count world change records", connection -> {
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

    public List<String> findAllNicknames() {
        String sql = "SELECT DISTINCT nickname FROM world WHERE nickname IS NOT NULL AND TRIM(nickname) <> '' ORDER BY nickname COLLATE NOCASE";

        return databaseManager.query("Failed to fetch world change nicknames", connection -> {
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

    public int deleteOlderThan(LocalDateTime threshold) {
        String whereClause = buildCleanupClause("event_date");
        String sql = "DELETE FROM world WHERE " + whereClause;
        String thresholdValue = threshold.format(DateFormats.SQLITE_DATE_TIME);

        return databaseManager.query("Failed to delete expired world change records", connection -> {
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

    private StoredWorldInventory mapRecord(ResultSet resultSet) throws SQLException {
        long id = resultSet.getLong("id");
        String fromWorld = resultSet.getString("from_world");
        if (fromWorld == null || fromWorld.isEmpty()) {
            fromWorld = "Unknown";
        }
        String toWorld = resultSet.getString("to_world");
        if (toWorld == null || toWorld.isEmpty()) {
            toWorld = "Unknown";
        }
        String eventDate = resultSet.getString("event_date");
        String uuid = resultSet.getString("uuid");
        String nickname = resultSet.getString("nickname");
        String inventory = resultSet.getString("inventory");
        boolean returned = resultSet.getInt("returned") == 1 || "returned".equalsIgnoreCase(inventory);
        if (returned && "returned".equalsIgnoreCase(inventory)) {
            inventory = "returned";
        }
        return new StoredWorldInventory(id, fromWorld, toWorld, eventDate, uuid, nickname, inventory, returned);
    }
}
