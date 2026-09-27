package com.seng403.mutanthunt.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seng403.mutanthunt.dto.*;
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
class GameServiceTest {

    @Autowired GameService gameService;
    @Autowired AdminService adminService;
    @Autowired ParticipantRepository participantRepo;
    @Autowired ObjectMapper om;

    private static Long sessionId;
    private static Long roundId;
    private static Participant participant;

    // ── Setup ─────────────────────────────────────────

    @Test @Order(1)
    void setup() {
        GameSession s = adminService.createSession("GS Test");
        adminService.activateSession(s.getId());
        adminService.updateSettings(s.getId(), Map.of("submissionRateLimitMs", "0"));
        sessionId = s.getId();

        Round r = adminService.createRound(sessionId, "GS Round", "indexOf", 0, 0);
        adminService.openRound(r.getId());
        roundId = r.getId();
    }

    // ── Join tests ────────────────────────────────────

    @Test @Order(10)
    void join_success() {
        JoinResponse jr = gameService.join("Alice GS", "55555", null);
        assertTrue(jr.success());
        assertNotNull(jr.token());
        assertEquals("Alice GS", jr.name());

        participant = participantRepo.findByToken(jr.token()).orElseThrow();
        assertNotNull(participant.getAnonymousName());
    }

    @Test @Order(11)
    void join_withNickname() {
        JoinResponse jr = gameService.join("Bob GS", "55556", "BobNick");
        assertTrue(jr.success());
        Participant p = participantRepo.findByToken(jr.token()).orElseThrow();
        assertEquals("BobNick", p.getNickname());
    }

    @Test @Order(12)
    void join_reconnect() {
        String oldToken = participant.getToken();
        JoinResponse jr = gameService.join("Alice Updated", "55555", null);
        assertTrue(jr.success());
        assertNotEquals(oldToken, jr.token());
        assertEquals("Alice Updated", jr.name());
        participant = participantRepo.findByToken(jr.token()).orElseThrow();
    }

    @Test @Order(13)
    void join_closedSession() {
        GameSession session = adminService.getSession(sessionId).orElseThrow();
        adminService.updateSettings(sessionId, Map.of("joinOpen", "false"));
        assertThrows(IllegalStateException.class, () ->
            gameService.join("X", "99999", null)
        );
        adminService.updateSettings(sessionId, Map.of("joinOpen", "true"));
    }

    // ── Submit tests ──────────────────────────────────

    @Test @Order(20)
    void submitTest_validKills() {
        SubmitResponse sr = gameService.submitTest(participant,
            Map.of("list", "5", "target", "5", "expected", "0"));
        assertTrue(sr.success());
        assertEquals("VALID", sr.validity());
        assertTrue(sr.killed() > 0);
    }

    @Test @Order(21)
    void submitTest_invalidOracle() {
        SubmitResponse sr = gameService.submitTest(participant,
            Map.of("list", "5", "target", "5", "expected", "1"));
        assertTrue(sr.success());
        assertEquals("INVALID", sr.validity());
        assertEquals("test.result.invalid.oracle", sr.message());
    }

    @Test @Order(22)
    void submitTest_validationError() {
        SubmitResponse sr = gameService.submitTest(participant,
            Map.of("list", "a,b", "target", "5", "expected", "0"));
        assertFalse(sr.success());
    }

    @Test @Order(23)
    void submitTest_roundClosed() {
        adminService.closeRound(roundId);
        assertThrows(IllegalStateException.class, () ->
            gameService.submitTest(participant,
                Map.of("list", "1", "target", "1", "expected", "0"))
        );
        adminService.revealRound(roundId);
    }

    // ── Score computation ─────────────────────────────

    @Test @Order(30)
    void computeScore() {
        var problem = new com.seng403.mutanthunt.problem.ProblemRegistry().get("indexOf");
        GameService.ScoreInfo score = gameService.computeScore(roundId, participant.getId(), problem);
        assertTrue(score.killed() >= 0);
        assertEquals(7, score.total());
    }

    // ── Display name resolution ───────────────────────

    @Test @Order(40)
    void resolveDisplayName_fullName() {
        assertEquals(participant.getFullName(),
            gameService.resolveDisplayName(participant, AnonymityMode.FULL_NAME));
    }

    @Test @Order(41)
    void resolveDisplayName_anonymous() {
        String name = gameService.resolveDisplayName(participant, AnonymityMode.ANONYMOUS);
        assertEquals(participant.getAnonymousName(), name);
    }

    @Test @Order(42)
    void resolveDisplayName_numberMasked() {
        String name = gameService.resolveDisplayName(participant, AnonymityMode.NUMBER_MASKED);
        assertEquals(participant.getMaskedNumber(), name);
    }

    @Test @Order(43)
    void resolveDisplayName_nicknameWithNickname() {
        participant.setNickname("TestNick");
        participantRepo.save(participant);
        String name = gameService.resolveDisplayName(participant, AnonymityMode.NICKNAME);
        assertEquals("TestNick", name);
    }

    @Test @Order(44)
    void resolveDisplayName_nicknameWithoutNickname() {
        participant.setNickname(null);
        participantRepo.save(participant);
        String name = gameService.resolveDisplayName(participant, AnonymityMode.NICKNAME);
        assertEquals(participant.getAnonymousName(), name);
    }

    // ── Player status ─────────────────────────────────

    @Test @Order(50)
    void getPlayerStatus() {
        PlayerStatus ps = gameService.getPlayerStatus(participant);
        assertNotNull(ps);
        assertEquals(participant.getFullName(), ps.playerName());
        assertNotNull(ps.roundStatus());
    }

    // ── Scoreboard ────────────────────────────────────

    @Test @Order(60)
    void getScoreboard() {
        Round r = adminService.getRounds(sessionId).stream()
            .filter(round -> round.getId().equals(roundId)).findFirst().orElseThrow();
        List<ScoreboardEntry> sb = gameService.getScoreboard(sessionId, r);
        assertNotNull(sb);
    }

    // ── Board data ────────────────────────────────────

    @Test @Order(70)
    void getBoardData() {
        BoardData bd = gameService.getBoardData(sessionId);
        assertNotNull(bd);
        assertNotNull(bd.session());
        assertTrue(bd.participantCount() > 0);
    }

    // ── Analytics ─────────────────────────────────────

    @Test @Order(80)
    void getAnalytics() {
        AnalyticsData ad = gameService.getAnalytics(roundId);
        assertNotNull(ad);
        assertTrue(ad.totalSubmissions() >= 0);
        assertNotNull(ad.mutantKillCounts());
        assertNotNull(ad.mutantNames());
    }

    // ── Parse raw inputs ──────────────────────────────

    @Test @Order(90)
    void parseRawInputs_validJson() {
        Map<String, String> result = gameService.parseRawInputs("{\"list\":\"1,2\",\"target\":\"3\"}");
        assertEquals("1,2", result.get("list"));
        assertEquals("3", result.get("target"));
    }

    @Test @Order(91)
    void parseRawInputs_invalidJson() {
        Map<String, String> result = gameService.parseRawInputs("not json");
        assertTrue(result.isEmpty());
    }

    @Test @Order(92)
    void parseRawInputs_null() {
        Map<String, String> result = gameService.parseRawInputs(null);
        assertTrue(result.isEmpty());
    }

    // ── Active session ────────────────────────────────

    @Test @Order(100)
    void getActiveSession() {
        assertTrue(gameService.getActiveSession().isPresent());
    }

    @Test @Order(101)
    void getOpenRound() {
        assertTrue(gameService.getOpenRound(sessionId).isEmpty());
    }

    @Test @Order(102)
    void getVisibleRound() {
        assertTrue(gameService.getVisibleRound(sessionId).isPresent());
    }
}
