package com.foxy.inventoryRestore.database;

import com.foxy.inventoryRestore.database.record.StoredPendingInventory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public final class PendingInventoryRepository {

    private final DatabaseManager databaseManager;

    public PendingInventoryRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void save(String uuid, String nickname, String inventory) {
        String sql = "INSERT INTO pending_inventory (uuid, nickname, inventory) VALUES (?, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET nickname = excluded.nickname, inventory = excluded.inventory";
        databaseManager.execute("Failed to store pending inventory", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, uuid);
                statement.setString(2, nickname);
                statement.setString(3, inventory);
                statement.executeUpdate();
            }
        });
    }

    public Optional<StoredPendingInventory> findByUuid(String uuid) {
        String sql = "SELECT uuid, nickname, inventory FROM pending_inventory WHERE uuid = ? COLLATE NOCASE LIMIT 1";
        return databaseManager.query("Failed to fetch pending inventory by uuid", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, uuid);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(mapRecord(resultSet));
                }
            }
        });
    }

    public Optional<StoredPendingInventory> findByNickname(String nickname) {
        String sql = "SELECT uuid, nickname, inventory FROM pending_inventory WHERE nickname = ? COLLATE NOCASE LIMIT 1";
        return databaseManager.query("Failed to fetch pending inventory by nickname", connection -> {
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

    public void deleteByUuid(String uuid) {
        String sql = "DELETE FROM pending_inventory WHERE uuid = ? COLLATE NOCASE";
        databaseManager.execute("Failed to delete pending inventory", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, uuid);
                statement.executeUpdate();
            }
        });
    }

    private StoredPendingInventory mapRecord(ResultSet resultSet) throws SQLException {
        String uuid = resultSet.getString("uuid");
        String nickname = resultSet.getString("nickname");
        String inventory = resultSet.getString("inventory");
        return new StoredPendingInventory(uuid, nickname, inventory);
    }
}

