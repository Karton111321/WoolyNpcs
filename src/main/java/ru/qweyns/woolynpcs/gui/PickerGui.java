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
    private static final int ROWS = 5;
    private static final int PAGE_SIZE = ROWS * 9;

    private static final int SLOT_PREV  = PAGE_SIZE + 3;
    private static final int SLOT_CLOSE = PAGE_SIZE + 4;
    private static final int SLOT_NEXT  = PAGE_SIZE + 5;

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
        this.icon    = icon == null ? Material.PAPER : icon;
        this.onPick  = onPick;
        this.onBack  = onBack;
    }

    public void open(Player player, int page) {
        this.page = Math.max(0, Math.min(page, lastPage()));

        Inventory inv = Bukkit.createInventory(this, PAGE_SIZE + 9,
                ColorUtil.format(title + " &#9CA3AF(" + (this.page + 1) + "/" + (lastPage() + 1) + ")"));
        this.inventory = inv;

        int from = this.page * PAGE_SIZE;
        int to = Math.min(options.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            inv.setItem(i - from, button(icon, "&#F5F5F0" + options.get(i), null));
        }

        if (this.page > 0) {
            inv.setItem(SLOT_PREV, button(Material.ARROW, message("gui-prev"), null));
        }
        if (this.page < lastPage()) {
            inv.setItem(SLOT_NEXT, button(Material.ARROW, message("gui-next"), null));
        }
        inv.setItem(SLOT_CLOSE, button(Material.BARRIER,
                message(onBack == null ? "gui-close" : "gui-back"), null));

        player.openInventory(inv);
    }

    public boolean click(Player player, int slot) {
        if (slot == SLOT_PREV)  { open(player, page - 1); return true; }
        if (slot == SLOT_NEXT)  { open(player, page + 1); return true; }
        if (slot == SLOT_CLOSE) {
            if (onBack != null) onBack.run();
            else player.closeInventory();
            return true;
        }

        if (slot < 0 || slot >= PAGE_SIZE) return false;

        int index = page * PAGE_SIZE + slot;
        if (index >= options.size()) return false;

        onPick.accept(options.get(index));
        return true;
    }

    private int lastPage() {
        if (options.isEmpty()) return 0;
        return (options.size() - 1) / PAGE_SIZE;
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
