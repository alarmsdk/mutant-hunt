package com.seng403.mutanthunt.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seng403.mutanthunt.dto.*;
import com.seng403.mutanthunt.entity.*;
import com.seng403.mutanthunt.problem.*;
import com.seng403.mutanthunt.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GameService {

    private static final Logger log = LoggerFactory.getLogger(GameService.class);

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
        GameSession session = sessionRepo.findByActiveTrue()
            .orElseThrow(() -> new IllegalStateException("join.error.session.none"));

        if (!session.isJoinOpen()) {
            throw new IllegalStateException("join.error.session.closed");
        }

        int reqLen = session.getStudentIdLength();
        if (reqLen > 0 && studentNumber.length() != reqLen) {
            throw new IllegalArgumentException("join.error.studentId.length");
        }

        // check if student number already exists in this session
        Optional<Participant> existing = participantRepo
            .findBySessionIdAndStudentNumber(session.getId(), studentNumber);

        Participant p;
        if (existing.isPresent()) {
            p = existing.get();
            if (p.isKicked()) {
                throw new IllegalStateException("join.error.session.closed");
            }
            String oldToken = p.getToken();
            String newToken = UUID.randomUUID().toString();
            p.setToken(newToken);
            p.setFullName(fullName);
            if (nickname != null && !nickname.isBlank()) p.setNickname(nickname);
            participantRepo.save(p);
            log.info("Participant reconnected: {} ({}), old token replaced", fullName, studentNumber);
        } else {
            String token = UUID.randomUUID().toString();
            p = new Participant(session.getId(), studentNumber, fullName, token);
            if (nickname != null && !nickname.isBlank()) p.setNickname(nickname);
            p.setAnonymousName(nameGenerator.generate());
            participantRepo.save(p);
            log.info("New participant joined: {} ({})", fullName, studentNumber);
            notifyParticipantCount(session.getId());
        }

        return new JoinResponse(true, p.getToken(), p.getFullName());
    }

    // ── Submit test ───────────────────────────────────

    @Transactional
    public SubmitResponse submitTest(Participant participant, Map<String, String> rawInputs) {
        GameSession session = sessionRepo.findById(participant.getSessionId()).orElseThrow();

        // find open round (check BEFORE rate limit so "closed" error is clear)
        Round round = roundRepo.findBySessionIdAndStatus(session.getId(), RoundStatus.OPEN)
            .orElseThrow(() -> new IllegalStateException("test.round.closed"));

        // rate limit
        if (!rateLimitService.isSubmissionAllowed(participant.getId(), session.getSubmissionRateLimitMs())) {
            return SubmitResponse.error("test.rate.limited");
        }

        // check test limit
        if (!round.isUnlimited()) {
            long used = submissionRepo.countByRoundIdAndParticipantId(round.getId(), participant.getId());
            if (used >= round.getTestLimit()) {
                return SubmitResponse.error("test.limit.reached");
            }
        }

        Problem problem = problemRegistry.get(round.getProblemId());

        // validate
        try {
            problem.validate(rawInputs);
        } catch (IllegalArgumentException e) {
            return SubmitResponse.error(e.getMessage());
        }

        // execute reference
        String referenceResult = problem.executeReference(rawInputs);
        String expected = problem.getExpected(rawInputs);
        Validity validity = referenceResult.equals(expected) ? Validity.VALID : Validity.INVALID;

        // store raw inputs as JSON
        String rawJson;
        try {
            rawJson = objectMapper.writeValueAsString(rawInputs);
        } catch (Exception e) {
            rawJson = rawInputs.toString();
        }

        Submission sub = new Submission(
            round.getId(), participant.getId(), rawJson,
            expected, referenceResult, validity
        );
        submissionRepo.save(sub);

        // if valid, run against all mutants
        List<MutantKillInfo> killInfos = new ArrayList<>();
        if (validity == Validity.VALID) {
            for (MutantDefinition md : problem.getMutants()) {
                ExecutionResult result = problem.executeMutant(md.id(), rawInputs);
                boolean killed = result.isException() || !result.value().equals(expected);
                MutantResult mr = new MutantResult(
                    sub.getId(), md.id(),
                    result.isException() ? null : result.value(),
                    result.isException() ? result.exception() : null,
                    killed
                );
                mutantResultRepo.save(mr);
                killInfos.add(new MutantKillInfo(md.id(), killed));
            }
        }

        // compute current score for this participant in this round
        ScoreInfo score = computeScore(round.getId(), participant.getId(), problem);
        long totalUsed = submissionRepo.countByRoundIdAndParticipantId(round.getId(), participant.getId());
        long remaining = round.isUnlimited() ? -1 : Math.max(0, round.getTestLimit() - totalUsed);

        // notify board and admin
        notifyScoreboard(session.getId(), round);
        notifyAdminSubmission(sub, participant, killInfos, validity);

        String message = validity == Validity.INVALID ? "test.result.invalid.oracle" : null;

        return new SubmitResponse(
            true, validity.name(), message,
            score.killed(), score.total(),
            remaining, sub.getId()
        );
    }

    // ── Score computation ─────────────────────────────

    public ScoreInfo computeScore(Long roundId, Long participantId, Problem problem) {
        List<Submission> subs = submissionRepo
            .findByRoundIdAndParticipantIdOrderByCreatedAt(roundId, participantId);

        Set<String> killedMutants = new HashSet<>();
        for (Submission s : subs) {
            if (s.getValidity() != Validity.VALID) continue;
            List<MutantResult> results = mutantResultRepo.findBySubmissionId(s.getId());
            for (MutantResult mr : results) {
                if (mr.isKilled()) killedMutants.add(mr.getMutantId());
            }
        }
        return new ScoreInfo(killedMutants.size(), problem.getMutants().size());
    }

    // ── Scoreboard ────────────────────────────────────

    public List<ScoreboardEntry> getScoreboard(Long sessionId, Round round) {
        Problem problem = problemRegistry.get(round.getProblemId());
        List<Participant> participants = participantRepo
            .findBySessionIdAndKickedFalseOrderByJoinedAt(sessionId);
        GameSession session = sessionRepo.findById(sessionId).orElseThrow();

        List<ScoreboardEntry> entries = new ArrayList<>();
        for (Participant p : participants) {
            List<Submission> subs = submissionRepo
                .findByRoundIdAndParticipantIdOrderByCreatedAt(round.getId(), p.getId());
            if (subs.isEmpty()) continue;

            Set<String> killed = new HashSet<>();
            long validCount = 0;
            Instant lastKillTime = null;

            for (Submission s : subs) {
                if (s.getValidity() != Validity.VALID) continue;
                validCount++;
                List<MutantResult> results = mutantResultRepo.findBySubmissionId(s.getId());
                boolean newKill = false;
                for (MutantResult mr : results) {
                    if (mr.isKilled() && killed.add(mr.getMutantId())) {
                        newKill = true;
                    }
                }
                if (newKill) lastKillTime = s.getCreatedAt();
            }

            if (killed.isEmpty() && validCount == 0) continue;

            String displayName = resolveDisplayName(p, session.getAnonymityMode());
            entries.add(new ScoreboardEntry(
                p.getId(), displayName,
                killed.size(), problem.getMutants().size(),
                validCount, lastKillTime
            ));
        }

        // sort: score DESC, validCount ASC, lastKillTime ASC
        entries.sort(Comparator
            .comparingInt(ScoreboardEntry::score).reversed()
            .thenComparingLong(ScoreboardEntry::validTestCount)
            .thenComparing(ScoreboardEntry::lastScoreTime,
                Comparator.nullsLast(Comparator.naturalOrder()))
        );

        return entries;
    }

    // ── Display name resolution ───────────────────────

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

        // find current visible round (OPEN, CLOSED, or REVEALED)
        List<Round> visibleRounds = roundRepo.findBySessionIdAndStatusIn(
            session.getId(),
            List.of(RoundStatus.OPEN, RoundStatus.CLOSED, RoundStatus.REVEALED)
        );
        Round round = visibleRounds.isEmpty() ? null : visibleRounds.getFirst();

        if (round == null) {
            return PlayerStatus.waiting(participant.getFullName(),
                participantRepo.countBySessionIdAndKickedFalse(session.getId()));
        }

        Problem problem = problemRegistry.get(round.getProblemId());
        ScoreInfo score = computeScore(round.getId(), participant.getId(), problem);

        long totalUsed = submissionRepo.countByRoundIdAndParticipantId(round.getId(), participant.getId());
        long remaining = round.isUnlimited() ? -1 : Math.max(0, round.getTestLimit() - totalUsed);

        List<SubmissionInfo> submissions = buildSubmissionInfos(
            round, participant.getId(), problem, round.getStatus() == RoundStatus.REVEALED
        );

        return new PlayerStatus(
            participant.getFullName(),
            round.getStatus().name(),
            round.getName(),
            problem.getSpecification(),
            problem.getFunctionInputFields(),
            problem.getExpectedField(),
            score.killed(), score.total(),
            totalUsed, remaining,
            round.getTimeLimitSeconds(),
            round.getOpenedAt() != null ? round.getOpenedAt().toEpochMilli() : null,
            submissions,
            round.getStatus() == RoundStatus.REVEALED ? buildMutantInfos(problem) : null,
            participantRepo.countBySessionIdAndKickedFalse(session.getId()),
            session.getAnonymityMode() == AnonymityMode.NICKNAME
        );
    }

    private List<SubmissionInfo> buildSubmissionInfos(Round round, Long participantId,
                                                       Problem problem, boolean revealed) {
        List<Submission> subs = submissionRepo
            .findByRoundIdAndParticipantIdOrderByCreatedAt(round.getId(), participantId);

        List<SubmissionInfo> infos = new ArrayList<>();
        for (Submission s : subs) {
            Map<String, String> inputs = parseRawInputs(s.getRawInputsJson());
            List<MutantKillInfo> mutantResults = null;
            if (revealed && s.getValidity() == Validity.VALID) {
                List<MutantResult> mrs = mutantResultRepo.findBySubmissionId(s.getId());
                mutantResults = mrs.stream()
                    .map(mr -> new MutantKillInfo(mr.getMutantId(), mr.isKilled()))
                    .toList();
            }
            infos.add(new SubmissionInfo(
                s.getId(),
                inputs.getOrDefault("list", ""),
                inputs.getOrDefault("target", ""),
                inputs.getOrDefault("expected", ""),
                s.getValidity().name(),
                s.getReferenceResult(),
                mutantResults,
                s.getCreatedAt().toEpochMilli()
            ));
        }
        return infos;
    }

    private List<MutantInfo> buildMutantInfos(Problem problem) {
        return problem.getMutants().stream()
            .map(md -> new MutantInfo(md.id(), md.shortName(), md.description()))
            .toList();
    }

    @SuppressWarnings("unchecked")
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

    public Optional<Round> getVisibleRound(Long sessionId) {
        List<Round> rounds = roundRepo.findBySessionIdAndStatusIn(
            sessionId, List.of(RoundStatus.OPEN, RoundStatus.CLOSED, RoundStatus.REVEALED));
        return rounds.isEmpty() ? Optional.empty() : Optional.of(rounds.getFirst());
    }

    // ── Notifications ─────────────────────────────────

    public void notifyParticipantCount(Long sessionId) {
        long count = participantRepo.countBySessionIdAndKickedFalse(sessionId);
        sseService.sendToBoard("participants", Map.of("count", count));
        sseService.sendToAdmin("participants", Map.of("count", count));
    }

    public void notifyScoreboard(Long sessionId, Round round) {
        List<ScoreboardEntry> scoreboard = getScoreboard(sessionId, round);
        sseService.sendToBoard("scoreboard", Map.of("entries", scoreboard));
        sseService.sendToAdmin("scoreboard", Map.of("entries", scoreboard));
    }

    public void notifyRoundState(Long sessionId, Round round) {
        Map<String, Object> data = new HashMap<>();
        data.put("status", round.getStatus().name());
        data.put("roundName", round.getName());
        data.put("roundId", round.getId());
        if (round.getOpenedAt() != null) data.put("openedAt", round.getOpenedAt().toEpochMilli());
        if (round.getTimeLimitSeconds() != null) data.put("timeLimitSeconds", round.getTimeLimitSeconds());
        if (round.getTestLimit() != null) data.put("testLimit", round.getTestLimit());

        sseService.sendToBoard("round-state", data);
        sseService.sendToAdmin("round-state", data);
        sseService.sendToAllStudents("round-state", data);
    }

    private void notifyAdminSubmission(Submission sub, Participant p,
                                        List<MutantKillInfo> kills, Validity validity) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", sub.getId());
        data.put("participantName", p.getFullName());
        data.put("studentNumber", p.getStudentNumber());
        data.put("inputs", parseRawInputs(sub.getRawInputsJson()));
        data.put("referenceResult", sub.getReferenceResult());
        data.put("validity", validity.name());
        data.put("killedMutants", kills.stream()
            .filter(MutantKillInfo::killed).map(MutantKillInfo::mutantId).toList());
        data.put("time", sub.getCreatedAt().toEpochMilli());
        sseService.sendToAdmin("submission", data);
    }

    // ── Board data ────────────────────────────────────

    public BoardData getBoardData(Long sessionId) {
        GameSession session = sessionRepo.findById(sessionId).orElseThrow();
        long participantCount = participantRepo.countBySessionIdAndKickedFalse(sessionId);

        Optional<Round> visibleRound = getVisibleRound(sessionId);
        if (visibleRound.isEmpty()) {
            return new BoardData(session, null, null, null,
                participantCount, null, null);
        }

        Round round = visibleRound.get();
        Problem problem = problemRegistry.get(round.getProblemId());
        List<ScoreboardEntry> scoreboard = getScoreboard(sessionId, round);

        Map<String, Long> mutantKillCounts = null;
        List<MutantInfo> mutantInfos = null;
        if (session.getMutantPanelMode() != MutantPanelMode.HIDDEN) {
            mutantKillCounts = computeMutantKillCounts(round, problem);
        }
        if (session.getMutantPanelMode() == MutantPanelMode.REVEALED) {
            mutantInfos = buildMutantInfos(problem);
        }

        return new BoardData(session, round, problem, scoreboard,
            participantCount, mutantKillCounts, mutantInfos);
    }

    private Map<String, Long> computeMutantKillCounts(Round round, Problem problem) {
        List<MutantResult> allKills = mutantResultRepo.findKilledByRound(round.getId());

        // Group by participantId → Set<mutantId>
        Map<Long, Set<String>> participantKills = new HashMap<>();
        Map<Long, Long> subToParticipant = new HashMap<>();

        // load submission→participant mapping
        List<Submission> validSubs = submissionRepo.findByRoundIdAndValidity(round.getId(), Validity.VALID);
        for (Submission s : validSubs) {
            subToParticipant.put(s.getId(), s.getParticipantId());
        }

        for (MutantResult mr : allKills) {
            Long pid = subToParticipant.get(mr.getSubmissionId());
            if (pid != null) {
                participantKills.computeIfAbsent(pid, k -> new HashSet<>()).add(mr.getMutantId());
            }
        }

        // count: for each mutant, how many distinct participants killed it
        Map<String, Long> counts = new LinkedHashMap<>();
        for (MutantDefinition md : problem.getMutants()) {
            long count = participantKills.values().stream()
                .filter(set -> set.contains(md.id()))
                .count();
            counts.put(md.id(), count);
        }
        return counts;
    }

    // ── Analytics ─────────────────────────────────────

    public AnalyticsData getAnalytics(Long roundId) {
        Round round = roundRepo.findById(roundId).orElseThrow();
        Problem problem = problemRegistry.get(round.getProblemId());

        long totalSubs = submissionRepo.countByRoundId(roundId);
        long invalidSubs = submissionRepo.countByRoundIdAndValidity(roundId, Validity.INVALID);

        Map<String, Long> mutantKillCounts = computeMutantKillCounts(round, problem);
        long participantCount = participantRepo.countBySessionIdAndKickedFalse(round.getSessionId());

        List<ScoreboardEntry> scoreboard = getScoreboard(round.getSessionId(), round);
        double avgScore = scoreboard.stream().mapToInt(ScoreboardEntry::score).average().orElse(0);

        return new AnalyticsData(
            totalSubs, invalidSubs, participantCount,
            avgScore, mutantKillCounts,
            problem.getMutants().stream()
                .collect(Collectors.toMap(MutantDefinition::id, MutantDefinition::shortName,
                    (a, b) -> a, LinkedHashMap::new))
        );
    }

    public record ScoreInfo(int killed, int total) {}
}
