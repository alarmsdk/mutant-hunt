package com.seng403.mutanthunt.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MutantResultTest {

    @Test
    void constructor_normalResult() {
        MutantResult mr = new MutantResult(1L, "M1", "0", null, false);
        assertEquals(1L, mr.getSubmissionId());
        assertEquals("M1", mr.getMutantId());
        assertEquals("0", mr.getMutantOutput());
        assertNull(mr.getExceptionMessage());
        assertFalse(mr.isKilled());
    }

    @Test
    void constructor_killedResult() {
        MutantResult mr = new MutantResult(1L, "M1", "1", null, true);
        assertTrue(mr.isKilled());
    }

    @Test
    void constructor_exceptionResult() {
        MutantResult mr = new MutantResult(1L, "M2", null, "NPE", true);
        assertNull(mr.getMutantOutput());
        assertEquals("NPE", mr.getExceptionMessage());
        assertTrue(mr.isKilled());
    }

    @Test
    void displayOutput_normalValue() {
        MutantResult mr = new MutantResult(1L, "M1", "42", null, false);
        assertEquals("42", mr.getDisplayOutput());
    }

    @Test
    void displayOutput_exceptionValue() {
        MutantResult mr = new MutantResult(1L, "M1", null, "ArrayIndexOutOfBoundsException", true);
        assertEquals("EXCEPTION: ArrayIndexOutOfBoundsException", mr.getDisplayOutput());
    }
}
