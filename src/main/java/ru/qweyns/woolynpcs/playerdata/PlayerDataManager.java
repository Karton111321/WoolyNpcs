package ru.qweyns.woolynpcs.playerdata;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.qweyns.woolynpcs.WoolyNpcs;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {
    private final WoolyNpcs plugin;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    private PlayerDataStore store;

    public PlayerDataManager(WoolyNpcs plugin) {
        this.plugin = plugin;
        this.store  = createStore();
        plugin.getLogger().info("Хранилище данных игроков: " + store.getName());
    }

    private PlayerDataStore createStore() {
        String type = plugin.getConfigManager().getStorageType().toLowerCase(Locale.ROOT);

        if (type.equals("sqlite")) {
            SqlPlayerDataStore sqlite = SqlPlayerDataStore.sqlite(plugin);
            if (sqlite != null && sqlite.initialize()) return sqlite;
            plugin.getLogger().warning(
                    "SQLite недоступен (нет драйвера org.sqlite.JDBC или файл занят) — используется YAML.");
            return new YamlPlayerDataStore(plugin);
        }

        if (type.equals("mysql")) {
            SqlPlayerDataStore mysql = SqlPlayerDataStore.mysql(plugin,
                    plugin.getConfigManager().getMysqlHost(),
                    plugin.getConfigManager().getMysqlPort(),
                    plugin.getConfigManager().getMysqlDatabase(),
                    plugin.getConfigManager().getMysqlUser(),
                    plugin.getConfigManager().getMysqlPassword());
            if (mysql != null && mysql.initialize()) return mysql;
            plugin.getLogger().warning(
                    "MySQL недоступен (нет драйвера или не удалось подключиться) — используется YAML.");
            return new YamlPlayerDataStore(plugin);
        }

        return new YamlPlayerDataStore(plugin);
    }

    public PlayerData get(UUID playerId) {
        return cache.computeIfAbsent(playerId, store::load);
    }

    public PlayerData get(Player player) {
        return get(player.getUniqueId());
    }

    public void onJoin(Player player) {
        get(player.getUniqueId());
    }

    public void onQuit(UUID playerId) {
        PlayerData data = cache.remove(playerId);
        if (data == null || !data.isDirty()) return;
        data.clearDirty();
        saveAsync(data);
    }

    public void flushDirty() {
        for (PlayerData data : cache.values()) {
            if (!data.isDirty()) continue;
            data.clearDirty();
            saveAsync(data);
        }
    }

    public void saveAllSync() {
        for (PlayerData data : cache.values()) {
            data.clearDirty();
            store.save(data);
        }
    }

    private void saveAsync(PlayerData data) {
        if (!plugin.isEnabled()) {
            store.save(data);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> store.save(data));
    }

    public void migrate(Map<UUID, java.util.Set<UUID>> executed,
                        Map<UUID, java.util.Set<UUID>> hidden) {
        java.util.Set<UUID> players = new java.util.HashSet<>(executed.keySet());
        players.addAll(hidden.keySet());

        for (UUID playerId : players) {
            PlayerData cached = cache.get(playerId);
            PlayerData data = cached != null ? cached : store.load(playerId);

            for (UUID actionId : executed.getOrDefault(playerId, java.util.Set.of())) {
                data.markExecuted(actionId);
            }
            for (UUID npcId : hidden.getOrDefault(playerId, java.util.Set.of())) {
                data.hideNpc(npcId);
            }

            data.clearDirty();
            store.save(data);
        }
    }

    public void forgetNpc(UUID npcId) {
        for (PlayerData data : cache.values()) {
            data.forgetNpc(npcId);
        }
    }

    public Collection<PlayerData> getLoaded() {
        return cache.values();
    }

    public PlayerDataStore getStore() {
        return store;
    }

    public void close() {
        saveAllSync();
        cache.clear();
        store.close();
    }
}
