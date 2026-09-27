package com.seng403.mutanthunt.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameSessionTest {

    @Test
    void constructor_setsDefaults() {
        GameSession s = new GameSession("Test");
        assertEquals("Test", s.getName());
        assertFalse(s.isActive());
        assertFalse(s.isArchived());
        assertEquals(AnonymityMode.FULL_NAME, s.getAnonymityMode());
        assertTrue(s.isJoinOpen());
        assertEquals(0, s.getStudentIdLength());
        assertEquals(MutantPanelMode.HIDDEN, s.getMutantPanelMode());
        assertEquals(2000, s.getSubmissionRateLimitMs());
        assertNotNull(s.getCreatedAt());
    }

    @Test
    void setters() {
        GameSession s = new GameSession("Test");

        s.setName("New Name");
        assertEquals("New Name", s.getName());

        s.setActive(true);
        assertTrue(s.isActive());

        s.setArchived(true);
        assertTrue(s.isArchived());

        s.setAnonymityMode(AnonymityMode.NICKNAME);
        assertEquals(AnonymityMode.NICKNAME, s.getAnonymityMode());

        s.setJoinOpen(false);
        assertFalse(s.isJoinOpen());

        s.setStudentIdLength(10);
        assertEquals(10, s.getStudentIdLength());

        s.setMutantPanelMode(MutantPanelMode.REVEALED);
        assertEquals(MutantPanelMode.REVEALED, s.getMutantPanelMode());

        s.setSubmissionRateLimitMs(5000);
        assertEquals(5000, s.getSubmissionRateLimitMs());
    }
}
