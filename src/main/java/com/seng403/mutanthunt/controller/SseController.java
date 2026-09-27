package com.seng403.mutanthunt.controller;

import com.seng403.mutanthunt.entity.Participant;
import com.seng403.mutanthunt.repository.ParticipantRepository;
import com.seng403.mutanthunt.service.SseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
        Optional<Participant> p = StudentApiController.tokenFrom(request)
            .flatMap(participantRepo::findByToken)
            .filter(x -> !x.isKicked());
        if (p.isPresent()) {
            return sseService.registerStudent(p.get().getId());
        }
        SseEmitter emitter = new SseEmitter(0L);
        try { emitter.complete(); } catch (Exception ignored) {}
        return emitter;
    }

    /** Disable proxy buffering (nginx / Cloudflare) and caching of the event stream. */
    private void setSseHeaders(HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
    }
}
