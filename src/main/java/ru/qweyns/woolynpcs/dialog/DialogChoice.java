package ru.qweyns.woolynpcs.dialog;

import org.bukkit.entity.Player;
import ru.qweyns.woolynpcs.condition.Condition;
import ru.qweyns.woolynpcs.model.NpcAction;

import java.util.ArrayList;
import java.util.List;

public class DialogChoice {
    private final String label;
    private final String targetNodeId;
    private final String permission;

    private final List<Condition> conditions = new ArrayList<>();
    private final List<NpcAction> actions = new ArrayList<>();

    private boolean hideIfLocked = true;
    private String lockedLabel = "";

    private String material = "PAPER";
    private int    slot     = -1;

    public DialogChoice(String label, String targetNodeId, String permission) {
        this.label        = label;
        this.targetNodeId = targetNodeId;
        this.permission   = permission;
    }

    public boolean isAvailable(Player player) {
        if (permission != null && !permission.isEmpty() && !player.hasPermission(permission)) {
            return false;
        }
        for (Condition condition : conditions) {
            if (!condition.test(player)) return false;
        }
        return true;
    }

    public String getLabel()        { return label;        }
    public String getTargetNodeId() { return targetNodeId; }
    public String getPermission()   { return permission;   }

    public List<Condition> getConditions() { return conditions; }
    public List<NpcAction> getActions()    { return actions;    }

    public boolean isHideIfLocked()          { return hideIfLocked;   }
    public void    setHideIfLocked(boolean v){ this.hideIfLocked = v; }

    public String getLockedLabel()           { return lockedLabel; }
    public void   setLockedLabel(String v)   { this.lockedLabel = v == null ? "" : v; }

    public String getMaterial()              { return material; }
    public void   setMaterial(String v)      { if (v != null && !v.isBlank()) this.material = v; }

    public int  getSlot()                    { return slot; }
    public void setSlot(int v)               { this.slot = v; }
}
