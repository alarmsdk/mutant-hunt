package com.seng403.mutanthunt.service;

import com.seng403.mutanthunt.entity.*;
import com.seng403.mutanthunt.problem.Problem;
import com.seng403.mutanthunt.problem.ProblemRegistry;
import com.seng403.mutanthunt.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminService {

    public static final Set<String> BOARD_COMMANDS =
        Set.of("SHOW_QR", "SHOW_SCOREBOARD", "FULLSCREEN_SCORE");

    static final int MAX_TEST_LIMIT = 1000;
    static final int MAX_TIME_LIMIT_SECONDS = 24 * 3600;
    static final int MAX_RATE_LIMIT_MS = 60_000;
    static final int MAX_STUDENT_ID_LENGTH = 20;
    static final int MAX_LABEL_LENGTH = 100;

    private final SessionRepository sessionRepo;
    private final RoundRepository roundRepo;
    private final ParticipantRepository participantRepo;
    private final SubmissionRepository submissionRepo;
    private final MutantResultRepository mutantResultRepo;
    private final GameService gameService;
    private final SseService sseService;
    private final ProblemRegistry problemRegistry;

    @Value("${app.default-student-id-length}")
    private int defaultStudentIdLength;

    @Value("${app.default-rate-limit-ms}")
    private int defaultRateLimitMs;

    public AdminService(SessionRepository sessionRepo, RoundRepository roundRepo,
                        ParticipantRepository participantRepo, SubmissionRepository submissionRepo,
                        MutantResultRepository mutantResultRepo, GameService gameService,
                        SseService sseService, ProblemRegistry problemRegistry) {
        this.sessionRepo = sessionRepo;
        this.roundRepo = roundRepo;
        this.participantRepo = participantRepo;
        this.submissionRepo = submissionRepo;
        this.mutantResultRepo = mutantResultRepo;
        this.gameService = gameService;
        this.sseService = sseService;
        this.problemRegistry = problemRegistry;
    }

    // ── Session CRUD ──────────────────────────────────

    public List<GameSession> getActiveSessions() {
        return sessionRepo.findByArchivedFalseOrderByCreatedAtDesc();
    }

    public List<GameSession> getArchivedSessions() {
        return sessionRepo.findByArchivedTrueOrderByCreatedAtDesc();
    }

    public Optional<GameSession> getSession(Long id) {
        return sessionRepo.findById(id);
    }

    @Transactional
    public GameSession createSession(String name) {
        GameSession s = new GameSession(label(name, "New Session"));
        s.setStudentIdLength(defaultStudentIdLength);
        s.setSubmissionRateLimitMs(defaultRateLimitMs);
        return sessionRepo.save(s);
    }

    /** Makes the session the live one (the one students join and the board shows). */
    @Transactional
    public void activateSession(Long sessionId) {
        GameSession session = sessionRepo.findById(sessionId).orElseThrow();
        if (session.isArchived()) throw new IllegalStateException("admin.error.session.archived");
        sessionRepo.findByActiveTrue().ifPresent(s -> {
            s.setActive(false);
            sessionRepo.save(s);
        });
        session.setActive(true);
        sessionRepo.save(session);
        notifySessionChanged();
    }

    @Transactional
    public void archiveSession(Long sessionId) {
        GameSession session = sessionRepo.findById(sessionId).orElseThrow();
        boolean wasActive = session.isActive();
        session.setArchived(true);
        session.setActive(false);
        sessionRepo.save(session);
        if (wasActive) notifySessionChanged();
    }

    @Transactional
    public void unarchiveSession(Long sessionId) {
        GameSession session = sessionRepo.findById(sessionId).orElseThrow();
        session.setArchived(false);
        sessionRepo.save(session);
    }

    @Transactional
    public void deleteSession(Long sessionId) {
        GameSession session = sessionRepo.findById(sessionId).orElseThrow();
        boolean wasActive = session.isActive();
        List<Round> rounds = roundRepo.findBySessionIdOrderByCreatedAtDesc(sessionId);
        for (Round r : rounds) {
            deleteRoundData(r.getId());
        }
        roundRepo.deleteAll(rounds);
        // includes removed (kicked) participants, otherwise they would be orphaned
        participantRepo.deleteAll(participantRepo.findBySessionId(sessionId));
        sessionRepo.delete(session);
        if (wasActive) notifySessionChanged();
    }

    private void deleteRoundData(Long roundId) {
        mutantResultRepo.deleteByRoundId(roundId);
        submissionRepo.deleteAll(submissionRepo.findByRoundIdOrderByCreatedAtDesc(roundId));
    }

    private void notifySessionChanged() {
        AfterCommit.run(() -> {
            sseService.sendToBoard("session-changed", Map.of());
            sseService.sendToAllStudents("session-changed", Map.of());
        });
    }

    // ── Session Settings ──────────────────────────────

    /** Applies the given settings (all optional) and pushes them live to board and students. */
    @Transactional
    public GameSession updateSettings(Long sessionId, Map<String, String> settings) {
        GameSession session = sessionRepo.findById(sessionId).orElseThrow();

        if (settings.containsKey("anonymityMode"))
            session.setAnonymityMode(parseEnum(AnonymityMode.class, settings.get("anonymityMode")));
        if (settings.containsKey("joinOpen"))
            session.setJoinOpen(Boolean.parseBoolean(settings.get("joinOpen")));
        if (settings.containsKey("studentIdLength"))
            session.setStudentIdLength(parseRange(settings.get("studentIdLength"), 0, MAX_STUDENT_ID_LENGTH));
        if (settings.containsKey("mutantPanelMode"))
            session.setMutantPanelMode(parseEnum(MutantPanelMode.class, settings.get("mutantPanelMode")));
        if (settings.containsKey("submissionRateLimitMs"))
            session.setSubmissionRateLimitMs(parseRange(settings.get("submissionRateLimitMs"), 0, MAX_RATE_LIMIT_MS));
        if (settings.containsKey("name"))
            session.setName(label(settings.get("name"), session.getName()));

        sessionRepo.save(session);

        gameService.notifySettingsChanged(session);
        gameService.getVisibleRound(sessionId).ifPresent(gameService::notifyScoreboard);
        return session;
    }

    // ── Round management ──────────────────────────────

    public Round getRound(Long roundId) {
        return roundRepo.findById(roundId).orElseThrow();
    }

    public List<Round> getRounds(Long sessionId) {
        return roundRepo.findBySessionIdOrderByCreatedAtDesc(sessionId);
    }

    @Transactional
    public Round createRound(Long sessionId, String name, String problemId,
                             Integer testLimit, Integer timeLimitSeconds) {
        sessionRepo.findById(sessionId).orElseThrow();
        String pid = problemId == null || problemId.isBlank() ? problemRegistry.getDefault().getId() : problemId;
        if (problemRegistry.get(pid) == null) throw new IllegalArgumentException("admin.error.problem.unknown");
        Round r = new Round(sessionId, label(name, "Round"), pid);
        applyLimits(r, testLimit, timeLimitSeconds);
        return roundRepo.save(r);
    }

    /** Edit name, test limit and time limit; takes effect immediately for an OPEN round. */
    @Transactional
    public Round updateRound(Long roundId, String name, Integer testLimit, Integer timeLimitSeconds) {
        Round r = roundRepo.findById(roundId).orElseThrow();
        if (name != null) r.setName(label(name, r.getName()));
        applyLimits(r, testLimit, timeLimitSeconds);
        roundRepo.save(r);
        if (r.getStatus() == RoundStatus.OPEN) {
            gameService.notifyRoundState(r.getSessionId(), r);
        }
        return r;
    }

    private void applyLimits(Round r, Integer testLimit, Integer timeLimitSeconds) {
        if (testLimit != null && (testLimit < 0 || testLimit > MAX_TEST_LIMIT))
            throw new IllegalArgumentException("admin.error.testLimit.range");
        if (timeLimitSeconds != null && (timeLimitSeconds < 0 || timeLimitSeconds > MAX_TIME_LIMIT_SECONDS))
            throw new IllegalArgumentException("admin.error.timeLimit.range");
        r.setTestLimit(testLimit != null && testLimit > 0 ? testLimit : null);
        r.setTimeLimitSeconds(timeLimitSeconds != null && timeLimitSeconds > 0 ? timeLimitSeconds : null);
    }

    @Transactional
    public void deleteRound(Long roundId) {
        Round r = roundRepo.findById(roundId).orElseThrow();
        if (r.getStatus() != RoundStatus.DRAFT) throw new IllegalStateException("admin.error.round.notDraft");
        roundRepo.delete(r);
    }

    /** DRAFT → OPEN. Any other OPEN round of the session is closed first (only one may be open). */
    @Transactional
    public Round openRound(Long roundId) {
        Round r = roundRepo.findById(roundId).orElseThrow();
        if (r.getStatus() != RoundStatus.DRAFT) {
            throw new IllegalStateException("admin.error.round.openFromDraft");
        }

        roundRepo.findBySessionIdAndStatus(r.getSessionId(), RoundStatus.OPEN)
            .ifPresent(open -> {
                open.setStatus(RoundStatus.CLOSED);
                open.setClosedAt(Instant.now());
                roundRepo.save(open);
            });

        r.setStatus(RoundStatus.OPEN);
        r.setOpenedAt(Instant.now());
        roundRepo.save(r);

        gameService.notifyRoundState(r.getSessionId(), r);
        return r;
    }

    @Transactional
    public Round closeRound(Long roundId) {
        Round r = roundRepo.findById(roundId).orElseThrow();
        if (r.getStatus() != RoundStatus.OPEN) {
            throw new IllegalStateException("admin.error.round.closeFromOpen");
        }
        r.setStatus(RoundStatus.CLOSED);
        r.setClosedAt(Instant.now());
        roundRepo.save(r);

        gameService.notifyRoundState(r.getSessionId(), r);
        return r;
    }

    @Transactional
    public Round revealRound(Long roundId) {
        Round r = roundRepo.findById(roundId).orElseThrow();
        if (r.getStatus() != RoundStatus.CLOSED) {
            throw new IllegalStateException("admin.error.round.revealFromClosed");
        }
        r.setStatus(RoundStatus.REVEALED);
        roundRepo.save(r);

        gameService.notifyRoundState(r.getSessionId(), r);
        return r;
    }

    // ── Participant management ────────────────────────

    public List<Participant> getParticipants(Long sessionId) {
        return participantRepo.findBySessionIdAndKickedFalseOrderByJoinedAt(sessionId);
    }

    /** Participants with display name and score per round (for the admin table). */
    public List<Map<String, Object>> getParticipantRows(Long sessionId) {
        GameSession session = sessionRepo.findById(sessionId).orElseThrow();
        List<Round> rounds = roundsOldestFirst(sessionId);
        Map<Long, Map<Long, GameService.PlayerRoundStats>> statsByRound = new HashMap<>();
        for (Round r : rounds) statsByRound.put(r.getId(), gameService.computeRoundStats(r));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Participant p : getParticipants(sessionId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("fullName", p.getFullName());
            m.put("studentNumber", p.getStudentNumber());
            m.put("nickname", p.getNickname());
            m.put("anonymousName", p.getAnonymousName());
            m.put("displayName", gameService.resolveDisplayName(p, session.getAnonymityMode()));
            m.put("joinedAt", p.getJoinedAt().toEpochMilli());
            m.put("rejoinCount", p.getRejoinCount());
            List<Map<String, Object>> scores = new ArrayList<>();
            for (Round r : rounds) {
                GameService.PlayerRoundStats st = statsByRound.get(r.getId()).get(p.getId());
                Map<String, Object> sc = new LinkedHashMap<>();
                sc.put("roundId", r.getId());
                sc.put("score", st == null ? 0 : st.killed.size());
                sc.put("testsUsed", st == null ? 0 : st.testsUsed);
                scores.add(sc);
            }
            m.put("scores", scores);
            rows.add(m);
        }
        return rows;
    }

    public List<Round> roundsOldestFirst(Long sessionId) {
        List<Round> rounds = new ArrayList<>(roundRepo.findBySessionIdOrderByCreatedAtDesc(sessionId));
        Collections.reverse(rounds);
        return rounds;
    }

    @Transactional
    public void kickParticipant(Long participantId) {
        Participant p = participantRepo.findById(participantId).orElseThrow();
        p.setKicked(true);
        participantRepo.save(p);
        gameService.notifyParticipantCount(p.getSessionId());
        gameService.getVisibleRound(p.getSessionId()).ifPresent(gameService::notifyScoreboard);
        AfterCommit.run(() -> sseService.sendToStudent(participantId, "kicked", Map.of()));
    }

    /** Replace an inappropriate nickname (blank → falls back to the anonymous name on the board). */
    @Transactional
    public void updateNickname(Long participantId, String nickname) {
        Participant p = participantRepo.findById(participantId).orElseThrow();
        String nick = nickname == null ? null : nickname.strip();
        if (nick != null && nick.length() > GameService.MAX_NICKNAME_LENGTH) {
            throw new IllegalArgumentException("join.error.nickname.tooLong");
        }
        p.setNickname(nick == null || nick.isEmpty() ? null : nick);
        participantRepo.save(p);
        gameService.getVisibleRound(p.getSessionId()).ifPresent(gameService::notifyScoreboard);
    }

    // ── Submission queries ────────────────────────────

    public List<Submission> getSubmissions(Long roundId) {
        return submissionRepo.findByRoundIdOrderByCreatedAtDesc(roundId);
    }

    /**
     * Submission rows for the admin table, newest first, optionally filtered.
     * Two queries in total regardless of the number of rows.
     */
    public List<Map<String, Object>> getSubmissionRows(Long sessionId, Long roundId,
                                                       Long participantId, Validity validity) {
        List<Round> rounds = roundRepo.findBySessionIdOrderByCreatedAtDesc(sessionId);
        Map<Long, Round> roundById = rounds.stream().collect(Collectors.toMap(Round::getId, r -> r));
        List<Long> roundIds = roundId != null
            ? (roundById.containsKey(roundId) ? List.of(roundId) : List.of())
            : List.copyOf(roundById.keySet());
        if (roundIds.isEmpty()) return List.of();

        List<Submission> subs = submissionRepo.findByRoundIdInOrderByCreatedAtDesc(roundIds).stream()
            .filter(s -> participantId == null || participantId.equals(s.getParticipantId()))
            .filter(s -> validity == null || validity == s.getValidity())
            .toList();
        Map<Long, List<String>> killed = mutantResultRepo.findByRoundIds(roundIds).stream()
            .filter(MutantResult::isKilled)
            .collect(Collectors.groupingBy(MutantResult::getSubmissionId, TreeMap::new,
                Collectors.mapping(MutantResult::getMutantId, Collectors.toList())));
        Map<Long, Participant> participants = participantRepo.findBySessionId(sessionId).stream()
            .collect(Collectors.toMap(Participant::getId, p -> p));

        List<Map<String, Object>> rows = new ArrayList<>(subs.size());
        for (Submission s : subs) {
            Participant p = participants.get(s.getParticipantId());
            Map<String, String> inputs = gameService.parseRawInputs(s.getRawInputsJson());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("roundId", s.getRoundId());
            m.put("roundName", roundById.get(s.getRoundId()).getName());
            m.put("time", s.getCreatedAt().toEpochMilli());
            m.put("participantId", s.getParticipantId());
            m.put("participantName", p != null ? p.getFullName() : s.getParticipantName());
            m.put("studentNumber", p != null ? p.getStudentNumber() : s.getStudentNumber());
            m.put("list", inputs.getOrDefault("list", ""));
            m.put("target", inputs.getOrDefault("target", ""));
            m.put("expected", s.getExpectedValue());
            m.put("referenceResult", s.getReferenceResult());
            m.put("validity", s.getValidity().name());
            m.put("killedMutants", killed.getOrDefault(s.getId(), List.of()).stream().sorted().toList());
            rows.add(m);
        }
        return rows;
    }

    // ── Board commands ────────────────────────────────

    public void sendBoardCommand(String command) {
        if (!BOARD_COMMANDS.contains(command)) throw new IllegalArgumentException("admin.error.board.command");
        sseService.sendToBoard("board-command", Map.of("command", command));
    }

    // ── Helpers ───────────────────────────────────────

    public Problem problemOf(Round r) {
        return problemRegistry.get(r.getProblemId());
    }

    private static String label(String value, String fallback) {
        String v = value == null ? "" : value.strip();
        if (v.isEmpty()) return fallback;
        return v.length() > MAX_LABEL_LENGTH ? v.substring(0, MAX_LABEL_LENGTH) : v;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("admin.error.settings.invalid");
        }
    }

    private static int parseRange(String value, int min, int max) {
        try {
            int v = Integer.parseInt(value.trim());
            if (v < min || v > max) throw new IllegalArgumentException("admin.error.settings.invalid");
            return v;
        } catch (NumberFormatException | NullPointerException e) {
            throw new IllegalArgumentException("admin.error.settings.invalid");
        }
    }
}
