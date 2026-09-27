package com.seng403.mutanthunt.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class ParticipantTest {

    @Test
    void maskedNumber_standard10Digit() {
        Participant p = new Participant(1L, "2021001234", "Alice", "token");
        assertEquals("2021****34", p.getMaskedNumber());
    }

    @Test
    void maskedNumber_shortNumber() {
        Participant p = new Participant(1L, "123", "Bob", "token");
        assertEquals("****", p.getMaskedNumber());
    }

    @Test
    void maskedNumber_fourDigits() {
        Participant p = new Participant(1L, "1234", "Charlie", "token");
        assertEquals("****", p.getMaskedNumber());
    }

    @Test
    void maskedNumber_fiveDigits() {
        Participant p = new Participant(1L, "12345", "Dave", "token");
        assertEquals("1234****45", p.getMaskedNumber());
    }

    @Test
    void constructor_setsFields() {
        Participant p = new Participant(1L, "9999", "Test User", "abc");
        assertEquals(1L, p.getSessionId());
        assertEquals("9999", p.getStudentNumber());
        assertEquals("Test User", p.getFullName());
        assertEquals("abc", p.getToken());
        assertFalse(p.isKicked());
        assertNull(p.getNickname());
        assertNull(p.getAnonymousName());
    }

    @Test
    void setters() {
        Participant p = new Participant(1L, "1111", "User", "tok");
        p.setFullName("New Name");
        assertEquals("New Name", p.getFullName());

        p.setNickname("Nick");
        assertEquals("Nick", p.getNickname());

        p.setAnonymousName("Blue Tiger #1");
        assertEquals("Blue Tiger #1", p.getAnonymousName());

        p.setToken("newtoken");
        assertEquals("newtoken", p.getToken());

        p.setKicked(true);
        assertTrue(p.isKicked());
    }

    @Test
    void joinedAt_notNull() {
        Participant p = new Participant(1L, "1111", "User", "tok");
        assertNotNull(p.getJoinedAt());
    }
}
