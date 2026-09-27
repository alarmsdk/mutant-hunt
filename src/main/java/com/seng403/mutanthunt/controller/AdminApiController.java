package com.seng403.mutanthunt.controller;

import com.seng403.mutanthunt.dto.AnalyticsData;
import com.seng403.mutanthunt.entity.*;
import com.seng403.mutanthunt.problem.ProblemRegistry;
import com.seng403.mutanthunt.service.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/admin/api")
public class AdminApiController {

    private final AdminService adminService;
    private final GameService gameService;
    private final ExportService exportService;
    private final ProblemRegistry problemRegistry;

    public AdminApiController(AdminService adminService, GameService gameService,
                               ExportService exportService, ProblemRegistry problemRegistry) {
        this.adminService = adminService;
        this.gameService = gameService;
        this.exportService = exportService;
        this.problemRegistry = problemRegistry;
    }

    // ── Sessions ──────────────────────────────────────

    @GetMapping("/sessions")
    public List<Map<String, Object>> getSessions() {
        return adminService.getActiveSessions().stream().map(this::sessionToMap).toList();
    }

    @GetMapping("/sessions/archived")
    public List<Map<String, Object>> getArchivedSessions() {
        return adminService.getArchivedSessions().stream().map(this::sessionToMap).toList();
    }

    @PostMapping("/sessions")
    public Map<String, Object> createSession(@RequestBody Map<String, String> body) {
        return sessionToMap(adminService.createSession(body.get("name")));
    }

    @PostMapping("/sessions/{id}/activate")
    public ResponseEntity<?> activateSession(@PathVariable Long id) {
        adminService.activateSession(id);
        return ok();
    }

    @PostMapping("/sessions/{id}/archive")
    public ResponseEntity<?> archiveSession(@PathVariable Long id) {
        adminService.archiveSession(id);
        return ok();
    }

    @PostMapping("/sessions/{id}/unarchive")
    public ResponseEntity<?> unarchiveSession(@PathVariable Long id) {
        adminService.unarchiveSession(id);
        return ok();
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<?> deleteSession(@PathVariable Long id) {
        adminService.deleteSession(id);
        return ok();
    }

    // ── Settings ──────────────────────────────────────

    @GetMapping("/sessions/{id}/settings")
    public Map<String, Object> getSettings(@PathVariable Long id) {
        return sessionToMap(adminService.getSession(id).orElseThrow());
    }

    @PostMapping("/sessions/{id}/settings")
    public Map<String, Object> updateSettings(@PathVariable Long id,
                                               @RequestBody Map<String, String> settings) {
        return sessionToMap(adminService.updateSettings(id, settings));
    }

    // ── Rounds ────────────────────────────────────────

    @GetMapping("/sessions/{sessionId}/rounds")
    public List<Map<String, Object>> getRounds(@PathVariable Long sessionId) {
        return adminService.getRounds(sessionId).stream().map(this::roundToMap).toList();
    }

    @PostMapping("/sessions/{sessionId}/rounds")
    public Map<String, Object> createRound(@PathVariable Long sessionId,
                                            @RequestBody Map<String, String> body) {
        Round r = adminService.createRound(
            sessionId,
            body.get("name"),
            body.get("problemId"),
            parseIntOrNull(body.get("testLimit")),
            parseIntOrNull(body.get("timeLimitSeconds"))
        );
        return roundToMap(r);
    }

    @PutMapping("/rounds/{id}")
    public Map<String, Object> updateRound(@PathVariable Long id,
                                            @RequestBody Map<String, String> body) {
        Round r = adminService.updateRound(id,
            body.get("name"),
            parseIntOrNull(body.get("testLimit")),
            parseIntOrNull(body.get("timeLimitSeconds"))
        );
        return roundToMap(r);
    }

    @DeleteMapping("/rounds/{id}")
    public ResponseEntity<?> deleteRound(@PathVariable Long id) {
        adminService.deleteRound(id);
        return ok();
    }

    @PostMapping("/rounds/{id}/open")
    public Map<String, Object> openRound(@PathVariable Long id) {
        return roundToMap(adminService.openRound(id));
    }

    @PostMapping("/rounds/{id}/close")
    public Map<String, Object> closeRound(@PathVariable Long id) {
        return roundToMap(adminService.closeRound(id));
    }

    @PostMapping("/rounds/{id}/reveal")
    public Map<String, Object> revealRound(@PathVariable Long id) {
        return roundToMap(adminService.revealRound(id));
    }

    // ── Participants ──────────────────────────────────

    /** Name, number, board name, join time, rejoin count and score per round. */
    @GetMapping("/sessions/{sessionId}/participants")
    public Map<String, Object> getParticipants(@PathVariable Long sessionId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rounds", adminService.roundsOldestFirst(sessionId).stream()
            .map(r -> Map.of("id", r.getId(), "name", r.getName(),
                "totalMutants", adminService.problemOf(r).getMutants().size()))
            .toList());
        result.put("participants", adminService.getParticipantRows(sessionId));
        return result;
    }

    @PostMapping("/participants/{id}/kick")
    public ResponseEntity<?> kickParticipant(@PathVariable Long id) {
        adminService.kickParticipant(id);
        return ok();
    }

    @PostMapping("/participants/{id}/nickname")
    public ResponseEntity<?> updateNickname(@PathVariable Long id,
                                             @RequestBody Map<String, String> body) {
        adminService.updateNickname(id, body.get("nickname"));
        return ok();
    }

    // ── Submissions ───────────────────────────────────

    /** All submissions of a session, filterable by round, student and validity. */
    @GetMapping("/sessions/{sessionId}/submissions")
    public List<Map<String, Object>> getSessionSubmissions(@PathVariable Long sessionId,
                                                           @RequestParam(required = false) Long roundId,
                                                           @RequestParam(required = false) Long participantId,
                                                           @RequestParam(required = false) Validity validity) {
        return adminService.getSubmissionRows(sessionId, roundId, participantId, validity);
    }

    @GetMapping("/rounds/{roundId}/submissions")
    public List<Map<String, Object>> getSubmissions(@PathVariable Long roundId) {
        Round r = adminService.getRound(roundId);
        return adminService.getSubmissionRows(r.getSessionId(), roundId, null, null);
    }

    // ── Analytics ─────────────────────────────────────

    @GetMapping("/rounds/{roundId}/analytics")
    public AnalyticsData getAnalytics(@PathVariable Long roundId) {
        return gameService.getAnalytics(roundId);
    }

    // ── Export (CSV, UTF-8 with BOM) ──────────────────

    @GetMapping("/sessions/{sessionId}/export/participants")
    public void exportParticipants(@PathVariable Long sessionId, HttpServletResponse response) throws IOException {
        exportService.exportParticipants(csv(response, "participants_session_" + sessionId), sessionId);
    }

    @GetMapping("/sessions/{sessionId}/export/submissions")
    public void exportSessionSubmissions(@PathVariable Long sessionId, HttpServletResponse response) throws IOException {
        exportService.exportSessionSubmissions(csv(response, "submissions_session_" + sessionId), sessionId);
    }

    @GetMapping("/rounds/{roundId}/export/participants")
    public void exportRoundParticipants(@PathVariable Long roundId, HttpServletResponse response) throws IOException {
        exportService.exportParticipantsForRound(csv(response, "participants_round_" + roundId), roundId);
    }

    @GetMapping("/rounds/{roundId}/export/submissions")
    public void exportSubmissions(@PathVariable Long roundId, HttpServletResponse response) throws IOException {
        exportService.exportSubmissions(csv(response, "submissions_round_" + roundId), roundId);
    }

    private PrintWriter csv(HttpServletResponse response, String baseName) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + baseName + ".csv\"");
        return new PrintWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8));
    }

    // ── Board control ─────────────────────────────────

    @PostMapping("/board-command")
    public ResponseEntity<?> boardCommand(@RequestBody Map<String, String> body) {
        adminService.sendBoardCommand(body.getOrDefault("command", "SHOW_SCOREBOARD"));
        return ok();
    }

    /** Same payload as the public board API (kept for the admin preview). */
    @GetMapping("/board-data")
    public Map<String, Object> getBoardData() {
        return gameService.getBoardPayload();
    }

    // ── Problems ──────────────────────────────────────

    @GetMapping("/problems")
    public List<Map<String, String>> getProblems() {
        return problemRegistry.getAll().stream()
            .map(p -> Map.of("id", p.getId(), "title", p.getTitle()))
            .toList();
    }

    // ── Helpers ───────────────────────────────────────

    private static ResponseEntity<Map<String, Object>> ok() {
        return ResponseEntity.ok(Map.of("success", true));
    }

    private Map<String, Object> sessionToMap(GameSession s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("name", s.getName());
        m.put("active", s.isActive());
        m.put("archived", s.isArchived());
        m.put("anonymityMode", s.getAnonymityMode().name());
        m.put("joinOpen", s.isJoinOpen());
        m.put("studentIdLength", s.getStudentIdLength());
        m.put("mutantPanelMode", s.getMutantPanelMode().name());
        m.put("submissionRateLimitMs", s.getSubmissionRateLimitMs());
        m.put("createdAt", s.getCreatedAt().toEpochMilli());
        return m;
    }

    private Map<String, Object> roundToMap(Round r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("sessionId", r.getSessionId());
        m.put("name", r.getName());
        m.put("problemId", r.getProblemId());
        m.put("testLimit", r.getTestLimit());
        m.put("timeLimitSeconds", r.getTimeLimitSeconds());
        m.put("status", r.getStatus().name());
        m.put("openedAt", r.getOpenedAt() != null ? r.getOpenedAt().toEpochMilli() : null);
        m.put("closedAt", r.getClosedAt() != null ? r.getClosedAt().toEpochMilli() : null);
        m.put("deadline", r.getDeadline() != null && r.getStatus() == RoundStatus.OPEN
            ? r.getDeadline().toEpochMilli() : null);
        return m;
    }

    private Integer parseIntOrNull(String val) {
        if (val == null || val.isBlank()) return null;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("admin.error.number");
        }
    }
}
