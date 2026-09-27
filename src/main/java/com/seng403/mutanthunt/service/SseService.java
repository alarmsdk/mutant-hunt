package com.seng403.mutanthunt.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages Server-Sent Event emitters for students, board, and admin.
 */
@Service
public class SseService {

    private static final Logger log = LoggerFactory.getLogger(SseService.class);
    private static final long TIMEOUT = 0L; // no timeout – rely on heartbeat

    private final List<SseEmitter> boardEmitters = new CopyOnWriteArrayList<>();
    private final List<SseEmitter> adminEmitters = new CopyOnWriteArrayList<>();
    /** participantId → list of emitters (multi-device support) */
    private final Map<Long, List<SseEmitter>> studentEmitters = new java.util.concurrent.ConcurrentHashMap<>();

    // ── Registration ──────────────────────────────────

    public SseEmitter registerBoard() {
        return register(boardEmitters);
    }

    public SseEmitter registerAdmin() {
        return register(adminEmitters);
    }

    public SseEmitter registerStudent(Long participantId) {
        SseEmitter emitter = newEmitter();
        List<SseEmitter> list = studentEmitters.computeIfAbsent(participantId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable cleanup = () -> list.remove(emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
        return emitter;
    }

    private SseEmitter register(List<SseEmitter> list) {
        SseEmitter emitter = newEmitter();
        list.add(emitter);
        Runnable cleanup = () -> list.remove(emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
        return emitter;
    }

    /**
     * Emits an initial event right away so that proxies (Cloudflare Tunnel) forward the
     * response headers immediately instead of waiting for the first real update.
     */
    private SseEmitter newEmitter() {
        SseEmitter emitter = new SseEmitter(TIMEOUT);
        try {
            emitter.send(SseEmitter.event().name("connected").data("{}").reconnectTime(3000));
        } catch (IOException | IllegalStateException ignored) {
            // client already gone; cleanup callbacks will remove it
        }
        return emitter;
    }

    // ── Broadcasting ──────────────────────────────────

    public void sendToBoard(String eventName, Object data) {
        broadcast(boardEmitters, eventName, data);
    }

    public void sendToAdmin(String eventName, Object data) {
        broadcast(adminEmitters, eventName, data);
    }

    public void sendToStudent(Long participantId, String eventName, Object data) {
        List<SseEmitter> list = studentEmitters.get(participantId);
        if (list != null) {
            broadcast(list, eventName, data);
        }
    }

    public void sendToAllStudents(String eventName, Object data) {
        studentEmitters.values().forEach(list -> broadcast(list, eventName, data));
    }

    private void broadcast(List<SseEmitter> emitters, String eventName, Object data) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
            }
        }
    }

    // ── Heartbeat (every 15 seconds) ──────────────────

    @Scheduled(fixedRate = 15000)
    public void heartbeat() {
        String ping = "{\"ts\":" + System.currentTimeMillis() + "}";
        broadcast(boardEmitters, "heartbeat", ping);
        broadcast(adminEmitters, "heartbeat", ping);
        studentEmitters.values().forEach(list -> broadcast(list, "heartbeat", ping));
    }

    // ── Stats ─────────────────────────────────────────

    public int boardConnectionCount() { return boardEmitters.size(); }
    public int adminConnectionCount() { return adminEmitters.size(); }
    public int studentConnectionCount() {
        return studentEmitters.values().stream().mapToInt(List::size).sum();
    }
}
