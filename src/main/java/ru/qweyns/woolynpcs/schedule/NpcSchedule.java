package ru.qweyns.woolynpcs.schedule;

public class NpcSchedule {
    private final long spawnTimeTicks;
    private final long despawnTimeTicks;

    public NpcSchedule(long spawnTimeTicks, long despawnTimeTicks) {
        this.spawnTimeTicks   = spawnTimeTicks;
        this.despawnTimeTicks = despawnTimeTicks;
    }

    public long getSpawnTimeTicks()   { return spawnTimeTicks;   }
    public long getDespawnTimeTicks() { return despawnTimeTicks; }

    public boolean shouldBeSpawned(long worldTime) {
        long time = worldTime % 24000;
        if (spawnTimeTicks <= despawnTimeTicks) {
            return time >= spawnTimeTicks && time < despawnTimeTicks;
        } else {
            return time >= spawnTimeTicks || time < despawnTimeTicks;
        }
    }
}
