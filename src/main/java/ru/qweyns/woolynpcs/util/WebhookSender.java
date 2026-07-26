package ru.qweyns.woolynpcs.util;

import org.bukkit.Bukkit;
import ru.qweyns.woolynpcs.WoolyNpcs;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class WebhookSender {
    private WebhookSender() {}

    private static final int TIMEOUT_MS = 5_000;
    private static final int MAX_CONTENT = 1900;

    public static void send(String url, String message) {
        if (url == null || url.isBlank() || message == null || message.isBlank()) return;

        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            WoolyNpcs.getInstance().getLogger().warning(
                    "DISCORD_WEBHOOK: адрес должен начинаться с http:// или https://");
            return;
        }

        String content = message.length() > MAX_CONTENT ? message.substring(0, MAX_CONTENT) : message;

        Bukkit.getScheduler().runTaskAsynchronously(WoolyNpcs.getInstance(), () -> post(url, content));
    }

    private static void post(String url, String content) {
        HttpURLConnection connection = null;
        try {
            URL target = URI.create(url).toURL();
            connection = (HttpURLConnection) target.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("User-Agent", "WoolyNpcs");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);

            byte[] body = ("{\"content\":\"" + escape(content) + "\"}").getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }

            int code = connection.getResponseCode();
            if (code >= 300) {
                WoolyNpcs.getInstance().getLogger().warning(
                        "DISCORD_WEBHOOK: сервер ответил кодом " + code);
            }
        } catch (Exception e) {
            WoolyNpcs.getInstance().getLogger().warning("DISCORD_WEBHOOK: " + e.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String escape(String text) {
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default   -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
