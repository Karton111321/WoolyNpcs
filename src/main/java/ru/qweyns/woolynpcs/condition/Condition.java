package ru.qweyns.woolynpcs.condition;

import org.bukkit.entity.Player;

public interface Condition {
    boolean test(Player player);

    String serialize();
}
