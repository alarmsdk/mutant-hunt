package com.seng403.mutanthunt.service;

import com.seng403.mutanthunt.dto.ScoreboardEntry;
import com.seng403.mutanthunt.entity.*;
import com.seng403.mutanthunt.problem.Problem;
import com.seng403.mutanthunt.problem.ProblemRegistry;
import com.seng403.mutanthunt.repository.*;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExportService {

    private static final String BOM = "\uFEFF";

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

    public void exportParticipants(PrintWriter pw, Long sessionId) {
        pw.print(BOM);
        GameSession session = sessionRepo.findById(sessionId).orElseThrow();
        List<Round> rounds = roundRepo.findBySessionIdOrderByCreatedAtDesc(sessionId);
        List<Participant> participants = participantRepo
            .findBySessionIdAndKickedFalseOrderByJoinedAt(sessionId);

        // header
        StringBuilder header = new StringBuilder("Full Name,Student Number,Nickname,Anonymous Name,Joined At");
        for (Round r : rounds) {
            header.append(",").append(csvEscape(r.getName() + " Score"));
            header.append(",").append(csvEscape(r.getName() + " Tests Used"));
        }
        pw.println(header);

        for (Participant p : participants) {
            StringBuilder row = new StringBuilder();
            row.append(csvEscape(p.getFullName())).append(",");
            row.append(csvEscape(p.getStudentNumber())).append(",");
            row.append(csvEscape(p.getNickname() != null ? p.getNickname() : "")).append(",");
            row.append(csvEscape(p.getAnonymousName() != null ? p.getAnonymousName() : "")).append(",");
            row.append(p.getJoinedAt());

            for (Round r : rounds) {
                Problem problem = problemRegistry.get(r.getProblemId());
                GameService.ScoreInfo score = gameService.computeScore(r.getId(), p.getId(), problem);
                long testsUsed = submissionRepo.countByRoundIdAndParticipantId(r.getId(), p.getId());
                row.append(",").append(score.killed()).append("/").append(score.total());
                row.append(",").append(testsUsed);
            }
            pw.println(row);
        }
    }

    public void exportSubmissions(PrintWriter pw, Long roundId) {
        pw.print(BOM);
        Round round = roundRepo.findById(roundId).orElseThrow();
        Problem problem = problemRegistry.get(round.getProblemId());
        List<Submission> subs = submissionRepo.findByRoundIdOrderByCreatedAtDesc(roundId);

        // header
        StringBuilder header = new StringBuilder(
            "Timestamp,Full Name,Student Number,List,Target,Expected,Reference Result,Validity");
        for (var md : problem.getMutants()) {
            header.append(",").append(md.id()).append(" Output");
            header.append(",").append(md.id()).append(" Killed");
        }
        pw.println(header);

        Map<Long, Participant> participantCache = new HashMap<>();

        for (Submission s : subs) {
            Participant p = participantCache.computeIfAbsent(s.getParticipantId(),
                id -> participantRepo.findById(id).orElse(null));
            if (p == null) continue;

            Map<String, String> inputs = gameService.parseRawInputs(s.getRawInputsJson());

            StringBuilder row = new StringBuilder();
            row.append(s.getCreatedAt()).append(",");
            row.append(csvEscape(p.getFullName())).append(",");
            row.append(csvEscape(p.getStudentNumber())).append(",");
            row.append(csvEscape(inputs.getOrDefault("list", ""))).append(",");
            row.append(csvEscape(inputs.getOrDefault("target", ""))).append(",");
            row.append(csvEscape(s.getExpectedValue())).append(",");
            row.append(csvEscape(s.getReferenceResult())).append(",");
            row.append(s.getValidity().name());

            if (s.getValidity() == Validity.VALID) {
                List<MutantResult> results = mutantResultRepo.findBySubmissionId(s.getId());
                Map<String, MutantResult> resultMap = results.stream()
                    .collect(Collectors.toMap(MutantResult::getMutantId, r -> r));
                for (var md : problem.getMutants()) {
                    MutantResult mr = resultMap.get(md.id());
                    if (mr != null) {
                        row.append(",").append(csvEscape(mr.getDisplayOutput()));
                        row.append(",").append(mr.isKilled() ? "YES" : "NO");
                    } else {
                        row.append(",,");
                    }
                }
            } else {
                for (int i = 0; i < problem.getMutants().size(); i++) {
                    row.append(",,");
                }
            }
            pw.println(row);
        }
    }

    private String csvEscape(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }
}
