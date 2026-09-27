package com.seng403.mutanthunt.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GameFlowIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    private static Long sessionId;
    private static Long roundId;
    private static Cookie playerCookie;

    // ── 1. Create session ─────────────────────────────

    @Test @Order(1)
    void adminCreatesSession() throws Exception {
        MvcResult r = mvc.perform(post("/admin/api/sessions")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("name", "Test Session"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNumber())
            .andReturn();

        Map<String, Object> body = om.readValue(r.getResponse().getContentAsString(),
            new TypeReference<>() {});
        sessionId = ((Number) body.get("id")).longValue();
    }

    @Test @Order(2)
    void adminActivatesAndConfiguresSession() throws Exception {
        mvc.perform(post("/admin/api/sessions/" + sessionId + "/activate")
                .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk());

        // disable rate limit for tests
        mvc.perform(post("/admin/api/sessions/" + sessionId + "/settings")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("submissionRateLimitMs", "0"))))
            .andExpect(status().isOk());
    }

    // ── 2. Create and open round ──────────────────────

    @Test @Order(3)
    void adminCreatesRound() throws Exception {
        MvcResult r = mvc.perform(post("/admin/api/sessions/" + sessionId + "/rounds")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                    "name", "Round 1",
                    "problemId", "indexOf",
                    "testLimit", "10"
                ))))
            .andExpect(status().isOk())
            .andReturn();

        Map<String, Object> body = om.readValue(r.getResponse().getContentAsString(),
            new TypeReference<>() {});
        roundId = ((Number) body.get("id")).longValue();
    }

    @Test @Order(4)
    void adminOpensRound() throws Exception {
        mvc.perform(post("/admin/api/rounds/" + roundId + "/open")
                .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("OPEN"));
    }

    // ── 3. Student joins ──────────────────────────────

    @Test @Order(5)
    void studentJoins() throws Exception {
        MvcResult r = mvc.perform(post("/api/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                    "fullName", "Alice Test",
                    "studentNumber", "2021001234"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn();

        jakarta.servlet.http.Cookie c = r.getResponse().getCookie("player_token");
        Assertions.assertNotNull(c, "player_token cookie must be set");
        playerCookie = new Cookie(c.getName(), c.getValue());
    }

    // ── 4. Student submits valid test ─────────────────

    @Test @Order(6)
    void studentSubmitsValidTest() throws Exception {
        mvc.perform(post("/api/submit")
                .cookie(playerCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                    "list", "5",
                    "target", "5",
                    "expected", "0"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.validity").value("VALID"))
            .andExpect(jsonPath("$.killed").value(greaterThan(0)));
    }

    // ── 5. Student submits invalid test ───────────────

    @Test @Order(7)
    void studentSubmitsInvalidTest() throws Exception {
        mvc.perform(post("/api/submit")
                .cookie(playerCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                    "list", "5",
                    "target", "5",
                    "expected", "1"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.validity").value("INVALID"))
            .andExpect(jsonPath("$.message").value("test.result.invalid.oracle"));
    }

    // ── 6. Check score doesn't count invalid ──────────

    @Test @Order(8)
    void scoreUnaffectedByInvalidTest() throws Exception {
        mvc.perform(get("/api/status")
                .cookie(playerCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.killed").value(greaterThan(0)))
            .andExpect(jsonPath("$.testsUsed").value(2));
    }

    // ── 7. Close and reveal ───────────────────────────

    @Test @Order(9)
    void adminClosesRound() throws Exception {
        mvc.perform(post("/admin/api/rounds/" + roundId + "/close")
                .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test @Order(10)
    void studentCannotSubmitAfterClose() throws Exception {
        // round is closed → should get a bad-request error
        mvc.perform(post("/api/submit")
                .cookie(playerCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                    "list", "1,2,3",
                    "target", "5",
                    "expected", "-1"
                ))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("test.round.closed"));
    }

    @Test @Order(11)
    void adminRevealsRound() throws Exception {
        mvc.perform(post("/admin/api/rounds/" + roundId + "/reveal")
                .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REVEALED"));
    }

    @Test @Order(12)
    void studentSeesRevealedResults() throws Exception {
        mvc.perform(get("/api/status")
                .cookie(playerCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roundStatus").value("REVEALED"))
            .andExpect(jsonPath("$.mutantInfos").isArray())
            .andExpect(jsonPath("$.submissions[0].mutantResults").isArray());
    }

    // ── 8. CSV export ─────────────────────────────────

    @Test @Order(13)
    void exportParticipants() throws Exception {
        mvc.perform(get("/admin/api/sessions/" + sessionId + "/export/participants")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", containsString("text/csv")));
    }

    @Test @Order(14)
    void exportSubmissions() throws Exception {
        mvc.perform(get("/admin/api/rounds/" + roundId + "/export/submissions")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", containsString("text/csv")));
    }

    // ── 9. Test limit enforcement ─────────────────────

    @Test @Order(15)
    void testLimitEnforced() throws Exception {
        // Create a new round with limit 1, open it
        MvcResult r = mvc.perform(post("/admin/api/sessions/" + sessionId + "/rounds")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                    "name", "Limit Round",
                    "problemId", "indexOf",
                    "testLimit", "1"
                ))))
            .andExpect(status().isOk()).andReturn();

        Map<String, Object> body = om.readValue(r.getResponse().getContentAsString(),
            new TypeReference<>() {});
        Long limitRoundId = ((Number) body.get("id")).longValue();

        mvc.perform(post("/admin/api/rounds/" + limitRoundId + "/open")
                .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk());

        // first submission OK
        mvc.perform(post("/api/submit")
                .cookie(playerCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                    "list", "", "target", "3", "expected", "-1"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.validity").exists());

        // second submission should be blocked by test limit
        mvc.perform(post("/api/submit")
                .cookie(playerCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                    "list", "1,2", "target", "1", "expected", "0"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("test.limit.reached"));
    }
}
