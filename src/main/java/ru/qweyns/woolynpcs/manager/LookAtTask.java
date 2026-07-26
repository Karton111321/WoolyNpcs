package ru.qweyns.woolynpcs.manager;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import ru.qweyns.woolynpcs.WoolyNpcs;
import ru.qweyns.woolynpcs.model.WoolyNpc;

public class LookAtTask implements Runnable {
    private final WoolyNpcs plugin;

    public LookAtTask(WoolyNpcs plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        boolean resetDirection = plugin.getConfigManager().isLookResetDirection();
        float   pitchClamp     = plugin.getConfigManager().getLookPitchClamp();

        for (WoolyNpc npc : plugin.getNpcManager().getActiveNpcs()) {
            if (!npc.isSpawned() || npc.getBaseEntity() == null || !npc.getBaseEntity().isValid()) continue;

            if (npc.hasForcedLook()) {
                npc.rotateSmoothly(npc.getForcedLookYaw(), npc.getForcedLookPitch());
                continue;
            }

            if (!npc.isLookAtPlayer()) {
                if (resetDirection) npc.rotateLookAt(npc.getDefaultYaw(), npc.getDefaultPitch());
                continue;
            }

            Location npcLoc = npc.getBaseEntity().getLocation();
            double   radius = npc.getLookDistance();

            Player nearest      = null;
            double nearestDistSq = Double.MAX_VALUE;

            for (Player player : npcLoc.getNearbyPlayers(radius)) {
                double distSq = player.getLocation().distanceSquared(npcLoc);
                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq;
                    nearest       = player;
                }
            }

            if (nearest != null) {
                Location playerEye = nearest.getEyeLocation();
                double   npcEyeY   = npcLoc.getY() + npc.getEyeHeight();

                double dx = playerEye.getX() - npcLoc.getX();
                double dz = playerEye.getZ() - npcLoc.getZ();
                double dy = playerEye.getY() - npcEyeY;

                double distXZ  = Math.sqrt(dx * dx + dz * dz);
                float  yaw     = (float)  Math.toDegrees(Math.atan2(-dx, dz));
                float  pitch   = (float) -Math.toDegrees(Math.atan2(dy, distXZ));

                pitch = Math.max(-pitchClamp, Math.min(pitchClamp, pitch));
                npc.rotateLookAt(yaw, pitch);
            } else if (resetDirection) {
                npc.rotateLookAt(npc.getDefaultYaw(), npc.getDefaultPitch());
            }
        }
    }
}
