package ru.qweyns.woolynpcs.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.qweyns.woolynpcs.WoolyNpcs;
import ru.qweyns.woolynpcs.data.StorageManager;
import ru.qweyns.woolynpcs.gui.GuiManager;
import ru.qweyns.woolynpcs.api.WoolyNpcsApi;
import ru.qweyns.woolynpcs.model.*;
import ru.qweyns.woolynpcs.schedule.NpcSchedule;
import ru.qweyns.woolynpcs.schedule.RoutineEntry;
import ru.qweyns.woolynpcs.util.ColorUtil;
import ru.qweyns.woolynpcs.util.ModelEngineUtil;
import ru.qweyns.woolynpcs.waypoint.Waypoint;
import ru.qweyns.woolynpcs.waypoint.WaypointPath;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.Comparator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NpcCommand implements CommandExecutor, TabCompleter {
    private final WoolyNpcs plugin;

    public NpcCommand(WoolyNpcs plugin) {
        this.plugin = plugin;
    }

    public static final String PERM_ADMIN = "woolynpcs.admin";
    public static final String PERM_COMMAND_PREFIX = "woolynpcs.command.";
    public static final String PERM_NPC_PREFIX = "woolynpcs.npc.";

    private static final Pattern NPC_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!sender.hasPermission(PERM_ADMIN)) { sendMessage(sender, "no-permission"); return true; }
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("dialogchoice")) {
            handleDialogChoice(sender, args);
            return true;
        }

        if (!canUse(sender, sub)) {
            sendMessage(sender, "no-permission");
            return true;
        }

        switch (sub) {
            case "help"       -> sendHelp(sender, label);
            case "reload"     -> handleReload(sender);
            case "create"     -> handleCreate(sender, args, label);
            case "remove"     -> handleRemove(sender, args, label);
            case "copy"       -> handleCopy(sender, args, label);
            case "rename"     -> handleRename(sender, args, label);
            case "list"       -> handleList(sender, args);
            case "info"       -> handleInfo(sender, args, label);
            case "edit"       -> handleEdit(sender, args, label);
            case "movehere"   -> handleMovehere(sender, args, label);
            case "tpto"       -> handleTpto(sender, args, label);
            case "model"      -> handleModel(sender, args, label);
            case "anim"       -> handleAnim(sender, args, label);
            case "turn"       -> handleTurn(sender, args, label);
            case "lookat"     -> handleLookat(sender, args, label);
            case "holo"       -> handleHolo(sender, args, label);
            case "action"     -> handleAction(sender, args, label);
            case "visibility" -> handleVisibility(sender, args, label);
            case "proximity"  -> handleProximity(sender, args, label);
            case "spawn"      -> handleSpawn(sender, args, label);
            case "despawn"    -> handleDespawn(sender, args, label);
            case "stats"      -> handleStats(sender, args, label);
            case "dialog"     -> handleDialog(sender, args, label);
            case "waypoint"   -> handleWaypoint(sender, args, label);
            case "schedule"   -> handleSchedule(sender, args, label);
            case "clickreward" -> handleClickReward(sender, args, label);
            case "playerhide" -> handlePlayerHide(sender, args, label);
            case "playershow" -> handlePlayerShow(sender, args, label);
            case "export"     -> handleExport(sender, args, label);
            case "import"     -> handleImport(sender, args, label);
            case "permission" -> handlePermission(sender, args, label);
            case "select"     -> handleSelect(sender, args, label);
            case "menu"       -> handleMenu(sender, args, label);
            case "move"       -> handleMove(sender, args, label);
            case "routine"    -> handleRoutine(sender, args, label);
            case "backup"     -> handleBackup(sender);
            case "near"       -> handleNear(sender, args);
            case "search"     -> handleSearch(sender, args, label);
            case "undo"       -> handleUndo(sender);
            case "spawnall"   -> handleSpawnAll(sender);
            case "despawnall" -> handleDespawnAll(sender);
            default           -> sendHelp(sender, label);
        }
        return true;
    }

    private void handleCreate(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player p)) { sendMessage(sender, "only-players"); return; }
        if (args.length < 3) { usage(sender, "create", label); return; }

        String name = args[1];
        String modelId = args[2];

        if (!isValidNpcName(name)) {
            sendMessage(sender, "invalid-name");
            return;
        }

        if (plugin.getNpcManager().getNpcByName(name) != null) {
            sendMessage(sender, "npc-exists");
            return;
        }

        Location loc = p.getLocation().clone();
        WoolyNpc npc = new WoolyNpc(UUID.randomUUID(), name, loc, modelId);
        plugin.getNpcManager().addNpc(npc);
        plugin.getStorageManager().flushNow();
        sendMessage(sender, "npc-created", "name", name, "model", modelId);
    }

    private void handleRemove(CommandSender sender, String[] args, String label) {
        if (args.length < 2) { usage(sender, "remove", label); return; }
        WoolyNpc npc = requireNpc(sender, args[1]);
        if (npc == null) return;

        plugin.getRecycleBin().remember(npc);

        plugin.getNpcManager().removeNpc(npc.getId());
        plugin.getWalkingTask().resetState(npc.getId());
        plugin.getNpcListener().forgetNpc(npc.getId());
        plugin.getPlayerDataManager().forgetNpc(npc.getId());
        plugin.getSelectionManager().forgetNpc(npc.getId());
        plugin.getWalkingTask().cancelWalkTo(npc.getId());
        plugin.getStorageManager().flushNow();

        int undoMinutes = plugin.getConfigManager().getUndoMinutes();
        if (undoMinutes > 0) {
            sendMessage(sender, "npc-removed-undo", "name", npc.getName(),
                    "minutes", String.valueOf(undoMinutes));
        } else {
            sendMessage(sender, "npc-removed", "name", npc.getName());
        }
    }

    private void handleCopy(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player p)) { sendMessage(sender, "only-players"); return; }
        if (args.length < 3) { usage(sender, "copy", label); return; }

        WoolyNpc source = requireNpc(sender, args[1]);
        if (source == null) return;

        String newName = args[2];
        if (!isValidNpcName(newName)) {
            sendMessage(sender, "invalid-name");
            return;
        }
        if (plugin.getNpcManager().getNpcByName(newName) != null) {
            sendMessage(sender, "npc-exists");
            return;
        }

        Location loc = p.getLocation().clone();
        WoolyNpc copy = new WoolyNpc(UUID.randomUUID(), newName, loc, source.getModelId());
        copy.getHologramLines().addAll(source.getHologramLines());
        copy.setHoloOffsetY(source.getHoloOffsetY());
        copy.setHoloScale(source.getHoloScale());
        copy.setHoloShadow(source.isHoloShadow());
        copy.setHoloBackground(source.isHoloBackground());
        copy.setHoloBgAlpha(source.getHoloBgAlpha());
        copy.setHoloBillboard(source.getHoloBillboard());
        copy.setHoloAlignment(source.getHoloAlignment());
        copy.setHoloSeeThrough(source.isHoloSeeThrough());
        copy.setHoloLineWidth(source.getHoloLineWidth());
        copy.setHoloViewRange(source.getHoloViewRange());
        copy.setLookAtPlayer(source.isLookAtPlayer());
        copy.setLookDistance(source.getLookDistance());
        copy.setSmoothBody(source.isSmoothBody());
        copy.setEyeHeight(source.getEyeHeight());
        copy.setVisibilityRange(source.getVisibilityRange());
        copy.setProximityTriggerRange(source.getProximityTriggerRange());
        copy.setDefaultAnimation(source.getDefaultAnimation());

        copy.setDialogId(source.getDialogId());
        copy.setRequiredPermission(source.getRequiredPermission());
        copy.setClickRewardThreshold(source.getClickRewardThreshold());
        copy.setClickRewardAction(source.getClickRewardAction());
        copy.setSchedule(source.getSchedule());

        WaypointPath sourcePath = source.getWaypointPath();
        if (sourcePath != null) {
            WaypointPath copyPath = new WaypointPath(newName);
            copyPath.setLoop(sourcePath.isLoop());
            copyPath.setSpeed(sourcePath.getSpeed());
            for (Waypoint w : sourcePath.getWaypoints()) {
                copyPath.getWaypoints().add(
                        new Waypoint(w.getLocation().clone(), w.getWaitTicks(), w.getAnimation()));
            }
            copy.setWaypointPath(copyPath);
        }

        for (NpcAction a : source.getActions()) {
            copy.getActions().add(new NpcAction(a.getClickType(), a.getType(), a.rebuildRawValue()));
        }

        plugin.getNpcManager().addNpc(copy);
        plugin.getStorageManager().flushNow();
        sendMessage(sender, "npc-copied", "source", source.getName(), "name", newName);
    }

    private void handleRename(CommandSender sender, String[] args, String label) {
        if (args.length < 3) { usage(sender, "rename", label); return; }
        WoolyNpc npc = requireNpc(sender, args[1]);
        if (npc == null) return;

        String newName = args[2];
        if (!isValidNpcName(newName)) {
            sendMessage(sender, "invalid-name");
            return;
        }
        if (plugin.getNpcManager().getNpcByName(newName) != null) {
            sendMessage(sender, "npc-exists");
            return;
        }

        String oldName = npc.getName();
        npc.setName(newName);
        if (npc.getWaypointPath() != null) npc.getWaypointPath().setId(newName);
        plugin.getStorageManager().flushNow();
        sendMessage(sender, "npc-renamed", "old", oldName, "name", newName);
    }

    private void handleList(CommandSender sender, String[] args) {
        int page = 1;
        String worldFilter = null;

        if (args.length >= 2) {
            try {
                page = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException e) {
                worldFilter = args[1];
            }
        }
        if (args.length >= 3) worldFilter = args[2];

        List<WoolyNpc> npcs = new ArrayList<>(plugin.getNpcManager().getActiveNpcs());
        if (worldFilter != null) {
            String wanted = worldFilter;
            npcs.removeIf(n -> n.getLocation().getWorld() == null
                    || !n.getLocation().getWorld().getName().equalsIgnoreCase(wanted));
        }
        npcs.sort(Comparator.comparing(WoolyNpc::getName, String.CASE_INSENSITIVE_ORDER));

        if (npcs.isEmpty()) {
            sendMessage(sender, "list-header");
            sendMessage(sender, "list-empty");
            return;
        }

        printPage(sender, npcs, page, "list", worldFilter);
    }

    private void printPage(CommandSender sender, List<WoolyNpc> npcs, int page,
                           String command, String extraArg) {
        int perPage = Math.max(1, plugin.getConfigManager().getListPageSize());
        int pages = (npcs.size() + perPage - 1) / perPage;
        int current = Math.min(Math.max(1, page), pages);

        sendMessage(sender, "list-header-paged",
                "page", String.valueOf(current), "pages", String.valueOf(pages),
                "total", String.valueOf(npcs.size()));

        int from = (current - 1) * perPage;
        int to = Math.min(npcs.size(), from + perPage);

        for (int i = from; i < to; i++) {
            WoolyNpc npc = npcs.get(i);
            Location loc = npc.getLocation();
            String world = loc.getWorld() == null ? "?" : loc.getWorld().getName();

            String line = plugin.getConfigManager().getMessage("list-format-full",
                    "name", npc.getName(), "model", npc.getModelId(),
                    "spawned", plugin.getConfigManager().formatSpawned(npc.isSpawned()),
                    "world", world,
                    "x", String.format("%.0f", loc.getX()),
                    "y", String.format("%.0f", loc.getY()),
                    "z", String.format("%.0f", loc.getZ()));

            Component component = ColorUtil.format(line)
                    .clickEvent(ClickEvent.runCommand("/woolynpcs tpto " + npc.getName()))
                    .hoverEvent(HoverEvent.showText(ColorUtil.format(
                            plugin.getConfigManager().getMessage("list-hover-tp", "name", npc.getName()))));
            sender.sendMessage(component);
        }

        if (pages <= 1) return;

        String nextCommand = "/woolynpcs " + command + " " + (current + 1)
                + (extraArg == null ? "" : " " + extraArg);
        String prevCommand = "/woolynpcs " + command + " " + (current - 1)
                + (extraArg == null ? "" : " " + extraArg);

        Component nav = Component.empty();
        if (current > 1) {
            nav = nav.append(ColorUtil.format(plugin.getConfigManager().getMessage("list-prev"))
                    .clickEvent(ClickEvent.runCommand(prevCommand)));
        }
        if (current < pages) {
            nav = nav.append(ColorUtil.format(plugin.getConfigManager().getMessage("list-next"))
                    .clickEvent(ClickEvent.runCommand(nextCommand)));
        }
        sender.sendMessage(nav);
    }

    private void handleUndo(CommandSender sender) {
        if (plugin.getConfigManager().getUndoMinutes() <= 0) {
            sendMessage(sender, "undo-disabled");
            return;
        }

        String restored = plugin.getRecycleBin().restoreLast();
        if (restored == null) {
            sendMessage(sender, "undo-empty");
            return;
        }
        sendMessage(sender, "undo-done", "name", restored);
    }

    private void handleNear(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { sendMessage(sender, "only-players"); return; }

        double radius = 50.0;
        if (args.length >= 2) {
            double parsed = parseDouble(sender, args[1]);
            if (Double.isNaN(parsed)) return;
            radius = Math.max(1.0, parsed);
        }

        double radiusSq = radius * radius;
        List<WoolyNpc> found = new ArrayList<>();
        for (WoolyNpc npc : plugin.getNpcManager().getActiveNpcs()) {
            Location loc = plugin.getNpcManager().effectiveLocation(npc);
            if (loc.getWorld() == null || !loc.getWorld().equals(p.getWorld())) continue;
            if (loc.distanceSquared(p.getLocation()) <= radiusSq) found.add(npc);
        }

        if (found.isEmpty()) {
            sendMessage(sender, "near-empty", "radius", String.format("%.0f", radius));
            return;
        }

        Location origin = p.getLocation();
        found.sort(Comparator.comparingDouble(n ->
                plugin.getNpcManager().effectiveLocation(n).distanceSquared(origin)));
        printPage(sender, found, 1, "near", null);
    }

    private void handleSearch(CommandSender sender, String[] args, String label) {
        if (args.length < 2) { usage(sender, "search", label); return; }

        String query = args[1].toLowerCase(Locale.ROOT);
        int page = args.length >= 3 ? Math.max(1, parseIntOrDefault(args[2], 1)) : 1;

        List<WoolyNpc> found = new ArrayList<>();
        for (WoolyNpc npc : plugin.getNpcManager().getActiveNpcs()) {
            if (npc.getName().toLowerCase(Locale.ROOT).contains(query)
                    || npc.getModelId().toLowerCase(Locale.ROOT).contains(query)) {
                found.add(npc);
            }
        }

        if (found.isEmpty()) {
            sendMessage(sender, "search-empty", "query", args[1]);
            return;
        }
        found.sort(Comparator.comparing(WoolyNpc::getName, String.CASE_INSENSITIVE_ORDER));
        printPage(sender, found, page, "search", args[1]);
    }

    private int parseIntOrDefault(String value, int def) {
        try { return Integer.parseInt(value); } catch (NumberFormatException e) { return def; }
    }

    private void handleInfo(CommandSender sender, String[] args, String label) {
        if (args.length < 2) { usage(sender, "info", label); return; }
        WoolyNpc npc = requireNpc(sender, args[1]);
        if (npc == null) return;

        Location loc = npc.getLocation();
        String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "???";

        sendMessage(sender, "info-header", "name", npc.getName());
        sendMessage(sender, "info-model", "model", npc.getModelId());
        sendMessage(sender, "info-location", "world", worldName,
                "x", String.format("%.1f", loc.getX()),
                "y", String.format("%.1f", loc.getY()),
                "z", String.format("%.1f", loc.getZ()));
        sendMessage(sender, "info-rotation",
                "yaw", String.format("%.1f", npc.getDefaultYaw()),
                "pitch", String.format("%.1f", npc.getDefaultPitch()));
        sendMessage(sender, "info-anim", "anim", npc.getDefaultAnimation());

        String lookStatus = plugin.getConfigManager().formatBool(npc.isLookAtPlayer());
        sendMessage(sender, "info-lookat",
                "status", lookStatus,
                "distance", String.format("%.1f", npc.getLookDistance()),
                "eyeheight", String.format("%.2f", npc.getEyeHeight()));
        sendMessage(sender, "info-holo", "count", String.valueOf(npc.getHologramLines().size()));
        sendMessage(sender, "info-actions", "count", String.valueOf(npc.getActions().size()));

        String visMode = npc.getVisibilityRange() > 0
                ? plugin.getConfigManager().getMessage("info-vis-range")
                : plugin.getConfigManager().getMessage("info-vis-chunk");
        sendMessage(sender, "info-visibility",
                "range", String.format("%.1f", npc.getVisibilityRange()), "mode", visMode);
        sendMessage(sender, "info-spawned",
                "status", plugin.getConfigManager().formatSpawned(npc.isSpawned()));

        long totalClicks = npc.getTotalInteractions();
        sendMessage(sender, "info-stats", "clicks", String.valueOf(totalClicks));
    }

    private void handleEdit(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player p)) { sendMessage(sender, "only-players"); return; }
        if (args.length < 2) { usage(sender, "edit", label); return; }
        WoolyNpc npc = requireNpc(sender, args[1]);
        if (npc == null) return;
        GuiManager.openEditor(p, npc);
    }

    private void handleMovehere(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player p)) { sendMessage(sender, "only-players"); return; }
        if (args.length < 2) { usage(sender, "movehere", label); return; }
        WoolyNpc npc = requireNpc(sender, args[1]);
        if (npc == null) return;

        npc.teleport(p.getLocation().clone());
        plugin.getStorageManager().markDirty();
        sendMessage(sender, "npc-moved", "name", npc.getName());
    }

    private void handleTpto(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player p)) { sendMessage(sender, "only-players"); return; }
        if (args.length < 2) { usage(sender, "tpto", label); return; }
        WoolyNpc npc = requireNpc(sender, args[1]);
        if (npc == null) return;

        p.teleport(npc.getLocation());
        sendMessage(sender, "npc-teleported", "name", npc.getName());
    }

    private void handleModel(CommandSender sender, String[] args, String label) {
        if (args.length < 3) { usage(sender, "model", label); return; }
        WoolyNpc npc = requireNpc(sender, args[1]);
        if (npc == null) return;

        npc.setModelId(args[2]);
        plugin.getStorageManager().markDirty();
        sendMessage(sender, "npc-model-changed", "name", npc.getName(), "model", args[2]);
    }

    private void handleAnim(CommandSender sender, String[] args, String label) {
        if (args.length < 3) { usage(sender, "anim", label); return; }
        WoolyNpc npc = requireNpc(sender, args[1]);
        if (npc == null) return;

        String anim = args[2];
        npc.setDefaultAnimation(anim);
        plugin.getStorageManager().markDirty();
        sendMessage(sender, "npc-anim-started", "name", npc.getName(), "anim", anim);
    }

    private void handleTurn(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player p)) { sendMessage(sender, "only-players"); return; }
        if (args.length < 2) { usage(sender, "turn", label); return; }
        WoolyNpc npc = requireNpc(sender, args[1]);
        if (npc == null) return;

        Location playerLoc = p.getLocation();
        Location npcLoc = npc.getLocation();

        double dx = playerLoc.getX() - npcLoc.getX();
        double dz = playerLoc.getZ() - npcLoc.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = 0;

        npc.updateDefaultRotation(yaw, pitch);
        npc.rotateSmoothly(yaw, pitch);
        plugin.getStorageManager().markDirty();
        sendMessage(sender, "npc-turned", "name", npc.getName());
    }

    private void handleLookat(CommandSender sender, String[] args, String label) {
        if (args.length < 3) { usage(sender, "lookat", label); return; }
        WoolyNpc npc = requireNpc(sender, args[1]);
        if (npc == null) return;

        String param = args[2].toLowerCase(Locale.ROOT);

        switch (param) {
            case "enable" -> {
                if (args.length < 4) { usage(sender, "lookat-enable", label); return; }
                boolean val = Boolean.parseBoolean(args[3]);
                npc.setLookAtPlayer(val);
                sendMessage(sender, "npc-lookat-enabled", "name", npc.getName(),
                        "status", plugin.getConfigManager().formatBool(val));
            }
            case "distance" -> {
                if (args.length < 4) { usage(sender, "lookat-distance", label); return; }
                double val = parseDouble(sender, args[3]);
                if (Double.isNaN(val)) return;
                npc.setLookDistance(val);
                sendMessage(sender, "npc-lookat-distance", "name", npc.getName(),
                        "value", String.format("%.1f", val));
            }
            case "smooth" -> {
                if (args.length < 4) { usage(sender, "lookat-smooth", label); return; }
                boolean val = Boolean.parseBoolean(args[3]);
                npc.setSmoothBody(val);
                sendMessage(sender, "npc-lookat-smooth", "name", npc.getName(),
                        "value", plugin.getConfigManager().formatBool(val));
            }
            case "eyeheight" -> {
                if (args.length < 4) { usage(sender, "lookat-eyeheight", label); return; }
                double val = parseDouble(sender, args[3]);
                if (Double.isNaN(val)) return;
                npc.setEyeHeight(val);
                sendMessage(sender, "npc-eyeheight-changed", "name", npc.getName(),
                        "value", String.format("%.2f", val));
            }
            default -> usage(sender, "lookat", label);
        }
        plugin.getStorageManager().markDirty();
    }

    private void handleHolo(CommandSender sender, String[] args, String label) {
        if (args.length < 3) { usage(sender, "holo", label); return; }
        WoolyNpc npc = requireNpc(sender, args[1]);
        if (npc == null) return;

        String param = args[2].toLowerCase(Locale.ROOT);

        switch (param) {
            case "line" -> handleHoloLine(sender, npc, args, label);
            case "offset" -> {
                if (args.length < 4) { usage(sender, "holo-offset", label); return; }
                double val = parseDouble(sender, args[3]);
                if (Double.isNaN(val)) return;
                npc.setHoloOffsetY(val);
                npc.updateHologram();
                sendMessage(sender, "hologram-updated", "name", npc.getName());
            }
            case "scale" -> {
                if (args.length < 4) { usage(sender, "holo-scale", label); return; }
                double val = parseDouble(sender, args[3]);
                if (Double.isNaN(val)) return;
                npc.setHoloScale((float) val);
                npc.updateHologram();
                sendMessage(sender, "hologram-updated", "name", npc.getName());
            }
            case "shadow" -> {
                if (args.length < 4) { usage(sender, "holo-shadow", label); return; }
                npc.setHoloShadow(Boolean.parseBoolean(args[3]));
                npc.updateHologram();
                sendMessage(sender, "hologram-updated", "name", npc.getName());
            }
            case "background" -> {
                if (args.length < 4) { usage(sender, "holo-background", label); return; }
                npc.setHoloBackground(Boolean.parseBoolean(args[3]));
                npc.updateHologram();
                sendMessage(sender, "hologram-updated", "name", npc.getName());
            }
            case "bg-alpha" -> {
                if (args.length < 4) { usage(sender, "holo-bg-alpha", label); return; }
                int val = parseInt(sender, args[3]);
                if (val == Integer.MIN_VALUE) return;
                npc.setHoloBgAlpha(val);
                npc.updateHologram();
                sendMessage(sender, "hologram-updated", "name", npc.getName());
            }
            case "billboard" -> {
                if (args.length < 4) { usage(sender, "holo-billboard", label); return; }
                try {
                    Display.Billboard bb = Display.Billboard.valueOf(args[3].toUpperCase(Locale.ROOT));
                    npc.setHoloBillboard(bb);
                    npc.updateHologram();
                    sendMessage(sender, "hologram-updated", "name", npc.getName());
                } catch (IllegalArgumentException e) {
                    String valid = Arrays.stream(Display.Billboard.values())
                            .map(Enum::name).collect(Collectors.joining(", "));
                    sendMessage(sender, "invalid-enum", "values", valid);
                }
            }
            case "align" -> {
                if (args.length < 4) { usage(sender, "holo-align", label); return; }
                try {
                    TextDisplay.TextAlignment al = TextDisplay.TextAlignment.valueOf(args[3].toUpperCase(Locale.ROOT));
                    npc.setHoloAlignment(al);
                    npc.updateHologram();
                    sendMessage(sender, "hologram-updated", "name", npc.getName());
                } catch (IllegalArgumentException e) {
                    String valid = Arrays.stream(TextDisplay.TextAlignment.values())
                            .map(Enum::name).collect(Collectors.joining(", "));
                    sendMessage(sender, "invalid-enum", "values", valid);
                }
            }
            case "see-through" -> {
                if (args.length < 4) { usage(sender, "holo-see-through", label); return; }
                npc.setHoloSeeThrough(Boolean.parseBoolean(args[3]));
                npc.updateHologram();
                sendMessage(sender, "hologram-updated", "name", npc.getName());
            }
            case "line-width" -> {
                if (args.length < 4) { usage(sender, "holo-line-width", label); return; }
                int val = parseInt(sender, args[3]);
                if (val == Integer.MIN_VALUE) return;
                npc.setHoloLineWidth(val);
                npc.updateHologram();
                sendMessage(sender, "hologram-updated", "name", npc.getName());
            }
            case "view-range" -> {
                if (args.length < 4) { usage(sender, "holo-view-range", label); return; }
                double val = parseDouble(sender, args[3]);
                if (Double.isNaN(val)) return;
                npc.setHoloViewRange((float) val);
                npc.updateHologram();
                sendMessage(sender, "hologram-updated", "name", npc.getName());
            }
            default -> usage(sender, "holo", label);
        }
        plugin.getStorageManager().markDirty();
    }

    private void handleHoloLine(CommandSender sender, WoolyNpc npc, String[] args, String label) {
        if (args.length < 4) { usage(sender, "holo-line", label); return; }
        String sub = args[3].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "list" -> {
                sendMessage(sender, "holo-editor-header", "name", npc.getName());
                List<String> lines = npc.getHologramLines();
                if (lines.isEmpty()) {
                    sendMessage(sender, "holo-editor-empty");
                } else {
                    for (int i = 0; i < lines.size(); i++) {
                        String lineText = lines.get(i);
                        String format = plugin.getConfigManager().getMessage("holo-editor-format",
                                "index", String.valueOf(i), "text", lineText);
                        String btnEdit = plugin.getConfigManager().getMessage("holo-editor-btn-edit");
                        String hoverEdit = plugin.getConfigManager().getMessage("holo-editor-hover-edit");
                        String btnRemove = plugin.getConfigManager().getMessage("holo-editor-btn-remove");
                        String hoverRemove = plugin.getConfigManager().getMessage("holo-editor-hover-remove");

                        Component msg = ColorUtil.format(format)
                                .append(ColorUtil.format(btnEdit)
                                        .clickEvent(ClickEvent.suggestCommand("/" + label + " holo " + npc.getName() + " line set " + i + " " + lineText))
                                        .hoverEvent(HoverEvent.showText(ColorUtil.format(hoverEdit))))
                                .append(ColorUtil.format(btnRemove)
                                        .clickEvent(ClickEvent.runCommand("/" + label + " holo " + npc.getName() + " line remove " + i))
                                        .hoverEvent(HoverEvent.showText(ColorUtil.format(hoverRemove))));
                        if (sender instanceof Player p) p.sendMessage(msg);
                        else sender.sendMessage(msg);
                    }
                }
                String btnAdd = plugin.getConfigManager().getMessage("holo-editor-btn-add");
                String hoverAdd = plugin.getConfigManager().getMessage("holo-editor-hover-add");
                Component addMsg = ColorUtil.format(btnAdd)
                        .clickEvent(ClickEvent.suggestCommand("/" + label + " holo " + npc.getName() + " line add "))
                        .hoverEvent(HoverEvent.showText(ColorUtil.format(hoverAdd)));
                if (sender instanceof Player p) p.sendMessage(addMsg);
                else sender.sendMessage(addMsg);
                sender.sendMessage(ColorUtil.format(""));
            }
            case "add" -> {
                if (args.length < 5) { usage(sender, "holo-line-add", label); return; }
                String text = joinArgs(args, 4);
                npc.getHologramLines().add(text);
                npc.updateHologram();
                sendMessage(sender, "hologram-updated", "name", npc.getName());
            }
            case "set" -> {
                if (args.length < 6) { usage(sender, "holo-line-set", label); return; }
                int idx = parseInt(sender, args[4]);
                if (idx == Integer.MIN_VALUE) return;
                if (idx < 0 || idx >= npc.getHologramLines().size()) {
                    sendMessage(sender, "index-out-of-range",
                            "index", String.valueOf(idx),
                            "max", String.valueOf(npc.getHologramLines().size() - 1));
                    return;
                }
                String text = joinArgs(args, 5);
                npc.getHologramLines().set(idx, text);
                npc.updateHologram();
                sendMessage(sender, "hologram-updated", "name", npc.getName());
            }
            case "remove" -> {
                if (args.length < 5) { usage(sender, "holo-line-remove", label); return; }
                int idx = parseInt(sender, args[4]);
                if (idx == Integer.MIN_VALUE) return;
                if (idx < 0 || idx >= npc.getHologramLines().size()) {
                    sendMessage(sender, "index-out-of-range",
                            "index", String.valueOf(idx),
                            "max", String.valueOf(npc.getHologramLines().size() - 1));
                    return;
                }
                npc.getHologramLines().remove(idx);
                npc.updateHologram();
                sendMessage(sender, "hologram-updated", "name", npc.getName());
            }
            case "clear" -> {
                npc.getHologramLines().clear();
                npc.updateHologram();
                sendMessage(sender, "hologram-updated", "name", npc.getName());
            }
            default -> usage(sender, "holo-line", label);
        }
    }

    private void handleAction(CommandSender sender, String[] args, String label) {
        if (args.length < 3) { usage(sender, "action", label); return; }
        WoolyNpc npc = requireNpc(sender, args[1]);
        if (npc == null) return;

        String sub = args[2].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "list" -> {
                if (npc.getActions().isEmpty()) {
                    sendMessage(sender, "action-list-empty", "name", npc.getName());
                    return;
                }
                sendMessage(sender, "action-list-header", "name", npc.getName());
                for (int i = 0; i < npc.getActions().size(); i++) {
                    NpcAction a = npc.getActions().get(i);
                    String flags = buildFlagString(a);
                    String msg = plugin.getConfigManager().getMessage("action-list-format",
                            "index", String.valueOf(i),
                            "click", a.getClickType().name(),
                            "type", a.getTypeName(),
                            "flags", flags,
                            "value", a.getValue());
                    sender.sendMessage(ColorUtil.format(msg));
                }
            }
            case "add" -> {
                if (args.length < 6) {
                    usage(sender, "action-add", label);
                    sendMessage(sender, "action-add-hint-clicks");
                    sendMessage(sender, "action-add-hint-types");
                    sendMessage(sender, "action-add-hint-flags");
                    return;
                }
                ClickType clickType;
                try {
                    clickType = ClickType.valueOf(args[3].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    sendMessage(sender, "action-add-hint-clicks");
                    return;
                }

                String typeName = args[4].toUpperCase(Locale.ROOT);
                String value = joinArgs(args, 5);

                NpcAction action;
                try {
                    action = new NpcAction(clickType, ActionType.valueOf(typeName), value);
                } catch (IllegalArgumentException e) {
                    if (!WoolyNpcsApi.isRegistered(typeName)) {
                        sendMessage(sender, "action-add-hint-types");
                        return;
                    }
                    action = NpcAction.custom(UUID.randomUUID(), clickType, typeName, value);
                }
                npc.getActions().add(action);
                plugin.getStorageManager().markDirty();
                sendMessage(sender, "action-added", "name", npc.getName());
            }
            case "remove" -> {
                if (args.length < 4) { usage(sender, "action-remove", label); return; }
                int idx = parseInt(sender, args[3]);
                if (idx == Integer.MIN_VALUE) return;
                if (idx < 0 || idx >= npc.getActions().size()) {
                    sendMessage(sender, "index-out-of-range",
                            "index", String.valueOf(idx),
                            "max", String.valueOf(npc.getActions().size() - 1));
                    return;
                }
                npc.getActions().remove(idx);
                plugin.getStorageManager().markDirty();
                sendMessage(sender, "action-removed", "name", npc.getName(), "index", String.valueOf(idx));
            }
            case "clear" -> {
                npc.getActions().clear();
                plugin.getStorageManager().markDirty();
                sendMessage(sender, "action-cleared", "name", npc.getName());
            }
            default -> usage(sender, "action", label);
        }
    }

    private void handleVisibility(CommandSender sender, String[] args, String label) {
        if (args.length < 3) { usage(sender, "visibility", label); return; }
        WoolyNpc npc = requireNpc(sender, args[1]);
        if (npc == null) return;

        double val = parseDouble(sender, args[2]);
        if (Double.isNaN(val)) return;
        npc.setVisibilityRange(val);

        plugin.getNpcManager().getArbiter().apply(npc);

        plugin.getStorageManager().markDirty();
        sendMessage(sender, "npc-visibility-changed", "name", npc.getName(),
                "value", String.format("%.1f", val));
    }

    private void handleProximity(CommandSender sender, String[] args, String label) {
        if (args.length < 3) { usage(sender, "proximity", label); return; }
        WoolyNpc npc = requireNpc(sender, args[1]);
        if (npc == null) return;

        double val = parseDouble(sender, args[2]);
        if (Double.isNaN(val)) return;
        npc.setProximityTriggerRange(val);
        plugin.getStorageManager().markDirty();
        sendMessage(sender, "npc-proximity-changed", "name", npc.getName(),
                "value", String.format("%.1f", val));
    }

    private void handleSpawn(CommandSender sender, String[] args, String label) {
        if (args.length < 2) { usage(sender, "spawn", label); return; }
        WoolyNpc npc = requireNpc(sender, args[1]);
        if (npc == null) return;
        if (npc.isSpawned()) {
            sendMessage(sender, "npc-already-spawned", "name", npc.getName());
            return;
        }

        npc.setManualDespawn(false);
        plugin.getNpcManager().getArbiter().apply(npc);
        plugin.getStorageManager().markDirty();

        if (!npc.isSpawned()) {
            String reason = plugin.getNpcManager().getArbiter().blockReason(npc);
            sendMessage(sender, "npc-spawn-blocked", "name", npc.getName(),
                    "reason", plugin.getConfigManager().getMessage(
                            "spawn-block-" + (reason == null ? "unknown" : reason)));
            return;
        }
        sendMessage(sender, "npc-spawned", "name", npc.getName());
    }

    private void handleDespawn(CommandSender sender, String[] args, String label) {
        if (args.length < 2) { usage(sender, "despawn", label); return; }
        WoolyNpc npc = requireNpc(sender, args[1]);
        if (npc == null) return;
        if (!npc.isSpawned()) {
            sendMessage(sender, "npc-already-despawned", "name", npc.getName());
            return;
        }
        npc.setManualDespawn(true);
        npc.despawn(true);
        plugin.getStorageManager().markDirty();
        sendMessage(sender, "npc-despawned", "name", npc.getName());
    }

    private void handleStats(CommandSender sender, String[] args, String label) {
        if (args.length < 2) { usage(sender, "stats", label); return; }
        WoolyNpc npc = requireNpc(sender, args[1]);
        if (npc == null) return;

        boolean reset = args.length >= 3 && args[2].equalsIgnoreCase("reset");

        sendMessage(sender, "stats-header", "name", npc.getName());
        sendMessage(sender, "stats-total", "clicks", String.valueOf(npc.getTotalInteractions()));

        if (reset) {
            npc.resetInteractions();
            plugin.getStorageManager().markDirty();
            sendMessage(sender, "stats-reset", "name", npc.getName());
        }
    }

    private void handleDialog(CommandSender sender, String[] args, String label) {
        if (args.length < 3) { usage(sender, "dialog", label); return; }
        WoolyNpc npc = requireNpc(sender, args[1]);
        if (npc == null) return;

        String action = args[2].toLowerCase(Locale.ROOT);
        switch (action) {
            case "set" -> {
                if (args.length < 4) { usage(sender, "dialog", label); return; }
                String dialogId = args[3];
                if (plugin.getDialogManager().getDialog(dialogId) == null) {
                    sendMessage(sender, "dialog-not-found", "id", dialogId);
                    return;
                }
                npc.setDialogId(dialogId);
                plugin.getStorageManager().markDirty();
                sendMessage(sender, "dialog-set", "name", npc.getName(), "id", dialogId);
            }
            case "remove" -> {
                npc.setDialogId(null);
                plugin.getStorageManager().markDirty();
                sendMessage(sender, "dialog-removed", "name", npc.getName());
            }
            case "list" -> {
                var dialogs = plugin.getDialogManager().getDialogs();
                if (dialogs.isEmpty()) {
                    sendMessage(sender, "dialog-list-empty");
                    return;
                }
                sendMessage(sender, "dialog-list-header");
                for (var entry : dialogs.entrySet()) {
                    sendMessage(sender, "dialog-list-item", "id", entry.getKey(),
                            "nodes", String.valueOf(entry.getValue().getNodes().size()));
                }
            }
            case "reload" -> {
                plugin.getDialogManager().loadDialogs();
                sendMessage(sender, "dialog-reloaded");
            }
            default -> usage(sender, "dialog", label);
        }
    }

    private void handleDialogChoice(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) return;
        if (args.length < 2) return;
        int idx = parseInt(sender, args[1]);
        if (idx == Integer.MIN_VALUE) return;
        plugin.getDialogManager().handleChoice(p, idx);
    }

    private void handleWaypoint(CommandSender sender, String[] args, String label) {
        if (args.length < 3) { usage(sender, "waypoint", label); return; }
        WoolyNpc npc = requireNpc(sender, args[1]);
        if (npc == null) return;

        String action = args[2].toLowerCase(Locale.ROOT);
        switch (action) {
            case "add" -> {
                if (!(sender instanceof Player p)) { sendMessage(sender, "only-players"); return; }
                WaypointPath path = npc.getWaypointPath();
                if (path == null) {
                    path = new WaypointPath(npc.getName());
                    npc.setWaypointPath(path);
                }
                int waitTicks = args.length >= 4 ? parseInt(sender, args[3]) : 20;
                if (waitTicks == Integer.MIN_VALUE) waitTicks = 20;
                String anim = args.length >= 5 ? args[4] : "";
                path.getWaypoints().add(new Waypoint(p.getLocation().clone(), waitTicks, anim));
                plugin.getStorageManager().markDirty();
                sendMessage(sender, "waypoint-added", "name", npc.getName(),
                        "index", String.valueOf(path.getWaypoints().size()));
            }
            case "remove" -> {
                WaypointPath path = npc.getWaypointPath();
                if (path == null || path.getWaypoints().isEmpty()) {
                    sendMessage(sender, "waypoint-empty", "name", npc.getName());
                    return;
                }
                if (args.length < 4) { usage(sender, "waypoint", label); return; }
                int idx = parseInt(sender, args[3]);
                if (idx == Integer.MIN_VALUE) return;
                if (idx < 1 || idx > path.getWaypoints().size()) {
                    sendMessage(sender, "index-out-of-range",
                            "index", String.valueOf(idx),
                            "max", String.valueOf(path.getWaypoints().size()));
                    return;
                }
                path.getWaypoints().remove(idx - 1);
                if (path.getWaypoints().isEmpty()) npc.setWaypointPath(null);
                plugin.getWalkingTask().resetState(npc.getId());
                plugin.getStorageManager().markDirty();
                sendMessage(sender, "waypoint-removed", "name", npc.getName(), "index", String.valueOf(idx));
            }
            case "clear" -> {
                npc.setWaypointPath(null);
                plugin.getWalkingTask().resetState(npc.getId());
                npc.restoreDefaultAnimation();
                plugin.getStorageManager().markDirty();
                sendMessage(sender, "waypoint-cleared", "name", npc.getName());
            }
            case "list" -> {
                WaypointPath path = npc.getWaypointPath();
                if (path == null || path.getWaypoints().isEmpty()) {
                    sendMessage(sender, "waypoint-empty", "name", npc.getName());
                    return;
                }
                sendMessage(sender, "waypoint-list-header", "name", npc.getName());
                for (int i = 0; i < path.getWaypoints().size(); i++) {
                    Waypoint wp = path.getWaypoints().get(i);
                    Location l = wp.getLocation();
                    sendMessage(sender, "waypoint-list-item", "index", String.valueOf(i + 1),
                            "x", String.format("%.1f", l.getX()),
                            "y", String.format("%.1f", l.getY()),
                            "z", String.format("%.1f", l.getZ()),
                            "wait", String.valueOf(wp.getWaitTicks()));
                }
            }
            case "speed" -> {
                if (args.length < 4) { usage(sender, "waypoint", label); return; }
                WaypointPath path = npc.getWaypointPath();
                if (path == null) {
                    path = new WaypointPath(npc.getName());
                    npc.setWaypointPath(path);
                }
                double speed = parseDouble(sender, args[3]);
                if (Double.isNaN(speed)) return;
                path.setSpeed(speed);
                plugin.getStorageManager().markDirty();
                sendMessage(sender, "waypoint-speed-set", "name", npc.getName(),
                        "speed", String.format("%.2f", path.getSpeed()));
            }
            case "gravity" -> {
                WaypointPath path = npc.getWaypointPath();
                if (path == null) {
                    path = new WaypointPath(npc.getName());
                    npc.setWaypointPath(path);
                }
                boolean gravity = args.length >= 4 ? Boolean.parseBoolean(args[3]) : !path.isGravity();
                path.setGravity(gravity);
                plugin.getStorageManager().markDirty();
                sendMessage(sender, "waypoint-gravity-set", "name", npc.getName(),
                        "value", plugin.getConfigManager().formatBool(gravity));
            }
            case "show" -> {
                if (!(sender instanceof Player p)) { sendMessage(sender, "only-players"); return; }
                WaypointPath path = npc.getWaypointPath();
                if (path == null || path.getWaypoints().isEmpty()) {
                    sendMessage(sender, "waypoint-empty", "name", npc.getName());
                    return;
                }
                plugin.getWaypointVisualizer().toggle(p, npc);
            }
            case "loop" -> {
                WaypointPath path = npc.getWaypointPath();
                if (path == null) {
                    path = new WaypointPath(npc.getName());
                    npc.setWaypointPath(path);
                }
                boolean loop = args.length >= 4 ? Boolean.parseBoolean(args[3]) : !path.isLoop();
                path.setLoop(loop);
                plugin.getStorageManager().markDirty();
                sendMessage(sender, "waypoint-loop-set", "name", npc.getName(),
                        "value", String.valueOf(loop));
            }
            default -> usage(sender, "waypoint", label);
        }
    }

    private void handleSchedule(CommandSender sender, String[] args, String label) {
        if (args.length < 3) { usage(sender, "schedule", label); return; }
        WoolyNpc npc = requireNpc(sender, args[1]);
        if (npc == null) return;

        String action = args[2].toLowerCase(Locale.ROOT);
        switch (action) {
            case "set" -> {
                if (args.length < 5) { usage(sender, "schedule", label); return; }
                int spawnTime   = parseInt(sender, args[3]);
                int despawnTime = parseInt(sender, args[4]);
                if (spawnTime == Integer.MIN_VALUE || despawnTime == Integer.MIN_VALUE) return;
                if (spawnTime < 0 || spawnTime >= 24000 || despawnTime < 0 || despawnTime >= 24000) {
                    sendMessage(sender, "schedule-invalid-time");
                    return;
                }
                npc.setSchedule(new NpcSchedule(spawnTime, despawnTime));
                plugin.getNpcManager().getArbiter().apply(npc);
                plugin.getStorageManager().markDirty();
                sendMessage(sender, "schedule-set", "name", npc.getName(),
                        "spawn", String.valueOf(spawnTime), "despawn", String.valueOf(despawnTime));
            }
            case "remove" -> {
                npc.setSchedule(null);
                plugin.getNpcManager().getArbiter().apply(npc);
                plugin.getStorageManager().markDirty();
                sendMessage(sender, "schedule-removed", "name", npc.getName());
            }
            case "info" -> {
                NpcSchedule sch = npc.getSchedule();
                if (sch == null) {
                    sendMessage(sender, "schedule-none", "name", npc.getName());
                } else {
                    sendMessage(sender, "schedule-info", "name", npc.getName(),
                            "spawn", String.valueOf(sch.getSpawnTimeTicks()),
                            "despawn", String.valueOf(sch.getDespawnTimeTicks()));
                }
            }
            default -> usage(sender, "schedule", label);
        }
    }

    private void handleClickReward(CommandSender sender, String[] args, String label) {
        if (args.length < 3) { usage(sender, "clickreward", label); return; }
        WoolyNpc npc = requireNpc(sender, args[1]);
        if (npc == null) return;

        String action = args[2].toLowerCase(Locale.ROOT);
        switch (action) {
            case "set" -> {
                if (args.length < 5) { usage(sender, "clickreward", label); return; }
                int threshold = parseInt(sender, args[3]);
                if (threshold == Integer.MIN_VALUE) return;
                if (threshold <= 0) { sendMessage(sender, "clickreward-invalid-threshold"); return; }
                String rewardAction = joinArgs(args, 4);
                npc.setClickRewardThreshold(threshold);
                npc.setClickRewardAction(rewardAction);
                plugin.getStorageManager().markDirty();
                sendMessage(sender, "clickreward-set", "name", npc.getName(),
                        "threshold", String.valueOf(threshold), "action", rewardAction);
            }
            case "remove" -> {
                npc.setClickRewardThreshold(0);
                npc.setClickRewardAction(null);
                plugin.getStorageManager().markDirty();
                sendMessage(sender, "clickreward-removed", "name", npc.getName());
            }
            case "info" -> {
                if (npc.getClickRewardThreshold() <= 0) {
                    sendMessage(sender, "clickreward-none", "name", npc.getName());
                } else {
                    sendMessage(sender, "clickreward-info", "name", npc.getName(),
                            "threshold", String.valueOf(npc.getClickRewardThreshold()),
                            "action", npc.getClickRewardAction());
                }
            }
            default -> usage(sender, "clickreward", label);
        }
    }

    private void handlePlayerHide(CommandSender sender, String[] args, String label) {
        if (args.length < 3) { usage(sender, "playerhide", label); return; }
        WoolyNpc npc = requireNpc(sender, args[1]);
        if (npc == null) return;

        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sendMessage(sender, "player-not-found", "name", args[2]);
            return;
        }
        plugin.getPlayerVisibilityManager().hideNpcForPlayer(target, npc);
        sendMessage(sender, "playerhide-done", "npc", npc.getName(), "player", target.getName());
    }

    private void handlePlayerShow(CommandSender sender, String[] args, String label) {
        if (args.length < 3) { usage(sender, "playershow", label); return; }
        WoolyNpc npc = requireNpc(sender, args[1]);
        if (npc == null) return;

        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sendMessage(sender, "player-not-found", "name", args[2]);
            return;
        }
        plugin.getPlayerVisibilityManager().showNpcForPlayer(target, npc);
        sendMessage(sender, "playershow-done", "npc", npc.getName(), "player", target.getName());
    }

    private void handleExport(CommandSender sender, String[] args, String label) {
        if (args.length < 2) { usage(sender, "export", label); return; }
        WoolyNpc npc = requireNpc(sender, args[1]);
        if (npc == null) return;

        if (!isSafeFileName(npc.getName())) {
            sendMessage(sender, "invalid-name");
            return;
        }

        File exportDir = new File(plugin.getDataFolder(), "exports");
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            sendMessage(sender, "export-failed", "error", "не удалось создать папку exports/");
            return;
        }
        File file = new File(exportDir, npc.getName() + ".yml");

        YamlConfiguration config = new YamlConfiguration();
        config.set("model-id", npc.getModelId());
        config.set("active-animation", npc.getDefaultAnimation());
        config.set("look-at-player", npc.isLookAtPlayer());
        config.set("look-distance", npc.getLookDistance());
        config.set("smooth-body", npc.isSmoothBody());
        config.set("eye-height", npc.getEyeHeight());
        config.set("visibility-range", npc.getVisibilityRange());
        config.set("proximity-trigger-range", npc.getProximityTriggerRange());
        config.set("hologram.lines", npc.getHologramLines());
        config.set("hologram.offset-y", npc.getHoloOffsetY());
        config.set("hologram.scale", npc.getHoloScale());
        config.set("hologram.shadow", npc.isHoloShadow());
        config.set("hologram.background", npc.isHoloBackground());
        config.set("hologram.bg-alpha", npc.getHoloBgAlpha());
        config.set("hologram.billboard", npc.getHoloBillboard().name());
        config.set("hologram.alignment", npc.getHoloAlignment().name());
        config.set("hologram.see-through", npc.isHoloSeeThrough());
        config.set("hologram.line-width", npc.getHoloLineWidth());
        config.set("hologram.view-range", npc.getHoloViewRange());

        List<String> actionStrs = new ArrayList<>();
        for (NpcAction a : npc.getActions()) {
            actionStrs.add(a.getSaveString());
        }
        config.set("actions", actionStrs);

        if (npc.getDialogId() != null) config.set("dialog-id", npc.getDialogId());
        if (npc.getRequiredPermission() != null) config.set("required-permission", npc.getRequiredPermission());
        if (npc.getClickRewardThreshold() > 0) {
            config.set("click-reward.threshold", npc.getClickRewardThreshold());
            config.set("click-reward.action", npc.getClickRewardAction());
        }

        if (npc.getSchedule() != null) {
            config.set("schedule.spawn-time", npc.getSchedule().getSpawnTimeTicks());
            config.set("schedule.despawn-time", npc.getSchedule().getDespawnTimeTicks());
        }
        if (npc.getWaypointPath() != null) {
            config.set("waypoint.loop", npc.getWaypointPath().isLoop());
            config.set("waypoint.speed", npc.getWaypointPath().getSpeed());
            config.set("waypoint.points", StorageManager.serializeWaypoints(npc.getWaypointPath()));
        }

        try {
            config.save(file);
            sendMessage(sender, "export-success", "name", npc.getName(), "file", file.getName());
        } catch (IOException e) {
            sendMessage(sender, "export-failed", "error", e.getMessage());
        }
    }

    private void handleImport(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player p)) { sendMessage(sender, "only-players"); return; }
        if (args.length < 3) { usage(sender, "import", label); return; }

        String npcName = args[1];
        String fileName = args[2];

        if (!isValidNpcName(npcName) || !isSafeFileName(fileName)) {
            sendMessage(sender, "invalid-name");
            return;
        }

        if (plugin.getNpcManager().getNpcByName(npcName) != null) {
            sendMessage(sender, "npc-exists");
            return;
        }

        File file = new File(new File(plugin.getDataFolder(), "exports"), fileName + ".yml");
        if (!file.exists()) {
            sendMessage(sender, "import-file-not-found", "file", fileName);
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String modelId = config.getString("model-id", "");
        if (modelId.isEmpty()) {
            sendMessage(sender, "import-no-model");
            return;
        }

        WoolyNpc npc = new WoolyNpc(UUID.randomUUID(), npcName, p.getLocation().clone(), modelId);
        npc.setLookAtPlayer(config.getBoolean("look-at-player", false));
        npc.setLookDistance(config.getDouble("look-distance", 7.0));
        npc.setSmoothBody(config.getBoolean("smooth-body", true));
        npc.setEyeHeight(config.getDouble("eye-height", 1.62));
        npc.setVisibilityRange(config.getDouble("visibility-range", 0.0));
        npc.setProximityTriggerRange(config.getDouble("proximity-trigger-range", 5.0));

        npc.getHologramLines().addAll(config.getStringList("hologram.lines"));
        npc.setHoloOffsetY(config.getDouble("hologram.offset-y", 2.3));
        npc.setHoloScale((float) config.getDouble("hologram.scale", 1.0));
        npc.setHoloShadow(config.getBoolean("hologram.shadow", false));
        npc.setHoloBackground(config.getBoolean("hologram.background", true));
        npc.setHoloBgAlpha(config.getInt("hologram.bg-alpha", 64));

        try { npc.setHoloBillboard(Display.Billboard.valueOf(config.getString("hologram.billboard", "CENTER"))); } catch (Exception ignored) {}
        try { npc.setHoloAlignment(TextDisplay.TextAlignment.valueOf(config.getString("hologram.alignment", "CENTER"))); } catch (Exception ignored) {}

        npc.setHoloSeeThrough(config.getBoolean("hologram.see-through", false));
        npc.setHoloLineWidth(config.getInt("hologram.line-width", 200));
        npc.setHoloViewRange((float) config.getDouble("hologram.view-range", 64.0));

        for (String actionStr : config.getStringList("actions")) {
            NpcAction action = StorageManager.parseAction(actionStr);
            if (action != null && !action.isEmpty()) npc.getActions().add(action);
        }

        if (config.contains("dialog-id")) npc.setDialogId(config.getString("dialog-id"));
        if (config.contains("required-permission")) npc.setRequiredPermission(config.getString("required-permission"));
        if (config.contains("click-reward.threshold")) {
            npc.setClickRewardThreshold(config.getLong("click-reward.threshold"));
            npc.setClickRewardAction(config.getString("click-reward.action"));
        }

        if (config.contains("schedule.spawn-time")) {
            npc.setSchedule(new NpcSchedule(
                    config.getLong("schedule.spawn-time"),
                    config.getLong("schedule.despawn-time")));
        }

        if (config.contains("waypoint.points")) {
            WaypointPath path = new WaypointPath(npcName);
            path.setLoop (config.getBoolean("waypoint.loop",  true));
            path.setSpeed(config.getDouble ("waypoint.speed", 0.15));
            StorageManager.deserializeWaypoints(config.getStringList("waypoint.points"), path);
            if (!path.getWaypoints().isEmpty()) npc.setWaypointPath(path);
        }

        npc.setDefaultAnimation(config.getString("active-animation", "idle"));

        plugin.getNpcManager().addNpc(npc);
        plugin.getStorageManager().flushNow();
        sendMessage(sender, "import-success", "name", npcName, "file", fileName);
    }

    private boolean isSafeFileName(String name) {
        return name != null && !name.isBlank()
                && name.indexOf('/') < 0 && name.indexOf('\\') < 0
                && !name.contains("..") && name.indexOf('\0') < 0;
    }

    private void handlePermission(CommandSender sender, String[] args, String label) {
        if (args.length < 3) { usage(sender, "permission", label); return; }
        WoolyNpc npc = requireNpc(sender, args[1]);
        if (npc == null) return;

        String action = args[2].toLowerCase(Locale.ROOT);
        switch (action) {
            case "set" -> {
                if (args.length < 4) { usage(sender, "permission", label); return; }
                npc.setRequiredPermission(args[3]);
                plugin.getStorageManager().markDirty();
                sendMessage(sender, "permission-set", "name", npc.getName(), "permission", args[3]);
            }
            case "remove" -> {
                npc.setRequiredPermission(null);
                plugin.getStorageManager().markDirty();
                sendMessage(sender, "permission-removed", "name", npc.getName());
            }
            case "info" -> {
                String perm = npc.getRequiredPermission();
                if (perm == null || perm.isEmpty()) {
                    sendMessage(sender, "permission-none", "name", npc.getName());
                } else {
                    sendMessage(sender, "permission-info", "name", npc.getName(), "permission", perm);
                }
            }
            default -> usage(sender, "permission", label);
        }
    }

    private void handleSelect(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player p)) { sendMessage(sender, "only-players"); return; }

        if (args.length >= 2 && !isSelectionAlias(args[1])) {
            if (args[1].equalsIgnoreCase("clear") || args[1].equalsIgnoreCase("off")) {
                plugin.getSelectionManager().clear(p.getUniqueId());
                sendMessage(sender, "select-cleared");
                return;
            }
            WoolyNpc byName = plugin.getNpcManager().getNpcByName(args[1]);
            if (byName == null) { sendMessage(sender, "npc-not-found", "name", args[1]); return; }
            plugin.getSelectionManager().select(p, byName);
            sendMessage(sender, "select-done", "name", byName.getName());
            return;
        }

        WoolyNpc looked = plugin.getSelectionManager().findLookedAt(p);
        if (looked == null) { sendMessage(sender, "select-nothing"); return; }

        plugin.getSelectionManager().select(p, looked);
        sendMessage(sender, "select-done", "name", looked.getName());
    }

    private void handleMenu(CommandSender sender, String[] args, String label) {
        if (args.length < 2) { usage(sender, "menu", label); return; }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                var menus = plugin.getMenuManager().getMenus();
                if (menus.isEmpty()) { sendMessage(sender, "menu-list-empty"); return; }
                sendMessage(sender, "menu-list-header");
                for (var entry : menus.entrySet()) {
                    sendMessage(sender, "menu-list-item", "id", entry.getValue().getId(),
                            "items", String.valueOf(entry.getValue().getItems().size()));
                }
            }
            case "reload" -> {
                plugin.getMenuManager().reload();
                sendMessage(sender, "menu-reloaded");
            }
            case "open" -> {
                if (!(sender instanceof Player p)) { sendMessage(sender, "only-players"); return; }
                if (args.length < 3) { usage(sender, "menu", label); return; }
                if (plugin.getMenuManager().open(p, args[2], plugin.getSelectionManager().getSelected(p))) {
                    sendMessage(sender, "menu-opened", "id", args[2]);
                }
            }
            default -> usage(sender, "menu", label);
        }
    }

    private void handleMove(CommandSender sender, String[] args, String label) {
        if (args.length < 3) { usage(sender, "move", label); return; }
        WoolyNpc npc = requireNpc(sender, args[1]);
        if (npc == null) return;

        String mode = args[2].toLowerCase(Locale.ROOT);
        switch (mode) {
            case "none", "stop" -> {
                npc.setMovementMode(WoolyNpc.MovementMode.NONE);
                plugin.getWalkingTask().resetWander(npc.getId());
                plugin.getStorageManager().markDirty();
                sendMessage(sender, "move-none", "name", npc.getName());
            }
            case "follow", "wander" -> {
                if (args.length >= 4) {
                    double range = parseDouble(sender, args[3]);
                    if (Double.isNaN(range)) return;
                    npc.setMovementRange(range);
                }
                if (args.length >= 5) {
                    double speed = parseDouble(sender, args[4]);
                    if (Double.isNaN(speed)) return;
                    npc.setMovementSpeed(speed);
                }
                npc.setMovementMode(mode.equals("follow")
                        ? WoolyNpc.MovementMode.FOLLOW : WoolyNpc.MovementMode.WANDER);
                plugin.getWalkingTask().resetWander(npc.getId());
                plugin.getStorageManager().markDirty();
                sendMessage(sender, "move-set", "name", npc.getName(), "mode", mode,
                        "range", String.format("%.1f", npc.getMovementRange()),
                        "speed", String.format("%.2f", npc.getMovementSpeed()));
            }
            default -> usage(sender, "move", label);
        }
    }

    private void handleRoutine(CommandSender sender, String[] args, String label) {
        if (args.length < 3) { usage(sender, "routine", label); return; }
        WoolyNpc npc = requireNpc(sender, args[1]);
        if (npc == null) return;

        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.length < 5) { usage(sender, "routine", label); return; }
                int time = parseInt(sender, args[3]);
                if (time == Integer.MIN_VALUE) return;
                if (time < 0 || time >= 24000) { sendMessage(sender, "schedule-invalid-time"); return; }

                RoutineEntry.Type type;
                try {
                    type = RoutineEntry.Type.valueOf(args[4].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    sendMessage(sender, "routine-invalid-type", "types",
                            Arrays.stream(RoutineEntry.Type.values()).map(Enum::name)
                                    .collect(Collectors.joining(", ")));
                    return;
                }

                String value = args.length >= 6 ? joinArgs(args, 5) : "";
                if (value.isEmpty() && (type == RoutineEntry.Type.WALK || type == RoutineEntry.Type.TELEPORT)) {
                    if (!(sender instanceof Player p)) { sendMessage(sender, "only-players"); return; }
                    Location loc = p.getLocation();
                    value = String.format(Locale.ROOT, "%.2f %.2f %.2f", loc.getX(), loc.getY(), loc.getZ());
                }

                npc.getRoutine().add(new RoutineEntry(time, type, value));
                plugin.getStorageManager().markDirty();
                sendMessage(sender, "routine-added", "name", npc.getName(),
                        "time", String.valueOf(time), "type", type.name());
            }
            case "list" -> {
                if (npc.getRoutine().isEmpty()) {
                    sendMessage(sender, "routine-empty", "name", npc.getName());
                    return;
                }
                sendMessage(sender, "routine-list-header", "name", npc.getName());
                for (int i = 0; i < npc.getRoutine().size(); i++) {
                    RoutineEntry entry = npc.getRoutine().get(i);
                    sendMessage(sender, "routine-list-item",
                            "index", String.valueOf(i),
                            "time", String.valueOf(entry.getTimeTicks()),
                            "type", entry.getType().name(),
                            "value", entry.getValue());
                }
            }
            case "remove" -> {
                if (args.length < 4) { usage(sender, "routine", label); return; }
                int idx = parseInt(sender, args[3]);
                if (idx == Integer.MIN_VALUE) return;
                if (idx < 0 || idx >= npc.getRoutine().size()) {
                    sendMessage(sender, "index-out-of-range", "index", String.valueOf(idx),
                            "max", String.valueOf(npc.getRoutine().size() - 1));
                    return;
                }
                npc.getRoutine().remove(idx);
                plugin.getStorageManager().markDirty();
                sendMessage(sender, "routine-removed", "name", npc.getName(),
                        "index", String.valueOf(idx));
            }
            case "clear" -> {
                npc.getRoutine().clear();
                plugin.getStorageManager().markDirty();
                sendMessage(sender, "routine-cleared", "name", npc.getName());
            }
            default -> usage(sender, "routine", label);
        }
    }

    private void handleBackup(CommandSender sender) {
        if (plugin.getConfigManager().getBackupCount() <= 0) {
            sendMessage(sender, "backup-disabled");
            return;
        }
        plugin.getStorageManager().saveNpcsSync();
        plugin.getStorageManager().backupNow();
        sendMessage(sender, "backup-done");
    }

    private void handleSpawnAll(CommandSender sender) {
        int count = 0;
        for (WoolyNpc npc : plugin.getNpcManager().getActiveNpcs()) {
            if (npc.isSpawned()) continue;
            npc.setManualDespawn(false);
            plugin.getNpcManager().getArbiter().apply(npc);
            if (npc.isSpawned()) count++;
        }
        plugin.getStorageManager().markDirty();
        sendMessage(sender, "spawnall-done", "count", String.valueOf(count));
    }

    private void handleDespawnAll(CommandSender sender) {
        int count = 0;
        for (WoolyNpc npc : plugin.getNpcManager().getActiveNpcs()) {
            npc.setManualDespawn(true);
            if (npc.isSpawned()) { npc.despawn(true); count++; }
        }
        plugin.getStorageManager().markDirty();
        sendMessage(sender, "despawnall-done", "count", String.valueOf(count));
    }

    private void handleReload(CommandSender sender) {
        plugin.reload();
        sendMessage(sender, "reload-success");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length > 1 && !canUse(sender, args[0].toLowerCase(Locale.ROOT))) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filterAllowed(sender, args[0], "help", "reload", "create", "remove", "copy", "rename",
                    "list", "info", "edit", "movehere", "tpto", "model", "anim", "turn",
                    "lookat", "holo", "action", "visibility", "proximity", "spawn", "despawn", "stats",
                    "dialog", "waypoint", "schedule", "clickreward", "playerhide", "playershow",
                    "export", "import", "permission", "select", "menu", "backup", "move", "routine",
                    "near", "search", "undo", "spawnall", "despawnall");
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("menu")) {
            if (args.length == 2) return filter(args[1], "list", "reload", "open");
            if (args.length == 3 && args[1].equalsIgnoreCase("open")) {
                return filter(args[2], plugin.getMenuManager().getMenus().values().stream()
                        .map(m -> m.getId()).toArray(String[]::new));
            }
            return Collections.emptyList();
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("import")) {
            return args.length == 3 ? filter(args[2], listExportFiles()) : Collections.emptyList();
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2) {
            switch (sub) {
                case "remove", "copy", "rename", "info", "edit", "movehere", "tpto",
                     "model", "anim", "turn", "lookat", "holo", "action",
                     "visibility", "proximity", "spawn", "despawn", "stats",
                     "dialog", "waypoint", "schedule", "clickreward",
                     "playerhide", "playershow", "export", "permission", "select",
                     "move", "routine" -> {
                    return filterNpcNames(args[1]);
                }
                default -> { }
            }
        }

        if (args.length == 3) {
            switch (sub) {
                case "playerhide", "playershow" -> {
                    return filter(args[2], Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName).toArray(String[]::new));
                }
                case "model"  -> { return filter(args[2], ModelEngineUtil.getModelIds().toArray(new String[0])); }
                case "anim"   -> { return filter(args[2], animationsOf(args[1])); }
                case "lookat" -> { return filter(args[2], "enable", "distance", "smooth", "eyeheight"); }
                case "holo"   -> { return filter(args[2], "line", "offset", "scale", "shadow",
                        "background", "bg-alpha", "billboard", "align", "see-through", "line-width", "view-range"); }
                case "action" -> { return filter(args[2], "add", "remove", "clear", "list"); }
                case "stats"  -> { return filter(args[2], "reset"); }
                case "dialog" -> { return filter(args[2], "set", "remove", "list", "reload"); }
                case "waypoint" -> { return filter(args[2], "add", "remove", "clear", "list", "speed", "loop", "gravity", "show"); }
                case "schedule" -> { return filter(args[2], "set", "remove", "info"); }
                case "clickreward" -> { return filter(args[2], "set", "remove", "info"); }
                case "permission" -> { return filter(args[2], "set", "remove", "info"); }
                case "move"       -> { return filter(args[2], "none", "follow", "wander"); }
                case "routine"    -> { return filter(args[2], "add", "list", "remove", "clear"); }
            }
        }

        if (args.length == 4) {
            switch (sub) {
                case "lookat" -> {
                    String p = args[2].toLowerCase(Locale.ROOT);
                    if (p.equals("enable") || p.equals("smooth")) return filter(args[3], "true", "false");
                }
                case "holo" -> {
                    String p = args[2].toLowerCase(Locale.ROOT);
                    switch (p) {
                        case "line"       -> { return filter(args[3], "add", "set", "remove", "clear", "list"); }
                        case "shadow", "background", "see-through" -> { return filter(args[3], "true", "false"); }
                        case "billboard"  -> { return filter(args[3], Arrays.stream(Display.Billboard.values()).map(Enum::name).toArray(String[]::new)); }
                        case "align"      -> { return filter(args[3], Arrays.stream(TextDisplay.TextAlignment.values()).map(Enum::name).toArray(String[]::new)); }
                    }
                }
                case "action" -> {
                    if (args[2].equalsIgnoreCase("add")) {
                        return filter(args[3], Arrays.stream(ClickType.values()).map(Enum::name).toArray(String[]::new));
                    }
                }
            }
        }

        if (args.length == 5 && sub.equals("action") && args[2].equalsIgnoreCase("add")) {
            List<String> types = new ArrayList<>(Arrays.stream(ActionType.values()).map(Enum::name).toList());
            types.addAll(WoolyNpcsApi.getRegisteredActions());
            return filter(args[4], types.toArray(new String[0]));
        }

        if (args.length == 5 && sub.equals("waypoint") && args[2].equalsIgnoreCase("add")) {
            return filter(args[4], animationsOf(args[1]));
        }

        if (args.length == 6 && sub.equals("action") && args[2].equalsIgnoreCase("add")) {
            String type = args[4].toUpperCase(Locale.ROOT);
            if (type.equals("OPEN_GUI")) {
                return filter(args[5], plugin.getMenuManager().getMenus().values().stream()
                        .map(m -> m.getId()).toArray(String[]::new));
            }
            if (type.equals("NPC_ANIMATION")) return filter(args[5], animationsOf(args[1]));
        }

        return Collections.emptyList();
    }

    private boolean isValidNpcName(String name) {
        return name != null && !name.isEmpty() && name.length() <= 32
                && NPC_NAME_PATTERN.matcher(name).matches();
    }

    private WoolyNpc requireNpc(CommandSender sender, String name) {
        if (isSelectionAlias(name)) {
            if (!(sender instanceof Player p)) { sendMessage(sender, "only-players"); return null; }

            WoolyNpc selected = plugin.getSelectionManager().getSelected(p);
            if (selected == null) selected = plugin.getSelectionManager().findLookedAt(p);
            if (selected == null) { sendMessage(sender, "no-selection"); return null; }
            if (!canEdit(sender, selected)) {
                sendMessage(sender, "npc-no-edit-permission", "name", selected.getName());
                return null;
            }
            return selected;
        }

        WoolyNpc npc = plugin.getNpcManager().getNpcByName(name);
        if (npc == null) {
            sendMessage(sender, "npc-not-found", "name", name);
            return null;
        }
        if (!canEdit(sender, npc)) {
            sendMessage(sender, "npc-no-edit-permission", "name", npc.getName());
            return null;
        }
        return npc;
    }

    private boolean canUse(CommandSender sender, String sub) {
        return sender.hasPermission(PERM_ADMIN) || sender.hasPermission(PERM_COMMAND_PREFIX + sub);
    }

    private boolean canEdit(CommandSender sender, WoolyNpc npc) {
        if (sender.hasPermission(PERM_ADMIN)) return true;
        return sender.hasPermission(PERM_NPC_PREFIX + npc.getName().toLowerCase(Locale.ROOT))
                || sender.hasPermission(PERM_NPC_PREFIX + "*");
    }

    private boolean isSelectionAlias(String name) {
        return "@".equals(name) || "-".equals(name);
    }

    private void usage(CommandSender sender, String usageKey, String label) {
        String msg = plugin.getConfigManager().getMessage("invalid-usage",
                "usage", plugin.getConfigManager().getUsage(usageKey, label));
        sender.sendMessage(ColorUtil.format(msg));
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ColorUtil.format(plugin.getConfigManager().getMessage("help-header")));
        for (String line : plugin.getConfigManager().getHelpLines(label)) {
            sender.sendMessage(ColorUtil.format(line));
        }
    }

    private void sendMessage(CommandSender sender, String key, String... placeholders) {
        String msg = plugin.getConfigManager().getMessage(key, placeholders);
        if (msg != null && !msg.isBlank()) {
            sender.sendMessage(ColorUtil.format(msg));
        }
    }

    private String joinArgs(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private double parseDouble(CommandSender sender, String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            sendMessage(sender, "invalid-number");
            return Double.NaN;
        }
    }

    private int parseInt(CommandSender sender, String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            sendMessage(sender, "invalid-number");
            return Integer.MIN_VALUE;
        }
    }

    private String buildFlagString(NpcAction a) {
        StringBuilder sb = new StringBuilder();
        if (a.getPermission() != null) sb.append("[PERM:").append(a.getPermission()).append("] ");
        if (a.getCooldownSeconds() > 0) sb.append("[CD:").append(a.getCooldownSeconds()).append("] ");
        if (a.isOneTime()) sb.append("[ONCE] ");
        return sb.toString();
    }

    private List<String> filterNpcNames(String prefix) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        List<String> names = plugin.getNpcManager().getActiveNpcs().stream()
                .map(WoolyNpc::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(lowerPrefix))
                .collect(Collectors.toList());
        if (prefix.isEmpty() || prefix.startsWith("@")) names.add(0, "@");
        return names;
    }

    private String[] animationsOf(String npcName) {
        WoolyNpc npc = isSelectionAlias(npcName)
                ? null : plugin.getNpcManager().getNpcByName(npcName);
        if (npc == null) return new String[0];
        return ModelEngineUtil.getAnimations(npc.getModelId()).toArray(new String[0]);
    }

    private String[] listExportFiles() {
        File exportDir = new File(plugin.getDataFolder(), "exports");
        File[] files = exportDir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return new String[0];
        return Arrays.stream(files)
                .map(f -> f.getName().substring(0, f.getName().length() - ".yml".length()))
                .toArray(String[]::new);
    }

    private List<String> filterAllowed(CommandSender sender, String prefix, String... options) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        return Stream.of(options)
                .filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lowerPrefix))
                .filter(o -> canUse(sender, o))
                .collect(Collectors.toList());
    }

    private List<String> filter(String prefix, String... options) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        return Stream.of(options)
                .filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lowerPrefix))
                .collect(Collectors.toList());
    }
}
