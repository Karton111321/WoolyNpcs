package ru.qweyns.woolynpcs.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.qweyns.woolynpcs.WoolyNpcs;
import ru.qweyns.woolynpcs.model.WoolyNpc;
import ru.qweyns.woolynpcs.util.ColorUtil;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class GuiManager {
    private static ru.qweyns.woolynpcs.config.ConfigManager cfg() {
        return WoolyNpcs.getInstance().getConfigManager();
    }

    public static void openEditor(Player player, WoolyNpc npc) {
        FileConfiguration guiConfig = WoolyNpcs.getInstance().getConfigManager().getGuiConfig();
        ConfigurationSection editorSec = guiConfig.getConfigurationSection("editor");

        if (editorSec == null) {
            String errorMsg = WoolyNpcs.getInstance().getConfigManager().getMessage("gui-section-not-found");
            player.sendMessage(ColorUtil.format(errorMsg));
            return;
        }

        String titleRaw = editorSec.getString("title", "Редактор");
        String title = titleRaw.replace("{name}", npc.getName());

        int size = normalizeSize(editorSec.getInt("size", 27));

        NpcEditorHolder holder = new NpcEditorHolder(npc);
        Inventory inv = Bukkit.createInventory(holder, size, ColorUtil.format(title));
        holder.setInventory(inv);

        ConfigurationSection items = editorSec.getConfigurationSection("items");
        if (items != null) {
            loadItem(inv, items.getConfigurationSection("lookat"),
                    "{status}", npc.isLookAtPlayer() ? "&#A8E6CFВкл" : "&#FF8B94Выкл");

            loadItem(inv, items.getConfigurationSection("hologram"),
                    "{lines}", String.valueOf(npc.getHologramLines().size()));

            loadItem(inv, items.getConfigurationSection("actions"),
                    "{count}", String.valueOf(npc.getActions().size()));

            loadItem(inv, items.getConfigurationSection("movehere"));
            loadItem(inv, items.getConfigurationSection("turn"));
            loadItem(inv, items.getConfigurationSection("remove"));

            loadItem(inv, items.getConfigurationSection("model"),
                    "{model}", npc.getModelId());
            loadItem(inv, items.getConfigurationSection("animation"),
                    "{animation}", npc.getDefaultAnimation());
            loadItem(inv, items.getConfigurationSection("waypoints"),
                    "{count}", String.valueOf(npc.getWaypointPath() == null
                            ? 0 : npc.getWaypointPath().getWaypoints().size()));
            loadItem(inv, items.getConfigurationSection("movement"),
                    "{mode}", npc.getMovementMode().name());
            loadItem(inv, items.getConfigurationSection("dialog"),
                    "{dialog}", npc.getDialogId() == null ? "—" : npc.getDialogId());
        }

        player.openInventory(inv);
    }

    public static void openModelPicker(Player player, WoolyNpc npc) {
        WoolyNpcs plugin = WoolyNpcs.getInstance();
        List<String> models = ru.qweyns.woolynpcs.util.ModelEngineUtil.getModelIds();

        if (models.isEmpty()) {
            player.sendMessage(ColorUtil.format(plugin.getConfigManager().getMessage("gui-no-models")));
            return;
        }

        new PickerGui(plugin.getConfigManager().getMessage("gui-model-title"),
                models, cfg().guiMaterial("picker.model-material", Material.ARMOR_STAND),
                picked -> {
                    npc.setModelId(picked);
                    plugin.getStorageManager().markDirty();
                    player.sendMessage(ColorUtil.format(plugin.getConfigManager()
                            .getMessage("npc-model-changed", "name", npc.getName(), "model", picked)));
                    openEditor(player, npc);
                },
                () -> openEditor(player, npc)).open(player, 0);
    }

    public static void openAnimationPicker(Player player, WoolyNpc npc) {
        WoolyNpcs plugin = WoolyNpcs.getInstance();
        List<String> animations = ru.qweyns.woolynpcs.util.ModelEngineUtil.getAnimations(npc.getModelId());

        if (animations.isEmpty()) {
            player.sendMessage(ColorUtil.format(plugin.getConfigManager().getMessage("gui-no-animations")));
            return;
        }

        new PickerGui(plugin.getConfigManager().getMessage("gui-animation-title"),
                animations, cfg().guiMaterial("picker.animation-material", Material.FEATHER),
                picked -> {
                    npc.setDefaultAnimation(picked);
                    plugin.getStorageManager().markDirty();
                    player.sendMessage(ColorUtil.format(plugin.getConfigManager()
                            .getMessage("npc-anim-started", "name", npc.getName(), "anim", picked)));
                    openEditor(player, npc);
                },
                () -> openEditor(player, npc)).open(player, 0);
    }

    public static void openActionList(Player player, WoolyNpc npc) {
        WoolyNpcs plugin = WoolyNpcs.getInstance();

        if (npc.getActions().isEmpty()) {
            player.sendMessage(ColorUtil.format(plugin.getConfigManager()
                    .getMessage("action-list-empty", "name", npc.getName())));
            return;
        }

        List<String> labels = new java.util.ArrayList<>();
        for (int i = 0; i < npc.getActions().size(); i++) {
            var action = npc.getActions().get(i);
            labels.add(i + ". " + action.getClickType().name() + " " + action.getTypeName()
                    + " — " + shorten(action.getValue()));
        }

        new PickerGui(plugin.getConfigManager().getMessage("gui-actions-title"),
                labels, cfg().guiMaterial("picker.action-material", Material.COMMAND_BLOCK),
                picked -> {
                    int index = parseIndex(picked);
                    if (index < 0 || index >= npc.getActions().size()) return;

                    new ConfirmGui(plugin.getConfigManager().getMessage("gui-confirm-action"),
                            () -> {
                                npc.getActions().remove(index);
                                plugin.getStorageManager().markDirty();
                                player.sendMessage(ColorUtil.format(plugin.getConfigManager()
                                        .getMessage("action-removed", "name", npc.getName(),
                                                "index", String.valueOf(index))));
                                openActionList(player, npc);
                            },
                            () -> openActionList(player, npc)).open(player);
                },
                () -> openEditor(player, npc)).open(player, 0);
    }

    public static void cycleMovement(Player player, WoolyNpc npc) {
        WoolyNpcs plugin = WoolyNpcs.getInstance();
        WoolyNpc.MovementMode next = switch (npc.getMovementMode()) {
            case NONE   -> WoolyNpc.MovementMode.FOLLOW;
            case FOLLOW -> WoolyNpc.MovementMode.WANDER;
            case WANDER -> WoolyNpc.MovementMode.NONE;
        };
        npc.setMovementMode(next);
        plugin.getWalkingTask().resetWander(npc.getId());
        plugin.getStorageManager().markDirty();
        openEditor(player, npc);
    }

    private static int parseIndex(String label) {
        int dot = label.indexOf('.');
        if (dot <= 0) return -1;
        try {
            return Integer.parseInt(label.substring(0, dot));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String shorten(String value) {
        if (value == null) return "";
        return value.length() <= 24 ? value : value.substring(0, 24) + "...";
    }

    private static int normalizeSize(int size) {
        int rows = Math.round(size / 9.0f);
        return Math.max(9, Math.min(54, Math.max(1, rows) * 9));
    }

    private static void loadItem(Inventory inv, ConfigurationSection sec, String... placeholders) {
        if (sec == null) return;

        int slot = sec.getInt("slot", -1);
        if (slot < 0 || slot >= inv.getSize()) return;

        String matName = sec.getString("material", "STONE").toUpperCase(Locale.ROOT);
        Material mat = Material.matchMaterial(matName);
        if (mat == null) {
            WoolyNpcs.getInstance().getLogger().warning(
                    "gui.yml: неизвестный материал '" + matName + "', используется STONE.");
            mat = cfg().guiMaterial("fallback-material", Material.STONE);
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            String name = sec.getString("name", "Без названия");
            for (int i = 0; i + 1 < placeholders.length; i += 2) {
                name = name.replace(placeholders[i], placeholders[i+1]);
            }
            meta.displayName(ColorUtil.format(name));

            List<String> lore = sec.getStringList("lore");
            if (!lore.isEmpty()) {
                List<Component> coloredLore = lore.stream().map(line -> {
                    for (int i = 0; i + 1 < placeholders.length; i += 2) {
                        line = line.replace(placeholders[i], placeholders[i+1]);
                    }
                    return ColorUtil.format(line);
                }).collect(Collectors.toList());
                meta.lore(coloredLore);
            }
            item.setItemMeta(meta);
        }
        inv.setItem(slot, item);
    }
}
