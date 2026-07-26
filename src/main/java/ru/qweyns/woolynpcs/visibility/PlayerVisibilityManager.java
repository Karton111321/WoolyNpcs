package ru.qweyns.woolynpcs.visibility;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.qweyns.woolynpcs.WoolyNpcs;
import ru.qweyns.woolynpcs.model.WoolyNpc;
import ru.qweyns.woolynpcs.playerdata.PlayerData;

import java.util.Set;
import java.util.UUID;

public class PlayerVisibilityManager {
    private final WoolyNpcs plugin;

    public PlayerVisibilityManager(WoolyNpcs plugin) {
        this.plugin = plugin;
    }

    public void hideNpcForPlayer(Player player, WoolyNpc npc) {
        plugin.getPlayerDataManager().get(player).hideNpc(npc.getId());
        applyHide(player, npc);
    }

    public void showNpcForPlayer(Player player, WoolyNpc npc) {
        plugin.getPlayerDataManager().get(player).showNpc(npc.getId());

        if (npc.isSpawned() && npc.getBaseEntity() != null) {
            player.showEntity(plugin, npc.getBaseEntity());
            if (npc.getHologram() != null && npc.getHologram().isValid()) {
                player.showEntity(plugin, npc.getHologram());
            }
        }
    }

    public void applyHidden(WoolyNpc npc) {
        if (!npc.isSpawned() || npc.getBaseEntity() == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isHidden(player.getUniqueId(), npc.getId())) continue;
            applyHide(player, npc);
        }
    }

    public void applyForPlayer(Player player) {
        Set<UUID> hidden = plugin.getPlayerDataManager().get(player).getHiddenNpcs();
        if (hidden.isEmpty()) return;

        for (UUID npcId : hidden) {
            WoolyNpc npc = plugin.getNpcManager().getNpcById(npcId);
            if (npc == null || !npc.isSpawned() || npc.getBaseEntity() == null) continue;
            applyHide(player, npc);
        }
    }

    private void applyHide(Player player, WoolyNpc npc) {
        if (!npc.isSpawned() || npc.getBaseEntity() == null) return;

        player.hideEntity(plugin, npc.getBaseEntity());
        if (npc.getHologram() != null && npc.getHologram().isValid()) {
            player.hideEntity(plugin, npc.getHologram());
        }
    }

    public boolean isHidden(UUID playerId, UUID npcId) {
        PlayerData data = plugin.getPlayerDataManager().get(playerId);
        return data.isNpcHidden(npcId);
    }

    public Set<UUID> getHiddenNpcs(UUID playerId) {
        return plugin.getPlayerDataManager().get(playerId).getHiddenNpcs();
    }

    public void forgetNpc(UUID npcId) {
        plugin.getPlayerDataManager().forgetNpc(npcId);
    }
}
