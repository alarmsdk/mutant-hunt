package com.seng403.mutanthunt.controller;

import com.seng403.mutanthunt.service.SseService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import com.seng403.mutanthunt.entity.Participant;
import com.seng403.mutanthunt.repository.ParticipantRepository;

import java.util.Optional;

@RestController
@RequestMapping("/sse")
public class SseController {

    private final SseService sseService;
    private final ParticipantRepository participantRepo;

    public SseController(SseService sseService, ParticipantRepository participantRepo) {
        this.sseService = sseService;
        this.participantRepo = participantRepo;
    }

    @GetMapping(value = "/board", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter boardStream(HttpServletResponse response) {
        setSseHeaders(response);
        return sseService.registerBoard();
    }

    @GetMapping(value = "/admin", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter adminStream(HttpServletResponse response) {
        setSseHeaders(response);
        return sseService.registerAdmin();
    }

    @GetMapping(value = "/student", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter studentStream(HttpServletRequest request, HttpServletResponse response) {
        setSseHeaders(response);
        Optional<Participant> p = getParticipant(request);
        if (p.isPresent()) {
            return sseService.registerStudent(p.get().getId());
        }
        SseEmitter emitter = new SseEmitter(0L);
        try { emitter.complete(); } catch (Exception ignored) {}
        return emitter;
    }

    private void setSseHeaders(HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache, no-store");
        response.setHeader("Connection", "keep-alive");
    }

    private Optional<Participant> getParticipant(HttpServletRequest request) {
        if (request.getCookies() == null) return Optional.empty();
        for (Cookie c : request.getCookies()) {
            if ("player_token".equals(c.getName())) {
                return participantRepo.findByToken(c.getValue());
            }
        }
        return Optional.empty();
    }
}
