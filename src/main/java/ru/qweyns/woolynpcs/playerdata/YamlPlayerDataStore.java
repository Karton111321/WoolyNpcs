package ru.qweyns.woolynpcs.playerdata;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.qweyns.woolynpcs.WoolyNpcs;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class YamlPlayerDataStore implements PlayerDataStore {
    private final WoolyNpcs plugin;
    private final File folder;

    public YamlPlayerDataStore(WoolyNpcs plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "playerdata");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Не удалось создать папку playerdata/");
        }
    }

    private File fileOf(UUID playerId) {
        return new File(folder, playerId + ".yml");
    }

    @Override
    public PlayerData load(UUID playerId) {
        PlayerData data = new PlayerData(playerId);

        File file = fileOf(playerId);
        if (!file.exists()) return data;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection cooldowns = config.getConfigurationSection("cooldowns");
        if (cooldowns != null) {
            for (String key : cooldowns.getKeys(false)) {
                UUID actionId = parseUuid(key);
                if (actionId != null) data.getActionCooldowns().put(actionId, cooldowns.getLong(key));
            }
        }

        for (String raw : config.getStringList("executed")) {
            UUID actionId = parseUuid(raw);
            if (actionId != null) data.getExecutedActions().add(actionId);
        }

        ConfigurationSection flags = config.getConfigurationSection("flags");
        if (flags != null) {
            for (String key : flags.getKeys(false)) {
                String value = flags.getString(key);
                if (value != null) data.getFlags().put(key, value);
            }
        }

        for (String raw : config.getStringList("hidden-npcs")) {
            UUID npcId = parseUuid(raw);
            if (npcId != null) data.getHiddenNpcs().add(npcId);
        }

        ConfigurationSection clicks = config.getConfigurationSection("clicks");
        if (clicks != null) {
            for (String key : clicks.getKeys(false)) {
                UUID npcId = parseUuid(key);
                if (npcId != null) data.getNpcClicks().put(npcId, clicks.getLong(key));
            }
        }

        data.clearDirty();
        return data;
    }

    @Override
    public void save(PlayerData data) {
        File file = fileOf(data.getPlayerId());

        if (data.isEmpty()) {
            if (file.exists() && !file.delete()) {
                plugin.getLogger().warning("Не удалось удалить пустой " + file.getName());
            }
            return;
        }

        YamlConfiguration config = new YamlConfiguration();

        for (Map.Entry<UUID, Long> entry : data.getActionCooldowns().entrySet()) {
            config.set("cooldowns." + entry.getKey(), entry.getValue());
        }

        List<String> executed = new ArrayList<>();
        for (UUID id : data.getExecutedActions()) executed.add(id.toString());
        config.set("executed", executed);

        for (Map.Entry<String, String> entry : data.getFlags().entrySet()) {
            config.set("flags." + entry.getKey(), entry.getValue());
        }

        List<String> hidden = new ArrayList<>();
        for (UUID id : data.getHiddenNpcs()) hidden.add(id.toString());
        config.set("hidden-npcs", hidden);

        for (Map.Entry<UUID, Long> entry : data.getNpcClicks().entrySet()) {
            config.set("clicks." + entry.getKey(), entry.getValue());
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить " + file.getName() + ": " + e.getMessage());
        }
    }

    @Override
    public void close() {
    }

    @Override
    public String getName() {
        return "YAML (playerdata/)";
    }

    private UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
