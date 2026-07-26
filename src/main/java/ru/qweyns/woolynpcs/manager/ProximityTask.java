package ru.qweyns.woolynpcs.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import ru.qweyns.woolynpcs.WoolyNpcs;
import ru.qweyns.woolynpcs.model.ClickType;
import ru.qweyns.woolynpcs.model.WoolyNpc;

import java.util.*;

public class ProximityTask implements Runnable {
    private final WoolyNpcs plugin;
    private final Map<UUID, Set<UUID>> nearbyLastTick = new HashMap<>();

    public ProximityTask(WoolyNpcs plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        Collection<WoolyNpc> npcs = plugin.getNpcManager().getActiveNpcs();
        Set<UUID> activeIds = new HashSet<>();
        SpawnArbiter arbiter = plugin.getNpcManager().getArbiter();

        for (WoolyNpc npc : npcs) {
            activeIds.add(npc.getId());

            arbiter.apply(npc);

            Location loc = plugin.getNpcManager().effectiveLocation(npc);
            if (loc == null || loc.getWorld() == null) continue;

            int proxCx = loc.getBlockX() >> 4;
            int proxCz = loc.getBlockZ() >> 4;
            if (!loc.getWorld().isChunkLoaded(proxCx, proxCz)) continue;

            handleProximityTriggers(npc, loc);
            handleTimeTrigger(npc, loc);
        }

        nearbyLastTick.keySet().retainAll(activeIds);
        lastTimeTrigger.keySet().retainAll(activeIds);
    }

    private final Map<UUID, Long> lastTimeTrigger = new HashMap<>();

    private void handleTimeTrigger(WoolyNpc npc, Location loc) {
        if (npc.getActions().stream().noneMatch(a -> a.getClickType() == ClickType.ON_TIME)) return;

        int intervalSeconds = plugin.getConfigManager().getOnTimeInterval();
        long now = System.currentTimeMillis();
        Long last = lastTimeTrigger.get(npc.getId());
        if (last != null && now - last < intervalSeconds * 1_000L) return;
        lastTimeTrigger.put(npc.getId(), now);

        for (Player p : loc.getNearbyPlayers(npc.getProximityTriggerRange())) {
            if (plugin.getPlayerVisibilityManager().isHidden(p.getUniqueId(), npc.getId())) continue;
            npc.getActions().stream()
                    .filter(a -> a.getClickType() == ClickType.ON_TIME)
                    .forEach(a -> a.execute(p, npc));
        }
    }

    private void handleProximityTriggers(WoolyNpc npc, Location loc) {
        if (!npc.hasProximityActions()) return;

        double range = npc.getProximityTriggerRange();
        Set<UUID> currentNear = new HashSet<>();

        for (Player p : loc.getNearbyPlayers(range)) {
            if (plugin.getPlayerVisibilityManager().isHidden(p.getUniqueId(), npc.getId())) continue;
            currentNear.add(p.getUniqueId());
        }

        Set<UUID> prev = nearbyLastTick.getOrDefault(npc.getId(), Collections.emptySet());

        for (UUID uid : currentNear) {
            if (!prev.contains(uid)) {
                Player p = Bukkit.getPlayer(uid);
                if (p != null) {
                    npc.getActions().stream()
                            .filter(a -> a.getClickType() == ClickType.ON_ENTER)
                            .forEach(a -> a.execute(p, npc));

                    if (npc.isSpawned()) {
                        String anim = plugin.getConfigManager().getProximityAnimation();
                        if (anim != null && !anim.isBlank()) npc.playAnimation(anim);
                    }
                }
            }
        }

        for (UUID uid : prev) {
            if (!currentNear.contains(uid)) {
                Player p = Bukkit.getPlayer(uid);
                if (p != null) {
                    npc.getActions().stream()
                            .filter(a -> a.getClickType() == ClickType.ON_LEAVE)
                            .forEach(a -> a.execute(p, npc));
                }
                if (npc.isSpawned()) npc.restoreDefaultAnimation();
            }
        }

        nearbyLastTick.put(npc.getId(), currentNear);
    }
}
