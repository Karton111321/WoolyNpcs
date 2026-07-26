package ru.qweyns.woolynpcs.dialog;

import java.util.LinkedHashMap;
import java.util.Map;

public class Dialog {
    private final String id;
    private final String startNodeId;
    private final Map<String, DialogNode> nodes = new LinkedHashMap<>();

    public enum Mode { CHAT, GUI }

    private Mode   mode     = Mode.CHAT;
    private int    guiSize  = 27;
    private String guiTitle = "";

    public Dialog(String id, String startNodeId) {
        this.id          = id;
        this.startNodeId = startNodeId;
    }

    public Mode getMode()            { return mode; }
    public void setMode(Mode v)      { if (v != null) this.mode = v; }

    public int  getGuiSize()         { return guiSize; }
    public void setGuiSize(int v)    {
        int rows = Math.round(v / 9.0f);
        this.guiSize = Math.max(9, Math.min(54, Math.max(1, rows) * 9));
    }

    public String getGuiTitle()      { return guiTitle; }
    public void   setGuiTitle(String v) { this.guiTitle = v == null ? "" : v; }

    public void addNode(DialogNode node) {
        nodes.put(node.getId(), node);
    }

    public DialogNode getNode(String nodeId) {
        return nodes.get(nodeId);
    }

    public DialogNode getStartNode() {
        return nodes.get(startNodeId);
    }

    public String                   getId()         { return id;          }
    public String                   getStartNodeId(){ return startNodeId; }
    public Map<String, DialogNode>  getNodes()      { return nodes;       }
}
