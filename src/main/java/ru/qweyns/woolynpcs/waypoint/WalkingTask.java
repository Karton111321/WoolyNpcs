package ru.qweyns.woolynpcs.waypoint;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;
import ru.qweyns.woolynpcs.WoolyNpcs;
import ru.qweyns.woolynpcs.model.WoolyNpc;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class WalkingTask implements Runnable {
    private static final String WALK_ANIMATION = "walk";

    private final WoolyNpcs plugin;
    private final Map<UUID, WalkState> states = new HashMap<>();

    private final Map<UUID, Detour> detours = new HashMap<>();

    private final Map<UUID, WanderState> wanders = new HashMap<>();

    public WalkingTask(WoolyNpcs plugin) {
        this.plugin = plugin;
    }

    public void walkTo(WoolyNpc npc, Location target, double speed) {
        if (npc == null || target == null || target.getWorld() == null) return;
        detours.put(npc.getId(), new Detour(target.clone(), clampSpeed(speed)));
    }

    public void cancelWalkTo(UUID npcId) {
        detours.remove(npcId);
    }

    public void resetWander(UUID npcId) {
        wanders.remove(npcId);
    }

    public boolean hasDetour(UUID npcId) {
        return detours.containsKey(npcId);
    }

    private double clampSpeed(double speed) {
        return Math.max(0.01, Math.min(1.0, speed));
    }

    @Override
    public void run() {
        Collection<WoolyNpc> npcs = plugin.getNpcManager().getActiveNpcs();
        Set<UUID> alive = new HashSet<>();

        for (WoolyNpc npc : npcs) {
            alive.add(npc.getId());

            if (!npc.isSpawned() || npc.getBaseEntity() == null || !npc.getBaseEntity().isValid()) continue;

            Detour detour = detours.get(npc.getId());
            if (detour != null) {
                if (stepTowards(npc, detour.target, detour.speed)) {
                    detours.remove(npc.getId());
                    npc.restoreDefaultAnimation();
                }
                continue;
            }

            if (npc.getMovementMode() != WoolyNpc.MovementMode.NONE) {
                handleMovementMode(npc);
                continue;
            }

            WaypointPath path = npc.getWaypointPath();
            if (path == null || path.getWaypoints().isEmpty()) continue;

            WalkState state = states.computeIfAbsent(npc.getId(), k -> new WalkState());

            if (state.waitRemaining > 0) {
                state.waitRemaining--;
                continue;
            }

            if (state.currentIndex >= path.getWaypoints().size()) state.currentIndex = 0;

            Waypoint target = path.getWaypoints().get(state.currentIndex);
            Location current = npc.getBaseEntity().getLocation();
            Location dest = target.getLocation();

            if (dest.getWorld() == null || !dest.getWorld().equals(current.getWorld())) continue;

            double distSq = current.distanceSquared(dest);

            if (distSq < 0.25) {
                if (target.getAnimation() != null && !target.getAnimation().isEmpty()) {
                    npc.playAnimation(target.getAnimation());
                }

                state.waitRemaining = Math.max(0, target.getWaitTicks());

                state.currentIndex++;
                if (state.currentIndex >= path.getWaypoints().size()) {
                    if (path.isLoop()) {
                        state.currentIndex = 0;
                    } else {
                        state.currentIndex = path.getWaypoints().size() - 1;
                        states.remove(npc.getId());
                        npc.restoreDefaultAnimation();
                        continue;
                    }
                }

                Waypoint next = path.getWaypoints().get(state.currentIndex);
                if (next.getAnimation() == null || next.getAnimation().isEmpty()) {
                    npc.playAnimation(WALK_ANIMATION);
                }
                continue;
            }

            Vector delta = dest.toVector().subtract(current.toVector());
            if (delta.lengthSquared() <= 0) continue;

            Vector direction = delta.normalize();
            Vector movement  = direction.clone().multiply(path.getSpeed());

            Location newLoc = current.clone().add(movement);
            float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
            newLoc.setYaw(yaw);
            newLoc.setPitch(0);

            if (newLoc.getWorld() == null
                    || !newLoc.getWorld().isChunkLoaded(newLoc.getBlockX() >> 4, newLoc.getBlockZ() >> 4)) {
                continue;
            }

            teleportWithPassengers(npc, newLoc);
            npc.rotateSmoothly(yaw, 0);
        }

        states.keySet().retainAll(alive);
        detours.keySet().retainAll(alive);
        wanders.keySet().retainAll(alive);
    }

    private boolean stepTowards(WoolyNpc npc, Location dest, double speed) {
        return stepTowards(npc, dest, speed, false);
    }

    private boolean stepTowards(WoolyNpc npc, Location dest, double speed, boolean gravity) {
        Location current = npc.getBaseEntity().getLocation();
        if (dest.getWorld() == null || !dest.getWorld().equals(current.getWorld())) return true;

        double distSq = gravity ? horizontalDistanceSq(current, dest) : current.distanceSquared(dest);
        if (distSq < 0.25) return true;

        Vector delta = dest.toVector().subtract(current.toVector());
        if (gravity) delta.setY(0);
        if (delta.lengthSquared() <= 0) return true;

        Vector direction = delta.normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));

        Location newLoc = resolveStep(current, direction, speed, gravity);
        if (newLoc == null) return true;

        newLoc.setYaw(yaw);
        newLoc.setPitch(0);

        if (newLoc.getWorld() == null
                || !newLoc.getWorld().isChunkLoaded(newLoc.getBlockX() >> 4, newLoc.getBlockZ() >> 4)) {
            return true;
        }

        npc.playAnimation(WALK_ANIMATION);
        teleportWithPassengers(npc, newLoc);
        npc.rotateSmoothly(yaw, 0);
        return false;
    }

    private Location resolveStep(Location current, Vector direction, double speed, boolean gravity) {
        Location straight = current.clone().add(direction.clone().multiply(speed));
        if (!gravity) return straight;

        Location snapped = Terrain.snap(straight);
        if (snapped != null) return snapped;

        Vector alongX = new Vector(direction.getX(), 0, 0);
        if (alongX.lengthSquared() > 0) {
            Location candidate = Terrain.snap(
                    current.clone().add(alongX.normalize().multiply(speed)));
            if (candidate != null) return candidate;
        }

        Vector alongZ = new Vector(0, 0, direction.getZ());
        if (alongZ.lengthSquared() > 0) {
            Location candidate = Terrain.snap(
                    current.clone().add(alongZ.normalize().multiply(speed)));
            if (candidate != null) return candidate;
        }

        return null;
    }

    private void handleMovementMode(WoolyNpc npc) {
        Location current = npc.getBaseEntity().getLocation();
        boolean gravity = npc.getWaypointPath() != null && npc.getWaypointPath().isGravity();

        if (npc.getMovementMode() == WoolyNpc.MovementMode.FOLLOW) {
            Player nearest = null;
            double bestSq = Double.MAX_VALUE;
            for (Player player : current.getNearbyPlayers(npc.getMovementRange())) {
                if (plugin.getPlayerVisibilityManager().isHidden(player.getUniqueId(), npc.getId())) continue;
                double distSq = player.getLocation().distanceSquared(current);
                if (distSq < bestSq) { bestSq = distSq; nearest = player; }
            }

            if (nearest == null) {
                if (stepTowards(npc, npc.getLocation(), npc.getMovementSpeed(), gravity)) {
                    npc.restoreDefaultAnimation();
                }
                return;
            }

            if (bestSq <= 4.0) {
                npc.restoreDefaultAnimation();
                return;
            }
            stepTowards(npc, nearest.getLocation(), npc.getMovementSpeed(), gravity);
            return;
        }

        WanderState wander = wanders.computeIfAbsent(npc.getId(), k -> new WanderState());

        if (wander.pauseTicks > 0) {
            wander.pauseTicks--;
            return;
        }

        if (wander.target == null || stepTowards(npc, wander.target, npc.getMovementSpeed(), gravity)) {
            wander.target = randomAround(npc.getLocation(), npc.getMovementRange());
            wander.pauseTicks = 40 + ThreadLocalRandom.current().nextInt(80);
            npc.restoreDefaultAnimation();
        }
    }

    private Location randomAround(Location home, double range) {
        double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
        double distance = ThreadLocalRandom.current().nextDouble() * range;
        return home.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
    }

    private double horizontalDistanceSq(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private static final class Detour {
        final Location target;
        final double   speed;

        Detour(Location target, double speed) {
            this.target = target;
            this.speed  = speed;
        }
    }

    private void teleportWithPassengers(WoolyNpc npc, Location newLoc) {
        Entity base = npc.getBaseEntity();
        if (base == null || !base.isValid()) return;

        if (RetainPassengers.teleport(base, newLoc)) return;

        List<Entity> passengers = new ArrayList<>(base.getPassengers());
        for (Entity passenger : passengers) base.removePassenger(passenger);

        base.teleport(newLoc);

        for (Entity passenger : passengers) {
            if (passenger.isValid()) base.addPassenger(passenger);
        }
    }

    public void resetState(UUID npcId) {
        states.remove(npcId);
    }

    private static class WalkState {
        int currentIndex = 0;
        int waitRemaining = 0;
    }

    private static class WanderState {
        Location target = null;
        int pauseTicks = 0;
    }

    private static final class RetainPassengers {
        private static final Method TELEPORT;
        private static final Object FLAGS;

        static {
            Method method = null;
            Object flags  = null;
            try {
                Class<?> flagClass = Class.forName("io.papermc.paper.entity.TeleportFlag");
                Class<?> stateEnum = Class.forName("io.papermc.paper.entity.TeleportFlag$EntityState");

                Object retain = null;
                for (Object constant : stateEnum.getEnumConstants()) {
                    if (constant instanceof Enum<?> e && e.name().equals("RETAIN_PASSENGERS")) {
                        retain = constant;
                        break;
                    }
                }
                if (retain == null) throw new NoSuchFieldException("RETAIN_PASSENGERS");

                Object array = Array.newInstance(flagClass, 1);
                Array.set(array, 0, retain);

                method = Entity.class.getMethod("teleport", Location.class,
                        PlayerTeleportEvent.TeleportCause.class,
                        array.getClass());
                flags = array;
            } catch (Throwable ignored) {
                method = null;
                flags  = null;
            }
            TELEPORT = method;
            FLAGS    = flags;
        }

        static boolean teleport(Entity entity, Location location) {
            if (TELEPORT == null) return false;
            try {
                Object result = TELEPORT.invoke(entity, location,
                        PlayerTeleportEvent.TeleportCause.PLUGIN, FLAGS);
                return result instanceof Boolean b && b;
            } catch (Throwable ignored) {
                return false;
            }
        }
    }
}
