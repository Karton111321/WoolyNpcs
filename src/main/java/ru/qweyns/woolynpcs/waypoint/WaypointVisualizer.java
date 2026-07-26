package ru.qweyns.woolynpcs.waypoint;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.qweyns.woolynpcs.WoolyNpcs;
import ru.qweyns.woolynpcs.model.WoolyNpc;
import ru.qweyns.woolynpcs.util.ColorUtil;
import ru.qweyns.woolynpcs.util.RegistryUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WaypointVisualizer {
    private static final long REDRAW_INTERVAL = 10L;
    private static final double STEP = 0.5;
    private static final long AUTO_STOP_TICKS = 20L * 120;

    private final WoolyNpcs plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public WaypointVisualizer(WoolyNpcs plugin) {
        this.plugin = plugin;
    }

    public void toggle(Player player, WoolyNpc npc) {
        Session existing = sessions.remove(player.getUniqueId());
        if (existing != null) {
            existing.task.cancel();
            if (existing.npcId.equals(npc.getId())) {
                player.sendMessage(ColorUtil.format(
                        plugin.getConfigManager().getMessage("waypoint-show-off", "name", npc.getName())));
                return;
            }
        }

        long started = System.currentTimeMillis();
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) { stop(player.getUniqueId()); return; }

            WoolyNpc current = plugin.getNpcManager().getNpcById(npc.getId());
            if (current == null || current.getWaypointPath() == null) { stop(player.getUniqueId()); return; }

            if (System.currentTimeMillis() - started > AUTO_STOP_TICKS * 50L) {
                stop(player.getUniqueId());
                player.sendMessage(ColorUtil.format(
                        plugin.getConfigManager().getMessage("waypoint-show-off", "name", npc.getName())));
                return;
            }

            draw(player, current.getWaypointPath());
        }, 0L, REDRAW_INTERVAL);

        sessions.put(player.getUniqueId(), new Session(npc.getId(), task));
        player.sendMessage(ColorUtil.format(
                plugin.getConfigManager().getMessage("waypoint-show-on", "name", npc.getName())));
    }

    private void draw(Player player, WaypointPath path) {
        Particle line = RegistryUtil.resolveParticle("HAPPY_VILLAGER");
        Particle node = RegistryUtil.resolveParticle("FLAME");
        if (line == null || node == null) return;

        var points = path.getWaypoints();
        for (int i = 0; i < points.size(); i++) {
            Location from = points.get(i).getLocation();
            if (from.getWorld() == null || !from.getWorld().equals(player.getWorld())) continue;

            player.spawnParticle(node, from.clone().add(0, 0.5, 0), 3, 0.1, 0.2, 0.1, 0);

            boolean last = i == points.size() - 1;
            if (last && !path.isLoop()) continue;

            Location to = points.get(last ? 0 : i + 1).getLocation();
            if (to.getWorld() == null || !to.getWorld().equals(from.getWorld())) continue;

            drawSegment(player, line, from, to);
        }
    }

    private void drawSegment(Player player, Particle particle, Location from, Location to) {
        double distance = from.distance(to);
        if (distance <= 0) return;

        int steps = (int) Math.min(distance / STEP, 200);
        for (int i = 0; i <= steps; i++) {
            double ratio = steps == 0 ? 0 : (double) i / steps;
            Location point = from.clone().add(
                    (to.getX() - from.getX()) * ratio,
                    (to.getY() - from.getY()) * ratio + 0.3,
                    (to.getZ() - from.getZ()) * ratio);
            player.spawnParticle(particle, point, 1, 0, 0, 0, 0);
        }
    }

    public void stop(UUID playerId) {
        Session session = sessions.remove(playerId);
        if (session != null) session.task.cancel();
    }

    public void stopAll() {
        sessions.values().forEach(session -> session.task.cancel());
        sessions.clear();
    }

    private record Session(UUID npcId, BukkitTask task) {}
}
