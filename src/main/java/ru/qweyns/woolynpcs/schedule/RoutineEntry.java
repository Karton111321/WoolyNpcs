package ru.qweyns.woolynpcs.schedule;

import org.bukkit.Location;
import ru.qweyns.woolynpcs.WoolyNpcs;
import ru.qweyns.woolynpcs.model.WoolyNpc;

import java.util.Locale;

public class RoutineEntry {
    public enum Type {
        WALK,
        TELEPORT,
        ANIMATION,
        SPAWN,
        DESPAWN
    }

    private final long   timeTicks;
    private final Type   type;
    private final String value;

    public RoutineEntry(long timeTicks, Type type, String value) {
        this.timeTicks = ((timeTicks % 24000) + 24000) % 24000;
        this.type      = type;
        this.value     = value == null ? "" : value.trim();
    }

    public long   getTimeTicks() { return timeTicks; }
    public Type   getType()      { return type;      }
    public String getValue()     { return value;     }

    public void apply(WoolyNpc npc) {
        switch (type) {
            case SPAWN -> {
                npc.setManualDespawn(false);
                WoolyNpcs.getInstance().getNpcManager().getArbiter().apply(npc);
            }
            case DESPAWN -> {
                if (npc.isSpawned()) npc.despawn(true);
            }
            case ANIMATION -> {
                if (!value.isEmpty()) npc.setDefaultAnimation(value);
            }
            case WALK, TELEPORT -> {
                Location target = parseLocation(npc);
                if (target == null) return;

                if (type == Type.TELEPORT) {
                    npc.teleport(target);
                } else if (npc.isSpawned()) {
                    WoolyNpcs.getInstance().getWalkingTask().walkTo(npc, target, 0.15);
                }
            }
        }
    }

    private Location parseLocation(WoolyNpc npc) {
        String[] parts = value.split(" ");
        if (parts.length < 3) return null;

        Location home = npc.getLocation();
        if (home.getWorld() == null) return null;

        try {
            return new Location(home.getWorld(),
                    Double.parseDouble(parts[0]),
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]));
        } catch (NumberFormatException e) {
            WoolyNpcs.getInstance().getLogger().warning(
                    "Распорядок NPC '" + npc.getName() + "': '" + value + "' не координаты.");
            return null;
        }
    }

    public String serialize() {
        return timeTicks + ":" + type.name() + ":" + value;
    }

    public static RoutineEntry parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.split(":", 3);
        if (parts.length < 2) return null;

        try {
            long time = Long.parseLong(parts[0].trim());
            Type type = Type.valueOf(parts[1].trim().toUpperCase(Locale.ROOT));
            return new RoutineEntry(time, type, parts.length >= 3 ? parts[2] : "");
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
