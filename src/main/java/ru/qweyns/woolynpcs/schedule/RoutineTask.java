package ru.qweyns.woolynpcs.schedule;

import ru.qweyns.woolynpcs.WoolyNpcs;
import ru.qweyns.woolynpcs.model.WoolyNpc;

public class RoutineTask implements Runnable {
    private final WoolyNpcs plugin;

    public RoutineTask(WoolyNpcs plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (WoolyNpc npc : plugin.getNpcManager().getActiveNpcs()) {
            if (npc.getRoutine().isEmpty()) continue;
            if (npc.getLocation().getWorld() == null) continue;

            long now = npc.getLocation().getWorld().getTime() % 24000;
            long previous = npc.getLastRoutineTime();
            npc.setLastRoutineTime(now);

            if (previous < 0) continue;
            if (previous == now) continue;

            for (RoutineEntry entry : npc.getRoutine()) {
                if (crossed(previous, now, entry.getTimeTicks())) {
                    entry.apply(npc);
                }
            }
        }
    }

    private boolean crossed(long previous, long now, long mark) {
        if (previous < now) return mark > previous && mark <= now;
        return mark > previous || mark <= now;
    }
}
