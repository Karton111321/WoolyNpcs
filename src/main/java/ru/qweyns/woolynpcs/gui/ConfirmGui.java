package ru.qweyns.woolynpcs.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import ru.qweyns.woolynpcs.WoolyNpcs;
import ru.qweyns.woolynpcs.util.ColorUtil;

import java.util.List;

public class ConfirmGui implements InventoryHolder {
    private static final int SLOT_YES = 11;
    private static final int SLOT_NO  = 15;

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
        inventory = Bukkit.createInventory(this, 27, ColorUtil.format(question));

        inventory.setItem(SLOT_YES, PickerGui.button(Material.LIME_DYE,
                message("gui-confirm-yes"), List.of(message("gui-confirm-warning"))));
        inventory.setItem(SLOT_NO, PickerGui.button(Material.GRAY_DYE,
                message("gui-confirm-no"), null));

        player.openInventory(inventory);
    }

    public boolean click(Player player, int slot) {
        if (slot == SLOT_YES) {
            player.closeInventory();
            onConfirm.run();
            return true;
        }
        if (slot == SLOT_NO) {
            if (onCancel != null) onCancel.run();
            else player.closeInventory();
            return true;
        }
        return false;
    }

    private String message(String key) {
        return WoolyNpcs.getInstance().getConfigManager().getMessage(key);
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) throw new IllegalStateException("Окно подтверждения ещё не открыто");
        return inventory;
    }
}
