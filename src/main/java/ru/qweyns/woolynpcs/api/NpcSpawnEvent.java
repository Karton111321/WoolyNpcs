package ru.qweyns.woolynpcs.api;

import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import ru.qweyns.woolynpcs.model.WoolyNpc;

public class NpcSpawnEvent extends NpcEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    public NpcSpawnEvent(WoolyNpc npc) {
        super(npc);
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
