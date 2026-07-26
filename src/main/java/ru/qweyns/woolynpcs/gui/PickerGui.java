package ru.qweyns.woolynpcs.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import ru.qweyns.woolynpcs.WoolyNpcs;
import ru.qweyns.woolynpcs.util.ColorUtil;

import java.util.List;
import java.util.function.Consumer;

public class PickerGui implements InventoryHolder {
    private static int rows() {
        return Math.max(1, Math.min(5, cfg().guiInt("picker.rows", 5)));
    }

    private static int pageSize() {
        return rows() * 9;
    }

    private static int controlSlot(String key, int def) {
        int slot = Math.max(0, Math.min(8, cfg().guiInt("picker.items." + key + ".slot", def)));
        return pageSize() + slot;
    }

    private static ru.qweyns.woolynpcs.config.ConfigManager cfg() {
        return WoolyNpcs.getInstance().getConfigManager();
    }

    private final String         title;
    private final List<String>   options;
    private final Material       icon;
    private final Consumer<String> onPick;
    private final Runnable       onBack;

    private int page;
    private Inventory inventory;

    public PickerGui(String title, List<String> options, Material icon,
                     Consumer<String> onPick, Runnable onBack) {
        this.title   = title;
        this.options = options;
        this.icon    = icon;
        this.onPick  = onPick;
        this.onBack  = onBack;
    }

    public void open(Player player, int page) {
        int pageSize = pageSize();
        this.page = Math.max(0, Math.min(page, lastPage()));

        String rawTitle = cfg().guiString("picker.title", "{title} &#9CA3AF({page}/{pages})")
                .replace("{title}", title)
                .replace("{page}", String.valueOf(this.page + 1))
                .replace("{pages}", String.valueOf(lastPage() + 1));

        Inventory inv = Bukkit.createInventory(this, pageSize + 9, ColorUtil.format(rawTitle));
        this.inventory = inv;

        Material itemIcon = icon != null ? icon
                : cfg().guiMaterial("picker.item-material", Material.PAPER);
        String nameFormat = cfg().guiString("picker.item-name", "&#F5F5F0{value}");

        int from = this.page * pageSize;
        int to = Math.min(options.size(), from + pageSize);
        for (int i = from; i < to; i++) {
            inv.setItem(i - from, button(itemIcon, nameFormat.replace("{value}", options.get(i)), null));
        }

        if (this.page > 0) {
            inv.setItem(controlSlot("prev", 3),
                    button(cfg().guiMaterial("picker.items.prev.material", Material.ARROW),
                            cfg().guiString("picker.items.prev.name", "&#BBDEFB◀"), null));
        }
        if (this.page < lastPage()) {
            inv.setItem(controlSlot("next", 5),
                    button(cfg().guiMaterial("picker.items.next.material", Material.ARROW),
                            cfg().guiString("picker.items.next.name", "&#BBDEFB▶"), null));
        }
        inv.setItem(controlSlot("close", 4),
                button(cfg().guiMaterial("picker.items.close.material", Material.BARRIER),
                        cfg().guiString("picker.items.close.name", "&#FF8B94Закрыть"), null));

        player.openInventory(inv);
    }

    public boolean click(Player player, int slot) {
        int pageSize = pageSize();

        if (slot == controlSlot("prev", 3))  { open(player, page - 1); return true; }
        if (slot == controlSlot("next", 5))  { open(player, page + 1); return true; }
        if (slot == controlSlot("close", 4)) {
            if (onBack != null) onBack.run();
            else player.closeInventory();
            return true;
        }

        if (slot < 0 || slot >= pageSize) return false;

        int index = page * pageSize + slot;
        if (index >= options.size()) return false;

        onPick.accept(options.get(index));
        return true;
    }

    private int lastPage() {
        if (options.isEmpty()) return 0;
        return (options.size() - 1) / pageSize();
    }

    private String message(String key) {
        return WoolyNpcs.getInstance().getConfigManager().getMessage(key);
    }

    static ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtil.format(name));
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore.stream().map(ColorUtil::format).map(c -> (Component) c).toList());
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) throw new IllegalStateException("Список ещё не открыт");
        return inventory;
    }
}
