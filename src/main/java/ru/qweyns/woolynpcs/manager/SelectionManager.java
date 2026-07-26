package ru.qweyns.woolynpcs.manager;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import ru.qweyns.woolynpcs.WoolyNpcs;
import ru.qweyns.woolynpcs.model.WoolyNpc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SelectionManager {

    private final WoolyNpcs plugin;
    private final Map<UUID, UUID> selection = new ConcurrentHashMap<>();

    public SelectionManager(WoolyNpcs plugin) {
        this.plugin = plugin;
    }

    private double lookDistance() {
        return plugin.getConfigManager().getSelectionDistance();
    }

    private double coneThreshold() {
        return Math.cos(Math.toRadians(plugin.getConfigManager().getSelectionConeAngle()));
    }

    public void select(Player player, WoolyNpc npc) {
        selection.put(player.getUniqueId(), npc.getId());
    }

    public void clear(UUID playerId) {
        selection.remove(playerId);
    }

    public WoolyNpc getSelected(Player player) {
        UUID npcId = selection.get(player.getUniqueId());
        if (npcId == null) return null;

        WoolyNpc npc = plugin.getNpcManager().getNpcById(npcId);
        if (npc == null) {
            selection.remove(player.getUniqueId());
        }
        return npc;
    }

    public void forgetNpc(UUID npcId) {
        selection.values().removeIf(npcId::equals);
    }

    public WoolyNpc findLookedAt(Player player) {
        WoolyNpc byRay = rayTrace(player);
        if (byRay != null) return byRay;
        return searchCone(player);
    }

    private WoolyNpc rayTrace(Player player) {
        if (player.getWorld() == null) return null;
        try {
            Location eye = player.getEyeLocation();
            RayTraceResult result = player.getWorld().rayTraceEntities(
                    eye, eye.getDirection(), lookDistance(),
                    entity -> plugin.getNpcManager().getNpcByBaseEntityId(entity.getUniqueId()) != null);

            if (result == null) return null;
            Entity hit = result.getHitEntity();
            if (hit == null) return null;
            return plugin.getNpcManager().getNpcByBaseEntityId(hit.getUniqueId());
        } catch (Exception e) {
            return null;
        }
    }

    private WoolyNpc searchCone(Player player) {
        Location eye = player.getEyeLocation();
        if (eye.getWorld() == null) return null;
        Vector look = eye.getDirection().normalize();

        WoolyNpc best = null;
        double bestDistance = Double.MAX_VALUE;

        for (WoolyNpc npc : plugin.getNpcManager().getActiveNpcs()) {
            Location npcLoc = plugin.getNpcManager().effectiveLocation(npc);
            if (npcLoc.getWorld() == null || !npcLoc.getWorld().equals(eye.getWorld())) continue;

            Location aim = npcLoc.clone().add(0, 1.0, 0);
            Vector toNpc = aim.toVector().subtract(eye.toVector());

            double distance = toNpc.length();
            if (distance > lookDistance() || distance <= 0) continue;

            Vector direction = toNpc.normalize();
            double dot = direction.getX() * look.getX()
                    + direction.getY() * look.getY()
                    + direction.getZ() * look.getZ();
            if (dot < coneThreshold()) continue;

            if (distance < bestDistance) {
                bestDistance = distance;
                best = npc;
            }
        }
        return best;
    }
}
