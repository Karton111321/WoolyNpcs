package ru.qweyns.woolynpcs.manager;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Chunk;
import org.bukkit.Location;
import ru.qweyns.woolynpcs.WoolyNpcs;
import ru.qweyns.woolynpcs.model.WoolyNpc;
import ru.qweyns.woolynpcs.util.ColorUtil;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NpcManager {
    private final ConcurrentHashMap<UUID, WoolyNpc> activeNpcs      = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, WoolyNpc> baseEntityCache = new ConcurrentHashMap<>();
    private final Set<WoolyNpc> papiHolograms = ConcurrentHashMap.newKeySet();

    private final SpawnArbiter arbiter;

    public NpcManager(WoolyNpcs plugin) {
        this.arbiter = new SpawnArbiter(plugin);
    }

    public SpawnArbiter getArbiter() {
        return arbiter;
    }

    public void registerPapiHologram(WoolyNpc npc) {
        papiHolograms.add(npc);
    }

    public void unregisterPapiHologram(WoolyNpc npc) {
        papiHolograms.remove(npc);
    }

    public void updatePapiHolograms() {
        if (!WoolyNpcs.getInstance().hasPapi()) return;

        for (WoolyNpc npc : papiHolograms) {
            if (!npc.isSpawned() || npc.getHologram() == null || !npc.getHologram().isValid()) continue;

            double viewRange = Math.max(16.0, npc.getHoloViewRange());
            if (npc.getHologram().getLocation().getNearbyPlayers(viewRange).isEmpty()) continue;

            try {
                String rawText = String.join("\n", npc.getHologramLines());
                String parsedText = PlaceholderAPI.setPlaceholders(null, rawText);
                npc.getHologram().text(ColorUtil.format(parsedText));
            } catch (Exception e) {
                WoolyNpcs.getInstance().getLogger().warning(
                        "Ошибка обновления PAPI-голограммы NPC '" + npc.getName() + "': " + e.getMessage());
            }
        }
    }

    public void addNpc(WoolyNpc npc) {
        WoolyNpc previous = activeNpcs.put(npc.getId(), npc);
        if (previous != null && previous != npc) {
            previous.despawn(true);
            papiHolograms.remove(previous);
        }
        arbiter.apply(npc);
    }

    public void updateCache(WoolyNpc npc) {
        if (npc.getBaseEntity() != null) {
            baseEntityCache.put(npc.getBaseEntity().getUniqueId(), npc);
        }
    }

    public void removeFromCache(UUID entityId) {
        baseEntityCache.remove(entityId);
    }

    public void removeNpc(UUID id) {
        WoolyNpc npc = activeNpcs.remove(id);
        if (npc != null) {
            npc.despawn(true);
            papiHolograms.remove(npc);
        }
    }

    public Set<UUID> getLiveEntityIds() {
        Set<UUID> ids = new HashSet<>();
        for (WoolyNpc npc : activeNpcs.values()) {
            if (npc.getBaseEntity() != null) ids.add(npc.getBaseEntity().getUniqueId());
            if (npc.getHologram()   != null) ids.add(npc.getHologram().getUniqueId());
        }
        return ids;
    }

    public void despawnAll() {
        activeNpcs.values().forEach(npc -> npc.despawn(true));
        activeNpcs.clear();
        baseEntityCache.clear();
        papiHolograms.clear();
    }

    public void handleChunkLoad(Chunk chunk) {
        for (WoolyNpc npc : activeNpcs.values()) {
            if (npc.isSpawned()) continue;
            if (locationInChunk(npc.getLocation(), chunk)) {
                arbiter.apply(npc);
            }
        }
    }

    public void handleChunkUnload(Chunk chunk) {
        for (WoolyNpc npc : activeNpcs.values()) {
            if (!npc.isSpawned()) continue;
            if (locationInChunk(npc.getLocation(), chunk) || locationInChunk(effectiveLocation(npc), chunk)) {
                npc.despawn(false);
            }
        }
    }

    public Location effectiveLocation(WoolyNpc npc) {
        if (npc.isSpawned() && npc.getBaseEntity() != null && npc.getBaseEntity().isValid()) {
            return npc.getBaseEntity().getLocation();
        }
        return npc.getLocation();
    }

    private boolean locationInChunk(Location loc, Chunk chunk) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(chunk.getWorld().getName())) return false;
        return (loc.getBlockX() >> 4) == chunk.getX()
                && (loc.getBlockZ() >> 4) == chunk.getZ();
    }

    public Collection<WoolyNpc> getActiveNpcs() { return activeNpcs.values(); }

    public WoolyNpc getNpcByName(String name) {
        return activeNpcs.values().stream()
                .filter(n -> n.getName().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    public WoolyNpc getNpcByBaseEntityId(UUID entityId) {
        return baseEntityCache.get(entityId);
    }

    public WoolyNpc getNpcById(UUID id) {
        return activeNpcs.get(id);
    }
}
