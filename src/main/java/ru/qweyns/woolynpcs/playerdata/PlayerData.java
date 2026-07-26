package ru.qweyns.woolynpcs.playerdata;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerData {
    private final UUID playerId;

    private final Map<UUID, Long> actionCooldowns = new HashMap<>();
    private final Set<UUID> executedActions = new HashSet<>();
    private final Map<String, String> flags = new HashMap<>();
    private final Set<UUID> hiddenNpcs = new HashSet<>();
    private final Map<UUID, Long> npcClicks = new HashMap<>();

    private boolean dirty = false;

    public PlayerData(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() { return playerId; }

    public boolean isOnCooldown(UUID actionId, int cooldownSeconds) {
        if (cooldownSeconds <= 0) return false;
        Long last = actionCooldowns.get(actionId);
        return last != null && System.currentTimeMillis() - last < cooldownSeconds * 1_000L;
    }

    public void markCooldown(UUID actionId) {
        actionCooldowns.put(actionId, System.currentTimeMillis());
        dirty = true;
    }

    public Map<UUID, Long> getActionCooldowns() { return actionCooldowns; }

    public boolean hasExecuted(UUID actionId) {
        return executedActions.contains(actionId);
    }

    public void markExecuted(UUID actionId) {
        if (executedActions.add(actionId)) dirty = true;
    }

    public Set<UUID> getExecutedActions() { return executedActions; }

    public String getFlag(String key) {
        return key == null ? null : flags.get(key.toLowerCase(java.util.Locale.ROOT));
    }

    public void setFlag(String key, String value) {
        if (key == null) return;
        String normalized = key.toLowerCase(java.util.Locale.ROOT);
        if (value == null) {
            if (flags.remove(normalized) != null) dirty = true;
        } else if (!value.equals(flags.put(normalized, value))) {
            dirty = true;
        }
    }

    public boolean hasFlag(String key) {
        return getFlag(key) != null;
    }

    public Map<String, String> getFlags() { return flags; }

    public boolean isNpcHidden(UUID npcId) {
        return hiddenNpcs.contains(npcId);
    }

    public void hideNpc(UUID npcId) {
        if (hiddenNpcs.add(npcId)) dirty = true;
    }

    public void showNpc(UUID npcId) {
        if (hiddenNpcs.remove(npcId)) dirty = true;
    }

    public Set<UUID> getHiddenNpcs() { return hiddenNpcs; }

    public long getClicks(UUID npcId) {
        return npcClicks.getOrDefault(npcId, 0L);
    }

    public void addClick(UUID npcId) {
        npcClicks.merge(npcId, 1L, Long::sum);
        dirty = true;
    }

    public Map<UUID, Long> getNpcClicks() { return npcClicks; }

    public void forgetNpc(UUID npcId) {
        if (hiddenNpcs.remove(npcId)) dirty = true;
        if (npcClicks.remove(npcId) != null) dirty = true;
    }

    public boolean isDirty()      { return dirty;  }
    public void    markDirty()    { this.dirty = true;  }
    public void    clearDirty()   { this.dirty = false; }

    public boolean isEmpty() {
        return actionCooldowns.isEmpty() && executedActions.isEmpty() && flags.isEmpty()
                && hiddenNpcs.isEmpty() && npcClicks.isEmpty();
    }
}
