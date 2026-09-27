package com.seng403.mutanthunt.controller;

import com.seng403.mutanthunt.dto.*;
import com.seng403.mutanthunt.entity.Participant;
import com.seng403.mutanthunt.repository.ParticipantRepository;
import com.seng403.mutanthunt.service.GameService;
import com.seng403.mutanthunt.service.QrCodeService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Public API used by the student phone page and the projector board.
 * Never returns student numbers, and never mutant details before the round is REVEALED.
 */
@RestController
@RequestMapping("/api")
public class StudentApiController {

    static final String TOKEN_COOKIE = "player_token";

    private final GameService gameService;
    private final ParticipantRepository participantRepo;
    private final QrCodeService qrCodeService;

    public StudentApiController(GameService gameService,
                                 ParticipantRepository participantRepo,
                                 QrCodeService qrCodeService) {
        this.gameService = gameService;
        this.participantRepo = participantRepo;
        this.qrCodeService = qrCodeService;
    }

    @PostMapping("/join")
    public ResponseEntity<?> join(@RequestBody Map<String, String> body,
                                   HttpServletResponse response) {
        JoinResponse jr = gameService.join(
            body.getOrDefault("fullName", ""),
            body.getOrDefault("studentNumber", ""),
            body.getOrDefault("nickname", ""));
        setCookie(response, jr.token());
        // the token itself only travels in the httpOnly cookie
        return ResponseEntity.ok(Map.of("success", true, "name", jr.name()));
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(HttpServletRequest request) {
        Optional<Participant> p = findParticipant(request);
        if (p.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "not_joined"));
        }
        if (p.get().isKicked()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "kicked"));
        }
        return ResponseEntity.ok(gameService.getPlayerStatus(p.get()));
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submit(@RequestBody Map<String, String> body,
                                     HttpServletRequest request) {
        Optional<Participant> p = findParticipant(request);
        if (p.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "not_joined"));
        }
        if (p.get().isKicked()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "kicked"));
        }
        SubmitResponse sr = gameService.submitTest(p.get(), body);
        return ResponseEntity.ok(sr);
    }

    /** Data for the projector board (public: the board runs on the lecture PC without login). */
    @GetMapping("/board")
    public Map<String, Object> board() {
        Map<String, Object> result = new LinkedHashMap<>(gameService.getBoardPayload());
        result.put("joinUrl", qrCodeService.getJoinUrl());
        return result;
    }

    @GetMapping(value = "/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qrCode(@RequestParam(defaultValue = "400") int size) {
        try {
            size = Math.min(Math.max(size, 100), 1200);
            byte[] png = qrCodeService.generateQrPng(size);
            return ResponseEntity.ok()
                .header("Cache-Control", "public, max-age=3600")
                .body(png);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── Helpers ───────────────────────────────────────

    /** Participant for the request's cookie, including removed ones (caller decides). */
    private Optional<Participant> findParticipant(HttpServletRequest request) {
        return tokenFrom(request).flatMap(participantRepo::findByToken);
    }

    static Optional<String> tokenFrom(HttpServletRequest request) {
        if (request.getCookies() == null) return Optional.empty();
        for (Cookie c : request.getCookies()) {
            if (TOKEN_COOKIE.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return Optional.of(c.getValue());
            }
        }
        return Optional.empty();
    }

    private void setCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(TOKEN_COOKIE, token)
            .httpOnly(true)
            .secure(true)
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ofDays(30))
            .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
}
