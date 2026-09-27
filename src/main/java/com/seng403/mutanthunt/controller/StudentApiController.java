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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class StudentApiController {

    private static final String TOKEN_COOKIE = "player_token";

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
        String fullName = body.getOrDefault("fullName", "").trim();
        String studentNumber = body.getOrDefault("studentNumber", "").trim();
        String nickname = body.getOrDefault("nickname", "").trim();

        if (fullName.isEmpty()) {
            return bad("join.error.name.required");
        }
        if (studentNumber.isEmpty()) {
            return bad("join.error.studentId.required");
        }
        if (!studentNumber.matches("\\d+")) {
            return bad("join.error.studentId.numeric");
        }

        try {
            JoinResponse jr = gameService.join(fullName, studentNumber, nickname);
            setCookie(response, jr.token());
            return ResponseEntity.ok(jr);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return bad(e.getMessage());
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(HttpServletRequest request) {
        Optional<Participant> p = getParticipant(request);
        if (p.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "not_joined"));
        }
        if (p.get().isKicked()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "kicked"));
        }
        PlayerStatus ps = gameService.getPlayerStatus(p.get());
        return ResponseEntity.ok(ps);
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submit(@RequestBody Map<String, String> body,
                                     HttpServletRequest request) {
        Optional<Participant> p = getParticipant(request);
        if (p.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "not_joined"));
        }
        if (p.get().isKicked()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "kicked"));
        }

        try {
            SubmitResponse sr = gameService.submitTest(p.get(), body);
            return ResponseEntity.ok(sr);
        } catch (IllegalStateException e) {
            return bad(e.getMessage());
        }
    }

    @GetMapping(value = "/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qrCode(@RequestParam(defaultValue = "400") int size) {
        try {
            size = Math.min(Math.max(size, 100), 1000);
            byte[] png = qrCodeService.generateQrPng(size);
            return ResponseEntity.ok()
                .header("Cache-Control", "public, max-age=3600")
                .body(png);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── Helpers ───────────────────────────────────────

    private Optional<Participant> getParticipant(HttpServletRequest request) {
        if (request.getCookies() == null) return Optional.empty();
        for (Cookie c : request.getCookies()) {
            if (TOKEN_COOKIE.equals(c.getName())) {
                return participantRepo.findByToken(c.getValue())
                    .filter(p -> !p.isKicked());
            }
        }
        return Optional.empty();
    }

    private void setCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie(TOKEN_COOKIE, token);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(30 * 24 * 60 * 60); // 30 days
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    private ResponseEntity<Map<String, String>> bad(String messageKey) {
        return ResponseEntity.badRequest().body(Map.of("error", messageKey));
    }
}
