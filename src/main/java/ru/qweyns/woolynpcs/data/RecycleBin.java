package ru.qweyns.woolynpcs.data;

import org.bukkit.configuration.file.YamlConfiguration;
import ru.qweyns.woolynpcs.WoolyNpcs;
import ru.qweyns.woolynpcs.model.WoolyNpc;

import java.util.ArrayDeque;
import java.util.Deque;

public class RecycleBin {
    private static final int CAPACITY = 10;

    private final WoolyNpcs plugin;
    private final Deque<Entry> entries = new ArrayDeque<>();

    public RecycleBin(WoolyNpcs plugin) {
        this.plugin = plugin;
    }

    public void remember(WoolyNpc npc) {
        YamlConfiguration snapshot = new YamlConfiguration();
        plugin.getStorageManager().writeNpc(snapshot, "npcs." + npc.getId(), npc);

        entries.push(new Entry(npc.getId().toString(), npc.getName(),
                snapshot, System.currentTimeMillis()));

        while (entries.size() > CAPACITY) entries.removeLast();
    }

    public String restoreLast() {
        purgeExpired();

        Entry entry = entries.poll();
        if (entry == null) return null;

        if (plugin.getNpcManager().getNpcByName(entry.name) != null) {
            plugin.getLogger().warning("Восстановление NPC '" + entry.name
                    + "' невозможно: имя уже занято.");
            return null;
        }

        plugin.getStorageManager().loadNpc(entry.snapshot, entry.id);
        plugin.getStorageManager().flushNow();
        return entry.name;
    }

    public String peekName() {
        purgeExpired();
        Entry entry = entries.peek();
        return entry == null ? null : entry.name;
    }

    public int size() {
        purgeExpired();
        return entries.size();
    }

    public void clear() {
        entries.clear();
    }

    private void purgeExpired() {
        int minutes = plugin.getConfigManager().getUndoMinutes();
        if (minutes <= 0) { entries.clear(); return; }

        long deadline = System.currentTimeMillis() - minutes * 60_000L;
        entries.removeIf(entry -> entry.removedAt < deadline);
    }

    private record Entry(String id, String name, YamlConfiguration snapshot, long removedAt) {}
}
