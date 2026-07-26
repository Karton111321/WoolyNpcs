package ru.qweyns.woolynpcs.condition;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public final class GroupCondition implements Condition {
    private final boolean requireAll;
    private final List<Condition> conditions;

    public GroupCondition(boolean requireAll, List<Condition> conditions) {
        this.requireAll  = requireAll;
        this.conditions  = List.copyOf(conditions);
    }

    @Override
    public boolean test(Player player) {
        if (conditions.isEmpty()) return true;
        for (Condition condition : conditions) {
            boolean result = condition.test(player);
            if (requireAll && !result) return false;
            if (!requireAll && result) return true;
        }
        return requireAll;
    }

    @Override
    public String serialize() {
        return (requireAll ? "AND:" : "OR:") + conditions.stream()
                .map(Condition::serialize)
                .collect(Collectors.joining("|"));
    }
}
