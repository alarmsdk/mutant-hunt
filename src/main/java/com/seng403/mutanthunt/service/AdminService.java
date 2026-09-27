package com.seng403.mutanthunt.service;

import com.seng403.mutanthunt.entity.*;
import com.seng403.mutanthunt.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AdminService {

    private final SessionRepository sessionRepo;
    private final RoundRepository roundRepo;
    private final ParticipantRepository participantRepo;
    private final SubmissionRepository submissionRepo;
    private final MutantResultRepository mutantResultRepo;
    private final GameService gameService;
    private final SseService sseService;

    @Value("${app.default-student-id-length}")
    private int defaultStudentIdLength;

    @Value("${app.default-rate-limit-ms}")
    private int defaultRateLimitMs;

    public AdminService(SessionRepository sessionRepo, RoundRepository roundRepo,
                        ParticipantRepository participantRepo, SubmissionRepository submissionRepo,
                        MutantResultRepository mutantResultRepo, GameService gameService,
                        SseService sseService) {
        this.sessionRepo = sessionRepo;
        this.roundRepo = roundRepo;
        this.participantRepo = participantRepo;
        this.submissionRepo = submissionRepo;
        this.mutantResultRepo = mutantResultRepo;
        this.gameService = gameService;
        this.sseService = sseService;
    }

    // ── Session CRUD ──────────────────────────────────

    public List<GameSession> getActiveSessions() {
        return sessionRepo.findByArchivedFalseOrderByCreatedAtDesc();
    }

    public List<GameSession> getArchivedSessions() {
        return sessionRepo.findByArchivedTrueOrderByCreatedAtDesc();
    }

    @Transactional
    public GameSession createSession(String name) {
        GameSession s = new GameSession(name);
        s.setStudentIdLength(defaultStudentIdLength);
        s.setSubmissionRateLimitMs(defaultRateLimitMs);
        return sessionRepo.save(s);
    }

    @Transactional
    public void activateSession(Long sessionId) {
        sessionRepo.findByActiveTrue().ifPresent(s -> {
            s.setActive(false);
            sessionRepo.save(s);
        });
        GameSession session = sessionRepo.findById(sessionId).orElseThrow();
        session.setActive(true);
        sessionRepo.save(session);
    }

    @Transactional
    public void archiveSession(Long sessionId) {
        GameSession session = sessionRepo.findById(sessionId).orElseThrow();
        session.setArchived(true);
        session.setActive(false);
        sessionRepo.save(session);
    }

    @Transactional
    public void deleteSession(Long sessionId) {
        List<Round> rounds = roundRepo.findBySessionIdOrderByCreatedAtDesc(sessionId);
        for (Round r : rounds) {
            deleteRoundData(r.getId());
        }
        roundRepo.deleteAll(rounds);
        participantRepo.deleteAll(
            participantRepo.findBySessionIdAndKickedFalseOrderByJoinedAt(sessionId));
        sessionRepo.deleteById(sessionId);
    }

    private void deleteRoundData(Long roundId) {
        List<Submission> subs = submissionRepo.findByRoundIdOrderByCreatedAtDesc(roundId);
        for (Submission s : subs) {
            mutantResultRepo.deleteAll(mutantResultRepo.findBySubmissionId(s.getId()));
        }
        submissionRepo.deleteAll(subs);
    }

    // ── Session Settings ──────────────────────────────

    @Transactional
    public GameSession updateSettings(Long sessionId, Map<String, String> settings) {
        GameSession session = sessionRepo.findById(sessionId).orElseThrow();

        if (settings.containsKey("anonymityMode"))
            session.setAnonymityMode(AnonymityMode.valueOf(settings.get("anonymityMode")));
        if (settings.containsKey("joinOpen"))
            session.setJoinOpen(Boolean.parseBoolean(settings.get("joinOpen")));
        if (settings.containsKey("studentIdLength"))
            session.setStudentIdLength(Integer.parseInt(settings.get("studentIdLength")));
        if (settings.containsKey("mutantPanelMode"))
            session.setMutantPanelMode(MutantPanelMode.valueOf(settings.get("mutantPanelMode")));
        if (settings.containsKey("submissionRateLimitMs"))
            session.setSubmissionRateLimitMs(Integer.parseInt(settings.get("submissionRateLimitMs")));

        sessionRepo.save(session);

        // notify board of settings change
        sseService.sendToBoard("settings-changed", Map.of(
            "anonymityMode", session.getAnonymityMode().name(),
            "mutantPanelMode", session.getMutantPanelMode().name()
        ));

        return session;
    }

    // ── Round management ──────────────────────────────

    public List<Round> getRounds(Long sessionId) {
        return roundRepo.findBySessionIdOrderByCreatedAtDesc(sessionId);
    }

    @Transactional
    public Round createRound(Long sessionId, String name, String problemId,
                             Integer testLimit, Integer timeLimitSeconds) {
        Round r = new Round(sessionId, name, problemId);
        r.setTestLimit(testLimit != null && testLimit > 0 ? testLimit : null);
        r.setTimeLimitSeconds(timeLimitSeconds != null && timeLimitSeconds > 0 ? timeLimitSeconds : null);
        return roundRepo.save(r);
    }

    @Transactional
    public Round updateRound(Long roundId, String name, Integer testLimit, Integer timeLimitSeconds) {
        Round r = roundRepo.findById(roundId).orElseThrow();
        if (name != null) r.setName(name);
        r.setTestLimit(testLimit != null && testLimit > 0 ? testLimit : null);
        r.setTimeLimitSeconds(timeLimitSeconds != null && timeLimitSeconds > 0 ? timeLimitSeconds : null);
        roundRepo.save(r);
        if (r.getStatus() == RoundStatus.OPEN) {
            gameService.notifyRoundState(r.getSessionId(), r);
        }
        return r;
    }

    @Transactional
    public Round openRound(Long roundId) {
        Round r = roundRepo.findById(roundId).orElseThrow();
        if (r.getStatus() != RoundStatus.DRAFT) {
            throw new IllegalStateException("Round can only be opened from DRAFT status");
        }

        // close any currently open round
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
            throw new IllegalStateException("Only OPEN rounds can be closed");
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
            throw new IllegalStateException("Only CLOSED rounds can be revealed");
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

    @Transactional
    public void kickParticipant(Long participantId) {
        Participant p = participantRepo.findById(participantId).orElseThrow();
        p.setKicked(true);
        participantRepo.save(p);
        gameService.notifyParticipantCount(p.getSessionId());
    }

    @Transactional
    public void updateNickname(Long participantId, String nickname) {
        Participant p = participantRepo.findById(participantId).orElseThrow();
        p.setNickname(nickname);
        participantRepo.save(p);
    }

    // ── Submission queries ────────────────────────────

    public List<Submission> getSubmissions(Long roundId) {
        return submissionRepo.findByRoundIdOrderByCreatedAtDesc(roundId);
    }

    public Optional<GameSession> getSession(Long id) {
        return sessionRepo.findById(id);
    }

    // ── Board commands ────────────────────────────────

    public void sendBoardCommand(String command) {
        sseService.sendToBoard("board-command", Map.of("command", command));
    }
}
