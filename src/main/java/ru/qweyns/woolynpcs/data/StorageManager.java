package ru.qweyns.woolynpcs.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import ru.qweyns.woolynpcs.WoolyNpcs;
import ru.qweyns.woolynpcs.api.WoolyNpcsApi;
import org.bukkit.configuration.ConfigurationSection;
import ru.qweyns.woolynpcs.model.ActionType;
import ru.qweyns.woolynpcs.model.ClickType;
import ru.qweyns.woolynpcs.model.NpcAction;
import ru.qweyns.woolynpcs.model.WoolyNpc;
import ru.qweyns.woolynpcs.schedule.NpcSchedule;
import ru.qweyns.woolynpcs.schedule.RoutineEntry;
import ru.qweyns.woolynpcs.waypoint.Waypoint;
import ru.qweyns.woolynpcs.waypoint.WaypointPath;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class StorageManager {
    private final WoolyNpcs plugin;
    private final File      file;
    private final File      tempFile;

    private final AtomicBoolean dirty = new AtomicBoolean(false);

    public StorageManager(WoolyNpcs plugin) {
        this.plugin   = plugin;
        this.file     = new File(plugin.getDataFolder(), "npcs.yml");
        this.tempFile = new File(plugin.getDataFolder(), "npcs.tmp");
        if (!file.exists()) {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Не удалось создать папку плагина: " + parent);
            }
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Не удалось создать npcs.yml: " + e.getMessage());
            }
        }
    }

    public void markDirty() {
        dirty.set(true);
    }

    public void flushNow() {
        dirty.set(false);
        YamlConfiguration config = createConfigSync();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> saveConfigAsync(config));
    }

    public void flushIfDirty() {
        if (!dirty.compareAndSet(true, false)) return;
        YamlConfiguration config = createConfigSync();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> saveConfigAsync(config));
    }

    public static final int CONFIG_VERSION = 2;

    public YamlConfiguration createConfigSync() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("config-version", CONFIG_VERSION);

        for (WoolyNpc npc : plugin.getNpcManager().getActiveNpcs()) {
            writeNpc(config, "npcs." + npc.getId(), npc);
        }
        return config;
    }

    public void writeNpc(YamlConfiguration config, String base, WoolyNpc npc) {
            config.set(base + ".name",         npc.getName());
            config.set(base + ".modelId",      npc.getModelId());
            config.set(base + ".location",     npc.getLocation());
            config.set(base + ".defaultYaw",   npc.getDefaultYaw());
            config.set(base + ".defaultPitch", npc.getDefaultPitch());
            config.set(base + ".animation",    npc.getDefaultAnimation());

            config.set(base + ".lines", new ArrayList<>(npc.getHologramLines()));

            config.set(base + ".holo.offset-y",    npc.getHoloOffsetY());
            config.set(base + ".holo.scale",       npc.getHoloScale());
            config.set(base + ".holo.shadow",      npc.isHoloShadow());
            config.set(base + ".holo.background",  npc.isHoloBackground());
            config.set(base + ".holo.bg-alpha",    npc.getHoloBgAlpha());
            config.set(base + ".holo.billboard",   npc.getHoloBillboard().name());
            config.set(base + ".holo.alignment",   npc.getHoloAlignment().name());
            config.set(base + ".holo.see-through", npc.isHoloSeeThrough());
            config.set(base + ".holo.line-width",  npc.getHoloLineWidth());
            config.set(base + ".holo.view-range",  npc.getHoloViewRange());

            config.set(base + ".look-at-player",  npc.isLookAtPlayer());
            config.set(base + ".look-distance",   npc.getLookDistance());
            config.set(base + ".smooth-body",     npc.isSmoothBody());
            config.set(base + ".eye-height",      npc.getEyeHeight());

            config.set(base + ".visibility-range",        npc.getVisibilityRange());
            config.set(base + ".proximity-trigger-range", npc.getProximityTriggerRange());

            config.set(base + ".total-interactions", npc.getTotalInteractions());
            config.set(base + ".manual-despawn", npc.isManualDespawn());

            if (npc.getDialogId() != null) config.set(base + ".dialog-id", npc.getDialogId());
            if (npc.getRequiredPermission() != null) config.set(base + ".required-permission", npc.getRequiredPermission());

            if (npc.getClickRewardThreshold() > 0) {
                config.set(base + ".click-reward.threshold", npc.getClickRewardThreshold());
                config.set(base + ".click-reward.action", npc.getClickRewardAction());
            }

            if (npc.getSchedule() != null) {
                config.set(base + ".schedule.spawn-time", npc.getSchedule().getSpawnTimeTicks());
                config.set(base + ".schedule.despawn-time", npc.getSchedule().getDespawnTimeTicks());
            }

            if (npc.getWaypointPath() != null) {
                WaypointPath wp = npc.getWaypointPath();
                config.set(base + ".waypoint.loop", wp.isLoop());
                config.set(base + ".waypoint.speed", wp.getSpeed());
                config.set(base + ".waypoint.gravity", wp.isGravity());
                config.set(base + ".waypoint.points", serializeWaypoints(wp));
            }

            if (npc.getMovementMode() != WoolyNpc.MovementMode.NONE) {
                config.set(base + ".movement.mode",  npc.getMovementMode().name());
                config.set(base + ".movement.range", npc.getMovementRange());
                config.set(base + ".movement.speed", npc.getMovementSpeed());
            }

            if (!npc.getRoutine().isEmpty()) {
                List<String> entries = new ArrayList<>();
                for (RoutineEntry entry : npc.getRoutine()) entries.add(entry.serialize());
                config.set(base + ".routine", entries);
            }

            List<String> actions = new ArrayList<>();
            List<String> ids     = new ArrayList<>();

            for (NpcAction a : new ArrayList<>(npc.getActions())) {
                actions.add(a.getSaveString());
                ids.add(a.getUid().toString());
            }
            config.set(base + ".actions", actions);
            config.set(base + ".action-ids", ids);
            }

    public static List<String> serializeWaypoints(WaypointPath path) {
        List<String> result = new ArrayList<>();
        for (Waypoint w : path.getWaypoints()) {
            Location wl = w.getLocation();
            if (wl.getWorld() == null) continue;
            result.add(wl.getWorld().getName() + "," +
                    wl.getX() + "," + wl.getY() + "," + wl.getZ() + "," +
                    wl.getYaw() + "," + wl.getPitch() + "," +
                    w.getWaitTicks() + "," + (w.getAnimation() == null ? "" : w.getAnimation()));
        }
        return result;
    }

    public static void deserializeWaypoints(List<String> entries, WaypointPath into) {
        for (String entry : entries) {
            String[] p = entry.split(",");
            if (p.length < 6) continue;
            try {
                World w = Bukkit.getWorld(p[0]);
                if (w == null) continue;
                Location wl = new Location(w,
                        Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                        Float.parseFloat(p[4]), Float.parseFloat(p[5]));
                int    waitTicks = p.length > 6 ? Integer.parseInt(p[6]) : 20;
                String anim      = p.length > 7 ? p[7] : "";
                into.getWaypoints().add(new Waypoint(wl, Math.max(0, waitTicks), anim));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public synchronized void saveConfigAsync(YamlConfiguration config) {
        try {
            config.save(tempFile);
            try {
                Files.move(tempFile.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Ошибка при сохранении NPC: " + e.getMessage());
            try { Files.deleteIfExists(tempFile.toPath()); } catch (IOException ignored) {}
        }
    }

    public void saveNpcsSync() {
        dirty.set(false);
        saveConfigAsync(createConfigSync());
    }

    public void loadNpcs() {
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection npcsSection = config.getConfigurationSection("npcs");
        if (npcsSection == null) return;

        int version = config.getInt("config-version", 1);

        for (String key : npcsSection.getKeys(false)) {
            try {
                loadNpc(config, key);
            } catch (Exception e) {
                plugin.getLogger().warning("Не удалось загрузить NPC '" + key + "': " + e.getMessage());
            }
        }

        if (version < CONFIG_VERSION) migrateToCurrentVersion(version);
    }

    private void migrateToCurrentVersion(int fromVersion) {
        plugin.getLogger().info("Обновление формата данных: версия " + fromVersion
                + " -> " + CONFIG_VERSION + ". Создаётся резервная копия.");
        backupNow();

        Map<UUID, Set<UUID>> executedByPlayer = new HashMap<>();
        for (WoolyNpc npc : plugin.getNpcManager().getActiveNpcs()) {
            for (NpcAction action : npc.getActions()) {
                for (UUID playerId : action.getLegacyExecutedBy()) {
                    executedByPlayer.computeIfAbsent(playerId, k -> new HashSet<>()).add(action.getUid());
                }
            }
        }

        Map<UUID, Set<UUID>> hiddenByPlayer = readLegacyVisibility();

        if (!executedByPlayer.isEmpty() || !hiddenByPlayer.isEmpty()) {
            plugin.getPlayerDataManager().migrate(executedByPlayer, hiddenByPlayer);
            int players = new HashSet<>(executedByPlayer.keySet()).size()
                    + (int) hiddenByPlayer.keySet().stream()
                            .filter(id -> !executedByPlayer.containsKey(id)).count();
            plugin.getLogger().info("Перенесены персональные данные " + players + " игрок(ов).");
        }

        saveNpcsSync();
    }

    private Map<UUID, Set<UUID>> readLegacyVisibility() {
        Map<UUID, Set<UUID>> result = new HashMap<>();

        File visibility = new File(plugin.getDataFolder(), "visibility.yml");
        if (!visibility.exists()) return result;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(visibility);
        for (String playerKey : config.getKeys(false)) {
            UUID playerId;
            try {
                playerId = UUID.fromString(playerKey);
            } catch (IllegalArgumentException e) {
                continue;
            }

            Set<UUID> npcIds = new HashSet<>();
            for (String raw : config.getStringList(playerKey)) {
                try {
                    npcIds.add(UUID.fromString(raw));
                } catch (IllegalArgumentException ignored) {}
            }
            if (!npcIds.isEmpty()) result.put(playerId, npcIds);
        }

        File renamed = new File(plugin.getDataFolder(), "visibility.yml.migrated");
        if (!visibility.renameTo(renamed)) {
            plugin.getLogger().warning(
                    "Не удалось переименовать visibility.yml — удалите его вручную, иначе перенос повторится.");
        }
        return result;
    }

    public void backupNow() {
        int keep = plugin.getConfigManager().getBackupCount();
        if (keep <= 0 || !file.exists()) return;

        File backupDir = new File(plugin.getDataFolder(), "backups");
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            plugin.getLogger().warning("Не удалось создать папку backups/");
            return;
        }

        String stamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        File target = new File(backupDir, "npcs-" + stamp + ".yml");

        try {
            Files.copy(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось создать резервную копию: " + e.getMessage());
            return;
        }

        File[] existing = backupDir.listFiles((d, n) -> n.startsWith("npcs-") && n.endsWith(".yml"));
        if (existing == null || existing.length <= keep) return;

        java.util.Arrays.sort(existing, java.util.Comparator.comparing(File::getName));
        for (int i = 0; i < existing.length - keep; i++) {
            if (!existing[i].delete()) {
                plugin.getLogger().warning("Не удалось удалить старую копию " + existing[i].getName());
            }
        }
    }

    public void loadNpc(FileConfiguration config, String key) {
        String path = "npcs." + key;

        UUID id;
        try {
            id = UUID.fromString(key);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Пропущен NPC с некорректным UUID: " + key);
            return;
        }

        String   name    = config.getString(path + ".name");
        String   modelId = config.getString(path + ".modelId");
        Location loc     = config.getLocation(path + ".location");

        if (loc == null || loc.getWorld() == null) {
            plugin.getLogger().warning("Пропущен NPC '" + (name == null ? key : name)
                    + "': мир не найден или позиция повреждена.");
            return;
        }
        if (name == null || name.isBlank()) {
            plugin.getLogger().warning("Пропущен NPC " + key + ": отсутствует имя.");
            return;
        }
        if (modelId == null || modelId.isBlank()) {
            plugin.getLogger().warning("Пропущен NPC '" + name + "': отсутствует modelId.");
            return;
        }

        loc.setYaw  ((float) config.getDouble(path + ".defaultYaw",  loc.getYaw()));
        loc.setPitch((float) config.getDouble(path + ".defaultPitch", loc.getPitch()));

        WoolyNpc npc = new WoolyNpc(id, name, loc, modelId);

        npc.getHologramLines().addAll(config.getStringList(path + ".lines"));

        npc.setHoloOffsetY   (config.getDouble (path + ".holo.offset-y",   2.3));
        npc.setHoloScale     ((float) config.getDouble(path + ".holo.scale", 1.0));
        npc.setHoloShadow    (config.getBoolean(path + ".holo.shadow",      false));
        npc.setHoloBackground(config.getBoolean(path + ".holo.background",  true));
        npc.setHoloBgAlpha   (config.getInt    (path + ".holo.bg-alpha",    64));
        npc.setHoloSeeThrough(config.getBoolean(path + ".holo.see-through", false));
        npc.setHoloLineWidth (config.getInt    (path + ".holo.line-width",  200));
        npc.setHoloViewRange ((float) config.getDouble(path + ".holo.view-range", 64.0));

        try {
            npc.setHoloBillboard(Display.Billboard.valueOf(
                    config.getString(path + ".holo.billboard", "CENTER")));
        } catch (IllegalArgumentException | NullPointerException ignored) {}
        try {
            npc.setHoloAlignment(TextDisplay.TextAlignment.valueOf(
                    config.getString(path + ".holo.alignment", "CENTER")));
        } catch (IllegalArgumentException | NullPointerException ignored) {}

        npc.setLookAtPlayer (config.getBoolean(path + ".look-at-player",  false));
        npc.setLookDistance (config.getDouble (path + ".look-distance",   7.0));
        npc.setSmoothBody   (config.getBoolean(path + ".smooth-body",     true));
        npc.setEyeHeight    (config.getDouble (path + ".eye-height",      1.62));

        npc.setVisibilityRange      (config.getDouble(path + ".visibility-range",        0.0));
        npc.setProximityTriggerRange(config.getDouble(path + ".proximity-trigger-range", 5.0));

        npc.setTotalInteractions(Math.max(0, config.getLong(path + ".total-interactions", 0)));
        npc.setManualDespawn(config.getBoolean(path + ".manual-despawn", false));

        if (config.contains(path + ".dialog-id")) npc.setDialogId(config.getString(path + ".dialog-id"));
        if (config.contains(path + ".required-permission")) {
            npc.setRequiredPermission(config.getString(path + ".required-permission"));
        }

        if (config.contains(path + ".click-reward.threshold")) {
            npc.setClickRewardThreshold(config.getLong(path + ".click-reward.threshold"));
            npc.setClickRewardAction(config.getString(path + ".click-reward.action"));
        }

        if (config.contains(path + ".schedule.spawn-time")) {
            long spawnTime   = config.getLong(path + ".schedule.spawn-time");
            long despawnTime = config.getLong(path + ".schedule.despawn-time");
            npc.setSchedule(new NpcSchedule(spawnTime, despawnTime));
        }

        loadWaypoints(config, path, npc);

        try {
            npc.setMovementMode(WoolyNpc.MovementMode.valueOf(
                    config.getString(path + ".movement.mode", "NONE").toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {}
        npc.setMovementRange(config.getDouble(path + ".movement.range", 8.0));
        npc.setMovementSpeed(config.getDouble(path + ".movement.speed", 0.15));

        for (String raw : config.getStringList(path + ".routine")) {
            RoutineEntry entry = RoutineEntry.parse(raw);
            if (entry != null) npc.getRoutine().add(entry);
            else plugin.getLogger().warning("NPC '" + npc.getName()
                    + "': не удалось разобрать запись распорядка '" + raw + "'.");
        }

        npc.setDefaultAnimation(config.getString(path + ".animation", "idle"));

        loadActions(config, path, npc);

        plugin.getNpcManager().addNpc(npc);
    }

    private void loadWaypoints(FileConfiguration config, String path, WoolyNpc npc) {
        if (!config.contains(path + ".waypoint.points")) return;

        WaypointPath wp = new WaypointPath(npc.getName());
        wp.setLoop (config.getBoolean(path + ".waypoint.loop",  true));
        wp.setSpeed(config.getDouble (path + ".waypoint.speed", 0.15));
        wp.setGravity(config.getBoolean(path + ".waypoint.gravity", false));

        deserializeWaypoints(config.getStringList(path + ".waypoint.points"), wp);

        if (!wp.getWaypoints().isEmpty()) npc.setWaypointPath(wp);
    }

    private void loadActions(FileConfiguration config, String path, WoolyNpc npc) {
        List<String> actions = config.getStringList(path + ".actions");
        List<String> ids     = config.getStringList(path + ".action-ids");
        List<?> legacyExecuted = config.getList(path + ".actions-executed-by");

        for (int i = 0; i < actions.size(); i++) {
            UUID uid = null;
            if (i < ids.size()) {
                try { uid = UUID.fromString(ids.get(i)); } catch (IllegalArgumentException ignored) {}
            }

            NpcAction a = parseAction(actions.get(i), uid);
            if (a == null || a.isEmpty()) continue;

            List<String> executed = config.getStringList(path + ".executed-by." + a.getUid());
            if (executed.isEmpty() && legacyExecuted != null && i < legacyExecuted.size()
                    && legacyExecuted.get(i) instanceof List<?> uuidList) {
                for (Object uObj : uuidList) executed.add(String.valueOf(uObj));
            }

            for (String raw : executed) {
                try {
                    a.addExecutedBy(UUID.fromString(raw));
                } catch (IllegalArgumentException ignored) {}
            }

            npc.getActions().add(a);
        }
    }

    public static NpcAction parseAction(String actionStr) {
        return parseAction(actionStr, null);
    }

    public static NpcAction parseAction(String actionStr, UUID uid) {
        if (actionStr == null || actionStr.isBlank()) return null;
        String[] parts = actionStr.split(":", 3);

        try {
            if (parts.length == 3) {
                ClickType click = ClickType.valueOf(parts[0].trim().toUpperCase(Locale.ROOT));
                return build(uid, click, parts[1], parts[2]);
            }
            if (parts.length == 2) {
                return build(uid, ClickType.ANY, parts[0], parts[1]);
            }
        } catch (IllegalArgumentException ignored) {
        }
        return null;
    }

    private static NpcAction build(UUID uid, ClickType click, String typeName, String value) {
        String type = typeName.trim().toUpperCase(Locale.ROOT);
        try {
            return new NpcAction(uid, click, ActionType.valueOf(type), value);
        } catch (IllegalArgumentException e) {
            if (WoolyNpcsApi.isRegistered(type)) {
                return NpcAction.custom(uid, click, type, value);
            }
            return null;
        }
    }

    public void reload() {
        dirty.set(false);
        loadNpcs();
    }
}
