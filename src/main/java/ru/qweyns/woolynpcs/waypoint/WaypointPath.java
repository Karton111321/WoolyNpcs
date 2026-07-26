package ru.qweyns.woolynpcs.waypoint;

import java.util.ArrayList;
import java.util.List;

public class WaypointPath {
    private String id;
    private final List<Waypoint> waypoints = new ArrayList<>();
    private boolean loop = true;
    private double speed = 0.15;
    private boolean gravity = false;

    public WaypointPath(String id) {
        this.id = id;
    }

    public String          getId()       { return id;        }
    public void            setId(String v) { this.id = v;    }
    public List<Waypoint>  getWaypoints(){ return waypoints; }
    public boolean         isLoop()      { return loop;      }
    public double          getSpeed()    { return speed;     }
    public void            setLoop(boolean v) { this.loop = v; }
    public void            setSpeed(double v) { this.speed = Math.max(0.01, Math.min(1.0, v)); }
    public boolean         isGravity()        { return gravity; }
    public void            setGravity(boolean v) { this.gravity = v; }
}
