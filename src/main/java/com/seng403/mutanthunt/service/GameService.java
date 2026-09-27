package com.seng403.mutanthunt.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seng403.mutanthunt.dto.*;
import com.seng403.mutanthunt.entity.*;
import com.seng403.mutanthunt.problem.*;
import com.seng403.mutanthunt.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class GameService {

    private static final Logger log = LoggerFactory.getLogger(GameService.class);

    static final int MAX_NAME_LENGTH = 80;
    static final int MAX_NICKNAME_LENGTH = 24;
    static final int MAX_STUDENT_NUMBER_LENGTH = 20;
    /** Submissions arriving this long after the countdown hit zero are still accepted (network lag). */
    static final Duration DEADLINE_GRACE = Duration.ofSeconds(2);

    private static final List<RoundStatus> VISIBLE_STATUSES =
        List.of(RoundStatus.OPEN, RoundStatus.CLOSED, RoundStatus.REVEALED);

    private final SessionRepository sessionRepo;
    private final RoundRepository roundRepo;
    private final ParticipantRepository participantRepo;
    private final SubmissionRepository submissionRepo;
    private final MutantResultRepository mutantResultRepo;
    private final ProblemRegistry problemRegistry;
    private final AnonymousNameGenerator nameGenerator;
    private final SseService sseService;
    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    /** Rounds whose scoreboard changed since the last broadcast (coalesces bursts of submissions). */
    private final Set<Long> dirtyRounds = ConcurrentHashMap.newKeySet();

    public GameService(SessionRepository sessionRepo, RoundRepository roundRepo,
                       ParticipantRepository participantRepo, SubmissionRepository submissionRepo,
                       MutantResultRepository mutantResultRepo, ProblemRegistry problemRegistry,
                       AnonymousNameGenerator nameGenerator, SseService sseService,
                       RateLimitService rateLimitService, ObjectMapper objectMapper) {
        this.sessionRepo = sessionRepo;
        this.roundRepo = roundRepo;
        this.participantRepo = participantRepo;
        this.submissionRepo = submissionRepo;
        this.mutantResultRepo = mutantResultRepo;
        this.problemRegistry = problemRegistry;
        this.nameGenerator = nameGenerator;
        this.sseService = sseService;
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
    }

    // ── Session queries ───────────────────────────────

    public Optional<GameSession> getActiveSession() {
        return sessionRepo.findByActiveTrue();
    }

    // ── Join ──────────────────────────────────────────

    @Transactional
    public JoinResponse join(String fullName, String studentNumber, String nickname) {
        fullName = fullName == null ? "" : fullName.strip().replaceAll("\\s+", " ");
        studentNumber = studentNumber == null ? "" : studentNumber.strip();
        nickname = nickname == null ? "" : nickname.strip().replaceAll("\\s+", " ");

        if (fullName.isEmpty()) throw new IllegalArgumentException("join.error.name.required");
        if (fullName.length() > MAX_NAME_LENGTH) throw new IllegalArgumentException("join.error.name.tooLong");
        if (studentNumber.isEmpty()) throw new IllegalArgumentException("join.error.studentId.required");
        if (!studentNumber.matches("\\d+")) throw new IllegalArgumentException("join.error.studentId.numeric");
        if (studentNumber.length() > MAX_STUDENT_NUMBER_LENGTH) {
            throw new IllegalArgumentException("join.error.studentId.tooLong");
        }
        if (nickname.length() > MAX_NICKNAME_LENGTH) throw new IllegalArgumentException("join.error.nickname.tooLong");

        GameSession session = sessionRepo.findByActiveTrue()
            .orElseThrow(() -> new IllegalStateException("join.error.session.none"));

        Optional<Participant> existing = participantRepo
            .findBySessionIdAndStudentNumber(session.getId(), studentNumber);

        if (existing.isPresent()) {
            // Same student number again (page reload in a private tab, second device, …):
            // bind to the same player and keep the existing token so other devices stay logged in.
            Participant p = existing.get();
            if (p.isKicked()) throw new IllegalStateException("join.error.kicked");
            p.incrementRejoinCount();
            if ((p.getNickname() == null || p.getNickname().isBlank()) && !nickname.isEmpty()) {
                p.setNickname(nickname);
            }
            participantRepo.save(p);
            log.warn("Student number {} joined again (rejoin #{}) as '{}'; bound to existing participant {} ('{}')",
                studentNumber, p.getRejoinCount(), fullName, p.getId(), p.getFullName());
            Map<String, Object> event = Map.of(
                "participantId", p.getId(), "fullName", p.getFullName(),
                "studentNumber", p.getStudentNumber(), "rejoinCount", p.getRejoinCount());
            AfterCommit.run(() -> sseService.sendToAdmin("participant-rejoined", event));
            return new JoinResponse(true, p.getToken(), p.getFullName());
        }

        if (!session.isJoinOpen()) throw new IllegalStateException("join.error.session.closed");

        int reqLen = session.getStudentIdLength();
        if (reqLen > 0 && studentNumber.length() != reqLen) {
            throw new IllegalArgumentException("join.error.studentId.length");
        }

        Participant p = new Participant(session.getId(), studentNumber, fullName, UUID.randomUUID().toString());
        if (!nickname.isEmpty()) p.setNickname(nickname);
        participantRepo.save(p);
        p.setAnonymousName(nameGenerator.generate(p.getId()));
        log.info("New participant joined: {} ({})", fullName, studentNumber);
        notifyParticipantCount(session.getId());
        return new JoinResponse(true, p.getToken(), p.getFullName());
    }

    // ── Submit test ───────────────────────────────────

    @Transactional
    public SubmitResponse submitTest(Participant participant, Map<String, String> body) {
        // Row lock: two taps on "Submit" from the same student are serialised, so the
        // test limit cannot be exceeded by concurrent requests.
        Participant p = participantRepo.findByIdForUpdate(participant.getId())
            .orElseThrow(() -> new IllegalStateException("join.error.kicked"));
        if (p.isKicked()) throw new IllegalStateException("join.error.kicked");

        GameSession session = sessionRepo.findById(p.getSessionId()).orElseThrow();

        // find open round (check BEFORE rate limit so "closed" error is clear)
        Round round = roundRepo.findBySessionIdAndStatus(session.getId(), RoundStatus.OPEN)
            .orElseThrow(() -> new IllegalStateException("test.round.closed"));

        Instant deadline = round.getDeadline();
        if (deadline != null && Instant.now().isAfter(deadline.plus(DEADLINE_GRACE))) {
            throw new IllegalStateException("test.round.timeUp");
        }

        if (!rateLimitService.isSubmissionAllowed(p.getId(), session.getSubmissionRateLimitMs())) {
            return SubmitResponse.error("test.rate.limited");
        }

        if (!round.isUnlimited()) {
            long used = submissionRepo.countByRoundIdAndParticipantId(round.getId(), p.getId());
            if (used >= round.getTestLimit()) {
                return SubmitResponse.error("test.limit.reached");
            }
        }

        Problem problem = problemRegistry.get(round.getProblemId());

        // keep only declared fields, then validate (list length, int range, …)
        Map<String, String> inputs;
        String expected;
        try {
            inputs = problem.sanitizeInputs(body == null ? Map.of() : body);
            problem.validate(inputs);
            expected = problem.getExpected(inputs);
        } catch (IllegalArgumentException e) {
            return SubmitResponse.error(e.getMessage());
        }

        String referenceResult = problem.executeReference(inputs);
        Validity validity = referenceResult.equals(expected) ? Validity.VALID : Validity.INVALID;

        Submission sub = new Submission(
            round.getId(), p.getId(), toJson(inputs), expected, referenceResult, validity
        ).withSnapshot(session.getId(), p.getFullName(), p.getStudentNumber(),
            resolveDisplayName(p, session.getAnonymityMode()));
        submissionRepo.save(sub);

        // INVALID tests are stored for analysis but never run against the mutants
        List<String> killedNow = new ArrayList<>();
        if (validity == Validity.VALID) {
            List<MutantResult> results = new ArrayList<>();
            for (MutantDefinition md : problem.getMutants()) {
                ExecutionResult result = problem.executeMutant(md.id(), inputs);
                boolean killed = result.isException() || !result.value().equals(expected);
                results.add(new MutantResult(sub.getId(), md.id(),
                    result.isException() ? null : result.value(),
                    result.isException() ? result.exception() : null,
                    killed));
                if (killed) killedNow.add(md.id());
            }
            mutantResultRepo.saveAll(results);
        }

        ScoreInfo score = computeScore(round.getId(), p.getId(), problem);
        long totalUsed = submissionRepo.countByRoundIdAndParticipantId(round.getId(), p.getId());
        long remaining = round.isUnlimited() ? -1 : Math.max(0, round.getTestLimit() - totalUsed);

        Map<String, Object> adminEvent = submissionToAdminMap(sub, inputs, killedNow);
        Long roundId = round.getId();
        AfterCommit.run(() -> {
            dirtyRounds.add(roundId);
            sseService.sendToAdmin("submission", adminEvent);
        });

        String message = validity == Validity.INVALID ? "test.result.invalid.oracle" : null;
        return new SubmitResponse(true, validity.name(), message,
            score.killed(), score.total(), remaining, sub.getId());
    }

    private String toJson(Map<String, String> inputs) {
        try {
            return objectMapper.writeValueAsString(inputs);
        } catch (Exception e) {
            return inputs.toString();
        }
    }

    // ── Score computation ─────────────────────────────

    /** Distinct mutants killed by the participant's VALID tests in the round. */
    public ScoreInfo computeScore(Long roundId, Long participantId, Problem problem) {
        List<Long> validIds = submissionRepo
            .findByRoundIdAndParticipantIdOrderByCreatedAt(roundId, participantId).stream()
            .filter(s -> s.getValidity() == Validity.VALID)
            .map(Submission::getId)
            .toList();
        Set<String> killed = validIds.isEmpty() ? Set.of()
            : mutantResultRepo.findBySubmissionIdIn(validIds).stream()
                .filter(MutantResult::isKilled)
                .map(MutantResult::getMutantId)
                .collect(Collectors.toSet());
        return new ScoreInfo(killed.size(), problem.getMutants().size());
    }

    /** Per-participant round statistics computed from two bulk queries (no N+1). */
    public Map<Long, PlayerRoundStats> computeRoundStats(Round round) {
        List<Submission> subs = submissionRepo.findByRoundIdOrderByCreatedAtAscIdAsc(round.getId());
        Map<Long, List<String>> killsBySubmission = new HashMap<>();
        for (MutantResult mr : mutantResultRepo.findKilledByRound(round.getId())) {
            killsBySubmission.computeIfAbsent(mr.getSubmissionId(), k -> new ArrayList<>()).add(mr.getMutantId());
        }

        Map<Long, PlayerRoundStats> stats = new LinkedHashMap<>();
        for (Submission s : subs) {
            PlayerRoundStats st = stats.computeIfAbsent(s.getParticipantId(), PlayerRoundStats::new);
            st.testsUsed++;
            if (s.getValidity() != Validity.VALID) continue;
            st.validTests++;
            boolean newKill = false;
            for (String m : killsBySubmission.getOrDefault(s.getId(), List.of())) {
                newKill |= st.killed.add(m);
            }
            if (newKill) st.lastKillTime = s.getCreatedAt();
        }
        return stats;
    }

    public static final class PlayerRoundStats {
        public final long participantId;
        public final Set<String> killed = new TreeSet<>();
        public long testsUsed;
        public long validTests;
        public Instant lastKillTime;

        PlayerRoundStats(long participantId) { this.participantId = participantId; }
    }

    // ── Scoreboard ────────────────────────────────────

    /**
     * Everyone who submitted at least one test, ranked by
     * score DESC → tests used ASC → time the score was reached ASC.
     */
    public List<ScoreboardEntry> getScoreboard(Long sessionId, Round round) {
        Problem problem = problemRegistry.get(round.getProblemId());
        GameSession session = sessionRepo.findById(sessionId).orElseThrow();
        Map<Long, Participant> participants = participantRepo
            .findBySessionIdAndKickedFalseOrderByJoinedAt(sessionId).stream()
            .collect(Collectors.toMap(Participant::getId, p -> p));

        List<ScoreboardEntry> entries = new ArrayList<>();
        for (PlayerRoundStats st : computeRoundStats(round).values()) {
            Participant p = participants.get(st.participantId);
            if (p == null) continue; // removed by the instructor
            entries.add(new ScoreboardEntry(
                p.getId(), resolveDisplayName(p, session.getAnonymityMode()),
                st.killed.size(), problem.getMutants().size(),
                st.testsUsed, st.validTests, st.lastKillTime));
        }

        entries.sort(Comparator
            .comparingInt(ScoreboardEntry::score).reversed()
            .thenComparingLong(ScoreboardEntry::testsUsed)
            .thenComparing(ScoreboardEntry::lastScoreTime,
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingLong(ScoreboardEntry::participantId));
        return entries;
    }

    // ── Display name resolution ───────────────────────

    /** Name shown on the board. Never includes the student number (only a masked form). */
    public String resolveDisplayName(Participant p, AnonymityMode mode) {
        return switch (mode) {
            case FULL_NAME -> p.getFullName();
            case NICKNAME -> (p.getNickname() != null && !p.getNickname().isBlank())
                ? p.getNickname() : p.getAnonymousName();
            case ANONYMOUS -> p.getAnonymousName();
            case NUMBER_MASKED -> p.getMaskedNumber();
        };
    }

    // ── Player status (for student page) ──────────────

    public PlayerStatus getPlayerStatus(Participant participant) {
        GameSession session = sessionRepo.findById(participant.getSessionId()).orElseThrow();
        String displayName = resolveDisplayName(participant, session.getAnonymityMode());
        long participantCount = participantRepo.countBySessionIdAndKickedFalse(session.getId());

        Optional<Round> visible = getVisibleRound(session.getId());
        if (visible.isEmpty()) {
            return PlayerStatus.waiting(participant.getFullName(), displayName, participantCount);
        }
        Round round = visible.get();
        boolean revealed = round.getStatus() == RoundStatus.REVEALED;

        Problem problem = problemRegistry.get(round.getProblemId());
        List<Submission> subs = submissionRepo
            .findByRoundIdAndParticipantIdOrderByCreatedAt(round.getId(), participant.getId());
        Map<Long, List<MutantResult>> results = subs.isEmpty() ? Map.of()
            : mutantResultRepo.findBySubmissionIdIn(subs.stream().map(Submission::getId).toList()).stream()
                .collect(Collectors.groupingBy(MutantResult::getSubmissionId));

        Set<String> killed = results.values().stream().flatMap(List::stream)
            .filter(MutantResult::isKilled).map(MutantResult::getMutantId)
            .collect(Collectors.toSet());

        long totalUsed = subs.size();
        long remaining = round.isUnlimited() ? -1 : Math.max(0, round.getTestLimit() - totalUsed);

        List<SubmissionInfo> submissions = subs.stream().map(s -> {
            Map<String, String> inputs = parseRawInputs(s.getRawInputsJson());
            // which mutants a test killed is secret until the instructor reveals the round
            List<MutantKillInfo> mutantResults = revealed && s.getValidity() == Validity.VALID
                ? results.getOrDefault(s.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(MutantResult::getMutantId))
                    .map(mr -> new MutantKillInfo(mr.getMutantId(), mr.isKilled()))
                    .toList()
                : null;
            return new SubmissionInfo(s.getId(),
                inputs.getOrDefault("list", ""), inputs.getOrDefault("target", ""),
                inputs.getOrDefault("expected", ""), s.getValidity().name(),
                revealed ? s.getReferenceResult() : null,
                mutantResults, s.getCreatedAt().toEpochMilli());
        }).toList();

        return new PlayerStatus(
            participant.getFullName(), displayName,
            round.getStatus().name(), round.getName(),
            problem.getSpecification(), problem.getFunctionInputFields(), problem.getExpectedField(),
            killed.size(), problem.getMutants().size(),
            totalUsed, remaining,
            round.getTimeLimitSeconds(), remainingMs(round),
            submissions,
            revealed ? buildMutantInfos(problem) : null,
            revealed ? problem.getReferenceCode() : null,
            participantCount,
            session.getAnonymityMode() == AnonymityMode.NICKNAME
        );
    }

    /** Milliseconds left on an OPEN round's timer, or null if there is no running timer. */
    static Long remainingMs(Round round) {
        Instant deadline = round.getDeadline();
        if (deadline == null || round.getStatus() != RoundStatus.OPEN) return null;
        return Math.max(0, Duration.between(Instant.now(), deadline).toMillis());
    }

    private List<MutantInfo> buildMutantInfos(Problem problem) {
        return problem.getMutants().stream()
            .map(md -> new MutantInfo(md.id(), md.shortName(), md.description(), md.code()))
            .toList();
    }

    public Map<String, String> parseRawInputs(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    // ── Round queries ─────────────────────────────────

    public Optional<Round> getOpenRound(Long sessionId) {
        return roundRepo.findBySessionIdAndStatus(sessionId, RoundStatus.OPEN);
    }

    /**
     * The round students and the board should see: the OPEN round if there is one,
     * otherwise the most recently opened CLOSED/REVEALED round.
     */
    public Optional<Round> getVisibleRound(Long sessionId) {
        return roundRepo.findBySessionIdAndStatusIn(sessionId, VISIBLE_STATUSES).stream()
            .max(Comparator
                .comparing((Round r) -> r.getStatus() == RoundStatus.OPEN)
                .thenComparing(Round::getOpenedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(Round::getId));
    }

    // ── Notifications (all sent after commit) ─────────

    public void notifyParticipantCount(Long sessionId) {
        AfterCommit.run(() -> {
            long count = participantRepo.countBySessionIdAndKickedFalse(sessionId);
            sseService.sendToBoard("participants", Map.of("count", count));
            sseService.sendToAdmin("participants", Map.of("count", count));
            sseService.sendToAllStudents("participants", Map.of("count", count));
        });
    }

    /** Queue a scoreboard broadcast for the round (coalesced, see {@link #flushScoreboards()}). */
    public void notifyScoreboard(Round round) {
        Long roundId = round.getId();
        AfterCommit.run(() -> dirtyRounds.add(roundId));
    }

    public void notifyRoundState(Long sessionId, Round round) {
        Map<String, Object> data = new HashMap<>();
        data.put("status", round.getStatus().name());
        data.put("roundName", round.getName());
        data.put("roundId", round.getId());
        if (round.getTimeLimitSeconds() != null) data.put("timeLimitSeconds", round.getTimeLimitSeconds());
        if (round.getTestLimit() != null) data.put("testLimit", round.getTestLimit());
        Long remaining = remainingMs(round);
        if (remaining != null) data.put("remainingMs", remaining);

        Long roundId = round.getId();
        AfterCommit.run(() -> {
            sseService.sendToBoard("round-state", data);
            sseService.sendToAdmin("round-state", data);
            sseService.sendToAllStudents("round-state", data);
            dirtyRounds.add(roundId);
        });
    }

    /** Session settings changed: board re-renders, students re-fetch (e.g. nickname field). */
    public void notifySettingsChanged(GameSession session) {
        Map<String, Object> data = Map.of(
            "anonymityMode", session.getAnonymityMode().name(),
            "mutantPanelMode", session.getMutantPanelMode().name(),
            "joinOpen", session.isJoinOpen());
        AfterCommit.run(() -> {
            sseService.sendToBoard("settings-changed", data);
            sseService.sendToAllStudents("settings-changed", data);
        });
    }

    /**
     * Broadcasts at most ~3 scoreboard updates per second no matter how many tests arrive,
     * which keeps 100 students + board cheap while still feeling live.
     */
    @Scheduled(fixedDelay = 300)
    public void flushScoreboards() {
        if (dirtyRounds.isEmpty()) return;
        List<Long> ids = new ArrayList<>(dirtyRounds);
        dirtyRounds.removeAll(ids);
        for (Long roundId : ids) {
            try {
                roundRepo.findById(roundId).ifPresent(this::broadcastScoreboard);
            } catch (Exception e) {
                log.warn("Scoreboard broadcast for round {} failed: {}", roundId, e.toString());
            }
        }
    }

    private void broadcastScoreboard(Round round) {
        Map<String, Object> payload = buildLiveScoreboard(round);
        sseService.sendToAdmin("scoreboard", payload);
        // the board only shows the active session's visible round
        boolean onBoard = sessionRepo.findById(round.getSessionId()).map(GameSession::isActive).orElse(false)
            && getVisibleRound(round.getSessionId()).map(r -> r.getId().equals(round.getId())).orElse(false);
        if (onBoard) sseService.sendToBoard("scoreboard", payload);
    }

    private Map<String, Object> buildLiveScoreboard(Round round) {
        GameSession session = sessionRepo.findById(round.getSessionId()).orElseThrow();
        Problem problem = problemRegistry.get(round.getProblemId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("roundId", round.getId());
        m.put("entries", getScoreboard(session.getId(), round));
        m.put("participantCount", participantRepo.countBySessionIdAndKickedFalse(session.getId()));
        m.put("mutantPanel", buildMutantPanel(session, round, problem));
        return m;
    }

    private Map<String, Object> submissionToAdminMap(Submission sub, Map<String, String> inputs,
                                                     List<String> killedMutants) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", sub.getId());
        m.put("roundId", sub.getRoundId());
        m.put("participantId", sub.getParticipantId());
        m.put("participantName", sub.getParticipantName());
        m.put("studentNumber", sub.getStudentNumber());
        m.put("list", inputs.getOrDefault("list", ""));
        m.put("target", inputs.getOrDefault("target", ""));
        m.put("expected", sub.getExpectedValue());
        m.put("referenceResult", sub.getReferenceResult());
        m.put("validity", sub.getValidity().name());
        m.put("killedMutants", killedMutants);
        m.put("time", sub.getCreatedAt().toEpochMilli());
        return m;
    }

    // ── Board data ────────────────────────────────────

    /**
     * Public board payload (no student numbers, no real names unless FULL_NAME mode).
     * Mutant names/descriptions are included only when the panel is REVEALED
     * <em>and</em> the round itself has been revealed — the board URL is public, so
     * students could otherwise read them from their phones.
     */
    public Map<String, Object> getBoardPayload() {
        Map<String, Object> result = new LinkedHashMap<>();
        Optional<GameSession> active = getActiveSession();
        if (active.isEmpty()) {
            result.put("hasSession", false);
            return result;
        }
        GameSession session = active.get();
        result.put("hasSession", true);
        result.put("sessionName", session.getName());
        result.put("anonymityMode", session.getAnonymityMode().name());
        result.put("mutantPanelMode", session.getMutantPanelMode().name());
        result.put("joinOpen", session.isJoinOpen());
        result.put("participantCount", participantRepo.countBySessionIdAndKickedFalse(session.getId()));

        getVisibleRound(session.getId()).ifPresent(round -> {
            Problem problem = problemRegistry.get(round.getProblemId());
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", round.getId());
            r.put("name", round.getName());
            r.put("status", round.getStatus().name());
            r.put("testLimit", round.getTestLimit());
            r.put("timeLimitSeconds", round.getTimeLimitSeconds());
            r.put("remainingMs", remainingMs(round));
            result.put("round", r);
            result.put("specification", problem.getSpecification());
            result.put("totalMutants", problem.getMutants().size());
            result.put("scoreboard", getScoreboard(session.getId(), round));
            result.put("mutantPanel", buildMutantPanel(session, round, problem));
        });
        return result;
    }

    /** null when HIDDEN; otherwise one row per mutant with its kill count (and details once revealed). */
    private List<Map<String, Object>> buildMutantPanel(GameSession session, Round round, Problem problem) {
        MutantPanelMode mode = session.getMutantPanelMode();
        if (mode == MutantPanelMode.HIDDEN) return null;
        boolean details = mode == MutantPanelMode.REVEALED && round.getStatus() == RoundStatus.REVEALED;
        Map<String, Long> counts = computeMutantKillCounts(round, problem);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MutantDefinition md : problem.getMutants()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", md.id());
            row.put("killCount", counts.getOrDefault(md.id(), 0L));
            if (details) {
                row.put("shortName", md.shortName());
                row.put("description", md.description());
            }
            rows.add(row);
        }
        return rows;
    }

    /** mutant id → number of distinct (non-removed) students whose VALID tests killed it. */
    public Map<String, Long> computeMutantKillCounts(Round round, Problem problem) {
        Set<Long> kicked = participantRepo.findBySessionId(round.getSessionId()).stream()
            .filter(Participant::isKicked).map(Participant::getId).collect(Collectors.toSet());
        Map<String, Long> counts = new LinkedHashMap<>();
        for (MutantDefinition md : problem.getMutants()) counts.put(md.id(), 0L);
        for (PlayerRoundStats st : computeRoundStats(round).values()) {
            if (kicked.contains(st.participantId)) continue;
            for (String m : st.killed) counts.merge(m, 1L, Long::sum);
        }
        return counts;
    }

    // ── Analytics ─────────────────────────────────────

    public AnalyticsData getAnalytics(Long roundId) {
        Round round = roundRepo.findById(roundId).orElseThrow();
        Problem problem = problemRegistry.get(round.getProblemId());

        long totalSubs = submissionRepo.countByRoundId(roundId);
        long invalidSubs = submissionRepo.countByRoundIdAndValidity(roundId, Validity.INVALID);
        long participantCount = participantRepo.countBySessionIdAndKickedFalse(round.getSessionId());

        List<ScoreboardEntry> scoreboard = getScoreboard(round.getSessionId(), round);
        double avgScore = scoreboard.stream().mapToInt(ScoreboardEntry::score).average().orElse(0);

        return new AnalyticsData(
            totalSubs, invalidSubs,
            totalSubs == 0 ? 0 : (double) invalidSubs / totalSubs,
            participantCount, scoreboard.size(), avgScore,
            computeMutantKillCounts(round, problem),
            problem.getMutants().stream()
                .collect(Collectors.toMap(MutantDefinition::id, MutantDefinition::shortName,
                    (a, b) -> a, LinkedHashMap::new))
        );
    }

    public record ScoreInfo(int killed, int total) {}
}
