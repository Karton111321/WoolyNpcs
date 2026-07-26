package ru.qweyns.woolynpcs.schedule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RoutineEntryTest {
    @Test
    @DisplayName("Строка распорядка читается и пишется без потерь")
    void roundTrip() {
        RoutineEntry entry = new RoutineEntry(1000, RoutineEntry.Type.WALK, "10.5 64.0 -20.5");

        assertEquals("1000:WALK:10.5 64.0 -20.5", entry.serialize());

        RoutineEntry parsed = RoutineEntry.parse(entry.serialize());
        assertEquals(1000, parsed.getTimeTicks());
        assertEquals(RoutineEntry.Type.WALK, parsed.getType());
        assertEquals("10.5 64.0 -20.5", parsed.getValue());
    }

    @Test
    @DisplayName("Запись без значения допустима — например DESPAWN")
    void valueIsOptional() {
        RoutineEntry parsed = RoutineEntry.parse("18000:DESPAWN");

        assertEquals(18000, parsed.getTimeTicks());
        assertEquals(RoutineEntry.Type.DESPAWN, parsed.getType());
        assertEquals("", parsed.getValue());
    }

    @Test
    @DisplayName("Время приводится к суткам, в том числе отрицательное")
    void timeIsNormalized() {
        assertEquals(2000, new RoutineEntry(26000, RoutineEntry.Type.SPAWN, "").getTimeTicks());
        assertEquals(23000, new RoutineEntry(-1000, RoutineEntry.Type.SPAWN, "").getTimeTicks());
    }

    @Test
    @DisplayName("Битые строки не превращаются в записи")
    void rejectsBroken() {
        assertNull(RoutineEntry.parse(null));
        assertNull(RoutineEntry.parse(""));
        assertNull(RoutineEntry.parse("1000"));
        assertNull(RoutineEntry.parse("утро:WALK:1 2 3"));
        assertNull(RoutineEntry.parse("1000:ЛЕТАТЬ:1 2 3"));
    }
}
