package ru.qweyns.woolynpcs.util;

import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RegistryUtil {
    private RegistryUtil() {}

    private static final Object MISS = new Object();

    private static final Object SOUND_REGISTRY    = findRegistry("SOUNDS", "SOUND");
    private static final Object PARTICLE_REGISTRY = findRegistry("PARTICLE_TYPE", "PARTICLE");
    private static final Object EFFECT_REGISTRY   = findRegistry("POTION_EFFECT_TYPE", "EFFECT", "MOB_EFFECT");

    private static final Method REGISTRY_GET = findRegistryGet();

    private static final Map<String, Object> CACHE = new ConcurrentHashMap<>();

    public static Sound resolveSound(String name) {
        return resolve("sound", SOUND_REGISTRY, Sound.class, name, () -> Sound.valueOf(normalizeEnum(name)));
    }

    public static Particle resolveParticle(String name) {
        return resolve("particle", PARTICLE_REGISTRY, Particle.class, name, () -> Particle.valueOf(normalizeEnum(name)));
    }

    public static PotionEffectType resolveEffect(String name) {
        return resolve("effect", EFFECT_REGISTRY, PotionEffectType.class, name,
                () -> PotionEffectType.getByName(normalizeEnum(name)));
    }

    private static <T> T resolve(String cachePrefix, Object registry, Class<T> type,
                                 String name, LegacyLookup<T> legacy) {
        if (name == null || name.isBlank()) return null;

        String cacheKey = cachePrefix + '/' + name.toLowerCase(Locale.ROOT);
        Object cached = CACHE.get(cacheKey);
        if (cached != null) return cached == MISS ? null : type.cast(cached);

        T found = lookupInRegistry(registry, type, name);
        if (found == null) {
            try {
                found = legacy.get();
            } catch (Throwable ignored) {
                found = null;
            }
        }

        CACHE.put(cacheKey, found == null ? MISS : found);
        return found;
    }

    private static <T> T lookupInRegistry(Object registry, Class<T> type, String name) {
        if (registry == null) return null;

        if (name.indexOf(':') >= 0 || name.indexOf('.') >= 0) {
            T byKey = getByKey(registry, type, name.toLowerCase(Locale.ROOT));
            if (byKey != null) return byKey;
        }

        String wanted = normalizeEnum(name);
        if (!(registry instanceof Iterable<?> iterable)) return null;

        for (Object element : iterable) {
            if (!(element instanceof Keyed keyed)) continue;
            String key = keyed.getKey().getKey().replace('.', '_').replace('/', '_');
            if (key.equalsIgnoreCase(wanted)) return type.cast(element);
        }
        return null;
    }

    private static <T> T getByKey(Object registry, Class<T> type, String rawKey) {
        if (REGISTRY_GET == null) return null;
        try {
            NamespacedKey key = NamespacedKey.fromString(rawKey);
            if (key == null) return null;
            Object result = REGISTRY_GET.invoke(registry, key);
            return result == null ? null : type.cast(result);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String normalizeEnum(String name) {
        String value = name;
        int colon = value.indexOf(':');
        if (colon >= 0) value = value.substring(colon + 1);
        return value.replace('.', '_').replace('/', '_').toUpperCase(Locale.ROOT);
    }

    private static Object findRegistry(String... fieldNames) {
        try {
            Class<?> registryClass = Class.forName("org.bukkit.Registry");
            for (String fieldName : fieldNames) {
                try {
                    return registryClass.getField(fieldName).get(null);
                } catch (NoSuchFieldException ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Method findRegistryGet() {
        try {
            return Class.forName("org.bukkit.Registry").getMethod("get", NamespacedKey.class);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @FunctionalInterface
    private interface LegacyLookup<T> {
        T get();
    }
}
