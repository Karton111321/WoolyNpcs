package ru.qweyns.woolynpcs.condition;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionParserTest {
    @Test
    @DisplayName("Известные теги распознаются, неизвестные — нет")
    void recognisesTags() {
        assertTrue(ConditionParser.isConditionBody("PERM:vip"));
        assertTrue(ConditionParser.isConditionBody("ITEM:DIAMOND:5"));
        assertTrue(ConditionParser.isConditionBody("FLAG:quest"));
        assertTrue(ConditionParser.isConditionBody("!REGION:spawn"));

        assertFalse(ConditionParser.isConditionBody("Сервер"));
        assertFalse(ConditionParser.isConditionBody("ВНИМАНИЕ: обновление"));
    }

    @Test
    @DisplayName("Отрицание оборачивает условие и сохраняется при записи")
    void parsesNegation() {
        Condition condition = ConditionParser.parse("!PERM:vip");
        assertNotNull(condition);
        assertEquals("!PERM:vip", condition.serialize());
    }

    @Test
    @DisplayName("Группы OR и AND собираются из частей, разделённых чертой")
    void parsesGroups() {
        Condition or = ConditionParser.parse("OR:PERM:vip|PERM:admin");
        assertNotNull(or);
        assertEquals("OR:PERM:vip|PERM:admin", or.serialize());

        Condition and = ConditionParser.parse("AND:ITEM:DIAMOND:1|!REGION:spawn");
        assertNotNull(and);
        assertEquals("AND:ITEM:DIAMOND:1|!REGION:spawn", and.serialize());
    }

    @Test
    @DisplayName("Лишние скобки внутри группы прощаются")
    void toleratesBracketsInsideGroups() {
        Condition condition = ConditionParser.parse("OR:[PERM:vip]|[PERM:admin]");
        assertNotNull(condition);
        assertEquals("OR:PERM:vip|PERM:admin", condition.serialize());
    }

    @Test
    @DisplayName("Условия с составным значением не рассыпаются по двоеточиям")
    void keepsCompoundValues() {
        assertEquals("ITEM:DIAMOND:5", ConditionParser.parse("ITEM:DIAMOND:5").serialize());
        assertEquals("REALTIME:09:00-18:00", ConditionParser.parse("REALTIME:09:00-18:00").serialize());
        assertEquals("FLAG:stage=2", ConditionParser.parse("FLAG:stage=2").serialize());
    }

    @Test
    @DisplayName("Битые записи не превращаются в условия")
    void rejectsBroken() {
        assertNull(ConditionParser.parse(null));
        assertNull(ConditionParser.parse(""));
        assertNull(ConditionParser.parse("ЧТО-ТО:значение"));
        assertNull(ConditionParser.parse("TIME:УТРО"));
        assertNull(ConditionParser.parse("CHANCE:много"));
        assertNull(ConditionParser.parse("REALTIME:09:00"), "нет второй границы");
    }

    @Test
    @DisplayName("FIRSTJOIN работает без аргументов")
    void parsesArgumentlessTag() {
        Condition condition = ConditionParser.parse("FIRSTJOIN");
        assertNotNull(condition);
        assertEquals("FIRSTJOIN", condition.serialize());
    }
}
