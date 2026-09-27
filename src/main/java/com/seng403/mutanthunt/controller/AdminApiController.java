package com.seng403.mutanthunt.controller;

import com.seng403.mutanthunt.dto.*;
import com.seng403.mutanthunt.entity.*;
import com.seng403.mutanthunt.problem.ProblemRegistry;
import com.seng403.mutanthunt.repository.*;
import com.seng403.mutanthunt.service.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/api")
public class AdminApiController {

    private final AdminService adminService;
    private final GameService gameService;
    private final ExportService exportService;
    private final ProblemRegistry problemRegistry;
    private final SubmissionRepository submissionRepo;
    private final MutantResultRepository mutantResultRepo;
    private final ParticipantRepository participantRepo;

    public AdminApiController(AdminService adminService, GameService gameService,
                               ExportService exportService, ProblemRegistry problemRegistry,
                               SubmissionRepository submissionRepo,
                               MutantResultRepository mutantResultRepo,
                               ParticipantRepository participantRepo) {
        this.adminService = adminService;
        this.gameService = gameService;
        this.exportService = exportService;
        this.problemRegistry = problemRegistry;
        this.submissionRepo = submissionRepo;
        this.mutantResultRepo = mutantResultRepo;
        this.participantRepo = participantRepo;
    }

    // ── Sessions ──────────────────────────────────────

    @GetMapping("/sessions")
    public List<Map<String, Object>> getSessions() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (GameSession s : adminService.getActiveSessions()) {
            result.add(sessionToMap(s));
        }
        return result;
    }

    @GetMapping("/sessions/archived")
    public List<Map<String, Object>> getArchivedSessions() {
        return adminService.getArchivedSessions().stream()
            .map(this::sessionToMap).toList();
    }

    @PostMapping("/sessions")
    public Map<String, Object> createSession(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "New Session");
        return sessionToMap(adminService.createSession(name));
    }

    @PostMapping("/sessions/{id}/activate")
    public ResponseEntity<?> activateSession(@PathVariable Long id) {
        adminService.activateSession(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/sessions/{id}/archive")
    public ResponseEntity<?> archiveSession(@PathVariable Long id) {
        adminService.archiveSession(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<?> deleteSession(@PathVariable Long id) {
        adminService.deleteSession(id);
        return ResponseEntity.ok(Map.of("success", true));
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
        return adminService.getRounds(sessionId).stream()
            .map(this::roundToMap).toList();
    }

    @PostMapping("/sessions/{sessionId}/rounds")
    public Map<String, Object> createRound(@PathVariable Long sessionId,
                                            @RequestBody Map<String, String> body) {
        Round r = adminService.createRound(
            sessionId,
            body.getOrDefault("name", "Round"),
            body.getOrDefault("problemId", "indexOf"),
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

    @GetMapping("/sessions/{sessionId}/participants")
    public List<Map<String, Object>> getParticipants(@PathVariable Long sessionId) {
        List<Participant> participants = adminService.getParticipants(sessionId);
        GameSession session = adminService.getSession(sessionId).orElseThrow();

        return participants.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("fullName", p.getFullName());
            m.put("studentNumber", p.getStudentNumber());
            m.put("nickname", p.getNickname());
            m.put("anonymousName", p.getAnonymousName());
            m.put("displayName", gameService.resolveDisplayName(p, session.getAnonymityMode()));
            m.put("joinedAt", p.getJoinedAt().toEpochMilli());
            return m;
        }).toList();
    }

    @PostMapping("/participants/{id}/kick")
    public ResponseEntity<?> kickParticipant(@PathVariable Long id) {
        adminService.kickParticipant(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/participants/{id}/nickname")
    public ResponseEntity<?> updateNickname(@PathVariable Long id,
                                             @RequestBody Map<String, String> body) {
        adminService.updateNickname(id, body.get("nickname"));
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ── Submissions ───────────────────────────────────

    @GetMapping("/rounds/{roundId}/submissions")
    public List<Map<String, Object>> getSubmissions(@PathVariable Long roundId) {
        List<Submission> subs = adminService.getSubmissions(roundId);
        Map<Long, Participant> cache = new HashMap<>();

        return subs.stream().map(s -> {
            Participant p = cache.computeIfAbsent(s.getParticipantId(),
                id -> participantRepo.findById(id).orElse(null));
            Map<String, String> inputs = gameService.parseRawInputs(s.getRawInputsJson());

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("time", s.getCreatedAt().toEpochMilli());
            m.put("participantName", p != null ? p.getFullName() : "?");
            m.put("studentNumber", p != null ? p.getStudentNumber() : "?");
            m.put("list", inputs.getOrDefault("list", ""));
            m.put("target", inputs.getOrDefault("target", ""));
            m.put("expected", s.getExpectedValue());
            m.put("referenceResult", s.getReferenceResult());
            m.put("validity", s.getValidity().name());

            if (s.getValidity() == Validity.VALID) {
                List<MutantResult> results = mutantResultRepo.findBySubmissionId(s.getId());
                m.put("killedMutants", results.stream()
                    .filter(MutantResult::isKilled)
                    .map(MutantResult::getMutantId)
                    .toList());
            } else {
                m.put("killedMutants", List.of());
            }
            return m;
        }).toList();
    }

    // ── Analytics ─────────────────────────────────────

    @GetMapping("/rounds/{roundId}/analytics")
    public AnalyticsData getAnalytics(@PathVariable Long roundId) {
        return gameService.getAnalytics(roundId);
    }

    // ── Export ─────────────────────────────────────────

    @GetMapping("/sessions/{sessionId}/export/participants")
    public void exportParticipants(@PathVariable Long sessionId, HttpServletResponse response)
            throws Exception {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition",
            "attachment; filename=participants_session_" + sessionId + ".csv");
        exportService.exportParticipants(response.getWriter(), sessionId);
    }

    @GetMapping("/rounds/{roundId}/export/submissions")
    public void exportSubmissions(@PathVariable Long roundId, HttpServletResponse response)
            throws Exception {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition",
            "attachment; filename=submissions_round_" + roundId + ".csv");
        exportService.exportSubmissions(response.getWriter(), roundId);
    }

    // ── Board control ─────────────────────────────────

    @PostMapping("/board-command")
    public ResponseEntity<?> boardCommand(@RequestBody Map<String, String> body) {
        adminService.sendBoardCommand(body.getOrDefault("command", "SHOW_SCOREBOARD"));
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ── Problems ──────────────────────────────────────

    @GetMapping("/problems")
    public List<Map<String, String>> getProblems() {
        return problemRegistry.getAll().stream()
            .map(p -> Map.of("id", p.getId(), "title", p.getTitle()))
            .toList();
    }

    // ── Board data (initial load) ─────────────────────

    @GetMapping("/board-data")
    public ResponseEntity<?> getBoardData() {
        var session = gameService.getActiveSession();
        if (session.isEmpty()) {
            return ResponseEntity.ok(Map.of("hasSession", false));
        }
        BoardData bd = gameService.getBoardData(session.get().getId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hasSession", true);
        result.put("sessionName", bd.session().getName());
        result.put("anonymityMode", bd.session().getAnonymityMode().name());
        result.put("mutantPanelMode", bd.session().getMutantPanelMode().name());
        result.put("participantCount", bd.participantCount());
        if (bd.round() != null) {
            result.put("round", roundToMap(bd.round()));
            result.put("specification", bd.problem().getSpecification());
            result.put("scoreboard", bd.scoreboard());
            result.put("mutantKillCounts", bd.mutantKillCounts());
            result.put("mutantInfos", bd.mutantInfos());
        }
        return ResponseEntity.ok(result);
    }

    // ── Helpers ───────────────────────────────────────

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
        m.put("name", r.getName());
        m.put("problemId", r.getProblemId());
        m.put("testLimit", r.getTestLimit());
        m.put("timeLimitSeconds", r.getTimeLimitSeconds());
        m.put("status", r.getStatus().name());
        m.put("openedAt", r.getOpenedAt() != null ? r.getOpenedAt().toEpochMilli() : null);
        m.put("closedAt", r.getClosedAt() != null ? r.getClosedAt().toEpochMilli() : null);
        return m;
    }

    private Integer parseIntOrNull(String val) {
        if (val == null || val.isBlank()) return null;
        try { return Integer.parseInt(val.trim()); } catch (NumberFormatException e) { return null; }
    }
}
