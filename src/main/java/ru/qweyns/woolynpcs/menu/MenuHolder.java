package ru.qweyns.woolynpcs.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import ru.qweyns.woolynpcs.model.WoolyNpc;

public class MenuHolder implements InventoryHolder {
    private final Menu menu;
    private final WoolyNpc source;
    private Inventory inventory;

    public MenuHolder(Menu menu, WoolyNpc source) {
        this.menu   = menu;
        this.source = source;
    }

    public Menu     getMenu()   { return menu;   }
    public WoolyNpc getSource() { return source; }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("Инвентарь меню '" + menu.getId() + "' ещё не создан");
        }
        return inventory;
    }
}
