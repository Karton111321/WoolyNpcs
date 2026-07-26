package ru.qweyns.woolynpcs.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import ru.qweyns.woolynpcs.WoolyNpcs;
import ru.qweyns.woolynpcs.util.ColorUtil;

public class ConfirmGui implements InventoryHolder {

    private final String   question;
    private final Runnable onConfirm;
    private final Runnable onCancel;

    private Inventory inventory;

    public ConfirmGui(String question, Runnable onConfirm, Runnable onCancel) {
        this.question  = question;
        this.onConfirm = onConfirm;
        this.onCancel  = onCancel;
    }

    public void open(Player player) {
        var cfg = WoolyNpcs.getInstance().getConfigManager();

        int size = normalizeSize(cfg.guiInt("confirm.size", 27));
        Inventory inv = Bukkit.createInventory(this, size,
                ColorUtil.format(cfg.guiString("confirm.title", "&#FF8B94Подтвердите действие")
                        .replace("{question}", question)));
        this.inventory = inv;

        inv.setItem(clamp(cfg.guiInt("confirm.items.yes.slot", 11), size),
                PickerGui.button(cfg.guiMaterial("confirm.items.yes.material", Material.LIME_DYE),
                        cfg.guiString("confirm.items.yes.name", "&#A8E6CFПодтвердить"),
                        cfg.guiLore("confirm.items.yes.lore")));

        inv.setItem(clamp(cfg.guiInt("confirm.items.no.slot", 15), size),
                PickerGui.button(cfg.guiMaterial("confirm.items.no.material", Material.GRAY_DYE),
                        cfg.guiString("confirm.items.no.name", "&#9CA3AFОтмена"),
                        cfg.guiLore("confirm.items.no.lore")));

        player.openInventory(inv);
    }

    private static int clamp(int slot, int size) {
        return Math.max(0, Math.min(size - 1, slot));
    }

    private static int normalizeSize(int size) {
        int rows = Math.round(size / 9.0f);
        return Math.max(9, Math.min(54, Math.max(1, rows) * 9));
    }

    public boolean click(Player player, int slot) {
        var cfg = WoolyNpcs.getInstance().getConfigManager();
        int size = normalizeSize(cfg.guiInt("confirm.size", 27));

        if (slot == clamp(cfg.guiInt("confirm.items.yes.slot", 11), size)) {
            player.closeInventory();
            onConfirm.run();
            return true;
        }
        if (slot == clamp(cfg.guiInt("confirm.items.no.slot", 15), size)) {
            if (onCancel != null) onCancel.run();
            else player.closeInventory();
            return true;
        }
        return false;
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) throw new IllegalStateException("Окно подтверждения ещё не открыто");
        return inventory;
    }
}
