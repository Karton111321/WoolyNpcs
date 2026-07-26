package ru.qweyns.woolynpcs.model;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.qweyns.woolynpcs.WoolyNpcs;
import ru.qweyns.woolynpcs.api.CustomAction;
import ru.qweyns.woolynpcs.api.WoolyNpcsApi;
import ru.qweyns.woolynpcs.condition.Condition;
import ru.qweyns.woolynpcs.condition.ConditionParser;
import ru.qweyns.woolynpcs.playerdata.PlayerData;
import ru.qweyns.woolynpcs.util.ColorUtil;
import ru.qweyns.woolynpcs.util.RegistryUtil;
import ru.qweyns.woolynpcs.util.WebhookSender;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NpcAction {
    private static final Pattern PERM_PATTERN    = Pattern.compile("\\[PERM:([^\\]]+)\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern CD_PATTERN      = Pattern.compile("\\[CD:(\\d{1,6})\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern ONCE_PATTERN    = Pattern.compile("\\[ONCE\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern DELAY_PATTERN   = Pattern.compile("\\[DELAY:(\\d{1,6})\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern MONEY_PATTERN   = Pattern.compile("\\[MONEY:(\\d+(?:\\.\\d+)?)\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXP_PATTERN     = Pattern.compile("\\[EXP:(\\d{1,6})\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern WORLD_PATTERN   = Pattern.compile("\\[WORLD:([^\\]]+)\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_PATTERN    = Pattern.compile("\\[TIME:(DAY|NIGHT)\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern WEATHER_PATTERN = Pattern.compile("\\[WEATHER:(CLEAR|RAIN|THUNDER)\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAPI_PATTERN    = Pattern.compile("\\[PAPI:([^\\]]+)\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONDITION_PATTERN = Pattern.compile("\\[([^\\[\\]]+)\\]");

    private final UUID uid;

    private final ClickType  clickType;
    private final ActionType type;
    private final String     customType;
    private final String     value;

    private final String  permission;
    private final int     cooldownSeconds;
    private final boolean oneTime;
    private final int     delayTicks;
    private final double  moneyRequired;
    private final int     expRequired;

    private final String  worldCondition;
    private final String  timeCondition;
    private final String  weatherCondition;
    private final List<PapiCondition> papiConditions;
    private final List<Condition> conditions;

    private final List<SubStep> subSteps;

    private final Set<UUID> legacyExecutedBy = ConcurrentHashMap.newKeySet();

    public NpcAction(ClickType clickType, ActionType type, String rawValue) {
        this(UUID.randomUUID(), clickType, type, null, rawValue);
    }

    public NpcAction(UUID uid, ClickType clickType, ActionType type, String rawValue) {
        this(uid, clickType, type, null, rawValue);
    }

    public static NpcAction custom(UUID uid, ClickType clickType, String customType, String rawValue) {
        return new NpcAction(uid, clickType, null, customType, rawValue);
    }

    private NpcAction(UUID uid, ClickType clickType, ActionType type, String customType, String rawValue) {
        this.uid        = (uid == null) ? UUID.randomUUID() : uid;
        this.clickType  = clickType;
        this.type       = type;
        this.customType = (type == null && customType != null)
                ? customType.trim().toUpperCase(Locale.ROOT) : null;

        String  remaining    = (rawValue == null) ? "" : rawValue;
        String  parsedPerm   = null;
        String  parsedWorld  = null;
        String  parsedTime   = null;
        String  parsedWeather = null;
        int     parsedCd     = 0, parsedDelay = 0, parsedExp = 0;
        double  parsedMoney  = 0.0;
        boolean parsedOnce   = false;

        Matcher m;

        m = PERM_PATTERN.matcher(remaining);
        if (m.find()) { parsedPerm = m.group(1).trim(); remaining = m.replaceFirst(""); }

        m = CD_PATTERN.matcher(remaining);
        if (m.find()) { try { parsedCd = Integer.parseInt(m.group(1)); } catch (Exception ignored) {} remaining = m.replaceFirst(""); }

        m = ONCE_PATTERN.matcher(remaining);
        if (m.find()) { parsedOnce = true; remaining = m.replaceFirst(""); }

        m = DELAY_PATTERN.matcher(remaining);
        if (m.find()) { try { parsedDelay = Integer.parseInt(m.group(1)) * 20; } catch (Exception ignored) {} remaining = m.replaceFirst(""); }

        m = MONEY_PATTERN.matcher(remaining);
        if (m.find()) { try { parsedMoney = Double.parseDouble(m.group(1)); } catch (Exception ignored) {} remaining = m.replaceFirst(""); }

        m = EXP_PATTERN.matcher(remaining);
        if (m.find()) { try { parsedExp = Integer.parseInt(m.group(1)); } catch (Exception ignored) {} remaining = m.replaceFirst(""); }

        m = WORLD_PATTERN.matcher(remaining);
        if (m.find()) { parsedWorld = m.group(1).trim(); remaining = m.replaceFirst(""); }

        m = TIME_PATTERN.matcher(remaining);
        if (m.find()) { parsedTime = m.group(1).toUpperCase(Locale.ROOT); remaining = m.replaceFirst(""); }

        m = WEATHER_PATTERN.matcher(remaining);
        if (m.find()) { parsedWeather = m.group(1).toUpperCase(Locale.ROOT); remaining = m.replaceFirst(""); }

        List<PapiCondition> parsedPapi = new ArrayList<>();
        m = PAPI_PATTERN.matcher(remaining);
        StringBuilder withoutPapi = new StringBuilder();
        int last = 0;
        while (m.find()) {
            PapiCondition condition = PapiCondition.parse(m.group(1));
            if (condition != null) parsedPapi.add(condition);
            else WoolyNpcs.getInstance().getLogger().warning(
                    "Не удалось разобрать условие [PAPI:" + m.group(1) + "] — нет оператора сравнения.");
            withoutPapi.append(remaining, last, m.start());
            last = m.end();
        }
        withoutPapi.append(remaining.substring(last));
        remaining = withoutPapi.toString();
        this.papiConditions = parsedPapi.isEmpty() ? List.of() : List.copyOf(parsedPapi);

        List<Condition> parsedConditions = new ArrayList<>();
        Matcher cm = CONDITION_PATTERN.matcher(remaining);
        StringBuilder withoutConditions = new StringBuilder();
        int cursor = 0;
        while (cm.find()) {
            String body = cm.group(1);
            if (!ConditionParser.isConditionBody(body)) continue;

            Condition condition = ConditionParser.parse(body);
            if (condition != null) parsedConditions.add(condition);

            withoutConditions.append(remaining, cursor, cm.start());
            cursor = cm.end();
        }
        withoutConditions.append(remaining.substring(cursor));
        remaining = withoutConditions.toString();
        this.conditions = parsedConditions.isEmpty() ? List.of() : List.copyOf(parsedConditions);

        this.permission       = parsedPerm;
        this.cooldownSeconds  = Math.max(0, parsedCd);
        this.oneTime          = parsedOnce;
        this.delayTicks       = Math.max(0, parsedDelay);
        this.moneyRequired    = Math.max(0, parsedMoney);
        this.expRequired      = Math.max(0, parsedExp);
        this.worldCondition   = parsedWorld;
        this.timeCondition    = parsedTime;
        this.weatherCondition = parsedWeather;
        this.value            = remaining.trim();
        this.subSteps         = (type == ActionType.RANDOM || type == ActionType.SEQUENCE)
                ? buildSubSteps(this.value) : List.of();
    }

    private static List<SubStep> buildSubSteps(String rawValue) {
        List<SubStep> steps = new ArrayList<>();
        for (String piece : rawValue.split("\\|")) {
            String entry = piece.trim();
            if (entry.isEmpty()) continue;

            String[] parts = entry.split(":", 2);
            if (parts.length < 2) {
                warnPlugin("Подзадача '" + entry + "' записана без типа — пропущена.");
                continue;
            }

            String head = parts[0].trim().toUpperCase(Locale.ROOT);
            if (head.equals("WAIT")) {
                try {
                    steps.add(SubStep.wait(Math.max(0, Integer.parseInt(parts[1].trim()))));
                } catch (NumberFormatException e) {
                    warnPlugin("WAIT ожидает число тиков, получено '" + parts[1] + "'.");
                }
                continue;
            }

            NpcAction sub;
            try {
                sub = new NpcAction(ClickType.ANY, ActionType.valueOf(head), parts[1]);
            } catch (IllegalArgumentException e) {
                if (!WoolyNpcsApi.isRegistered(head)) {
                    warnPlugin("Неизвестный тип подзадачи '" + head + "' — пропущена.");
                    continue;
                }
                sub = NpcAction.custom(UUID.randomUUID(), ClickType.ANY, head, parts[1]);
            }

            if (!sub.isEmpty() || !sub.subSteps.isEmpty()) steps.add(SubStep.action(sub));
        }
        return steps.isEmpty() ? List.of() : List.copyOf(steps);
    }

    private static void warnPlugin(String message) {
        WoolyNpcs plugin = WoolyNpcs.getInstance();
        if (plugin != null) plugin.getLogger().warning("Действие: " + message);
    }

    private record SubStep(NpcAction action, int waitTicks) {
        static SubStep action(NpcAction action) { return new SubStep(action, 0); }
        static SubStep wait(int ticks)          { return new SubStep(null, ticks); }
        boolean isWait()                        { return action == null; }
    }

    public void execute(Player player) {
        execute(player, null);
    }

    public void execute(Player player, WoolyNpc npc) {
        if (value.isEmpty() && subSteps.isEmpty()) return;

        UUID pid = player.getUniqueId();

        if (permission != null && !player.hasPermission(permission)) {
            String msg = WoolyNpcs.getInstance().getConfigManager().getMessage("action-no-permission");
            if (msg != null && !msg.isBlank()) player.sendMessage(ColorUtil.format(msg));
            return;
        }

        PlayerData data = WoolyNpcs.getInstance().getPlayerDataManager().get(pid);

        if (oneTime && data.hasExecuted(uid)) return;
        if (data.isOnCooldown(uid, cooldownSeconds)) return;

        if (!checkConditions(player)) return;

        if (moneyRequired > 0) {
            if (WoolyNpcs.getInstance().getEconomy() == null) return;
            if (!WoolyNpcs.getInstance().getEconomy().has(player, moneyRequired)) {
                String msg = WoolyNpcs.getInstance().getConfigManager().getMessage("action-no-money",
                        "amount", String.valueOf(moneyRequired));
                player.sendMessage(ColorUtil.format(msg));
                return;
            }
        }

        if (expRequired > 0) {
            if (player.getLevel() < expRequired) {
                String msg = WoolyNpcs.getInstance().getConfigManager().getMessage("action-no-exp",
                        "amount", String.valueOf(expRequired));
                player.sendMessage(ColorUtil.format(msg));
                return;
            }
        }

        if (delayTicks > 0) {
            markCooldown(data);
            Bukkit.getScheduler().runTaskLater(WoolyNpcs.getInstance(), () -> processExecution(player, pid, npc), delayTicks);
        } else {
            processExecution(player, pid, npc);
        }
    }

    private boolean checkConditions(Player player) {
        if (worldCondition != null) {
            if (player.getWorld() == null || !player.getWorld().getName().equalsIgnoreCase(worldCondition)) {
                return false;
            }
        }

        if (timeCondition != null) {
            long time = player.getWorld().getTime();
            boolean isDay = time >= 0 && time < 13000;
            if (timeCondition.equals("DAY") && !isDay) return false;
            if (timeCondition.equals("NIGHT") && isDay) return false;
        }

        for (PapiCondition condition : papiConditions) {
            if (!condition.test(player)) return false;
        }

        for (Condition condition : conditions) {
            if (!condition.test(player)) return false;
        }

        if (weatherCondition != null) {
            boolean storm = player.getWorld().hasStorm();
            boolean thunder = player.getWorld().isThundering();
            switch (weatherCondition) {
                case "CLEAR"   -> { if (storm) return false; }
                case "RAIN"    -> { if (!storm || thunder) return false; }
                case "THUNDER" -> { if (!thunder) return false; }
            }
        }

        return true;
    }

    private void processExecution(Player player, UUID pid, WoolyNpc npc) {
        if (!player.isOnline()) return;

        if (moneyRequired > 0 && WoolyNpcs.getInstance().getEconomy() != null) {
            if (!WoolyNpcs.getInstance().getEconomy().has(player, moneyRequired)) {
                String msg = WoolyNpcs.getInstance().getConfigManager().getMessage("action-no-money",
                        "amount", String.valueOf(moneyRequired));
                player.sendMessage(ColorUtil.format(msg));
                return;
            }
            WoolyNpcs.getInstance().getEconomy().withdrawPlayer(player, moneyRequired);
        }
        if (expRequired > 0) {
            if (player.getLevel() < expRequired) {
                String msg = WoolyNpcs.getInstance().getConfigManager().getMessage("action-no-exp",
                        "amount", String.valueOf(expRequired));
                player.sendMessage(ColorUtil.format(msg));
                return;
            }
            player.setLevel(player.getLevel() - expRequired);
        }

        recordExecution(pid);

        String parsed = value
                .replace("{player}", player.getName())
                .replace("%player%", player.getName());
        if (WoolyNpcs.getInstance().hasPapi()) {
            parsed = PlaceholderAPI.setPlaceholders(player, parsed);
        }

        dispatch(player, parsed, npc);
    }

    private void dispatch(Player player, String parsed, WoolyNpc npc) {
        if (type == null) {
            CustomAction handler = WoolyNpcsApi.getAction(customType);
            if (handler == null) return;
            try {
                handler.execute(player, npc, parsed);
            } catch (Exception e) {
                WoolyNpcs.getInstance().getLogger().warning(
                        "Ошибка в пользовательском действии '" + customType + "': " + e.getMessage());
            }
            return;
        }

        switch (type) {
            case MESSAGE -> player.sendMessage(ColorUtil.format(parsed));

            case PLAYER_COMMAND, CONSOLE_COMMAND -> {
                String cmd = ColorUtil.stripForCommand(parsed);
                if (cmd.startsWith("/")) cmd = cmd.substring(1).trim();
                if (cmd.isEmpty()) return;

                String base = cmd.split("\\s+")[0].toLowerCase(Locale.ROOT);
                int colon = base.indexOf(':');
                if (colon >= 0) base = base.substring(colon + 1);

                if (WoolyNpcs.getInstance().getConfigManager().getBlockedCommands().contains(base)) {
                    player.sendMessage(ColorUtil.format(
                            WoolyNpcs.getInstance().getConfigManager().getMessage("command-blocked")));
                    return;
                }

                if (type == ActionType.CONSOLE_COMMAND) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                else player.performCommand(cmd);
            }

            case SERVER -> {
                try {
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    DataOutputStream out = new DataOutputStream(b);
                    out.writeUTF("Connect");
                    out.writeUTF(parsed);
                    player.sendPluginMessage(WoolyNpcs.getInstance(), "BungeeCord", b.toByteArray());
                } catch (Exception e) {
                    WoolyNpcs.getInstance().getLogger().warning("BungeeCord transfer error: " + e.getMessage());
                }
            }

            case SOUND -> {
                String[] args = parsed.split(" ");
                if (args.length >= 1) {
                    Sound sound = RegistryUtil.resolveSound(args[0]);
                    if (sound == null) return;
                    try {
                        float vol = args.length >= 2 ? Float.parseFloat(args[1]) : 1.0f;
                        float pitch = args.length >= 3 ? Float.parseFloat(args[2]) : 1.0f;
                        player.playSound(player.getLocation(), sound, vol, pitch);
                    } catch (Exception ignored) {}
                }
            }

            case TITLE -> {
                String[] parts = parsed.split("\\|", 2);
                String titleText = parts[0].trim();
                String subtitle  = parts.length > 1 ? parts[1].trim() : "";
                var titleCfg = WoolyNpcs.getInstance().getConfigManager();
                player.showTitle(Title.title(
                        ColorUtil.format(titleText),
                        ColorUtil.format(subtitle),
                        Title.Times.times(
                                Duration.ofMillis(titleCfg.getTitleFadeIn()  * 50L),
                                Duration.ofMillis(titleCfg.getTitleStay()    * 50L),
                                Duration.ofMillis(titleCfg.getTitleFadeOut() * 50L))));
            }

            case TELEPORT -> {
                String[] args = parsed.split(" ");
                if (args.length >= 4) {
                    try {
                        World w = Bukkit.getWorld(args[0]);
                        double x = Double.parseDouble(args[1]);
                        double y = Double.parseDouble(args[2]);
                        double z = Double.parseDouble(args[3]);
                        float yaw = args.length >= 5 ? Float.parseFloat(args[4]) : 0;
                        float pitch = args.length >= 6 ? Float.parseFloat(args[5]) : 0;
                        if (w != null) player.teleport(new Location(w, x, y, z, yaw, pitch));
                    } catch (Exception ignored) {}
                }
            }

            case ACTIONBAR -> player.sendActionBar(ColorUtil.format(parsed));

            case PARTICLE -> {
                String[] args = parsed.split(" ");
                if (args.length >= 1) {
                    Particle particle = RegistryUtil.resolveParticle(args[0]);
                    if (particle == null) return;
                    try {
                        int count = args.length >= 2 ? Integer.parseInt(args[1]) : 10;
                        double spread = args.length >= 3 ? Double.parseDouble(args[2]) : 0.5;
                        player.getWorld().spawnParticle(particle, player.getLocation().add(0, 1, 0),
                                count, spread, spread, spread, 0);
                    } catch (Exception ignored) {}
                }
            }

            case EFFECT -> {
                String[] args = parsed.split(" ");
                if (args.length >= 1) {
                    PotionEffectType effectType = RegistryUtil.resolveEffect(args[0]);
                    if (effectType == null) return;
                    try {
                        int duration = args.length >= 2 ? Integer.parseInt(args[1]) * 20 : 200;
                        int amplifier = args.length >= 3 ? Integer.parseInt(args[2]) : 0;
                        player.addPotionEffect(new PotionEffect(effectType, duration, amplifier));
                    } catch (Exception ignored) {}
                }
            }

            case GIVE_ITEM -> {
                String[] args = parsed.split(" ");
                if (args.length >= 1) {
                    try {
                        Material mat = Material.matchMaterial(args[0]);
                        if (mat != null) {
                            int amount = args.length >= 2 ? Integer.parseInt(args[1]) : 1;
                            ItemStack item = new ItemStack(mat, amount);
                            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                            for (ItemStack drop : leftover.values()) {
                                player.getWorld().dropItemNaturally(player.getLocation(), drop);
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }

            case FIREWORK -> {
                try {
                    Firework fw = (Firework) player.getWorld().spawnEntity(
                            player.getLocation(), EntityType.FIREWORK_ROCKET);
                    FireworkMeta meta = fw.getFireworkMeta();
                    String[] args = parsed.split(" ");
                    FireworkEffect.Type fwType = FireworkEffect.Type.BALL;
                    if (args.length >= 1) {
                        try { fwType = FireworkEffect.Type.valueOf(args[0].toUpperCase(Locale.ROOT)); }
                        catch (Exception ignored) {}
                    }
                    meta.addEffect(FireworkEffect.builder()
                            .withColor(org.bukkit.Color.fromRGB(
                                    (int)(Math.random()*255),
                                    (int)(Math.random()*255),
                                    (int)(Math.random()*255)))
                            .withFade(org.bukkit.Color.WHITE)
                            .with(fwType)
                            .flicker(true)
                            .trail(true)
                            .build());
                    meta.setPower(args.length >= 2 ? Integer.parseInt(args[1]) : 1);
                    fw.setFireworkMeta(meta);
                } catch (Exception ignored) {}
            }

            case BOSSBAR -> {
                String[] args = parsed.split("\\|");
                String text = args.length >= 1 ? args[0].trim() : "BossBar";
                int defaultSeconds = WoolyNpcs.getInstance().getConfigManager().getBossbarDefaultSeconds();
                int durationSec = Math.max(1, args.length >= 2 ? parseInt(args[1].trim(), defaultSeconds) : defaultSeconds);
                BossBar.Color barColor = BossBar.Color.PURPLE;
                if (args.length >= 3) {
                    try { barColor = BossBar.Color.valueOf(args[2].trim().toUpperCase(Locale.ROOT)); }
                    catch (IllegalArgumentException ignored) {}
                }

                Component barText = ColorUtil.format(text);
                BossBar bar = BossBar.bossBar(barText, 1.0f, barColor, BossBar.Overlay.PROGRESS);
                player.showBossBar(bar);

                float step = 1.0f / (durationSec * 20f);
                Bukkit.getScheduler().runTaskTimer(WoolyNpcs.getInstance(), task -> {
                    float progress = bar.progress() - step;
                    if (progress <= 0f || !player.isOnline()) {
                        player.hideBossBar(bar);
                        task.cancel();
                    } else {
                        bar.progress(Math.min(1.0f, progress));
                    }
                }, 1L, 1L);
            }

            case BROADCAST -> {
                Component text = ColorUtil.format(parsed);
                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.sendMessage(text);
                }
            }

            case MONEY_GIVE -> {
                if (WoolyNpcs.getInstance().getEconomy() == null) return;
                try {
                    double amount = Double.parseDouble(parsed.trim());
                    if (amount > 0) WoolyNpcs.getInstance().getEconomy().depositPlayer(player, amount);
                } catch (NumberFormatException ignored) {}
            }

            case MONEY_TAKE -> {
                if (WoolyNpcs.getInstance().getEconomy() == null) return;
                try {
                    double amount = Double.parseDouble(parsed.trim());
                    if (amount <= 0) return;
                    if (!WoolyNpcs.getInstance().getEconomy().has(player, amount)) {
                        player.sendMessage(ColorUtil.format(
                                WoolyNpcs.getInstance().getConfigManager().getMessage("action-no-money",
                                        "amount", String.valueOf(amount))));
                        return;
                    }
                    WoolyNpcs.getInstance().getEconomy().withdrawPlayer(player, amount);
                } catch (NumberFormatException ignored) {}
            }

            case ITEM_TAKE -> {
                String[] args = parsed.split(" ");
                Material material = Material.matchMaterial(args[0].trim());
                if (material == null) return;

                int amount = 1;
                if (args.length >= 2) {
                    try { amount = Math.max(1, Integer.parseInt(args[1].trim())); }
                    catch (NumberFormatException ignored) {}
                }

                ItemStack stack = new ItemStack(material, amount);
                if (!player.getInventory().containsAtLeast(stack, amount)) return;
                player.getInventory().removeItem(stack);
            }

            case HEAL -> {
                double amount = parseDouble(parsed.trim(), -1);
                double max = maxHealthOf(player);
                double target = amount <= 0 ? max : Math.min(max, player.getHealth() + amount);
                player.setHealth(Math.max(0.0, target));
            }

            case FEED -> {
                int amount = parseInt(parsed.trim(), -1);
                int target = amount <= 0 ? 20 : Math.min(20, player.getFoodLevel() + amount);
                player.setFoodLevel(target);
                if (target >= 20) player.setSaturation(5.0f);
            }

            case XP_GIVE -> {
                String[] args = parsed.split(" ");
                int amount = parseInt(args[0].trim(), 0);
                if (amount <= 0) return;

                boolean levels = args.length >= 2 && args[1].trim().toLowerCase(Locale.ROOT).startsWith("lvl")
                        || args.length >= 2 && args[1].trim().toLowerCase(Locale.ROOT).startsWith("level");
                if (levels) player.setLevel(player.getLevel() + amount);
                else player.giveExp(amount);
            }

            case OPEN_GUI -> WoolyNpcs.getInstance().getMenuManager().open(player, parsed.trim(), npc);

            case CLOSE_INVENTORY -> player.closeInventory();

            case DISCORD_WEBHOOK -> {
                int space = parsed.indexOf(' ');
                if (space <= 0) return;
                String url  = parsed.substring(0, space).trim();
                String text = parsed.substring(space + 1).trim();
                WebhookSender.send(url, text);
            }

            case RANDOM -> {
                if (subSteps.isEmpty()) return;
                List<SubStep> choices = subSteps.stream().filter(step -> !step.isWait()).toList();
                if (choices.isEmpty()) return;
                SubStep picked = choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
                picked.action().execute(player, npc);
            }

            case SEQUENCE -> runSequence(player, npc, 0, 0L);

            case FLAG_SET -> {
                String[] args = parsed.trim().split(" ", 2);
                if (args[0].isBlank()) return;
                String flagValue = args.length >= 2 ? args[1].trim() : "1";
                WoolyNpcs.getInstance().getPlayerDataManager().get(player).setFlag(args[0], flagValue);
            }

            case FLAG_CLEAR -> {
                String key = parsed.trim();
                if (!key.isBlank()) {
                    WoolyNpcs.getInstance().getPlayerDataManager().get(player).setFlag(key, null);
                }
            }

            case NPC_ANIMATION -> {
                if (npc == null || !npc.isSpawned()) return;
                String[] args = parsed.split(" ");
                String animation = args[0].trim();
                if (animation.isEmpty()) return;

                npc.playAnimation(animation);

                if (args.length >= 2) {
                    int seconds = parseInt(args[1].trim(), 0);
                    if (seconds > 0) {
                        Bukkit.getScheduler().runTaskLater(WoolyNpcs.getInstance(), () -> {
                            if (npc.isSpawned()) npc.restoreDefaultAnimation();
                        }, seconds * 20L);
                    }
                }
            }

            case NPC_LOOK -> {
                if (npc == null || !npc.isSpawned()) return;
                String[] args = parsed.split(" ");
                String mode = args[0].trim().toLowerCase(Locale.ROOT);

                if (mode.equals("reset")) {
                    npc.clearForcedLook();
                    npc.rotateSmoothly(npc.getDefaultYaw(), npc.getDefaultPitch());
                    return;
                }

                org.bukkit.Location npcLoc = npc.getBaseEntity() == null
                        ? npc.getLocation() : npc.getBaseEntity().getLocation();
                org.bukkit.Location target = player.getEyeLocation();

                double dx = target.getX() - npcLoc.getX();
                double dz = target.getZ() - npcLoc.getZ();
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

                int seconds = args.length >= 2 ? parseInt(args[1].trim(), 3) : 3;
                npc.forceLook(yaw, 0f, Math.max(1, seconds));
                npc.rotateSmoothly(yaw, 0f);
            }

            case NPC_WALK_TO -> {
                if (npc == null || !npc.isSpawned()) return;
                String[] args = parsed.split(" ");
                if (args.length < 3) return;
                try {
                    org.bukkit.Location base = npc.getBaseEntity() == null
                            ? npc.getLocation() : npc.getBaseEntity().getLocation();
                    if (base.getWorld() == null) return;

                    org.bukkit.Location target = new org.bukkit.Location(base.getWorld(),
                            Double.parseDouble(args[0]),
                            Double.parseDouble(args[1]),
                            Double.parseDouble(args[2]));
                    double speed = args.length >= 4 ? Double.parseDouble(args[3]) : 0.15;
                    WoolyNpcs.getInstance().getWalkingTask().walkTo(npc, target, speed);
                } catch (NumberFormatException ignored) {}
            }

            case NPC_HOLOGRAM_SET -> {
                if (npc == null) return;
                npc.getHologramLines().clear();
                if (!parsed.equalsIgnoreCase("none")) {
                    for (String line : parsed.split("\\|")) {
                        npc.getHologramLines().add(line.trim());
                    }
                }
                npc.updateHologram();
                WoolyNpcs.getInstance().getStorageManager().markDirty();
            }
        }
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private double parseDouble(String s, double def) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }

    private double maxHealthOf(Player player) {
        try {
            var method = player.getClass().getMethod("getMaxHealth");
            Object value = method.invoke(player);
            if (value instanceof Number number && number.doubleValue() > 0) return number.doubleValue();
        } catch (Throwable ignored) {}
        return 20.0;
    }

    private void runSequence(Player player, WoolyNpc npc, int index, long delayTicks) {
        if (index >= subSteps.size()) return;

        SubStep step = subSteps.get(index);
        if (step.isWait()) {
            runSequence(player, npc, index + 1, delayTicks + step.waitTicks());
            return;
        }

        NpcAction action = step.action();
        if (delayTicks <= 0) {
            action.execute(player, npc);
        } else {
            Bukkit.getScheduler().runTaskLater(WoolyNpcs.getInstance(), () -> {
                if (player.isOnline()) action.execute(player, npc);
            }, delayTicks);
        }
        runSequence(player, npc, index + 1, delayTicks);
    }

    private void recordExecution(UUID pid) {
        PlayerData data = WoolyNpcs.getInstance().getPlayerDataManager().get(pid);
        markCooldown(data);
        if (oneTime) data.markExecuted(uid);
    }

    private void markCooldown(PlayerData data) {
        if (cooldownSeconds <= 0) return;
        data.markCooldown(uid);
    }

    public String getSaveString() {
        return clickType.name() + ':' + getTypeName() + ':' + rebuildRawValue();
    }

    public String getTypeName() {
        return type != null ? type.name() : customType;
    }

    public String rebuildRawValue() {
        StringBuilder sb = new StringBuilder();
        if (permission       != null) sb.append("[PERM:").append(permission).append(']');
        if (cooldownSeconds  >  0)    sb.append("[CD:").append(cooldownSeconds).append(']');
        if (delayTicks       >  0)    sb.append("[DELAY:").append(delayTicks / 20).append(']');
        if (moneyRequired    >  0)    sb.append("[MONEY:").append(moneyRequired).append(']');
        if (expRequired      >  0)    sb.append("[EXP:").append(expRequired).append(']');
        if (oneTime)                  sb.append("[ONCE]");
        if (worldCondition   != null) sb.append("[WORLD:").append(worldCondition).append(']');
        if (timeCondition    != null) sb.append("[TIME:").append(timeCondition).append(']');
        if (weatherCondition != null) sb.append("[WEATHER:").append(weatherCondition).append(']');
        for (PapiCondition condition : papiConditions) {
            sb.append("[PAPI:").append(condition.getRaw()).append(']');
        }
        for (Condition condition : conditions) {
            sb.append('[').append(condition.serialize()).append(']');
        }
        sb.append(value);
        return sb.toString();
    }

    public UUID       getUid()             { return uid;             }
    public ClickType  getClickType()       { return clickType;       }
    public ActionType getType()            { return type;            }
    public String     getCustomType()      { return customType;      }
    public boolean    isCustom()           { return type == null;    }
    public List<PapiCondition> getPapiConditions() { return papiConditions; }
    public List<Condition>     getConditions()     { return conditions;     }
    public String     getValue()           { return value;           }
    public String     getPermission()      { return permission;      }
    public int        getCooldownSeconds() { return cooldownSeconds; }
    public boolean    isOneTime()          { return oneTime;         }
    public boolean    isEmpty()            { return value.isEmpty() && subSteps.isEmpty(); }
    public Set<UUID>  getLegacyExecutedBy()    { return legacyExecutedBy; }
    public void       addExecutedBy(UUID uuid) { legacyExecutedBy.add(uuid); }
}
