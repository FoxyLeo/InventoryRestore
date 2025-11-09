package com.foxy.inventoryRestore.database;

import com.foxy.inventoryRestore.database.record.DeathRecord;
import com.foxy.inventoryRestore.database.record.StoredDeathInventory;

import com.foxy.inventoryRestore.util.DateFormats;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DeathInventoryRepository {

    private final DatabaseManager databaseManager;

    public DeathInventoryRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void save(DeathRecord record) {
        String sql = "INSERT INTO death (death_type, death_date, uuid, nickname, inventory, location, world, returned) VALUES (?, ?, ?, ?, ?, ?, ?, 0)";

        databaseManager.execute("Failed to store death record", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, record.deathType());
                statement.setString(2, record.deathDate());
                statement.setString(3, record.uuid());
                statement.setString(4, record.nickname());
                statement.setString(5, record.inventory());
                statement.setString(6, record.location());
                statement.setString(7, record.world());
                statement.executeUpdate();
            }
        });
    }

    public List<String> findAllNicknames() {
        String sql = "SELECT DISTINCT nickname FROM death WHERE nickname IS NOT NULL AND TRIM(nickname) <> '' " +
                "ORDER BY nickname COLLATE NOCASE";

        return databaseManager.query("Failed to fetch player nicknames", connection -> {
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

    public Optional<StoredDeathInventory> findLatest(String nickname) {
        String sql = "SELECT id, death_type, death_date, uuid, nickname, inventory, location, world, returned FROM death " +
                "WHERE nickname = ? COLLATE NOCASE ORDER BY id DESC LIMIT 1";

        return databaseManager.query("Failed to fetch stored death inventory", connection -> {
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

    public void markInventoryReturned(long id) {
        String sql = "UPDATE death SET returned = 1 WHERE id = ?";

        databaseManager.execute("Failed to mark inventory as returned", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, id);
                statement.executeUpdate();
            }
        });
    }

    public List<StoredDeathInventory> findByNickname(String nickname, int limit, int offset) {
        String sql = "SELECT id, death_type, death_date, uuid, nickname, inventory, location, world, returned FROM death " +
                "WHERE nickname = ? COLLATE NOCASE ORDER BY id DESC LIMIT ? OFFSET ?";

        int safeLimit = Math.max(0, limit);
        int safeOffset = Math.max(0, offset);

        return databaseManager.query("Failed to fetch stored death inventories", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, nickname);
                statement.setInt(2, safeLimit);
                statement.setInt(3, safeOffset);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<StoredDeathInventory> inventories = new ArrayList<>();
                    while (resultSet.next()) {
                        inventories.add(mapRecord(resultSet));
                    }
                    return inventories;
                }
            }
        });
    }

    public int countByNickname(String nickname) {
        String sql = "SELECT COUNT(*) FROM death WHERE nickname = ? COLLATE NOCASE";

        return databaseManager.query("Failed to count death records", connection -> {
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

    public Optional<StoredDeathInventory> findById(long id) {
        String sql = "SELECT id, death_type, death_date, uuid, nickname, inventory, location, world, returned FROM death WHERE id = ?";

        return databaseManager.query("Failed to fetch stored death inventory", connection -> {
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
        String sql = "DELETE FROM death WHERE id = ?";

        databaseManager.execute("Failed to delete death inventory", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, id);
                statement.executeUpdate();
            }
        });
    }

    public boolean hasRecords(String nickname) {
        String sql = "SELECT 1 FROM death WHERE nickname = ? COLLATE NOCASE LIMIT 1";

        return databaseManager.query("Failed to determine if death records exist", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, nickname);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next();
                }
            }
        });
    }

    public int deleteOlderThan(LocalDateTime threshold) {
        String whereClause = buildCleanupClause("death_date");
        String sql = "DELETE FROM death WHERE " + whereClause;
        String thresholdValue = threshold.format(DateFormats.SQLITE_DATE_TIME);

        return databaseManager.query("Failed to delete expired death records", connection -> {
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

    private StoredDeathInventory mapRecord(ResultSet resultSet) throws SQLException {
        long id = resultSet.getLong("id");
        String deathType = resultSet.getString("death_type");
        String deathDate = resultSet.getString("death_date");
        String uuid = resultSet.getString("uuid");
        String storedNickname = resultSet.getString("nickname");
        String inventory = resultSet.getString("inventory");
        String location = resultSet.getString("location");
        String world = resultSet.getString("world");
        boolean returned = resultSet.getInt("returned") == 1 || "returned".equalsIgnoreCase(inventory);
        if (returned && "returned".equalsIgnoreCase(inventory)) {
            inventory = "returned";
        }
        return new StoredDeathInventory(id, deathType, deathDate, uuid, storedNickname, inventory, returned, location, world);
    }
}
