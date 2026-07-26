package ru.qweyns.woolynpcs.waypoint;

import org.bukkit.Location;

public class Waypoint {
    private final Location location;
    private final int      waitTicks;
    private final String   animation;

    public Waypoint(Location location, int waitTicks, String animation) {
        this.location  = location;
        this.waitTicks = waitTicks;
        this.animation = animation;
    }

    public Location getLocation()  { return location;  }
    public int      getWaitTicks() { return waitTicks; }
    public String   getAnimation() { return animation; }
}
