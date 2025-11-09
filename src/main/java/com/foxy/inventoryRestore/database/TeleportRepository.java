package com.foxy.inventoryRestore.database;

import com.foxy.inventoryRestore.database.record.StoredTeleportInventory;
import com.foxy.inventoryRestore.database.record.TeleportRecord;
import com.foxy.inventoryRestore.util.DateFormats;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TeleportRepository {

    private final DatabaseManager databaseManager;

    public TeleportRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void save(TeleportRecord record) {
        String sql = "INSERT INTO teleport (from_location, to_location, event_date, uuid, nickname, inventory, returned) VALUES (?, ?, ?, ?, ?, ?, 0)";

        databaseManager.execute("Failed to store teleport record", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, record.fromLocation());
                statement.setString(2, record.toLocation());
                statement.setString(3, record.eventDate());
                statement.setString(4, record.uuid());
                statement.setString(5, record.nickname());
                statement.setString(6, record.inventory());
                statement.executeUpdate();
            }
        });
    }

    public List<StoredTeleportInventory> findByNickname(String nickname, int limit, int offset) {
        String sql = "SELECT id, from_location, to_location, event_date, uuid, nickname, inventory, returned FROM teleport " +
                "WHERE nickname = ? COLLATE NOCASE ORDER BY id DESC LIMIT ? OFFSET ?";

        int safeLimit = Math.max(0, limit);
        int safeOffset = Math.max(0, offset);

        return databaseManager.query("Failed to fetch teleport records", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, nickname);
                statement.setInt(2, safeLimit);
                statement.setInt(3, safeOffset);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<StoredTeleportInventory> records = new ArrayList<>();
                    while (resultSet.next()) {
                        records.add(mapRecord(resultSet));
                    }
                    return records;
                }
            }
        });
    }

    public Optional<StoredTeleportInventory> findLatest(String nickname) {
        String sql = "SELECT id, from_location, to_location, event_date, uuid, nickname, inventory, returned FROM teleport " +
                "WHERE nickname = ? COLLATE NOCASE ORDER BY id DESC LIMIT 1";

        return databaseManager.query("Failed to fetch latest teleport record", connection -> {
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

    public Optional<StoredTeleportInventory> findById(long id) {
        String sql = "SELECT id, from_location, to_location, event_date, uuid, nickname, inventory, returned FROM teleport WHERE id = ?";

        return databaseManager.query("Failed to fetch teleport record", connection -> {
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
        String sql = "DELETE FROM teleport WHERE id = ?";

        databaseManager.execute("Failed to delete teleport record", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, id);
                statement.executeUpdate();
            }
        });
    }

    public void markInventoryReturned(long id) {
        String sql = "UPDATE teleport SET returned = 1 WHERE id = ?";

        databaseManager.execute("Failed to mark teleport record as returned", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, id);
                statement.executeUpdate();
            }
        });
    }

    public boolean hasRecords(String nickname) {
        String sql = "SELECT 1 FROM teleport WHERE nickname = ? COLLATE NOCASE LIMIT 1";

        return databaseManager.query("Failed to check if teleport records exist", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, nickname);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next();
                }
            }
        });
    }

    public int countByNickname(String nickname) {
        String sql = "SELECT COUNT(*) FROM teleport WHERE nickname = ? COLLATE NOCASE";

        return databaseManager.query("Failed to count teleport records", connection -> {
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
        String sql = "SELECT DISTINCT nickname FROM teleport WHERE nickname IS NOT NULL AND TRIM(nickname) <> '' ORDER BY nickname COLLATE NOCASE";

        return databaseManager.query("Failed to fetch teleport nicknames", connection -> {
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
        String sql = "DELETE FROM teleport WHERE " + whereClause;
        String thresholdValue = threshold.format(DateFormats.SQLITE_DATE_TIME);

        return databaseManager.query("Failed to delete expired teleport records", connection -> {
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

    private StoredTeleportInventory mapRecord(ResultSet resultSet) throws SQLException {
        long id = resultSet.getLong("id");
        String fromLocation = defaultLocation(resultSet.getString("from_location"));
        String toLocation = defaultLocation(resultSet.getString("to_location"));
        String eventDate = resultSet.getString("event_date");
        String uuid = resultSet.getString("uuid");
        String nickname = resultSet.getString("nickname");
        String inventory = resultSet.getString("inventory");
        boolean returned = resultSet.getInt("returned") == 1 || "returned".equalsIgnoreCase(inventory);
        if (returned && "returned".equalsIgnoreCase(inventory)) {
            inventory = "returned";
        }
        return new StoredTeleportInventory(id, fromLocation, toLocation, eventDate, uuid, nickname, inventory, returned);
    }

    private String defaultLocation(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "Unknown";
        }
        return raw;
    }
}
