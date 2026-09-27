package com.seng403.mutanthunt.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnumsTest {

    @Test
    void anonymityMode_allValues() {
        AnonymityMode[] modes = AnonymityMode.values();
        assertEquals(4, modes.length);
        assertEquals(AnonymityMode.FULL_NAME, AnonymityMode.valueOf("FULL_NAME"));
        assertEquals(AnonymityMode.NICKNAME, AnonymityMode.valueOf("NICKNAME"));
        assertEquals(AnonymityMode.ANONYMOUS, AnonymityMode.valueOf("ANONYMOUS"));
        assertEquals(AnonymityMode.NUMBER_MASKED, AnonymityMode.valueOf("NUMBER_MASKED"));
    }

    @Test
    void mutantPanelMode_allValues() {
        MutantPanelMode[] modes = MutantPanelMode.values();
        assertEquals(3, modes.length);
        assertEquals(MutantPanelMode.HIDDEN, MutantPanelMode.valueOf("HIDDEN"));
        assertEquals(MutantPanelMode.COUNT_ONLY, MutantPanelMode.valueOf("COUNT_ONLY"));
        assertEquals(MutantPanelMode.REVEALED, MutantPanelMode.valueOf("REVEALED"));
    }

    @Test
    void roundStatus_allValues() {
        RoundStatus[] statuses = RoundStatus.values();
        assertEquals(4, statuses.length);
        assertEquals(RoundStatus.DRAFT, RoundStatus.valueOf("DRAFT"));
        assertEquals(RoundStatus.OPEN, RoundStatus.valueOf("OPEN"));
        assertEquals(RoundStatus.CLOSED, RoundStatus.valueOf("CLOSED"));
        assertEquals(RoundStatus.REVEALED, RoundStatus.valueOf("REVEALED"));
    }

    @Test
    void validity_allValues() {
        Validity[] values = Validity.values();
        assertEquals(2, values.length);
        assertEquals(Validity.VALID, Validity.valueOf("VALID"));
        assertEquals(Validity.INVALID, Validity.valueOf("INVALID"));
    }
}
