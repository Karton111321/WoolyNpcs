package ru.qweyns.woolynpcs.listener;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import ru.qweyns.woolynpcs.WoolyNpcs;
import ru.qweyns.woolynpcs.model.ClickType;
import ru.qweyns.woolynpcs.model.NpcAction;
import ru.qweyns.woolynpcs.model.WoolyNpc;

public class TriggerListener implements Listener {
    private final WoolyNpcs plugin;

    public TriggerListener(WoolyNpcs plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        for (WoolyNpc npc : plugin.getNpcManager().getActiveNpcs()) {
            fire(npc, event.getPlayer(), ClickType.ON_JOIN, false);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        if (event.getEntity() instanceof Player) return;

        for (WoolyNpc npc : plugin.getNpcManager().getActiveNpcs()) {
            fire(npc, killer, ClickType.ON_KILL, true);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        for (WoolyNpc npc : plugin.getNpcManager().getActiveNpcs()) {
            fire(npc, event.getEntity(), ClickType.ON_DEATH_NEARBY, true);
        }
    }

    private void fire(WoolyNpc npc, Player player, ClickType trigger, boolean requireNearby) {
        if (npc.getActions().stream().noneMatch(a -> a.getClickType() == trigger)) return;
        if (plugin.getPlayerVisibilityManager().isHidden(player.getUniqueId(), npc.getId())) return;

        if (requireNearby && !isNear(npc, player)) return;

        for (NpcAction action : npc.getActions()) {
            if (action.getClickType() == trigger) action.execute(player, npc);
        }
    }

    private boolean isNear(WoolyNpc npc, Player player) {
        Location npcLoc = plugin.getNpcManager().effectiveLocation(npc);
        if (npcLoc.getWorld() == null || !npcLoc.getWorld().equals(player.getWorld())) return false;

        double range = npc.getProximityTriggerRange();
        return npcLoc.distanceSquared(player.getLocation()) <= range * range;
    }
}
