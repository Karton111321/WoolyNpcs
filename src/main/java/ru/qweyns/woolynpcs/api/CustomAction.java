package ru.qweyns.woolynpcs.api;

import org.bukkit.entity.Player;
import ru.qweyns.woolynpcs.model.WoolyNpc;

@FunctionalInterface
public interface CustomAction {
    void execute(Player player, WoolyNpc npc, String value);
}
