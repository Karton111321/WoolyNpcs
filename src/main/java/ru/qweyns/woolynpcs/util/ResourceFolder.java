package ru.qweyns.woolynpcs.util;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public final class ResourceFolder {

    private ResourceFolder() {}

    public static int copyDefaults(Plugin plugin, String folder) {
        File target = new File(plugin.getDataFolder(), folder);
        if (!target.exists() && !target.mkdirs()) {
            plugin.getLogger().warning("Не удалось создать папку " + folder + "/");
            return 0;
        }

        int copied = 0;
        for (String name : listResources(plugin, folder)) {
            File file = new File(target, name);
            if (file.exists()) continue;

            try (InputStream in = plugin.getResource(folder + "/" + name)) {
                if (in == null) continue;
                Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                copied++;
            } catch (IOException e) {
                plugin.getLogger().warning("Не удалось создать " + folder + "/" + name
                        + ": " + e.getMessage());
            }
        }
        return copied;
    }

    public static List<String> listResources(Plugin plugin, String folder) {
        List<String> names = new ArrayList<>();

        URL source = ResourceFolder.class.getProtectionDomain().getCodeSource() == null
                ? null : ResourceFolder.class.getProtectionDomain().getCodeSource().getLocation();
        if (source == null) return names;

        try {
            Path path = Paths.get(URI.create(source.toString()));

            if (Files.isDirectory(path)) {
                Path dir = path.resolve(folder);
                if (!Files.isDirectory(dir)) return names;
                try (Stream<Path> files = Files.list(dir)) {
                    files.filter(Files::isRegularFile)
                            .map(p -> p.getFileName().toString())
                            .filter(n -> n.toLowerCase().endsWith(".yml"))
                            .forEach(names::add);
                }
                return names;
            }

            try (JarFile jar = new JarFile(path.toFile())) {
                String prefix = folder + "/";
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.isDirectory()) continue;

                    String name = entry.getName();
                    if (!name.startsWith(prefix)) continue;

                    String relative = name.substring(prefix.length());
                    if (relative.isEmpty() || relative.contains("/")) continue;
                    if (!relative.toLowerCase().endsWith(".yml")) continue;

                    names.add(relative);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось прочитать содержимое папки " + folder
                    + " из плагина: " + e.getMessage());
        }
        return names;
    }
}
