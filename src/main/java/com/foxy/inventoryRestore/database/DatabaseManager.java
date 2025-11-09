package com.foxy.inventoryRestore.database;

import com.foxy.inventoryRestore.InventoryRestore;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseManager {

    private static final String INVENTORIES_FOLDER = "inventories";
    private static final String DATABASE_FILE = "data.db";

    private final InventoryRestore plugin;
    private final Object connectionLock = new Object();
    private Connection connection;

    public DatabaseManager(InventoryRestore plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        if (connection != null) {
            return;
        }

        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                throw new IllegalStateException("Unable to create plugin data folder: " + dataFolder.getAbsolutePath());
            }

            File inventoriesFolder = new File(dataFolder, INVENTORIES_FOLDER);
            if (!inventoriesFolder.exists() && !inventoriesFolder.mkdirs()) {
                throw new IllegalStateException("Unable to create inventories folder: " + inventoriesFolder.getAbsolutePath());
            }

            File databaseFile = new File(inventoriesFolder, DATABASE_FILE);
            String jdbcUrl = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
            connection = DriverManager.getConnection(jdbcUrl);
            createTables(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize the SQLite database", exception);
        }
    }

    public Connection getConnection() {
        if (connection == null) {
            throw new IllegalStateException("Database connection has not been initialized.");
        }
        return connection;
    }

    public void execute(String errorMessage, SqlConsumer consumer) {
        Connection current = getConnection();
        synchronized (connectionLock) {
            try {
                consumer.accept(current);
            } catch (SQLException exception) {
                throw new IllegalStateException(errorMessage, exception);
            }
        }
    }

    public <T> T query(String errorMessage, SqlFunction<T> function) {
        Connection current = getConnection();
        synchronized (connectionLock) {
            try {
                return function.apply(current);
            } catch (SQLException exception) {
                throw new IllegalStateException(errorMessage, exception);
            }
        }
    }

    public void shutdown() {
        if (connection == null) {
            return;
        }

        try {
            connection.close();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to close the SQLite database connection", exception);
        } finally {
            connection = null;
        }
    }

    private void createTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS death (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "death_type TEXT NOT NULL," +
                    "death_date TEXT NOT NULL," +
                    "uuid TEXT NOT NULL," +
                    "nickname TEXT NOT NULL," +
                    "inventory TEXT NOT NULL," +
                    "location TEXT," +
                    "world TEXT," +
                    "returned INTEGER NOT NULL DEFAULT 0" +
                    ")");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS disconnection (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "event_date TEXT NOT NULL," +
                    "uuid TEXT NOT NULL," +
                    "nickname TEXT NOT NULL," +
                    "inventory TEXT NOT NULL," +
                    "location TEXT," +
                    "world TEXT," +
                    "returned INTEGER NOT NULL DEFAULT 0" +
                    ")");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS connection (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "event_date TEXT NOT NULL," +
                    "uuid TEXT NOT NULL," +
                    "nickname TEXT NOT NULL," +
                    "inventory TEXT NOT NULL," +
                    "location TEXT," +
                    "world TEXT," +
                    "returned INTEGER NOT NULL DEFAULT 0" +
                    ")");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS world (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "from_world TEXT NOT NULL," +
                    "to_world TEXT NOT NULL," +
                    "event_date TEXT NOT NULL," +
                    "uuid TEXT NOT NULL," +
                    "nickname TEXT NOT NULL," +
                    "inventory TEXT NOT NULL," +
                    "returned INTEGER NOT NULL DEFAULT 0" +
                    ")");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS teleport (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "from_location TEXT NOT NULL," +
                    "to_location TEXT NOT NULL," +
                    "event_date TEXT NOT NULL," +
                    "uuid TEXT NOT NULL," +
                    "nickname TEXT NOT NULL," +
                    "inventory TEXT NOT NULL," +
                    "returned INTEGER NOT NULL DEFAULT 0" +
                    ")");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS pending_inventory (" +
                    "uuid TEXT PRIMARY KEY," +
                    "nickname TEXT NOT NULL," +
                    "inventory TEXT NOT NULL" +
                    ")");
        }

        ensureColumn(connection, "death", "returned", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(connection, "death", "location", "TEXT");
        ensureColumn(connection, "death", "world", "TEXT");
        ensureColumn(connection, "connection", "returned", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(connection, "disconnection", "returned", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(connection, "connection", "location", "TEXT");
        ensureColumn(connection, "connection", "world", "TEXT");
        ensureColumn(connection, "disconnection", "location", "TEXT");
        ensureColumn(connection, "disconnection", "world", "TEXT");
        ensureColumn(connection, "teleport", "from_location", "TEXT");
        ensureColumn(connection, "teleport", "to_location", "TEXT");
        ensureColumn(connection, "teleport", "event_date", "TEXT");
        ensureColumn(connection, "teleport", "uuid", "TEXT");
        ensureColumn(connection, "teleport", "nickname", "TEXT");
        ensureColumn(connection, "teleport", "inventory", "TEXT");
        ensureColumn(connection, "teleport", "returned", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(connection, "world", "from_world", "TEXT");
        ensureColumn(connection, "world", "to_world", "TEXT");
        ensureColumn(connection, "world", "event_date", "TEXT");
        ensureColumn(connection, "world", "uuid", "TEXT");
        ensureColumn(connection, "world", "nickname", "TEXT");
        ensureColumn(connection, "world", "inventory", "TEXT");
        ensureColumn(connection, "world", "returned", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(connection, "connection", "event_date", "TEXT");
        ensureColumn(connection, "connection", "uuid", "TEXT");
        ensureColumn(connection, "connection", "nickname", "TEXT");
        ensureColumn(connection, "connection", "inventory", "TEXT");
        ensureColumn(connection, "disconnection", "event_date", "TEXT");
        ensureColumn(connection, "disconnection", "uuid", "TEXT");
        ensureColumn(connection, "disconnection", "nickname", "TEXT");
        ensureColumn(connection, "disconnection", "inventory", "TEXT");
        synchronizeReturnedFlag(connection);
    }

    private void ensureColumn(Connection connection, String table, String column, String definition) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getColumns(null, null, table, column)) {
            if (resultSet.next()) {
                return;
            }
        }

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private void synchronizeReturnedFlag(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE death SET returned = 1 WHERE LOWER(inventory) = 'returned'");
            statement.executeUpdate("UPDATE connection SET returned = 1 WHERE LOWER(inventory) = 'returned'");
            statement.executeUpdate("UPDATE disconnection SET returned = 1 WHERE LOWER(inventory) = 'returned'");
            statement.executeUpdate("UPDATE world SET returned = 1 WHERE LOWER(inventory) = 'returned'");
            statement.executeUpdate("UPDATE teleport SET returned = 1 WHERE LOWER(inventory) = 'returned'");
        }
    }

    @FunctionalInterface
    public interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }
}
