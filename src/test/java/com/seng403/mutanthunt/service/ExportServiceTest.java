package com.seng403.mutanthunt.service;

import com.seng403.mutanthunt.dto.JoinResponse;
import com.seng403.mutanthunt.entity.*;
import com.seng403.mutanthunt.repository.ParticipantRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExportServiceTest {

    @Autowired ExportService exportService;
    @Autowired AdminService adminService;
    @Autowired GameService gameService;
    @Autowired ParticipantRepository participantRepo;

    private static Long sessionId;
    private static Long roundId;

    @Test @Order(1)
    void setup() {
        GameSession s = adminService.createSession("Export Test");
        adminService.activateSession(s.getId());
        adminService.updateSettings(s.getId(), Map.of("submissionRateLimitMs", "0"));
        sessionId = s.getId();

        Round r = adminService.createRound(sessionId, "Export Round", "indexOf", 0, 0);
        adminService.openRound(r.getId());
        roundId = r.getId();

        JoinResponse jr = gameService.join("Export Alice", "77770", null);
        Participant p = participantRepo.findByToken(jr.token()).orElseThrow();

        gameService.submitTest(p,
            Map.of("list", "5", "target", "5", "expected", "0"));
        gameService.submitTest(p,
            Map.of("list", "5", "target", "5", "expected", "1"));
    }

    @Test @Order(2)
    void exportParticipants_containsHeaders() {
        StringWriter sw = new StringWriter();
        exportService.exportParticipants(new PrintWriter(sw), sessionId);
        String csv = sw.toString();
        assertTrue(csv.contains("Full Name"));
        assertTrue(csv.contains("Student Number"));
        assertTrue(csv.contains("Joined At"));
    }

    @Test @Order(3)
    void exportParticipants_containsData() {
        StringWriter sw = new StringWriter();
        exportService.exportParticipants(new PrintWriter(sw), sessionId);
        String csv = sw.toString();
        assertTrue(csv.contains("Export Alice"));
        assertTrue(csv.contains("77770"));
    }

    @Test @Order(4)
    void exportParticipants_containsScoreColumns() {
        StringWriter sw = new StringWriter();
        exportService.exportParticipants(new PrintWriter(sw), sessionId);
        String csv = sw.toString();
        assertTrue(csv.contains("Export Round Score"));
        assertTrue(csv.contains("Export Round Tests Used"));
    }

    @Test @Order(5)
    void exportSubmissions_containsHeaders() {
        StringWriter sw = new StringWriter();
        exportService.exportSubmissions(new PrintWriter(sw), roundId);
        String csv = sw.toString();
        assertTrue(csv.contains("Timestamp"));
        assertTrue(csv.contains("Full Name"));
        assertTrue(csv.contains("Validity"));
        assertTrue(csv.contains("M1 Output"));
        assertTrue(csv.contains("M7 Killed"));
    }

    @Test @Order(6)
    void exportSubmissions_containsData() {
        StringWriter sw = new StringWriter();
        exportService.exportSubmissions(new PrintWriter(sw), roundId);
        String csv = sw.toString();
        assertTrue(csv.contains("Export Alice"));
        assertTrue(csv.contains("VALID"));
    }

    @Test @Order(7)
    void exportSubmissions_hasBOM() {
        StringWriter sw = new StringWriter();
        exportService.exportSubmissions(new PrintWriter(sw), roundId);
        String csv = sw.toString();
        assertTrue(csv.startsWith("\uFEFF"));
    }

    @Test @Order(8)
    void exportParticipants_hasBOM() {
        StringWriter sw = new StringWriter();
        exportService.exportParticipants(new PrintWriter(sw), sessionId);
        String csv = sw.toString();
        assertTrue(csv.startsWith("\uFEFF"));
    }
}
