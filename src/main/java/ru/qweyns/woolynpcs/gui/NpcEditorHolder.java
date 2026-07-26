package ru.qweyns.woolynpcs.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import ru.qweyns.woolynpcs.model.WoolyNpc;

public class NpcEditorHolder implements InventoryHolder {
    private final WoolyNpc npc;
    private Inventory inventory;

    public NpcEditorHolder(WoolyNpc npc) {
        this.npc = npc;
    }

    public WoolyNpc getNpc() {
        return npc;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("Inventory ещё не создан для NPC " + npc.getName());
        }
        return inventory;
    }
}
