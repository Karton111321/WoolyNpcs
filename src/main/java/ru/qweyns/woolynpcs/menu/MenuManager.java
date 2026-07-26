package ru.qweyns.woolynpcs.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.qweyns.woolynpcs.WoolyNpcs;
import ru.qweyns.woolynpcs.api.WoolyNpcsApi;
import ru.qweyns.woolynpcs.model.ActionType;
import ru.qweyns.woolynpcs.model.ClickType;
import ru.qweyns.woolynpcs.model.NpcAction;
import ru.qweyns.woolynpcs.model.WoolyNpc;
import ru.qweyns.woolynpcs.util.ColorUtil;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class MenuManager {
    private final WoolyNpcs plugin;
    private final File folder;
    private final Map<String, Menu> menus = new LinkedHashMap<>();

    public MenuManager(WoolyNpcs plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "menus");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Не удалось создать папку menus/");
        }
    }

    public void reload() {
        createExampleMenu();
        load();
    }

    public void load() {
        menus.clear();

        File[] files = folder.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            String fileName = file.getName();
            String menuId = fileName.substring(0, fileName.length() - ".yml".length());

            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            String title = config.getString("title", menuId);
            int size = normalizeSize(config.getInt("size", 27));

            Menu menu = new Menu(menuId, title, size);

            ConfigurationSection items = config.getConfigurationSection("items");
            if (items != null) {
                for (String slotKey : items.getKeys(false)) {
                    int slot;
                    try {
                        slot = Integer.parseInt(slotKey);
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Меню '" + menuId + "': '" + slotKey
                                + "' не номер слота.");
                        continue;
                    }
                    if (slot < 0 || slot >= size) {
                        plugin.getLogger().warning("Меню '" + menuId + "': слот " + slot
                                + " выходит за размер меню (" + size + ").");
                        continue;
                    }

                    ConfigurationSection sec = items.getConfigurationSection(slotKey);
                    if (sec == null) continue;

                    Menu.MenuItem item = new Menu.MenuItem(
                            sec.getString("material", "STONE"),
                            sec.getString("name", " "),
                            sec.getStringList("lore"),
                            sec.getBoolean("close-on-click", true));

                    for (String raw : sec.getStringList("actions")) {
                        NpcAction action = parseAction(menuId, slot, raw);
                        if (action != null) item.getActions().add(action);
                    }

                    menu.getItems().put(slot, item);
                }
            }

            menus.put(menuId.toLowerCase(Locale.ROOT), menu);
            plugin.getLogger().info("Loaded menu: " + menuId + " (" + menu.getItems().size() + " items)");
        }
    }

    private NpcAction parseAction(String menuId, int slot, String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.split(":", 2);
        if (parts.length < 2) {
            plugin.getLogger().warning("Меню '" + menuId + "', слот " + slot
                    + ": действие '" + raw + "' записано без типа.");
            return null;
        }

        String typeName = parts[0].trim().toUpperCase(Locale.ROOT);
        NpcAction action;
        try {
            action = new NpcAction(ClickType.ANY, ActionType.valueOf(typeName), parts[1]);
        } catch (IllegalArgumentException e) {
            if (!WoolyNpcsApi.isRegistered(typeName)) {
                plugin.getLogger().warning("Меню '" + menuId + "', слот " + slot
                        + ": неизвестный тип действия '" + parts[0] + "'.");
                return null;
            }
            action = NpcAction.custom(UUID.randomUUID(), ClickType.ANY, typeName, parts[1]);
        }
        return action.isEmpty() ? null : action;
    }

    public boolean open(Player player, String menuId, WoolyNpc source) {
        if (menuId == null) return false;
        Menu menu = menus.get(menuId.trim().toLowerCase(Locale.ROOT));
        if (menu == null) {
            player.sendMessage(ColorUtil.format(
                    plugin.getConfigManager().getMessage("menu-not-found", "id", menuId)));
            return false;
        }

        MenuHolder holder = new MenuHolder(menu, source);
        String title = menu.getTitle().replace("{player}", player.getName());
        Inventory inventory = Bukkit.createInventory(holder, menu.getSize(), ColorUtil.format(title));
        holder.setInventory(inventory);

        for (Map.Entry<Integer, Menu.MenuItem> entry : menu.getItems().entrySet()) {
            inventory.setItem(entry.getKey(), buildItem(entry.getValue(), player));
        }

        player.openInventory(inventory);
        return true;
    }

    private ItemStack buildItem(Menu.MenuItem item, Player player) {
        Material material = Material.matchMaterial(item.getMaterial().toUpperCase(Locale.ROOT));
        if (material == null) {
            plugin.getLogger().warning("Меню: неизвестный материал '" + item.getMaterial()
                    + "', используется STONE.");
            material = Material.STONE;
        }

        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtil.format(item.getName().replace("{player}", player.getName())));

            List<String> lore = item.getLore();
            if (!lore.isEmpty()) {
                meta.lore(lore.stream()
                        .map(line -> line.replace("{player}", player.getName()))
                        .map(ColorUtil::format)
                        .toList());
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private int normalizeSize(int size) {
        int rows = Math.round(size / 9.0f);
        return Math.max(9, Math.min(54, Math.max(1, rows) * 9));
    }

    public Menu getMenu(String id) {
        return id == null ? null : menus.get(id.trim().toLowerCase(Locale.ROOT));
    }

    public Map<String, Menu> getMenus() {
        return menus;
    }

    public void createExampleMenu() {
        File example = new File(folder, "example.yml");
        if (example.exists()) return;

        YamlConfiguration config = new YamlConfiguration();
        config.set("title", "&#BBDEFBПример меню");
        config.set("size", 27);

        config.set("items.11.material", "DIAMOND");
        config.set("items.11.name", "&#A8E6CFПолучить алмаз");
        config.set("items.11.lore", List.of("&#9CA3AFОдин раз на игрока", "", "&#BBDEFB▸ Нажмите"));
        config.set("items.11.actions", List.of(
                "GIVE_ITEM:[ONCE]DIAMOND 1",
                "MESSAGE:&#A8E6CFДержите!"));

        config.set("items.13.material", "GOLD_INGOT");
        config.set("items.13.name", "&#A8E6CFКупить набор за 100");
        config.set("items.13.lore", List.of("&#9CA3AFНужно 100 монет"));
        config.set("items.13.actions", List.of(
                "MONEY_TAKE:100",
                "PLAYER_COMMAND:kit starter"));

        config.set("items.15.material", "BARRIER");
        config.set("items.15.name", "&#FF8B94Закрыть");
        config.set("items.15.actions", List.of("CLOSE_INVENTORY:1"));

        try {
            config.save(example);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось создать пример меню: " + e.getMessage());
        }
    }
}
