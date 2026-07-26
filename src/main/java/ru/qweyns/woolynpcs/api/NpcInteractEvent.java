package ru.qweyns.woolynpcs.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import ru.qweyns.woolynpcs.model.ClickType;
import ru.qweyns.woolynpcs.model.WoolyNpc;

public class NpcInteractEvent extends NpcEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player    player;
    private final ClickType clickType;
    private boolean cancelled = false;

    public NpcInteractEvent(WoolyNpc npc, Player player, ClickType clickType) {
        super(npc);
        this.player    = player;
        this.clickType = clickType;
    }

    public @NotNull Player getPlayer() {
        return player;
    }

    public @NotNull ClickType getClickType() {
        return clickType;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
