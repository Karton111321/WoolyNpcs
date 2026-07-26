package ru.qweyns.woolynpcs.api;

import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import ru.qweyns.woolynpcs.model.WoolyNpc;

public abstract class NpcEvent extends Event {
    private final WoolyNpc npc;

    protected NpcEvent(WoolyNpc npc) {
        this.npc = npc;
    }

    public @NotNull WoolyNpc getNpc() {
        return npc;
    }

    public @NotNull String getNpcName() {
        return npc.getName();
    }
}
