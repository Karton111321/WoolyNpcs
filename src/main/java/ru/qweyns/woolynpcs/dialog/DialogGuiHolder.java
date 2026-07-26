package ru.qweyns.woolynpcs.dialog;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DialogGuiHolder implements InventoryHolder {
    private final String dialogId;
    private final String nodeId;
    private final List<SlotChoice> slots;

    private Inventory inventory;

    public DialogGuiHolder(String dialogId, String nodeId, List<SlotChoice> slots) {
        this.dialogId = dialogId;
        this.nodeId   = nodeId;
        this.slots    = List.copyOf(slots);
    }

    public String getDialogId() { return dialogId; }
    public String getNodeId()   { return nodeId;   }

    public int choiceIndexAt(int slot) {
        for (SlotChoice entry : slots) {
            if (entry.slot() == slot) return entry.choiceIndex();
        }
        return -1;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("Инвентарь диалога '" + dialogId + "' ещё не создан");
        }
        return inventory;
    }

    public record SlotChoice(int slot, int choiceIndex) {}
}
