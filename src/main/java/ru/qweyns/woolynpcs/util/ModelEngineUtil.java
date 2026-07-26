package ru.qweyns.woolynpcs.util;

import com.ticxo.modelengine.api.ModelEngineAPI;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ModelEngineUtil {
    private ModelEngineUtil() {}

    private static final String[] KEY_METHODS = {"getKeys", "keySet", "getIds", "getRegisteredModels"};
    private static final String[] VALUE_METHODS = {"getValues", "values", "getBlueprints"};
    private static final String[] ANIMATION_METHODS = {"getAnimations", "getAnimationMap"};
    private static final String[] NAME_METHODS = {"getName", "getId", "getModelId"};

    public static List<String> getModelIds() {
        Object registry = registry();
        if (registry == null) return Collections.emptyList();

        Object keys = invokeFirst(registry, KEY_METHODS);
        List<String> result = toStringList(keys);
        if (!result.isEmpty()) return result;

        Object values = invokeFirst(registry, VALUE_METHODS);
        for (Object blueprint : toCollection(values)) {
            String name = asString(invokeFirst(blueprint, NAME_METHODS));
            if (name != null && !name.isBlank()) result.add(name);
        }
        return result;
    }

    public static List<String> getAnimations(String modelId) {
        if (modelId == null || modelId.isBlank()) return Collections.emptyList();

        Object registry = registry();
        if (registry == null) return Collections.emptyList();

        Object blueprint;
        try {
            blueprint = registry.getClass().getMethod("get", String.class).invoke(registry, modelId);
        } catch (Throwable t) {
            return Collections.emptyList();
        }
        if (blueprint == null) return Collections.emptyList();

        Object animations = invokeFirst(blueprint, ANIMATION_METHODS);
        if (animations instanceof Map<?, ?> map) {
            List<String> result = new ArrayList<>();
            for (Object key : map.keySet()) {
                if (key != null) result.add(String.valueOf(key));
            }
            return result;
        }
        return toStringList(animations);
    }

    private static Object registry() {
        try {
            ModelEngineAPI api = ModelEngineAPI.getAPI();
            return api == null ? null : api.getModelRegistry();
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object invokeFirst(Object target, String[] methodNames) {
        if (target == null) return null;
        for (String name : methodNames) {
            try {
                Method method = target.getClass().getMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static List<String> toStringList(Object value) {
        List<String> result = new ArrayList<>();
        for (Object element : toCollection(value)) {
            if (element != null) result.add(String.valueOf(element));
        }
        return result;
    }

    private static Collection<?> toCollection(Object value) {
        if (value instanceof Collection<?> collection) return collection;
        if (value instanceof Set<?> set) return set;
        if (value instanceof Map<?, ?> map) return map.keySet();
        if (value instanceof Object[] array) return List.of(array);
        return Collections.emptyList();
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
