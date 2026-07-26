package ru.qweyns.woolynpcs.api;

import ru.qweyns.woolynpcs.WoolyNpcs;
import ru.qweyns.woolynpcs.model.ActionType;
import ru.qweyns.woolynpcs.model.WoolyNpc;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WoolyNpcsApi {
    private WoolyNpcsApi() {}

    private static final Map<String, CustomAction> CUSTOM_ACTIONS = new ConcurrentHashMap<>();

    public static void registerAction(String id, CustomAction action) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id действия не может быть пустым");
        }
        if (action == null) {
            throw new IllegalArgumentException("Обработчик действия не может быть null");
        }

        String key = normalize(id);
        if (key.indexOf(':') >= 0) {
            throw new IllegalArgumentException("id действия не может содержать ':'");
        }
        for (ActionType builtin : ActionType.values()) {
            if (builtin.name().equals(key)) {
                throw new IllegalArgumentException("Тип '" + key + "' уже занят встроенным действием");
            }
        }
        CUSTOM_ACTIONS.put(key, action);
    }

    public static void unregisterAction(String id) {
        if (id == null) return;
        CUSTOM_ACTIONS.remove(normalize(id));
    }

    public static CustomAction getAction(String id) {
        if (id == null) return null;
        return CUSTOM_ACTIONS.get(normalize(id));
    }

    public static boolean isRegistered(String id) {
        return getAction(id) != null;
    }

    public static Collection<String> getRegisteredActions() {
        return Collections.unmodifiableCollection(CUSTOM_ACTIONS.keySet());
    }

    private static String normalize(String id) {
        return id.trim().toUpperCase(Locale.ROOT);
    }

    public static WoolyNpc getNpc(String name) {
        WoolyNpcs plugin = WoolyNpcs.getInstance();
        if (plugin == null || plugin.getNpcManager() == null) return null;
        return plugin.getNpcManager().getNpcByName(name);
    }

    public static WoolyNpc getNpc(UUID id) {
        WoolyNpcs plugin = WoolyNpcs.getInstance();
        if (plugin == null || plugin.getNpcManager() == null) return null;
        return plugin.getNpcManager().getNpcById(id);
    }

    public static Collection<WoolyNpc> getNpcs() {
        WoolyNpcs plugin = WoolyNpcs.getInstance();
        if (plugin == null || plugin.getNpcManager() == null) return Collections.emptyList();
        return plugin.getNpcManager().getActiveNpcs();
    }
}
