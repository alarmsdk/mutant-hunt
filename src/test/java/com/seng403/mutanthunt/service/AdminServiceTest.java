package com.seng403.mutanthunt.service;

import com.seng403.mutanthunt.entity.*;
import com.seng403.mutanthunt.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminServiceTest {

    @Autowired AdminService adminService;
    @Autowired GameService gameService;
    @Autowired ParticipantRepository participantRepo;

    private static Long sessionId;
    private static Long roundId;

    // ── Session CRUD ──────────────────────────────────

    @Test @Order(1)
    void createSession() {
        GameSession s = adminService.createSession("Admin Test");
        assertNotNull(s.getId());
        assertEquals("Admin Test", s.getName());
        assertFalse(s.isActive());
        assertFalse(s.isArchived());
        sessionId = s.getId();
    }

    @Test @Order(2)
    void activateSession() {
        adminService.activateSession(sessionId);
        GameSession s = adminService.getSession(sessionId).orElseThrow();
        assertTrue(s.isActive());
    }

    @Test @Order(3)
    void activateSession_deactivatesOther() {
        GameSession s2 = adminService.createSession("Other");
        adminService.activateSession(s2.getId());

        GameSession original = adminService.getSession(sessionId).orElseThrow();
        assertFalse(original.isActive());

        adminService.activateSession(sessionId);
        GameSession other = adminService.getSession(s2.getId()).orElseThrow();
        assertFalse(other.isActive());
    }

    @Test @Order(4)
    void getActiveSessions() {
        List<GameSession> active = adminService.getActiveSessions();
        assertFalse(active.isEmpty());
    }

    @Test @Order(5)
    void updateSettings() {
        adminService.updateSettings(sessionId, Map.of(
            "anonymityMode", "ANONYMOUS",
            "joinOpen", "false",
            "studentIdLength", "10",
            "mutantPanelMode", "REVEALED",
            "submissionRateLimitMs", "0"
        ));
        GameSession s = adminService.getSession(sessionId).orElseThrow();
        assertEquals(AnonymityMode.ANONYMOUS, s.getAnonymityMode());
        assertFalse(s.isJoinOpen());
        assertEquals(10, s.getStudentIdLength());
        assertEquals(MutantPanelMode.REVEALED, s.getMutantPanelMode());
        assertEquals(0, s.getSubmissionRateLimitMs());

        adminService.updateSettings(sessionId, Map.of(
            "joinOpen", "true", "studentIdLength", "0"
        ));
    }

    // ── Round management ──────────────────────────────

    @Test @Order(10)
    void createRound() {
        Round r = adminService.createRound(sessionId, "Round 1", "indexOf", 10, 120);
        assertNotNull(r.getId());
        assertEquals("Round 1", r.getName());
        assertEquals("indexOf", r.getProblemId());
        assertEquals(10, r.getTestLimit());
        assertEquals(120, r.getTimeLimitSeconds());
        assertEquals(RoundStatus.DRAFT, r.getStatus());
        roundId = r.getId();
    }

    @Test @Order(11)
    void createRound_nullLimits() {
        Round r = adminService.createRound(sessionId, "Unlimited", "indexOf", null, null);
        assertNull(r.getTestLimit());
        assertNull(r.getTimeLimitSeconds());
        assertTrue(r.isUnlimited());
    }

    @Test @Order(12)
    void createRound_zeroLimits() {
        Round r = adminService.createRound(sessionId, "Zero", "indexOf", 0, 0);
        assertNull(r.getTestLimit());
        assertNull(r.getTimeLimitSeconds());
    }

    @Test @Order(13)
    void getRounds() {
        List<Round> rounds = adminService.getRounds(sessionId);
        assertTrue(rounds.size() >= 1);
    }

    @Test @Order(14)
    void openRound() {
        Round r = adminService.openRound(roundId);
        assertEquals(RoundStatus.OPEN, r.getStatus());
        assertNotNull(r.getOpenedAt());
    }

    @Test @Order(15)
    void openRound_fromNonDraft() {
        assertThrows(IllegalStateException.class, () -> adminService.openRound(roundId));
    }

    @Test @Order(16)
    void updateRound_whileOpen() {
        Round r = adminService.updateRound(roundId, "Renamed Round", 20, 300);
        assertEquals("Renamed Round", r.getName());
        assertEquals(20, r.getTestLimit());
        assertEquals(300, r.getTimeLimitSeconds());
    }

    @Test @Order(17)
    void closeRound() {
        Round r = adminService.closeRound(roundId);
        assertEquals(RoundStatus.CLOSED, r.getStatus());
        assertNotNull(r.getClosedAt());
    }

    @Test @Order(18)
    void closeRound_fromNonOpen() {
        assertThrows(IllegalStateException.class, () -> adminService.closeRound(roundId));
    }

    @Test @Order(19)
    void revealRound() {
        Round r = adminService.revealRound(roundId);
        assertEquals(RoundStatus.REVEALED, r.getStatus());
    }

    @Test @Order(20)
    void revealRound_fromNonClosed() {
        assertThrows(IllegalStateException.class, () -> adminService.revealRound(roundId));
    }

    // ── Participant management ────────────────────────

    @Test @Order(30)
    void participantManagement() {
        adminService.updateSettings(sessionId, Map.of("submissionRateLimitMs", "0"));
        gameService.join("Kick Test", "88888", null);

        List<Participant> participants = adminService.getParticipants(sessionId);
        assertFalse(participants.isEmpty());

        Participant p = participants.stream()
            .filter(pp -> "88888".equals(pp.getStudentNumber()))
            .findFirst().orElseThrow();

        adminService.updateNickname(p.getId(), "KickNick");
        Participant updated = participantRepo.findById(p.getId()).orElseThrow();
        assertEquals("KickNick", updated.getNickname());

        adminService.kickParticipant(p.getId());
        Participant kicked = participantRepo.findById(p.getId()).orElseThrow();
        assertTrue(kicked.isKicked());
    }

    // ── Submissions ───────────────────────────────────

    @Test @Order(40)
    void getSubmissions() {
        List<Submission> subs = adminService.getSubmissions(roundId);
        assertNotNull(subs);
    }

    // ── Archive and delete ────────────────────────────

    @Test @Order(50)
    void archiveSession() {
        GameSession s = adminService.createSession("ToArchive");
        adminService.archiveSession(s.getId());
        GameSession archived = adminService.getSession(s.getId()).orElseThrow();
        assertTrue(archived.isArchived());
        assertFalse(archived.isActive());
    }

    @Test @Order(51)
    void getArchivedSessions() {
        List<GameSession> archived = adminService.getArchivedSessions();
        assertFalse(archived.isEmpty());
    }

    @Test @Order(52)
    void deleteSession() {
        GameSession s = adminService.createSession("ToDelete");
        Long id = s.getId();
        adminService.deleteSession(id);
        assertTrue(adminService.getSession(id).isEmpty());
    }

    // ── Board command ─────────────────────────────────

    @Test @Order(60)
    void sendBoardCommand() {
        assertDoesNotThrow(() -> adminService.sendBoardCommand("SHOW_QR"));
    }
}
