package com.seng403.mutanthunt.service;

import com.seng403.mutanthunt.entity.*;
import com.seng403.mutanthunt.problem.MutantDefinition;
import com.seng403.mutanthunt.problem.ProblemRegistry;
import com.seng403.mutanthunt.repository.*;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CSV exports, per session or per round. Files start with a UTF-8 BOM so that Excel
 * shows Turkish characters (ç, ğ, ı, ö, ş, ü) correctly.
 */
@Service
public class ExportService {

    private static final String BOM = "﻿";
    private static final DateTimeFormatter TS =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final SessionRepository sessionRepo;
    private final RoundRepository roundRepo;
    private final ParticipantRepository participantRepo;
    private final SubmissionRepository submissionRepo;
    private final MutantResultRepository mutantResultRepo;
    private final GameService gameService;
    private final ProblemRegistry problemRegistry;

    public ExportService(SessionRepository sessionRepo, RoundRepository roundRepo,
                         ParticipantRepository participantRepo, SubmissionRepository submissionRepo,
                         MutantResultRepository mutantResultRepo, GameService gameService,
                         ProblemRegistry problemRegistry) {
        this.sessionRepo = sessionRepo;
        this.roundRepo = roundRepo;
        this.participantRepo = participantRepo;
        this.submissionRepo = submissionRepo;
        this.mutantResultRepo = mutantResultRepo;
        this.gameService = gameService;
        this.problemRegistry = problemRegistry;
    }

    // ── Participants + scores ─────────────────────────

    /** All participants of the session with score / tests per round (oldest round first). */
    public void exportParticipants(PrintWriter pw, Long sessionId) {
        List<Round> rounds = new ArrayList<>(roundRepo.findBySessionIdOrderByCreatedAtDesc(sessionId));
        Collections.reverse(rounds);
        writeParticipants(pw, sessionId, rounds);
    }

    /** All participants of the round's session with their score in that round only. */
    public void exportParticipantsForRound(PrintWriter pw, Long roundId) {
        Round round = roundRepo.findById(roundId).orElseThrow();
        writeParticipants(pw, round.getSessionId(), List.of(round));
    }

    private void writeParticipants(PrintWriter pw, Long sessionId, List<Round> rounds) {
        GameSession session = sessionRepo.findById(sessionId).orElseThrow();
        List<Participant> participants = participantRepo.findBySessionId(sessionId).stream()
            .sorted(Comparator.comparing(Participant::getJoinedAt))
            .toList();

        pw.print(BOM);
        List<String> header = new ArrayList<>(List.of("Session", "Full Name", "Student Number", "Nickname",
            "Anonymous Name", "Board Name", "Joined At", "Rejoins", "Removed"));
        for (Round r : rounds) {
            header.add(r.getName() + " Score");
            header.add(r.getName() + " Total Mutants");
            header.add(r.getName() + " Tests Used");
            header.add(r.getName() + " Valid Tests");
        }
        writeRow(pw, header);

        Map<Long, Map<Long, GameService.PlayerRoundStats>> stats = new HashMap<>();
        for (Round r : rounds) stats.put(r.getId(), gameService.computeRoundStats(r));

        for (Participant p : participants) {
            List<String> row = new ArrayList<>(List.of(
                text(session.getName()), text(p.getFullName()), p.getStudentNumber(),
                text(p.getNickname()), text(p.getAnonymousName()),
                text(gameService.resolveDisplayName(p, session.getAnonymityMode())),
                TS.format(p.getJoinedAt()), String.valueOf(p.getRejoinCount()), p.isKicked() ? "YES" : "NO"));
            for (Round r : rounds) {
                GameService.PlayerRoundStats st = stats.get(r.getId()).get(p.getId());
                row.add(String.valueOf(st == null ? 0 : st.killed.size()));
                row.add(String.valueOf(problemRegistry.get(r.getProblemId()).getMutants().size()));
                row.add(String.valueOf(st == null ? 0 : st.testsUsed));
                row.add(String.valueOf(st == null ? 0 : st.validTests));
            }
            writeRow(pw, row);
        }
        pw.flush();
    }

    // ── Submissions ───────────────────────────────────

    /** Every submission of one round (VALID and INVALID), oldest first. */
    public void exportSubmissions(PrintWriter pw, Long roundId) {
        Round round = roundRepo.findById(roundId).orElseThrow();
        writeSubmissions(pw, round.getSessionId(), List.of(round));
    }

    /** Every submission of every round in the session, oldest first. */
    public void exportSessionSubmissions(PrintWriter pw, Long sessionId) {
        writeSubmissions(pw, sessionId, roundRepo.findBySessionIdOrderByCreatedAtDesc(sessionId));
    }

    private void writeSubmissions(PrintWriter pw, Long sessionId, List<Round> rounds) {
        GameSession session = sessionRepo.findById(sessionId).orElseThrow();
        Map<Long, Round> roundById = rounds.stream().collect(Collectors.toMap(Round::getId, r -> r));

        // union of mutant ids over the problems used (normally just one problem)
        List<String> mutantIds = rounds.stream()
            .map(r -> problemRegistry.get(r.getProblemId()))
            .distinct()
            .flatMap(p -> p.getMutants().stream().map(MutantDefinition::id))
            .distinct()
            .toList();

        List<Submission> subs = roundById.isEmpty() ? List.of()
            : submissionRepo.findByRoundIdInOrderByCreatedAtDesc(roundById.keySet()).stream()
                .sorted(Comparator.comparing(Submission::getCreatedAt).thenComparing(Submission::getId))
                .toList();
        Map<Long, Map<String, MutantResult>> results = roundById.isEmpty() ? Map.of()
            : mutantResultRepo.findByRoundIds(roundById.keySet()).stream()
                .collect(Collectors.groupingBy(MutantResult::getSubmissionId,
                    Collectors.toMap(MutantResult::getMutantId, r -> r, (a, b) -> a)));
        Map<Long, Participant> participants = participantRepo.findBySessionId(sessionId).stream()
            .collect(Collectors.toMap(Participant::getId, p -> p));

        pw.print(BOM);
        List<String> header = new ArrayList<>(List.of("Timestamp", "Session", "Round", "Problem",
            "Full Name", "Student Number", "Board Name", "List", "Target", "Expected",
            "Reference Result", "Validity", "Killed Count", "Killed Mutants"));
        for (String id : mutantIds) {
            header.add(id + " Output");
            header.add(id + " Killed");
        }
        writeRow(pw, header);

        for (Submission s : subs) {
            Round r = roundById.get(s.getRoundId());
            Participant p = participants.get(s.getParticipantId());
            Map<String, String> inputs = gameService.parseRawInputs(s.getRawInputsJson());
            Map<String, MutantResult> mr = results.getOrDefault(s.getId(), Map.of());
            List<String> killed = mutantIds.stream()
                .filter(id -> mr.containsKey(id) && mr.get(id).isKilled()).toList();

            List<String> row = new ArrayList<>(List.of(
                TS.format(s.getCreatedAt()),
                text(session.getName()),
                text(r.getName()),
                r.getProblemId(),
                text(p != null ? p.getFullName() : s.getParticipantName()),
                p != null ? p.getStudentNumber() : nz(s.getStudentNumber()),
                text(s.getDisplayName()),
                inputs.getOrDefault("list", ""),
                inputs.getOrDefault("target", ""),
                s.getExpectedValue(),
                s.getReferenceResult(),
                s.getValidity().name(),
                s.getValidity() == Validity.VALID ? String.valueOf(killed.size()) : "",
                String.join(" ", killed)));
            for (String id : mutantIds) {
                MutantResult m = mr.get(id);
                row.add(m == null ? "" : m.getDisplayOutput());
                row.add(m == null ? "" : (m.isKilled() ? "YES" : "NO"));
            }
            writeRow(pw, row);
        }
        pw.flush();
    }

    // ── CSV helpers ───────────────────────────────────

    private static void writeRow(PrintWriter pw, List<String> cells) {
        pw.print(cells.stream().map(ExportService::csvEscape).collect(Collectors.joining(",")));
        pw.print("\r\n");
    }

    static String csvEscape(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n") || val.contains("\r")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    /**
     * Free text typed by students (names, nicknames) must not be interpreted as a
     * spreadsheet formula when the file is opened in Excel (CSV injection).
     */
    static String text(String val) {
        if (val == null || val.isEmpty()) return "";
        char c = val.charAt(0);
        return (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') ? "'" + val : val;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
