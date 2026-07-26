package ru.qweyns.woolynpcs.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import ru.qweyns.woolynpcs.model.WoolyNpc;

public class DialogNodeEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player   player;
    private final String   dialogId;
    private final String   nodeId;
    private final WoolyNpc npc;
    private final boolean  firstVisit;
    private boolean cancelled = false;

    public DialogNodeEvent(Player player, String dialogId, String nodeId, WoolyNpc npc, boolean firstVisit) {
        this.player     = player;
        this.dialogId   = dialogId;
        this.nodeId     = nodeId;
        this.npc        = npc;
        this.firstVisit = firstVisit;
    }

    public @NotNull Player getPlayer()   { return player;   }
    public @NotNull String getDialogId() { return dialogId; }
    public @NotNull String getNodeId()   { return nodeId;   }
    public boolean isFirstVisit()        { return firstVisit; }

    public WoolyNpc getNpc() { return npc; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
