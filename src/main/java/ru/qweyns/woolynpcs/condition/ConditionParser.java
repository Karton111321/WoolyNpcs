package ru.qweyns.woolynpcs.condition;

import ru.qweyns.woolynpcs.WoolyNpcs;
import ru.qweyns.woolynpcs.model.PapiCondition;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ConditionParser {
    private ConditionParser() {}

    public static final Set<String> TAGS = Set.of(
            "PERM", "WORLD", "TIME", "WEATHER", "PAPI",
            "ITEM", "GAMEMODE", "REGION", "REALTIME", "WEEKDAY", "CHANCE", "FIRSTJOIN", "FLAG",
            "OR", "AND");

    public static Condition parse(String body) {
        if (body == null) return null;
        String trimmed = body.trim();
        while (trimmed.length() > 1 && trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        if (trimmed.isEmpty()) return null;

        if (trimmed.startsWith("!")) {
            Condition inner = parse(trimmed.substring(1));
            return inner == null ? null : new NegatedCondition(inner);
        }

        int colon = trimmed.indexOf(':');
        String tag  = (colon < 0 ? trimmed : trimmed.substring(0, colon)).trim().toUpperCase(Locale.ROOT);
        String args = colon < 0 ? "" : trimmed.substring(colon + 1).trim();

        if (!TAGS.contains(tag)) return null;

        return switch (tag) {
            case "OR"        -> group(false, args);
            case "AND"       -> group(true,  args);
            case "PERM"      -> args.isEmpty() ? null : SimpleConditions.permission(args);
            case "WORLD"     -> args.isEmpty() ? null : SimpleConditions.world(args);
            case "TIME"      -> parseTime(args);
            case "WEATHER"   -> parseWeather(args);
            case "GAMEMODE"  -> args.isEmpty() ? null : SimpleConditions.gameMode(args);
            case "REGION"    -> args.isEmpty() ? null : SimpleConditions.region(args);
            case "WEEKDAY"   -> args.isEmpty() ? null : SimpleConditions.weekday(args);
            case "FIRSTJOIN" -> SimpleConditions.firstJoin();
            case "ITEM"      -> parseItem(args);
            case "FLAG"      -> parseFlag(args);
            case "REALTIME"  -> parseRealTime(args);
            case "CHANCE"    -> parseChance(args);
            case "PAPI"      -> parsePapi(args);
            default          -> null;
        };
    }

    public static boolean isConditionBody(String body) {
        if (body == null) return false;
        String trimmed = body.trim();
        if (trimmed.startsWith("!")) trimmed = trimmed.substring(1).trim();

        int colon = trimmed.indexOf(':');
        String tag = (colon < 0 ? trimmed : trimmed.substring(0, colon)).trim().toUpperCase(Locale.ROOT);
        return TAGS.contains(tag);
    }

    private static Condition group(boolean requireAll, String args) {
        List<Condition> parts = new ArrayList<>();
        for (String piece : splitTopLevel(args)) {
            Condition condition = parse(piece);
            if (condition == null) {
                warn("не удалось разобрать часть группы: '" + piece + "'");
                continue;
            }
            parts.add(condition);
        }
        if (parts.isEmpty()) return null;
        return new GroupCondition(requireAll, parts);
    }

    private static List<String> splitTopLevel(String input) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '[' || c == '(') depth++;
            else if (c == ']' || c == ')') depth = Math.max(0, depth - 1);

            if (c == '|' && depth == 0) {
                result.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) result.add(current.toString().trim());

        result.removeIf(String::isEmpty);
        return result;
    }

    private static Condition parseTime(String args) {
        String upper = args.toUpperCase(Locale.ROOT);
        if (!upper.equals("DAY") && !upper.equals("NIGHT")) {
            warn("[TIME:] принимает только DAY или NIGHT, получено '" + args + "'");
            return null;
        }
        return SimpleConditions.worldTime(upper);
    }

    private static Condition parseWeather(String args) {
        String upper = args.toUpperCase(Locale.ROOT);
        if (!upper.equals("CLEAR") && !upper.equals("RAIN") && !upper.equals("THUNDER")) {
            warn("[WEATHER:] принимает CLEAR, RAIN или THUNDER, получено '" + args + "'");
            return null;
        }
        return SimpleConditions.weather(upper);
    }

    private static Condition parseItem(String args) {
        String[] parts = args.split(":");
        if (parts.length == 0 || parts[0].isBlank()) {
            warn("[ITEM:] требует материал, например [ITEM:DIAMOND:5]");
            return null;
        }
        int amount = 1;
        if (parts.length >= 2) {
            try {
                amount = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                warn("[ITEM:] — количество '" + parts[1] + "' не число");
                return null;
            }
        }
        return SimpleConditions.item(parts[0].trim(), amount);
    }

    private static Condition parseFlag(String args) {
        if (args.isEmpty()) {
            warn("[FLAG:] требует имя флага, например [FLAG:quest_started]");
            return null;
        }
        int equals = args.indexOf('=');
        if (equals < 0) return SimpleConditions.flag(args, null);
        return SimpleConditions.flag(args.substring(0, equals).trim(), args.substring(equals + 1).trim());
    }

    private static Condition parseRealTime(String args) {
        int dash = args.indexOf('-');
        if (dash <= 0 || dash == args.length() - 1) {
            warn("[REALTIME:] ожидает промежуток вида 09:00-18:00, получено '" + args + "'");
            return null;
        }
        return SimpleConditions.realTime(args.substring(0, dash).trim(), args.substring(dash + 1).trim());
    }

    private static Condition parseChance(String args) {
        try {
            return SimpleConditions.chance(Double.parseDouble(args.replace("%", "").trim()));
        } catch (NumberFormatException e) {
            warn("[CHANCE:] ожидает число процентов, получено '" + args + "'");
            return null;
        }
    }

    private static Condition parsePapi(String args) {
        PapiCondition papi = PapiCondition.parse(args);
        if (papi == null) {
            warn("[PAPI:" + args + "] — не найден оператор сравнения");
            return null;
        }
        return SimpleConditions.of("PAPI:" + papi.getRaw(), papi::test);
    }

    private static void warn(String message) {
        WoolyNpcs plugin = WoolyNpcs.getInstance();
        if (plugin != null) plugin.getLogger().warning("Условие действия: " + message);
    }
}
