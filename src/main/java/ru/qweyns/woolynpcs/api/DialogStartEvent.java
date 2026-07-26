package ru.qweyns.woolynpcs.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import ru.qweyns.woolynpcs.model.WoolyNpc;

public class DialogStartEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player   player;
    private final String   dialogId;
    private final WoolyNpc npc;
    private boolean cancelled = false;

    public DialogStartEvent(Player player, String dialogId, WoolyNpc npc) {
        this.player   = player;
        this.dialogId = dialogId;
        this.npc      = npc;
    }

    public @NotNull Player getPlayer()   { return player;   }
    public @NotNull String getDialogId() { return dialogId; }

    public WoolyNpc getNpc() { return npc; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
