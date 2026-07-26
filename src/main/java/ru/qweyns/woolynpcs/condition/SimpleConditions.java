package ru.qweyns.woolynpcs.condition;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.qweyns.woolynpcs.WoolyNpcs;
import ru.qweyns.woolynpcs.hook.WorldGuardHook;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class SimpleConditions {
    private SimpleConditions() {}

    public static Condition permission(String node) {
        return of("PERM:" + node, player -> player.hasPermission(node));
    }

    public static Condition world(String worldName) {
        return of("WORLD:" + worldName, player ->
                player.getWorld() != null && player.getWorld().getName().equalsIgnoreCase(worldName));
    }

    public static Condition worldTime(String mode) {
        String upper = mode.toUpperCase(Locale.ROOT);
        return of("TIME:" + upper, player -> {
            long time = player.getWorld().getTime();
            boolean day = time >= 0 && time < 13000;
            return upper.equals("DAY") == day;
        });
    }

    public static Condition weather(String mode) {
        String upper = mode.toUpperCase(Locale.ROOT);
        return of("WEATHER:" + upper, player -> {
            boolean storm   = player.getWorld().hasStorm();
            boolean thunder = player.getWorld().isThundering();
            return switch (upper) {
                case "CLEAR"   -> !storm;
                case "RAIN"    -> storm && !thunder;
                case "THUNDER" -> thunder;
                default        -> true;
            };
        });
    }

    public static Condition item(String materialName, int amount) {
        int required = Math.max(1, amount);
        return of("ITEM:" + materialName + ":" + required, player -> {
            Material material = Material.matchMaterial(materialName);
            if (material == null) return false;
            return player.getInventory().containsAtLeast(new ItemStack(material), required);
        });
    }

    public static Condition gameMode(String modeName) {
        String upper = modeName.toUpperCase(Locale.ROOT);
        return of("GAMEMODE:" + upper, player -> {
            try {
                return player.getGameMode() == GameMode.valueOf(upper);
            } catch (IllegalArgumentException e) {
                return false;
            }
        });
    }

    public static Condition region(String regionId) {
        return of("REGION:" + regionId, player ->
                WorldGuardHook.getRegionsAt(player.getLocation()).stream()
                        .anyMatch(id -> id.equalsIgnoreCase(regionId)));
    }

    public static Condition realTime(String from, String to) {
        LocalTime start = parseTime(from);
        LocalTime end   = parseTime(to);
        String raw = "REALTIME:" + from + "-" + to;

        if (start == null || end == null) {
            WoolyNpcs.getInstance().getLogger().warning(
                    "Условие [" + raw + "] использует неверный формат времени, ожидается ЧЧ:ММ.");
            return of(raw, player -> false);
        }

        return of(raw, player -> {
            LocalTime now = LocalTime.now();
            if (start.isBefore(end) || start.equals(end)) {
                return !now.isBefore(start) && now.isBefore(end);
            }
            return !now.isBefore(start) || now.isBefore(end);
        });
    }

    private static LocalTime parseTime(String value) {
        try {
            String[] parts = value.trim().split(":");
            if (parts.length < 2) return null;
            return LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (Exception e) {
            return null;
        }
    }

    public static Condition weekday(String rawDays) {
        Set<DayOfWeek> days = new HashSet<>();
        for (String token : rawDays.split(",")) {
            DayOfWeek day = parseDay(token.trim());
            if (day != null) days.add(day);
        }

        String raw = "WEEKDAY:" + rawDays;
        if (days.isEmpty()) {
            WoolyNpcs.getInstance().getLogger().warning(
                    "Условие [" + raw + "] не содержит ни одного распознанного дня недели.");
            return of(raw, player -> false);
        }
        return of(raw, player -> days.contains(LocalDate.now().getDayOfWeek()));
    }

    private static DayOfWeek parseDay(String token) {
        String upper = token.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "MON", "MONDAY",    "ПН" -> DayOfWeek.MONDAY;
            case "TUE", "TUESDAY",   "ВТ" -> DayOfWeek.TUESDAY;
            case "WED", "WEDNESDAY", "СР" -> DayOfWeek.WEDNESDAY;
            case "THU", "THURSDAY",  "ЧТ" -> DayOfWeek.THURSDAY;
            case "FRI", "FRIDAY",    "ПТ" -> DayOfWeek.FRIDAY;
            case "SAT", "SATURDAY",  "СБ" -> DayOfWeek.SATURDAY;
            case "SUN", "SUNDAY",    "ВС" -> DayOfWeek.SUNDAY;
            default -> null;
        };
    }

    public static Condition flag(String key, String expected) {
        String raw = "FLAG:" + key + (expected == null ? "" : "=" + expected);
        return of(raw, player -> {
            String actual = ru.qweyns.woolynpcs.WoolyNpcs.getInstance()
                    .getPlayerDataManager().get(player).getFlag(key);
            if (actual == null) return false;
            return expected == null || actual.equalsIgnoreCase(expected);
        });
    }

    public static Condition chance(double percent) {
        double clamped = Math.max(0.0, Math.min(100.0, percent));
        String raw = "CHANCE:" + trimNumber(clamped);
        return of(raw, player -> ThreadLocalRandom.current().nextDouble() * 100.0 < clamped);
    }

    public static Condition firstJoin() {
        return of("FIRSTJOIN", player -> !player.hasPlayedBefore());
    }

    public static Condition of(String raw, Check check) {
        return new Condition() {
            @Override
            public boolean test(Player player) {
                try {
                    return check.test(player);
                } catch (Exception e) {
                    WoolyNpcs.getInstance().getLogger().warning(
                            "Ошибка проверки условия [" + raw + "]: " + e.getMessage());
                    return false;
                }
            }

            @Override
            public String serialize() {
                return raw;
            }
        };
    }

    private static String trimNumber(double value) {
        if (value == Math.floor(value)) return String.valueOf((long) value);
        return String.valueOf(value);
    }

    public static List<String> knownTags() {
        return new ArrayList<>(List.of("PERM", "WORLD", "TIME", "WEATHER", "PAPI",
                "ITEM", "GAMEMODE", "REGION", "REALTIME", "WEEKDAY", "CHANCE", "FIRSTJOIN", "FLAG",
                "OR", "AND"));
    }

    @FunctionalInterface
    public interface Check {
        boolean test(Player player) throws Exception;
    }
}
