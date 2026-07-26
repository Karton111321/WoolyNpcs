package ru.qweyns.woolynpcs.api;

import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import ru.qweyns.woolynpcs.model.WoolyNpc;

public class NpcDespawnEvent extends NpcEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final boolean entitiesRemoved;

    public NpcDespawnEvent(WoolyNpc npc, boolean entitiesRemoved) {
        super(npc);
        this.entitiesRemoved = entitiesRemoved;
    }

    public boolean isEntitiesRemoved() {
        return entitiesRemoved;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
