package ru.qweyns.woolynpcs.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class ColorUtil {
    private ColorUtil() {}

    private static final LegacyComponentSerializer SERIALIZER =
            LegacyComponentSerializer.builder()
                    .character('&')
                    .hexCharacter('#')
                    .hexColors()
                    .build();

    public static Component format(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        return SERIALIZER.deserialize(text);
    }

    public static String formatLegacy(String text) {
        if (text == null || text.isEmpty()) return "";
        return LegacyComponentSerializer.legacySection()
                .serialize(SERIALIZER.deserialize(text));
    }

    public static String stripForCommand(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') { sb.append(' '); continue; }
            if (c < ' ' || c == 127 || c == '§') continue;
            sb.append(c);
        }
        return sb.toString().trim();
    }
}
