package ru.qweyns.woolynpcs.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.qweyns.woolynpcs.WoolyNpcs;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class ConfigManager {
    private final WoolyNpcs     plugin;
    private FileConfiguration   config;
    private FileConfiguration   messages;
    private FileConfiguration   guiConfig;

    private File                messagesFile;
    private File                guiFile;

    public ConfigManager(WoolyNpcs plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        this.config = plugin.getConfig();
        loadMessages();
        loadGui();
    }

    private void loadMessages() {
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) plugin.saveResource("messages.yml", false);
        messages = YamlConfiguration.loadConfiguration(messagesFile);

        InputStream defMessagesStream = plugin.getResource("messages.yml");
        if (defMessagesStream != null) {
            messages.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defMessagesStream, StandardCharsets.UTF_8)));
        }
    }

    private void loadGui() {
        guiFile = new File(plugin.getDataFolder(), "gui.yml");
        if (!guiFile.exists()) plugin.saveResource("gui.yml", false);
        guiConfig = YamlConfiguration.loadConfiguration(guiFile);

        InputStream defGuiStream = plugin.getResource("gui.yml");
        if (defGuiStream != null) {
            guiConfig.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defGuiStream, StandardCharsets.UTF_8)));
        }
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        loadMessages();
        loadGui();
    }

    public int     getStartupDelay()           { return config.getInt    ("settings.startup-delay-ticks",             20);   }
    public boolean isAutosaveEnabled()         { return config.getBoolean("settings.autosave-enabled",                true); }
    public int     getAutosaveInterval()       { return config.getInt    ("settings.autosave-interval-minutes",       15);   }
    public int     getInteractionCooldown()    { return config.getInt    ("settings.interaction-cooldown-seconds",    2);    }
    public boolean disableCooldownMsg()        { return config.getBoolean("settings.disable-cooldown-message",        false);}
    public List<String> getBlockedCommands() {
        return config.getStringList("settings.blocked-commands").stream()
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public int     getLookUpdateInterval()     { return config.getInt    ("settings.look-update-interval-ticks",      2);    }
    public boolean isLookResetDirection()      { return config.getBoolean("settings.look-reset-direction",            true); }
    public int     getProximityUpdateInterval(){ return config.getInt    ("settings.proximity-update-interval-ticks", 10);  }
    public int     getMaxModelRetries()        { return Math.max(1, config.getInt("settings.max-model-retries",      10)); }

    public int getSaveFlushInterval() {
        return Math.max(20, config.getInt("settings.save-flush-interval-ticks", 100));
    }

    public int getListPageSize() {
        return Math.max(1, Math.min(50, config.getInt("settings.list-page-size", 8)));
    }

    public int getUndoMinutes() {
        return Math.max(0, config.getInt("settings.undo-minutes", 10));
    }

    public int getOnTimeInterval() {
        return Math.max(1, config.getInt("settings.on-time-interval-seconds", 60));
    }

    public double getVisibilityHysteresis() {
        return Math.max(0.0, config.getDouble("settings.visibility-range-hysteresis", 2.0));
    }

    public int getDialogTimeoutSeconds() {
        return Math.max(0, config.getInt("settings.dialog-timeout-seconds", 60));
    }

    public String getStorageType()     { return config.getString ("storage.type", "yaml"); }
    public String getMysqlHost()       { return config.getString ("storage.mysql.host", "localhost"); }
    public int    getMysqlPort()       { return config.getInt    ("storage.mysql.port", 3306); }
    public String getMysqlDatabase()   { return config.getString ("storage.mysql.database", "minecraft"); }
    public String getMysqlUser()       { return config.getString ("storage.mysql.user", "root"); }
    public String getMysqlPassword()   { return config.getString ("storage.mysql.password", ""); }

    public int getPlayerFlushInterval() {
        return Math.max(100, config.getInt("storage.player-flush-interval-ticks", 600));
    }

    public int getBackupCount() {
        return Math.max(0, Math.min(50, config.getInt("storage.backup-count", 3)));
    }

    public double getDialogMaxDistance() {
        return Math.max(0.0, config.getDouble("settings.dialog-max-distance", 10.0));
    }

    public float getLookPitchClamp() {
        double raw = config.getDouble("settings.look-pitch-clamp", 45.0);
        return (float) Math.max(0.0, Math.min(90.0, raw));
    }

    public String getInteractionSound()       { return config.getString ("settings.interaction-sound",                "");   }
    public float  getInteractionSoundVolume() { return (float) config.getDouble("settings.interaction-sound-volume", 0.5);  }
    public float  getInteractionSoundPitch()  { return (float) config.getDouble("settings.interaction-sound-pitch",  1.0);  }

    public String getMessage(String key, String... placeholders) {
        String prefix  = messages.getString("prefix", "");
        String message = messages.getString(key);
        if (message == null && messages.getDefaults() != null) {
            message = messages.getDefaults().getString(key);
        }
        if (message == null) return "[WoolyNpcs] Отсутствует ключ: " + key;

        message = message.replace("%prefix%", prefix);

        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            message = message
                    .replace("{" + placeholders[i] + "}", placeholders[i + 1])
                    .replace("%" + placeholders[i] + "%", placeholders[i + 1]);
        }
        return message;
    }

    public String getProximityAnimation() {
        return config.getString("settings.proximity-animation", "wave");
    }

    public String getUsage(String usageKey, String label) {
        String raw = messages.getString("usage." + usageKey);
        if (raw == null && messages.getDefaults() != null) {
            raw = messages.getDefaults().getString("usage." + usageKey);
        }
        if (raw == null) return "/wnpc " + usageKey;
        return raw.replace("{label}", label);
    }

    public List<String> getHelpLines(String label) {
        return messages.getStringList("help-lines").stream()
                .map(l -> l.replace("{label}", label))
                .collect(Collectors.toList());
    }

    public String formatBool(boolean value) {
        return getMessage(value ? "bool-true" : "bool-false", "value", String.valueOf(value));
    }

    public String formatSpawned(boolean spawned) {
        return getMessage(spawned ? "spawned-yes" : "spawned-no", "status", spawned ? "✔" : "✘");
    }

    public FileConfiguration getGuiConfig() {
        return guiConfig;
    }
}
