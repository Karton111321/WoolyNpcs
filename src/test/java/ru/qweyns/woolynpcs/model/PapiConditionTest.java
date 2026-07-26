package ru.qweyns.woolynpcs.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PapiConditionTest {
    @Test
    @DisplayName("Двухсимвольные операторы разбираются раньше односимвольных")
    void parsesTwoCharOperatorsFirst() {
        assertEquals("%player_level%>=10", PapiCondition.parse("%player_level%>=10").getRaw());
        assertEquals("%a%<=5",  PapiCondition.parse("%a%<=5").getRaw());
        assertEquals("%a%!=b",  PapiCondition.parse("%a%!=b").getRaw());
        assertEquals("%a%==b",  PapiCondition.parse("%a%==b").getRaw());
    }

    @Test
    @DisplayName("Односимвольные операторы и мягкая форма '='")
    void parsesSingleCharOperators() {
        assertNotNull(PapiCondition.parse("%a%>1"));
        assertNotNull(PapiCondition.parse("%a%<1"));
        assertNotNull(PapiCondition.parse("%a%=b"));
        assertNotNull(PapiCondition.parse("%a%~=часть"));
    }

    @Test
    @DisplayName("Выражения без оператора или с пустой стороной отвергаются")
    void rejectsBroken() {
        assertNull(PapiCondition.parse(null));
        assertNull(PapiCondition.parse(""));
        assertNull(PapiCondition.parse("%player_level%"));
        assertNull(PapiCondition.parse(">=10"), "пустая левая часть");
        assertNull(PapiCondition.parse("%a%>="), "пустая правая часть");
    }

    @Test
    @DisplayName("Исходная запись сохраняется для getSaveString()")
    void keepsRawText() {
        PapiCondition condition = PapiCondition.parse("  %vault_eco_balance% >= 500  ");
        assertEquals("%vault_eco_balance% >= 500", condition.getRaw());
    }
}
