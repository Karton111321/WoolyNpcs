package ru.qweyns.woolynpcs.hook;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import ru.qweyns.woolynpcs.WoolyNpcs;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class WorldGuardHook {
    private WorldGuardHook() {}

    private static boolean initialized = false;
    private static boolean available   = false;

    private static Object regionContainer;
    private static Method createQuery;
    private static Method getApplicableRegions;
    private static Method adaptLocation;
    private static Method getRegions;

    public static Set<String> getRegionsAt(Location location) {
        if (location == null) return Collections.emptySet();
        if (!initialize()) return Collections.emptySet();

        try {
            Object query      = createQuery.invoke(regionContainer);
            Object weLocation = adaptLocation.invoke(null, location);
            Object regionSet  = getApplicableRegions.invoke(query, weLocation);
            if (regionSet == null) return Collections.emptySet();

            Object regions = getRegions.invoke(regionSet);
            if (!(regions instanceof Iterable<?> iterable)) return Collections.emptySet();

            Set<String> ids = new HashSet<>();
            for (Object region : iterable) {
                Method getId = region.getClass().getMethod("getId");
                Object id = getId.invoke(region);
                if (id != null) ids.add(String.valueOf(id));
            }
            return ids;
        } catch (Throwable t) {
            return Collections.emptySet();
        }
    }

    public static boolean isAvailable() {
        return initialize();
    }

    private static synchronized boolean initialize() {
        if (initialized) return available;
        initialized = true;

        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) return false;

        try {
            Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object worldGuard = worldGuardClass.getMethod("getInstance").invoke(null);
            Object platform   = worldGuardClass.getMethod("getPlatform").invoke(worldGuard);

            regionContainer = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            createQuery     = regionContainer.getClass().getMethod("createQuery");

            Class<?> weLocationClass = Class.forName("com.sk89q.worldedit.util.Location");
            Class<?> queryClass      = createQuery.getReturnType();
            getApplicableRegions     = queryClass.getMethod("getApplicableRegions", weLocationClass);

            Class<?> adapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            adaptLocation = adapterClass.getMethod("adapt", Location.class);

            getRegions = getApplicableRegions.getReturnType().getMethod("getRegions");

            available = true;
            WoolyNpcs.getInstance().getLogger().info("WorldGuard подключён — доступно условие [REGION:].");
        } catch (Throwable t) {
            WoolyNpcs.getInstance().getLogger().warning(
                    "WorldGuard найден, но его API несовместимо — условие [REGION:] работать не будет: "
                            + t.getMessage());
            available = false;
        }
        return available;
    }
}
