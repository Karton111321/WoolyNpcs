package ru.qweyns.woolynpcs.condition;

import org.bukkit.entity.Player;

public final class NegatedCondition implements Condition {
    private final Condition inner;

    public NegatedCondition(Condition inner) {
        this.inner = inner;
    }

    @Override
    public boolean test(Player player) {
        return !inner.test(player);
    }

    @Override
    public String serialize() {
        return "!" + inner.serialize();
    }
}
