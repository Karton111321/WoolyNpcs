package ru.qweyns.woolynpcs.manager;

import org.bukkit.Location;
import org.bukkit.World;
import ru.qweyns.woolynpcs.WoolyNpcs;
import ru.qweyns.woolynpcs.model.WoolyNpc;
import ru.qweyns.woolynpcs.schedule.NpcSchedule;

public class SpawnArbiter {
    private final WoolyNpcs plugin;

    public SpawnArbiter(WoolyNpcs plugin) {
        this.plugin = plugin;
    }

    private double hysteresis() {
        return Math.max(0.0, plugin.getConfigManager().getVisibilityHysteresis());
    }

    public boolean shouldBeSpawned(WoolyNpc npc) {
        if (npc.isManualDespawn()) return false;

        Location loc = npc.getLocation();
        World world = loc.getWorld();
        if (world == null) return false;
        if (!world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return false;

        NpcSchedule schedule = npc.getSchedule();
        if (schedule != null && !schedule.shouldBeSpawned(world.getTime())) return false;

        double range = npc.getVisibilityRange();
        if (range > 0) {
            double effective = npc.isSpawned() ? range + hysteresis() : range;
            return !loc.getNearbyPlayers(effective).isEmpty();
        }

        return true;
    }

    public void apply(WoolyNpc npc) {
        if (npc.isSpawned() && (npc.getBaseEntity() == null || !npc.getBaseEntity().isValid())) {
            npc.despawn(true);
        }

        boolean desired = shouldBeSpawned(npc);
        if (desired && !npc.isSpawned()) {
            npc.spawn();
        } else if (!desired && npc.isSpawned()) {
            npc.despawn(true);
        }
    }

    public String blockReason(WoolyNpc npc) {
        if (npc.isManualDespawn()) return "manual";

        Location loc = npc.getLocation();
        World world = loc.getWorld();
        if (world == null) return "world";
        if (!world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return "chunk";

        NpcSchedule schedule = npc.getSchedule();
        if (schedule != null && !schedule.shouldBeSpawned(world.getTime())) return "schedule";

        if (npc.getVisibilityRange() > 0 && loc.getNearbyPlayers(npc.getVisibilityRange()).isEmpty()) {
            return "visibility";
        }
        return null;
    }
}
