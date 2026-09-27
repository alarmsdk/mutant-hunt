package com.seng403.mutanthunt.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RoundTest {

    @Test
    void constructor_setsFields() {
        Round r = new Round(1L, "Round 1", "indexOf");
        assertEquals(1L, r.getSessionId());
        assertEquals("Round 1", r.getName());
        assertEquals("indexOf", r.getProblemId());
        assertEquals(RoundStatus.DRAFT, r.getStatus());
        assertNull(r.getTestLimit());
        assertNull(r.getTimeLimitSeconds());
        assertNull(r.getOpenedAt());
        assertNull(r.getClosedAt());
        assertNotNull(r.getCreatedAt());
    }

    @Test
    void isUnlimited_nullLimit() {
        Round r = new Round(1L, "R", "indexOf");
        r.setTestLimit(null);
        assertTrue(r.isUnlimited());
    }

    @Test
    void isUnlimited_zeroLimit() {
        Round r = new Round(1L, "R", "indexOf");
        r.setTestLimit(0);
        assertTrue(r.isUnlimited());
    }

    @Test
    void isUnlimited_negativeLimit() {
        Round r = new Round(1L, "R", "indexOf");
        r.setTestLimit(-1);
        assertTrue(r.isUnlimited());
    }

    @Test
    void isUnlimited_positiveLimit() {
        Round r = new Round(1L, "R", "indexOf");
        r.setTestLimit(10);
        assertFalse(r.isUnlimited());
    }

    @Test
    void setters() {
        Round r = new Round(1L, "R", "indexOf");

        r.setName("New Round");
        assertEquals("New Round", r.getName());

        r.setTestLimit(5);
        assertEquals(5, r.getTestLimit());

        r.setTimeLimitSeconds(300);
        assertEquals(300, r.getTimeLimitSeconds());

        r.setStatus(RoundStatus.OPEN);
        assertEquals(RoundStatus.OPEN, r.getStatus());

        Instant now = Instant.now();
        r.setOpenedAt(now);
        assertEquals(now, r.getOpenedAt());

        r.setClosedAt(now);
        assertEquals(now, r.getClosedAt());
    }
}
