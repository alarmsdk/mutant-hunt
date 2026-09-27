package com.seng403.mutanthunt.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SseServiceTest {

    private SseService sseService;

    @BeforeEach
    void setUp() {
        sseService = new SseService();
    }

    @Test
    void registerBoard_returnsEmitter() {
        SseEmitter emitter = sseService.registerBoard();
        assertNotNull(emitter);
        assertEquals(1, sseService.boardConnectionCount());
    }

    @Test
    void registerAdmin_returnsEmitter() {
        SseEmitter emitter = sseService.registerAdmin();
        assertNotNull(emitter);
        assertEquals(1, sseService.adminConnectionCount());
    }

    @Test
    void registerStudent_returnsEmitter() {
        SseEmitter emitter = sseService.registerStudent(1L);
        assertNotNull(emitter);
        assertEquals(1, sseService.studentConnectionCount());
    }

    @Test
    void registerMultipleStudents() {
        sseService.registerStudent(1L);
        sseService.registerStudent(2L);
        sseService.registerStudent(1L);
        assertEquals(3, sseService.studentConnectionCount());
    }

    @Test
    void connectionCounts_initiallyZero() {
        assertEquals(0, sseService.boardConnectionCount());
        assertEquals(0, sseService.adminConnectionCount());
        assertEquals(0, sseService.studentConnectionCount());
    }

    @Test
    void sendToBoard_doesNotThrow() {
        sseService.registerBoard();
        assertDoesNotThrow(() ->
            sseService.sendToBoard("test", Map.of("data", "value")));
    }

    @Test
    void sendToAdmin_doesNotThrow() {
        sseService.registerAdmin();
        assertDoesNotThrow(() ->
            sseService.sendToAdmin("test", Map.of("data", "value")));
    }

    @Test
    void sendToStudent_doesNotThrow() {
        sseService.registerStudent(1L);
        assertDoesNotThrow(() ->
            sseService.sendToStudent(1L, "test", Map.of("data", "value")));
    }

    @Test
    void sendToAllStudents_doesNotThrow() {
        sseService.registerStudent(1L);
        sseService.registerStudent(2L);
        assertDoesNotThrow(() ->
            sseService.sendToAllStudents("test", Map.of("data", "value")));
    }

    @Test
    void sendToNonExistentStudent_doesNotThrow() {
        assertDoesNotThrow(() ->
            sseService.sendToStudent(999L, "test", Map.of("data", "value")));
    }

    @Test
    void heartbeat_doesNotThrow() {
        sseService.registerBoard();
        sseService.registerAdmin();
        sseService.registerStudent(1L);
        assertDoesNotThrow(() -> sseService.heartbeat());
    }
}
