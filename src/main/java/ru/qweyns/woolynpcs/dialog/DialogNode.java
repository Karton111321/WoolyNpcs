package ru.qweyns.woolynpcs.dialog;

import ru.qweyns.woolynpcs.model.NpcAction;

import java.util.ArrayList;
import java.util.List;

public class DialogNode {
    private final String id;
    private final String text;
    private final List<DialogChoice> choices = new ArrayList<>();

    private final List<NpcAction> actions = new ArrayList<>();

    private String sound = "";
    private int typewriter = 0;
    private String autoGoto = null;
    private int autoDelay = 40;

    public DialogNode(String id, String text) {
        this.id   = id;
        this.text = text;
    }

    public String             getId()      { return id;      }
    public String             getText()    { return text;    }
    public List<DialogChoice> getChoices() { return choices; }
    public List<NpcAction>    getActions() { return actions; }

    public String getSound()             { return sound; }
    public void   setSound(String v)     { this.sound = v == null ? "" : v.trim(); }

    public int  getTypewriter()          { return typewriter; }
    public void setTypewriter(int v)     { this.typewriter = Math.max(0, v); }

    public String getAutoGoto()          { return autoGoto; }
    public void   setAutoGoto(String v)  { this.autoGoto = (v == null || v.isBlank()) ? null : v.trim(); }

    public int  getAutoDelay()           { return autoDelay; }
    public void setAutoDelay(int v)      { this.autoDelay = Math.max(1, v); }

    public boolean hasAutoGoto()         { return autoGoto != null; }
}
