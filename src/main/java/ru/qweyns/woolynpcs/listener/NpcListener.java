package ru.qweyns.woolynpcs.listener;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import ru.qweyns.woolynpcs.WoolyNpcs;
import ru.qweyns.woolynpcs.api.NpcInteractEvent;
import ru.qweyns.woolynpcs.model.ClickType;
import ru.qweyns.woolynpcs.model.NpcAction;
import ru.qweyns.woolynpcs.model.WoolyNpc;
import ru.qweyns.woolynpcs.util.ColorUtil;
import ru.qweyns.woolynpcs.util.RegistryUtil;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class NpcListener implements Listener {
    private final WoolyNpcs plugin;

    private final Map<UUID, Map<UUID, Long>> interactionCooldowns = new HashMap<>();

    public NpcListener(WoolyNpcs plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onNpcDamage(EntityDamageEvent event) {
        if (plugin.getNpcManager().getNpcByBaseEntityId(event.getEntity().getUniqueId()) == null) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onRightClick(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof ArmorStand)) return;

        WoolyNpc npc = plugin.getNpcManager().getNpcByBaseEntityId(clicked.getUniqueId());
        if (npc == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        if (!canInteract(player, npc)) return;

        ClickType clickType = player.isSneaking() ? ClickType.SHIFT_RIGHT_CLICK : ClickType.RIGHT_CLICK;
        if (!fireInteractEvent(npc, player, clickType)) return;

        npc.recordInteraction();
        plugin.getPlayerDataManager().get(player).addClick(npc.getId());
        checkClickReward(npc, player);

        if (npc.getDialogId() != null && !npc.getDialogId().isEmpty()) {
            plugin.getDialogManager().startDialog(player, npc.getDialogId(), npc);
            return;
        }

        executeActions(npc, player, clickType);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onLeftClick(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        Entity damaged = event.getEntity();
        if (!(damaged instanceof ArmorStand)) return;

        WoolyNpc npc = plugin.getNpcManager().getNpcByBaseEntityId(damaged.getUniqueId());
        if (npc == null) return;

        event.setCancelled(true);

        if (!canInteract(player, npc)) return;

        ClickType clickType = player.isSneaking() ? ClickType.SHIFT_LEFT_CLICK : ClickType.LEFT_CLICK;
        if (!fireInteractEvent(npc, player, clickType)) return;

        npc.recordInteraction();
        plugin.getPlayerDataManager().get(player).addClick(npc.getId());
        checkClickReward(npc, player);
        executeActions(npc, player, clickType);
    }

    private boolean fireInteractEvent(WoolyNpc npc, Player player, ClickType clickType) {
        NpcInteractEvent event = new NpcInteractEvent(npc, player, clickType);
        plugin.getServer().getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    private boolean canInteract(Player player, WoolyNpc npc) {
        if (plugin.getPlayerVisibilityManager().isHidden(player.getUniqueId(), npc.getId())) return false;

        String reqPerm = npc.getRequiredPermission();
        if (reqPerm != null && !reqPerm.isEmpty() && !player.hasPermission(reqPerm)) {
            player.sendMessage(ColorUtil.format(plugin.getConfigManager().getMessage("npc-no-permission")));
            return false;
        }

        return !isOnCooldown(player, npc);
    }

    private void executeActions(WoolyNpc npc, Player player, ClickType clickType) {
        String interactionSound = plugin.getConfigManager().getInteractionSound();
        if (interactionSound != null && !interactionSound.isEmpty()) {
            org.bukkit.Sound sound = RegistryUtil.resolveSound(interactionSound);
            if (sound != null) {
                float volume = plugin.getConfigManager().getInteractionSoundVolume();
                float pitch = plugin.getConfigManager().getInteractionSoundPitch();
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        }

        for (NpcAction action : npc.getActions()) {
            ClickType actionClick = action.getClickType();
            if (actionClick == ClickType.ANY || actionClick == clickType) {
                action.execute(player, npc);
            }
        }
    }

    private boolean isOnCooldown(Player player, WoolyNpc npc) {
        int cooldownSeconds = plugin.getConfigManager().getInteractionCooldown();
        if (cooldownSeconds <= 0) return false;

        long now = System.currentTimeMillis();
        Map<UUID, Long> perNpc = interactionCooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        Long last = perNpc.get(npc.getId());

        if (last != null && now - last < cooldownSeconds * 1_000L) {
            if (!plugin.getConfigManager().disableCooldownMsg()) {
                long remaining = ((last + cooldownSeconds * 1_000L) - now) / 1_000L + 1;
                String msg = plugin.getConfigManager().getMessage("cooldown-message",
                        "time", String.valueOf(remaining));
                player.sendMessage(ColorUtil.format(msg));
            }
            return true;
        }
        perNpc.put(npc.getId(), now);
        return false;
    }

    private void checkClickReward(WoolyNpc npc, Player player) {
        long threshold = npc.getClickRewardThreshold();
        if (threshold <= 0) return;
        if (npc.getTotalInteractions() % threshold != 0) return;

        NpcAction reward = npc.getClickRewardActionInstance();
        if (reward != null) reward.execute(player, npc);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getPlayerDataManager().onJoin(event.getPlayer());
        plugin.getPlayerVisibilityManager().applyForPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID pid = event.getPlayer().getUniqueId();
        pruneExpiredCooldowns(pid);
        plugin.getDialogManager().cancelDialog(pid);
        plugin.getSelectionManager().clear(pid);
        plugin.getPlayerDataManager().onQuit(pid);
        plugin.getWaypointVisualizer().stop(pid);
    }

    private void pruneExpiredCooldowns(UUID playerId) {
        int cooldownSeconds = plugin.getConfigManager().getInteractionCooldown();
        Map<UUID, Long> perNpc = interactionCooldowns.get(playerId);
        if (perNpc == null) return;

        long expireBefore = System.currentTimeMillis() - Math.max(0, cooldownSeconds) * 1_000L;
        perNpc.values().removeIf(last -> last < expireBefore);
        if (perNpc.isEmpty()) interactionCooldowns.remove(playerId);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Set<UUID> live = null;
        for (Entity entity : event.getChunk().getEntities()) {
            if (!plugin.isManagedEntity(entity)) continue;
            if (live == null) live = plugin.getNpcManager().getLiveEntityIds();
            if (!live.contains(entity.getUniqueId())) entity.remove();
        }
        plugin.getNpcManager().handleChunkLoad(event.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        plugin.getNpcManager().handleChunkUnload(event.getChunk());
    }

    public void forgetNpc(UUID npcId) {
        for (Iterator<Map<UUID, Long>> it = interactionCooldowns.values().iterator(); it.hasNext(); ) {
            Map<UUID, Long> perNpc = it.next();
            perNpc.remove(npcId);
            if (perNpc.isEmpty()) it.remove();
        }
    }
}
