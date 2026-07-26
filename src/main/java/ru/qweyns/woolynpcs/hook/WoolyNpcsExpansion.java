package ru.qweyns.woolynpcs.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import ru.qweyns.woolynpcs.WoolyNpcs;
import ru.qweyns.woolynpcs.model.WoolyNpc;

import java.util.Locale;

public class WoolyNpcsExpansion extends PlaceholderExpansion {
    private final WoolyNpcs plugin;

    public WoolyNpcsExpansion(WoolyNpcs plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "woolynpcs";
    }

    @Override
    public String getAuthor() {
        return "qweyns";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        if (params == null || params.isEmpty()) return null;
        String query = params.toLowerCase(Locale.ROOT);

        Player player = offlinePlayer == null ? null : offlinePlayer.getPlayer();

        switch (query) {
            case "count" -> {
                return String.valueOf(plugin.getNpcManager().getActiveNpcs().size());
            }
            case "spawned_count" -> {
                long spawned = plugin.getNpcManager().getActiveNpcs().stream()
                        .filter(WoolyNpc::isSpawned).count();
                return String.valueOf(spawned);
            }
            case "dialog_id" -> {
                if (player == null) return "";
                String id = plugin.getDialogManager().getActiveDialogId(player.getUniqueId());
                return id == null ? "" : id;
            }
            case "dialog_node" -> {
                if (player == null) return "";
                String node = plugin.getDialogManager().getActiveNodeId(player.getUniqueId());
                return node == null ? "" : node;
            }
            case "selected" -> {
                if (player == null) return "";
                WoolyNpc selected = plugin.getSelectionManager().getSelected(player);
                return selected == null ? "" : selected.getName();
            }
            default -> { }
        }

        if (query.startsWith("flag_")) {
            if (player == null) return "";
            String flagValue = plugin.getPlayerDataManager().get(player).getFlag(params.substring(5));
            return flagValue == null ? "" : flagValue;
        }

        int separator = query.indexOf('_');
        if (separator <= 0 || separator == query.length() - 1) return null;

        String key = query.substring(0, separator);
        String npcName = params.substring(separator + 1);

        WoolyNpc npc = plugin.getNpcManager().getNpcByName(npcName);
        if (npc == null) return "";

        return switch (key) {
            case "clicks"    -> String.valueOf(npc.getTotalInteractions());
            case "myclicks"  -> player == null ? "0"
                    : String.valueOf(plugin.getPlayerDataManager().get(player).getClicks(npc.getId()));
            case "spawned"   -> String.valueOf(npc.isSpawned());
            case "model"     -> npc.getModelId();
            case "animation" -> npc.getActiveAnimation();
            case "world"     -> npc.getLocation().getWorld() == null
                    ? "" : npc.getLocation().getWorld().getName();
            case "x"         -> String.format(Locale.ROOT, "%.1f", npc.getLocation().getX());
            case "y"         -> String.format(Locale.ROOT, "%.1f", npc.getLocation().getY());
            case "z"         -> String.format(Locale.ROOT, "%.1f", npc.getLocation().getZ());
            default          -> null;
        };
    }
}
