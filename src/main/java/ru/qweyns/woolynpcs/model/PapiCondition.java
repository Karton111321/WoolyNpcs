package ru.qweyns.woolynpcs.model;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;
import ru.qweyns.woolynpcs.WoolyNpcs;

import java.util.Locale;

public final class PapiCondition {
    private static final String[] OPERATORS = {">=", "<=", "!=", "==", "~=", ">", "<", "="};

    private final String left;
    private final String operator;
    private final String right;
    private final String raw;

    private PapiCondition(String left, String operator, String right, String raw) {
        this.left     = left;
        this.operator = operator;
        this.right    = right;
        this.raw      = raw;
    }

    public static PapiCondition parse(String expression) {
        if (expression == null) return null;
        String trimmed = expression.trim();
        if (trimmed.isEmpty()) return null;

        int    bestIndex = -1;
        String bestOp    = null;

        for (String op : OPERATORS) {
            int idx = trimmed.indexOf(op);
            if (idx < 0) continue;

            if (bestIndex < 0 || idx < bestIndex
                    || (idx == bestIndex && op.length() > bestOp.length())) {
                bestIndex = idx;
                bestOp    = op;
            }
        }

        if (bestIndex <= 0) return null;

        String left  = trimmed.substring(0, bestIndex).trim();
        String right = trimmed.substring(bestIndex + bestOp.length()).trim();
        if (left.isEmpty() || right.isEmpty()) return null;

        return new PapiCondition(left, bestOp.equals("=") ? "==" : bestOp, right, trimmed);
    }

    public boolean test(Player player) {
        String leftValue  = resolve(player, left);
        String rightValue = resolve(player, right);

        Double leftNum  = toNumber(leftValue);
        Double rightNum = toNumber(rightValue);

        if (leftNum != null && rightNum != null) {
            int cmp = Double.compare(leftNum, rightNum);
            return switch (operator) {
                case ">="  -> cmp >= 0;
                case "<="  -> cmp <= 0;
                case ">"   -> cmp >  0;
                case "<"   -> cmp <  0;
                case "!="  -> cmp != 0;
                case "~="  -> leftValue.toLowerCase(Locale.ROOT).contains(rightValue.toLowerCase(Locale.ROOT));
                default    -> cmp == 0;
            };
        }

        int cmp = leftValue.compareToIgnoreCase(rightValue);
        return switch (operator) {
            case ">="  -> cmp >= 0;
            case "<="  -> cmp <= 0;
            case ">"   -> cmp >  0;
            case "<"   -> cmp <  0;
            case "!="  -> cmp != 0;
            case "~="  -> leftValue.toLowerCase(Locale.ROOT).contains(rightValue.toLowerCase(Locale.ROOT));
            default    -> cmp == 0;
        };
    }

    private String resolve(Player player, String value) {
        String result = value.replace("{player}", player.getName());
        if (WoolyNpcs.getInstance().hasPapi()) {
            result = PlaceholderAPI.setPlaceholders(player, result);
        }
        return result == null ? "" : result.trim();
    }

    private Double toNumber(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return Double.valueOf(value.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String getRaw() {
        return raw;
    }

    @Override
    public String toString() {
        return "[PAPI:" + raw + "]";
    }
}
