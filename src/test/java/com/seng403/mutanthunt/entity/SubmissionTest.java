package com.seng403.mutanthunt.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubmissionTest {

    @Test
    void constructor_setsFields() {
        Submission s = new Submission(1L, 2L, "{}", "0", "0", Validity.VALID);
        assertEquals(1L, s.getRoundId());
        assertEquals(2L, s.getParticipantId());
        assertEquals("{}", s.getRawInputsJson());
        assertEquals("0", s.getExpectedValue());
        assertEquals("0", s.getReferenceResult());
        assertEquals(Validity.VALID, s.getValidity());
        assertNotNull(s.getCreatedAt());
    }

    @Test
    void constructor_invalidValidity() {
        Submission s = new Submission(1L, 2L, "{}", "1", "0", Validity.INVALID);
        assertEquals(Validity.INVALID, s.getValidity());
    }
}
