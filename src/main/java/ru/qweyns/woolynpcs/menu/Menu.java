package ru.qweyns.woolynpcs.menu;

import ru.qweyns.woolynpcs.model.NpcAction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Menu {
    private final String id;
    private final String title;
    private final int    size;

    private final Map<Integer, MenuItem> items = new HashMap<>();

    public Menu(String id, String title, int size) {
        this.id    = id;
        this.title = title;
        this.size  = size;
    }

    public String getId()    { return id;    }
    public String getTitle() { return title; }
    public int    getSize()  { return size;  }

    public Map<Integer, MenuItem> getItems() { return items; }

    public MenuItem getItem(int slot) {
        return items.get(slot);
    }

    public static class MenuItem {
        private final String       material;
        private final String       name;
        private final List<String> lore;
        private final List<NpcAction> actions = new ArrayList<>();
        private final boolean closeOnClick;

        public MenuItem(String material, String name, List<String> lore, boolean closeOnClick) {
            this.material     = material;
            this.name         = name;
            this.lore         = lore == null ? List.of() : List.copyOf(lore);
            this.closeOnClick = closeOnClick;
        }

        public String          getMaterial()   { return material;     }
        public String          getName()       { return name;         }
        public List<String>    getLore()       { return lore;         }
        public List<NpcAction> getActions()    { return actions;      }
        public boolean         isCloseOnClick(){ return closeOnClick; }
    }
}
