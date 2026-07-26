package ru.qweyns.woolynpcs.schedule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcScheduleTest {
    @Test
    @DisplayName("Обычный промежуток: NPC жив внутри и мёртв снаружи")
    void plainRange() {
        NpcSchedule schedule = new NpcSchedule(1000, 13000);

        assertTrue(schedule.shouldBeSpawned(1000),  "начало промежутка входит");
        assertTrue(schedule.shouldBeSpawned(6000),  "середина промежутка");
        assertFalse(schedule.shouldBeSpawned(13000), "конец промежутка не входит");
        assertFalse(schedule.shouldBeSpawned(500),   "до начала");
        assertFalse(schedule.shouldBeSpawned(20000), "после конца");
    }

    @Test
    @DisplayName("Промежуток через полночь работает в обе стороны от 24000")
    void rangeAcrossMidnight() {
        NpcSchedule schedule = new NpcSchedule(13000, 1000);

        assertTrue(schedule.shouldBeSpawned(13000), "начало ночи");
        assertTrue(schedule.shouldBeSpawned(23000), "до полуночи");
        assertTrue(schedule.shouldBeSpawned(0),     "ровно полночь");
        assertTrue(schedule.shouldBeSpawned(500),   "после полуночи");
        assertFalse(schedule.shouldBeSpawned(1000), "конец промежутка не входит");
        assertFalse(schedule.shouldBeSpawned(6000), "день");
    }

    @Test
    @DisplayName("Время больше суток приводится к диапазону 0..23999")
    void wrapsWorldTime() {
        NpcSchedule schedule = new NpcSchedule(1000, 13000);

        assertTrue(schedule.shouldBeSpawned(24000 + 6000));
        assertFalse(schedule.shouldBeSpawned(24000 * 3 + 20000));
    }
}
