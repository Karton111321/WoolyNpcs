package ru.qweyns.woolynpcs.gui;

import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import ru.qweyns.woolynpcs.WoolyNpcs;
import ru.qweyns.woolynpcs.command.NpcCommand;
import ru.qweyns.woolynpcs.dialog.DialogGuiHolder;
import ru.qweyns.woolynpcs.menu.Menu;
import ru.qweyns.woolynpcs.menu.MenuHolder;
import ru.qweyns.woolynpcs.model.NpcAction;
import ru.qweyns.woolynpcs.model.WoolyNpc;

public class GuiListener implements Listener {
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Object holder = event.getInventory().getHolder();
        if (holder instanceof NpcEditorHolder || holder instanceof MenuHolder
                || holder instanceof DialogGuiHolder
                || holder instanceof PickerGui || holder instanceof ConfirmGui) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDialogGuiClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof DialogGuiHolder holder)) return;
        event.setCancelled(true);

        if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != holder) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int choiceIndex = holder.choiceIndexAt(event.getSlot());
        if (choiceIndex < 0) return;

        WoolyNpcs.getInstance().getDialogManager().handleChoice(player, choiceIndex);
    }

    @EventHandler
    public void onDialogGuiClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof DialogGuiHolder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        org.bukkit.Bukkit.getScheduler().runTask(WoolyNpcs.getInstance(), () -> {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof DialogGuiHolder) {
                return;
            }
            WoolyNpcs.getInstance().getDialogManager().cancelDialog(player.getUniqueId());
        });
    }

    @EventHandler
    public void onPickerClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PickerGui picker)) return;
        event.setCancelled(true);

        if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != picker) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.hasPermission(NpcCommand.PERM_ADMIN)) { player.closeInventory(); return; }

        picker.click(player, event.getSlot());
    }

    @EventHandler
    public void onConfirmClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ConfirmGui confirm)) return;
        event.setCancelled(true);

        if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != confirm) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.hasPermission(NpcCommand.PERM_ADMIN)) { player.closeInventory(); return; }

        confirm.click(player, event.getSlot());
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);

        if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != holder) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Menu.MenuItem item = holder.getMenu().getItem(event.getSlot());
        if (item == null) return;

        if (item.isCloseOnClick()) player.closeInventory();

        for (NpcAction action : item.getActions()) {
            action.execute(player, holder.getSource());
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof NpcEditorHolder holder)) return;
        event.setCancelled(true);

        if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != holder) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (!player.hasPermission(NpcCommand.PERM_ADMIN)) {
            player.closeInventory();
            return;
        }

        WoolyNpc npc = holder.getNpc();
        int slot = event.getSlot();

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);

        ConfigurationSection items = WoolyNpcs.getInstance().getConfigManager()
                .getGuiConfig().getConfigurationSection("editor.items");

        if (items == null) return;

        if (isSlotMatch(items, "model", slot)) {
            GuiManager.openModelPicker(player, npc);
        } else if (isSlotMatch(items, "animation", slot)) {
            GuiManager.openAnimationPicker(player, npc);
        } else if (isSlotMatch(items, "movement", slot)) {
            GuiManager.cycleMovement(player, npc);
        } else if (isSlotMatch(items, "waypoints", slot)) {
            runCommand(player, "waypoint " + npc.getName() + " list");
        } else if (isSlotMatch(items, "dialog", slot)) {
            runCommand(player, "dialog " + npc.getName() + " list");
        } else if (isSlotMatch(items, "lookat", slot)) {
            npc.setLookAtPlayer(!npc.isLookAtPlayer());
            WoolyNpcs.getInstance().getStorageManager().markDirty();
            GuiManager.openEditor(player, npc);
        } else if (isSlotMatch(items, "hologram", slot)) {
            runCommand(player, "holo " + npc.getName() + " line list");
        } else if (isSlotMatch(items, "actions", slot)) {
            GuiManager.openActionList(player, npc);
        } else if (isSlotMatch(items, "movehere", slot)) {
            runCommand(player, "movehere " + npc.getName());
        } else if (isSlotMatch(items, "turn", slot)) {
            runCommand(player, "turn " + npc.getName());
        } else if (isSlotMatch(items, "remove", slot)) {
            new ConfirmGui(WoolyNpcs.getInstance().getConfigManager()
                    .getMessage("gui-confirm-remove", "name", npc.getName()),
                    () -> runCommand(player, "remove " + npc.getName()),
                    () -> GuiManager.openEditor(player, npc)).open(player);
        }
    }

    private void runCommand(Player player, String arguments) {
        player.closeInventory();
        player.performCommand("woolynpcs " + arguments);
    }

    private boolean isSlotMatch(ConfigurationSection itemsSec, String key, int clickedSlot) {
        ConfigurationSection sec = itemsSec.getConfigurationSection(key);
        if (sec == null) return false;
        return sec.getInt("slot", -1) == clickedSlot;
    }
}
