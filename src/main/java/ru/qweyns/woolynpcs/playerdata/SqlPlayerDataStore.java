package ru.qweyns.woolynpcs.playerdata;

import ru.qweyns.woolynpcs.WoolyNpcs;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

public class SqlPlayerDataStore implements PlayerDataStore {
    private final WoolyNpcs plugin;
    private final String url;
    private final String user;
    private final String password;
    private final String label;

    private Connection connection;

    private SqlPlayerDataStore(WoolyNpcs plugin, String url, String user, String password, String label) {
        this.plugin   = plugin;
        this.url      = url;
        this.user     = user;
        this.password = password;
        this.label    = label;
    }

    public static SqlPlayerDataStore sqlite(WoolyNpcs plugin) {
        if (!driverAvailable("org.sqlite.JDBC")) return null;
        File file = new File(plugin.getDataFolder(), "playerdata.db");
        return new SqlPlayerDataStore(plugin,
                "jdbc:sqlite:" + file.getAbsolutePath(), null, null, "SQLite (playerdata.db)");
    }

    public static SqlPlayerDataStore mysql(WoolyNpcs plugin, String host, int port, String database,
                                           String user, String password) {
        if (!driverAvailable("com.mysql.cj.jdbc.Driver") && !driverAvailable("com.mysql.jdbc.Driver")) {
            return null;
        }
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8";
        return new SqlPlayerDataStore(plugin, url, user, password, "MySQL (" + host + "/" + database + ")");
    }

    private static boolean driverAvailable(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean initialize() {
        try {
            connection = (user == null)
                    ? DriverManager.getConnection(url)
                    : DriverManager.getConnection(url, user, password);

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS wnpc_cooldowns (
                            player CHAR(36) NOT NULL,
                            action CHAR(36) NOT NULL,
                            stamp  BIGINT  NOT NULL,
                            PRIMARY KEY (player, action))""");
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS wnpc_executed (
                            player CHAR(36) NOT NULL,
                            action CHAR(36) NOT NULL,
                            PRIMARY KEY (player, action))""");
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS wnpc_flags (
                            player CHAR(36)     NOT NULL,
                            name   VARCHAR(128) NOT NULL,
                            value  VARCHAR(512) NOT NULL,
                            PRIMARY KEY (player, name))""");
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS wnpc_hidden (
                            player CHAR(36) NOT NULL,
                            npc    CHAR(36) NOT NULL,
                            PRIMARY KEY (player, npc))""");
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS wnpc_clicks (
                            player CHAR(36) NOT NULL,
                            npc    CHAR(36) NOT NULL,
                            amount BIGINT   NOT NULL,
                            PRIMARY KEY (player, npc))""");
            }
            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("Не удалось подключиться к " + label + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public PlayerData load(UUID playerId) {
        PlayerData data = new PlayerData(playerId);
        String id = playerId.toString();

        try {
            readPairs("SELECT action, stamp FROM wnpc_cooldowns WHERE player = ?", id, (key, value) -> {
                UUID actionId = parseUuid(key);
                if (actionId != null) data.getActionCooldowns().put(actionId, Long.parseLong(value));
            });
            readSingle("SELECT action FROM wnpc_executed WHERE player = ?", id, key -> {
                UUID actionId = parseUuid(key);
                if (actionId != null) data.getExecutedActions().add(actionId);
            });
            readPairs("SELECT name, value FROM wnpc_flags WHERE player = ?", id,
                    (key, value) -> data.getFlags().put(key, value));
            readSingle("SELECT npc FROM wnpc_hidden WHERE player = ?", id, key -> {
                UUID npcId = parseUuid(key);
                if (npcId != null) data.getHiddenNpcs().add(npcId);
            });
            readPairs("SELECT npc, amount FROM wnpc_clicks WHERE player = ?", id, (key, value) -> {
                UUID npcId = parseUuid(key);
                if (npcId != null) data.getNpcClicks().put(npcId, Long.parseLong(value));
            });
        } catch (SQLException e) {
            plugin.getLogger().warning("Ошибка чтения данных игрока " + playerId + ": " + e.getMessage());
        }

        data.clearDirty();
        return data;
    }

    @Override
    public void save(PlayerData data) {
        String id = data.getPlayerId().toString();

        try {
            deleteAll(id);

            batch("INSERT INTO wnpc_cooldowns (player, action, stamp) VALUES (?, ?, ?)", statement -> {
                for (Map.Entry<UUID, Long> entry : data.getActionCooldowns().entrySet()) {
                    statement.setString(1, id);
                    statement.setString(2, entry.getKey().toString());
                    statement.setLong(3, entry.getValue());
                    statement.addBatch();
                }
            });

            batch("INSERT INTO wnpc_executed (player, action) VALUES (?, ?)", statement -> {
                for (UUID actionId : data.getExecutedActions()) {
                    statement.setString(1, id);
                    statement.setString(2, actionId.toString());
                    statement.addBatch();
                }
            });

            batch("INSERT INTO wnpc_flags (player, name, value) VALUES (?, ?, ?)", statement -> {
                for (Map.Entry<String, String> entry : data.getFlags().entrySet()) {
                    statement.setString(1, id);
                    statement.setString(2, entry.getKey());
                    statement.setString(3, entry.getValue());
                    statement.addBatch();
                }
            });

            batch("INSERT INTO wnpc_hidden (player, npc) VALUES (?, ?)", statement -> {
                for (UUID npcId : data.getHiddenNpcs()) {
                    statement.setString(1, id);
                    statement.setString(2, npcId.toString());
                    statement.addBatch();
                }
            });

            batch("INSERT INTO wnpc_clicks (player, npc, amount) VALUES (?, ?, ?)", statement -> {
                for (Map.Entry<UUID, Long> entry : data.getNpcClicks().entrySet()) {
                    statement.setString(1, id);
                    statement.setString(2, entry.getKey().toString());
                    statement.setLong(3, entry.getValue());
                    statement.addBatch();
                }
            });
        } catch (SQLException e) {
            plugin.getLogger().warning("Ошибка записи данных игрока " + id + ": " + e.getMessage());
        }
    }

    private void deleteAll(String playerId) throws SQLException {
        for (String table : new String[]{"wnpc_cooldowns", "wnpc_executed", "wnpc_flags",
                "wnpc_hidden", "wnpc_clicks"}) {
            try (PreparedStatement statement =
                         connection.prepareStatement("DELETE FROM " + table + " WHERE player = ?")) {
                statement.setString(1, playerId);
                statement.executeUpdate();
            }
        }
    }

    private void batch(String sql, BatchFiller filler) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            filler.fill(statement);
            statement.executeBatch();
        }
    }

    private void readPairs(String sql, String playerId, PairConsumer consumer) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    consumer.accept(result.getString(1), result.getString(2));
                }
            }
        }
    }

    private void readSingle(String sql, String playerId, SingleConsumer consumer) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    consumer.accept(result.getString(1));
                }
            }
        }
    }

    @Override
    public void close() {
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }

    @Override
    public String getName() {
        return label;
    }

    private UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @FunctionalInterface
    private interface BatchFiller {
        void fill(PreparedStatement statement) throws SQLException;
    }

    @FunctionalInterface
    private interface PairConsumer {
        void accept(String key, String value);
    }

    @FunctionalInterface
    private interface SingleConsumer {
        void accept(String key);
    }
}
