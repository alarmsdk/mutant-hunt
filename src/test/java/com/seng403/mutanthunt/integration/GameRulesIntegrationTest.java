package com.seng403.mutanthunt.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seng403.mutanthunt.entity.*;
import com.seng403.mutanthunt.repository.*;
import com.seng403.mutanthunt.service.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end game rules through the real HTTP API: the kill matrix, scoring and
 * tie-breaks, round visibility, secrecy of mutants before reveal, limits, timers
 * and exports.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GameRulesIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired AdminService adminService;
    @Autowired GameService gameService;
    @Autowired ExportService exportService;
    @Autowired RoundTimerService roundTimerService;
    @Autowired RoundRepository roundRepo;
    @Autowired ParticipantRepository participantRepo;
    @Autowired SubmissionRepository submissionRepo;
    @Autowired MutantResultRepository mutantResultRepo;

    private Long sessionId;
    private int studentCounter;

    @BeforeEach
    void freshSession() {
        GameSession s = adminService.createSession("Rules " + UUID.randomUUID());
        adminService.activateSession(s.getId());
        adminService.updateSettings(s.getId(), Map.of("submissionRateLimitMs", "0", "studentIdLength", "0"));
        sessionId = s.getId();
    }

    // ── Kill matrix (acceptance table from the specification) ──

    @ParameterizedTest(name = "[{0}] target={1} expected={2} → {3}")
    @CsvSource(delimiter = ';', value = {
        "5;      5;    0;  M1 M2 M4",
        ";       3;    -1; M5",
        "1,2,2;  2;    1;  M3 M4",
        "9,4;    4;    1;  M2 M4 M6",
        "1000;   1000; 0;  M1 M2 M4 M7",
        "1,2,3;  5;    -1; M5",
        "5;      5;    1;  INVALID",
    })
    void killMatrix_isReproducedEndToEnd(String list, String target, String expected, String outcome) throws Exception {
        Long roundId = openRound("Matrix", 0, 0);
        Cookie c = join("Matrix Student");

        String validity = submit(c, list == null ? "" : list, target, expected).get("validity").toString();

        Submission sub = submissionRepo.findByRoundIdOrderByCreatedAtDesc(roundId).getFirst();
        List<MutantResult> results = mutantResultRepo.findBySubmissionId(sub.getId());
        if (outcome.equals("INVALID")) {
            assertEquals("INVALID", validity);
            assertTrue(results.isEmpty(), "INVALID tests must not be run against mutants");
            assertEquals(0, playerStatus(c).get("killed"));
        } else {
            assertEquals("VALID", validity);
            assertEquals(7, results.size(), "every mutant result is stored");
            Set<String> killed = results.stream().filter(MutantResult::isKilled)
                .map(MutantResult::getMutantId).collect(Collectors.toCollection(TreeSet::new));
            assertEquals(new TreeSet<>(List.of(outcome.split(" "))), killed);
            results.forEach(r -> assertTrue(r.getMutantOutput() != null || r.getExceptionMessage() != null));
        }
    }

    // ── Scoring ───────────────────────────────────────

    @Test
    void score_countsDistinctMutantsOfValidTestsOnly() throws Exception {
        openRound("Score", 0, 0);
        Cookie c = join("Scorer");
        assertEquals(3, submit(c, "5", "5", "0").get("killed"));        // M1 M2 M4
        assertEquals(3, submit(c, "5", "5", "1").get("killed"));        // INVALID → unchanged
        assertEquals(4, submit(c, "1,2,2", "2", "1").get("killed"));    // + M3 (M4 already)
        Map<String, Object> st = playerStatus(c);
        assertEquals(4, st.get("killed"));
        assertEquals(3, st.get("testsUsed"));
    }

    @Test
    void scoreboard_tieBreaksOnTestsUsedThenTime() throws Exception {
        Long roundId = openRound("Ties", 0, 0);
        Cookie a = join("Ada");
        Cookie b = join("Bob");
        Cookie c = join("Cem");
        submit(a, "5", "5", "0");                 // 3 kills, 1 test
        submit(b, "5", "5", "7");                 // INVALID
        submit(b, "5", "5", "0");                 // 3 kills, 2 tests
        submit(c, "5", "5", "0");                 // 3 kills, 1 test, later than Ada

        List<String> order = gameService.getScoreboard(sessionId, roundRepo.findById(roundId).orElseThrow())
            .stream().map(e -> e.displayName()).toList();
        assertEquals(List.of("Ada", "Cem", "Bob"), order);
    }

    @Test
    void testLimit_cannotBeExceededByConcurrentRequests() throws Exception {
        Long roundId = openRound("Limit", 3, 0);
        Cookie c = join("Fast Tapper");
        Participant p = participantRepo.findByToken(c.getValue()).orElseThrow();

        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            futures.add(pool.submit(() -> gameService.submitTest(p, Map.of("list", "1", "target", "1", "expected", "0"))));
        }
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(3, submissionRepo.countByRoundIdAndParticipantId(roundId, p.getId()));
        assertEquals("test.limit.reached", submit(c, "1", "1", "0").get("message"));
    }

    @Test
    void rateLimit_isEnforcedPerStudent() throws Exception {
        adminService.updateSettings(sessionId, Map.of("submissionRateLimitMs", "60000"));
        openRound("Rate", 0, 0);
        Cookie c = join("Spammer");
        assertEquals(true, submit(c, "1", "1", "0").get("success"));
        assertEquals("test.rate.limited", submit(c, "1", "1", "0").get("message"));
        Cookie other = join("Other");
        assertEquals(true, submit(other, "1", "1", "0").get("success"));
    }

    @Test
    void extraJsonFields_areNeverStored() throws Exception {
        Long roundId = openRound("Sanitize", 0, 0);
        Cookie c = join("Hacker");
        mvc.perform(post("/api/submit").cookie(c).contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("list", "1", "target", "1", "expected", "0",
                    "junk", "x".repeat(5000)))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
        String raw = submissionRepo.findByRoundIdOrderByCreatedAtDesc(roundId).getFirst().getRawInputsJson();
        assertFalse(raw.contains("junk"));
    }

    @Test
    void submission_storesSnapshotOfStudentAndSession() throws Exception {
        adminService.updateSettings(sessionId, Map.of("anonymityMode", "NUMBER_MASKED"));
        Long roundId = openRound("Snapshot", 0, 0);
        Cookie c = join("Snap Shot", "2021123445");
        submit(c, "1", "1", "0");
        Submission s = submissionRepo.findByRoundIdOrderByCreatedAtDesc(roundId).getFirst();
        assertEquals(sessionId, s.getSessionId());
        assertEquals("Snap Shot", s.getParticipantName());
        assertEquals("2021123445", s.getStudentNumber());
        assertEquals("2021****45", s.getDisplayName());
    }

    // ── Round visibility ──────────────────────────────

    @Test
    void students_alwaysSeeTheLatestRound() throws Exception {
        Long r1 = openRound("Round 1", 10, 0);
        Cookie c = join("Viewer");
        adminService.closeRound(r1);
        adminService.revealRound(r1);
        Long r2 = openRound("Round 2", 4, 0);

        Map<String, Object> st = playerStatus(c);
        assertEquals("Round 2", st.get("roundName"));
        assertEquals("OPEN", st.get("roundStatus"));

        adminService.closeRound(r2);
        // a new DRAFT round does not replace the round being discussed
        adminService.createRound(sessionId, "Round 3", "indexOf", 0, 0);
        assertEquals("Round 2", playerStatus(c).get("roundName"));
        assertEquals("Round 2", ((Map<?, ?>) board().get("round")).get("name"));
    }

    @Test
    void onlyOneRoundIsOpenPerSession() {
        Long r1 = openRound("A", 0, 0);
        Long r2 = openRound("B", 0, 0);
        assertEquals(RoundStatus.CLOSED, roundRepo.findById(r1).orElseThrow().getStatus());
        assertEquals(RoundStatus.OPEN, roundRepo.findById(r2).orElseThrow().getStatus());
    }

    // ── Secrecy before reveal ─────────────────────────

    @Test
    void mutantDetails_areNotLeakedBeforeReveal() throws Exception {
        adminService.updateSettings(sessionId, Map.of("mutantPanelMode", "REVEALED"));
        Long roundId = openRound("Secret", 0, 0);
        Cookie c = join("Curious", "2021000777");
        submit(c, "5", "5", "0");
        submit(c, "5", "5", "1");
        gameService.flushScoreboards();

        List<String> secrets = List.of("start-at-one", "skip-last", "last-occurrence", "Loop starts",
            "mutantResults\":[", "referenceCode\":\"", "shortName", "\"code\"");
        for (String json : List.of(statusJson(c), boardJson())) {
            for (String secret : secrets) assertFalse(json.contains(secret), secret + " leaked: " + json);
            assertFalse(json.contains("2021000777"), "student number leaked");
        }

        adminService.closeRound(roundId);
        assertFalse(boardJson().contains("start-at-one"));
        adminService.revealRound(roundId);

        String revealed = statusJson(c);
        assertTrue(revealed.contains("start-at-one"));
        assertTrue(revealed.contains("mutantResults\":["));
        assertTrue(boardJson().contains("start-at-one"));
        assertFalse(boardJson().contains("2021000777"));
    }

    @Test
    void boardCountOnlyMode_showsCountsWithoutNames() throws Exception {
        adminService.updateSettings(sessionId, Map.of("mutantPanelMode", "COUNT_ONLY"));
        openRound("Counts", 0, 0);
        submit(join("K1"), "5", "5", "0");
        submit(join("K2"), "1000", "1000", "0");
        List<Map<String, Object>> panel = castList(board().get("mutantPanel"));
        Map<String, Object> m1 = panel.stream().filter(m -> m.get("id").equals("M1")).findFirst().orElseThrow();
        Map<String, Object> m7 = panel.stream().filter(m -> m.get("id").equals("M7")).findFirst().orElseThrow();
        assertEquals(2, m1.get("killCount"));
        assertEquals(1, m7.get("killCount"));
        assertNull(m1.get("shortName"));
    }

    @Test
    void boardHiddenMode_hasNoMutantPanel() throws Exception {
        openRound("Hidden", 0, 0);
        assertNull(board().get("mutantPanel"));
    }

    @Test
    void board_isPublicAndUsesAnonymityMode() throws Exception {
        adminService.updateSettings(sessionId, Map.of("anonymityMode", "NICKNAME"));
        openRound("Nicks", 0, 0);
        Cookie c = joinWithNick("Real Name", "Neo");
        submit(c, "5", "5", "0");
        String json = boardJson();
        assertTrue(json.contains("Neo"));
        assertFalse(json.contains("Real Name"));
    }

    // ── Timer ─────────────────────────────────────────

    @Test
    void timeLimit_rejectsLateSubmissionsAndClosesTheRound() throws Exception {
        Long roundId = openRound("Timed", 0, 60);
        Cookie c = join("Late");
        assertEquals(true, submit(c, "1", "1", "0").get("success"));

        Round r = roundRepo.findById(roundId).orElseThrow();
        r.setOpenedAt(Instant.now().minusSeconds(120));     // pretend the minute is long over
        roundRepo.save(r);

        mvc.perform(post("/api/submit").cookie(c).contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("list", "1", "target", "1", "expected", "0"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("test.round.timeUp"));

        roundTimerService.closeExpiredRounds();
        assertEquals(RoundStatus.CLOSED, roundRepo.findById(roundId).orElseThrow().getStatus());
    }

    @Test
    void remainingTime_isReportedToStudentsAndBoard() throws Exception {
        openRound("Countdown", 0, 300);
        Cookie c = join("Clock");
        long remaining = ((Number) playerStatus(c).get("remainingMs")).longValue();
        assertTrue(remaining > 290_000 && remaining <= 300_000, "remaining=" + remaining);
        assertNotNull(((Map<?, ?>) board().get("round")).get("remainingMs"));
    }

    // ── Participants ──────────────────────────────────

    @Test
    void kickedStudent_cannotContinueOrRejoin() throws Exception {
        openRound("Kick", 0, 0);
        Cookie c = join("Troll", "5550001");
        Participant p = participantRepo.findByToken(c.getValue()).orElseThrow();
        adminService.kickParticipant(p.getId());

        mvc.perform(get("/api/status").cookie(c)).andExpect(status().isForbidden());
        mvc.perform(post("/api/submit").cookie(c).contentType(MediaType.APPLICATION_JSON)
                .content("{\"list\":\"1\",\"target\":\"1\",\"expected\":\"0\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/join").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("fullName", "Troll", "studentNumber", "5550001"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("join.error.kicked"));
    }

    @Test
    void join_validatesInputOnTheServer() throws Exception {
        adminService.updateSettings(sessionId, Map.of("studentIdLength", "10"));
        expectJoinError("", "2021000001", "join.error.name.required");
        expectJoinError("Ali", "", "join.error.studentId.required");
        expectJoinError("Ali", "20210000ab", "join.error.studentId.numeric");
        expectJoinError("Ali", "12345", "join.error.studentId.length");
        expectJoinError("x".repeat(81), "2021000001", "join.error.name.tooLong");
    }

    @Test
    void join_doesNotExposeTheTokenInTheBody() throws Exception {
        mvc.perform(post("/api/join").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("fullName", "Tok En", "studentNumber", "4040404"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").doesNotExist())
            .andExpect(header().string("Set-Cookie", allOf(
                containsString("player_token="), containsString("HttpOnly"),
                containsString("Secure"), containsString("SameSite=Lax"))));
    }

    // ── Export ────────────────────────────────────────

    @Test
    void export_sessionAndRoundScopes() throws Exception {
        Long r1 = openRound("Exp 1", 0, 0);
        Cookie c = join("=HYPERLINK(\"x\")", "7000001");
        submit(c, "5", "5", "0");
        submit(c, "5", "5", "1");
        Long r2 = openRound("Exp 2", 0, 0);
        submit(c, "", "3", "-1");

        String sessionSubs = csv(w -> exportService.exportSessionSubmissions(w, sessionId));
        assertTrue(sessionSubs.startsWith("﻿"));
        assertEquals(4, sessionSubs.split("\r\n").length, "header + 3 submissions");
        assertTrue(sessionSubs.contains("Exp 1") && sessionSubs.contains("Exp 2"));
        assertTrue(sessionSubs.contains("INVALID"));
        assertTrue(sessionSubs.contains("M1 M2 M4"));
        assertTrue(sessionSubs.contains("'=HYPERLINK"), "formula must be neutralised");

        String roundSubs = csv(w -> exportService.exportSubmissions(w, r2));
        assertEquals(2, roundSubs.split("\r\n").length);

        String roundParts = csv(w -> exportService.exportParticipantsForRound(w, r1));
        assertTrue(roundParts.contains("Exp 1 Score"));
        assertFalse(roundParts.contains("Exp 2 Score"));
        assertTrue(roundParts.split("\r\n")[1].contains(",3,7,2,1"), roundParts);

        String sessionParts = csv(w -> exportService.exportParticipants(w, sessionId));
        assertTrue(sessionParts.contains("Exp 1 Score") && sessionParts.contains("Exp 2 Score"));

        for (String url : List.of("/admin/api/sessions/" + sessionId + "/export/submissions",
                                  "/admin/api/rounds/" + r1 + "/export/participants")) {
            MvcResult res = mvc.perform(get(url).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andReturn();
            byte[] bytes = res.getResponse().getContentAsByteArray();
            assertArrayEquals(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, Arrays.copyOf(bytes, 3), "UTF-8 BOM");
        }
    }

    @Test
    void adminSubmissionsEndpoint_filters() throws Exception {
        Long r1 = openRound("F1", 0, 0);
        Cookie a = join("Filter A");
        Cookie b = join("Filter B");
        submit(a, "5", "5", "0");
        submit(a, "5", "5", "9");
        submit(b, "5", "5", "0");
        Long pa = participantRepo.findByToken(a.getValue()).orElseThrow().getId();

        mvc.perform(get("/admin/api/sessions/" + sessionId + "/submissions")
                .param("roundId", r1.toString()).param("participantId", pa.toString()).param("validity", "INVALID")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].participantName").value("Filter A"))
            .andExpect(jsonPath("$[0].killedMutants", hasSize(0)));

        mvc.perform(get("/admin/api/sessions/" + sessionId + "/participants").with(user("admin").roles("ADMIN")))
            .andExpect(jsonPath("$.participants[0].scores[0].score").value(3))
            .andExpect(jsonPath("$.participants[0].scores[0].testsUsed").value(2));
    }

    // ── HTTP details ──────────────────────────────────

    @Test
    void sseStreams_disableProxyBuffering() throws Exception {
        mvc.perform(get("/sse/board"))
            .andExpect(header().string("X-Accel-Buffering", "no"))
            .andExpect(header().string("Cache-Control", containsString("no-cache")));
    }

    @Test
    void adminLogout_worksWithPostAndCsrf() throws Exception {
        mvc.perform(post("/admin/logout").with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/login?logout"));
    }

    @Test
    void adminPage_withoutLogin_redirectsToLogin() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().is3xxRedirection());
    }

    @Test
    void adminApi_rejectsInvalidSettingsWithJsonError() throws Exception {
        mvc.perform(post("/admin/api/sessions/" + sessionId + "/settings")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"anonymityMode\":\"NOPE\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("admin.error.settings.invalid"));
    }

    @Test
    void adminApi_requiresCsrfToken() throws Exception {
        mvc.perform(post("/admin/api/sessions").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
            .andExpect(status().isForbidden());
    }

    // ── Helpers ───────────────────────────────────────

    private Long openRound(String name, int testLimit, int timeLimitSeconds) {
        Round r = adminService.createRound(sessionId, name, "indexOf", testLimit, timeLimitSeconds);
        return adminService.openRound(r.getId()).getId();
    }

    private Cookie join(String name) throws Exception {
        return join(name, String.valueOf(900000 + (++studentCounter)));
    }

    private Cookie join(String name, String number) throws Exception {
        return joinBody(Map.of("fullName", name, "studentNumber", number));
    }

    private Cookie joinWithNick(String name, String nick) throws Exception {
        return joinBody(Map.of("fullName", name, "studentNumber", String.valueOf(800000 + (++studentCounter)),
            "nickname", nick));
    }

    private Cookie joinBody(Map<String, String> body) throws Exception {
        MvcResult r = mvc.perform(post("/api/join").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(body)))
            .andExpect(status().isOk()).andReturn();
        Cookie c = r.getResponse().getCookie("player_token");
        assertNotNull(c);
        return new Cookie(c.getName(), c.getValue());
    }

    private void expectJoinError(String name, String number, String key) throws Exception {
        mvc.perform(post("/api/join").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("fullName", name, "studentNumber", number))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value(key));
    }

    private Map<String, Object> submit(Cookie c, String list, String target, String expected) throws Exception {
        String json = mvc.perform(post("/api/submit").cookie(c).contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("list", list, "target", target, "expected", expected))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return om.readValue(json, new TypeReference<>() {});
    }

    private String statusJson(Cookie c) throws Exception {
        return mvc.perform(get("/api/status").cookie(c)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    private Map<String, Object> playerStatus(Cookie c) throws Exception {
        return om.readValue(statusJson(c), new TypeReference<>() {});
    }

    private String boardJson() throws Exception {
        // deliberately without any authentication: the projector PC is not logged in
        return mvc.perform(get("/api/board")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    private Map<String, Object> board() throws Exception {
        return om.readValue(boardJson(), new TypeReference<>() {});
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object o) {
        return (List<Map<String, Object>>) o;
    }

    private static String csv(java.util.function.Consumer<PrintWriter> writer) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        writer.accept(pw);
        pw.flush();
        return sw.toString();
    }
}
