package com.seng403.mutanthunt.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminApiControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    private static Long sessionId;
    private static Long roundId;

    // ── Session CRUD ──────────────────────────────────

    @Test @Order(1)
    void createSession() throws Exception {
        MvcResult r = mvc.perform(post("/admin/api/sessions")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("name", "Coverage Session"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.name").value("Coverage Session"))
            .andReturn();

        Map<String, Object> body = om.readValue(r.getResponse().getContentAsString(),
            new TypeReference<>() {});
        sessionId = ((Number) body.get("id")).longValue();
    }

    @Test @Order(2)
    void getSessions() throws Exception {
        mvc.perform(get("/admin/api/sessions")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test @Order(3)
    void activateSession() throws Exception {
        mvc.perform(post("/admin/api/sessions/" + sessionId + "/activate")
                .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test @Order(4)
    void getSettings() throws Exception {
        mvc.perform(get("/admin/api/sessions/" + sessionId + "/settings")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.anonymityMode").value("FULL_NAME"))
            .andExpect(jsonPath("$.joinOpen").value(true));
    }

    @Test @Order(5)
    void updateSettings_anonymityMode() throws Exception {
        mvc.perform(post("/admin/api/sessions/" + sessionId + "/settings")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                    "anonymityMode", "NICKNAME",
                    "submissionRateLimitMs", "0"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.anonymityMode").value("NICKNAME"));
    }

    @Test @Order(6)
    void updateSettings_mutantPanelMode() throws Exception {
        mvc.perform(post("/admin/api/sessions/" + sessionId + "/settings")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("mutantPanelMode", "COUNT_ONLY"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mutantPanelMode").value("COUNT_ONLY"));
    }

    @Test @Order(7)
    void updateSettings_joinAndIdLength() throws Exception {
        mvc.perform(post("/admin/api/sessions/" + sessionId + "/settings")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("joinOpen", "true", "studentIdLength", "0"))))
            .andExpect(status().isOk());
    }

    // ── Round CRUD ────────────────────────────────────

    @Test @Order(10)
    void createRound() throws Exception {
        MvcResult r = mvc.perform(post("/admin/api/sessions/" + sessionId + "/rounds")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                    "name", "Coverage Round",
                    "problemId", "indexOf",
                    "testLimit", "10",
                    "timeLimitSeconds", "120"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Coverage Round"))
            .andExpect(jsonPath("$.testLimit").value(10))
            .andExpect(jsonPath("$.timeLimitSeconds").value(120))
            .andReturn();

        Map<String, Object> body = om.readValue(r.getResponse().getContentAsString(),
            new TypeReference<>() {});
        roundId = ((Number) body.get("id")).longValue();
    }

    @Test @Order(11)
    void getRounds() throws Exception {
        mvc.perform(get("/admin/api/sessions/" + sessionId + "/rounds")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].name").value("Coverage Round"));
    }

    @Test @Order(12)
    void updateRound() throws Exception {
        mvc.perform(put("/admin/api/rounds/" + roundId)
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                    "name", "Updated Round",
                    "testLimit", "15"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Updated Round"))
            .andExpect(jsonPath("$.testLimit").value(15));
    }

    @Test @Order(13)
    void openRound() throws Exception {
        mvc.perform(post("/admin/api/rounds/" + roundId + "/open")
                .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test @Order(14)
    void closeRound() throws Exception {
        mvc.perform(post("/admin/api/rounds/" + roundId + "/close")
                .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test @Order(15)
    void revealRound() throws Exception {
        mvc.perform(post("/admin/api/rounds/" + roundId + "/reveal")
                .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REVEALED"));
    }

    // ── Analytics, Submissions, Participants ──────────

    @Test @Order(16)
    void getSubmissions() throws Exception {
        mvc.perform(get("/admin/api/rounds/" + roundId + "/submissions")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test @Order(17)
    void getAnalytics() throws Exception {
        mvc.perform(get("/admin/api/rounds/" + roundId + "/analytics")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSubmissions").isNumber())
            .andExpect(jsonPath("$.mutantKillCounts").isMap());
    }

    @Test @Order(18)
    void getParticipants() throws Exception {
        mvc.perform(get("/admin/api/sessions/" + sessionId + "/participants")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rounds").isArray())
            .andExpect(jsonPath("$.participants").isArray());
    }

    // ── Problems ──────────────────────────────────────

    @Test @Order(19)
    void getProblems() throws Exception {
        mvc.perform(get("/admin/api/problems")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].id").value("indexOf"));
    }

    // ── Board data ────────────────────────────────────

    @Test @Order(20)
    void getBoardData() throws Exception {
        mvc.perform(get("/admin/api/board-data")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hasSession").value(true));
    }

    // ── Board command ─────────────────────────────────

    @Test @Order(21)
    void boardCommand() throws Exception {
        mvc.perform(post("/admin/api/board-command")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("command", "SHOW_QR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    // ── Export ─────────────────────────────────────────

    @Test @Order(22)
    void exportParticipants() throws Exception {
        mvc.perform(get("/admin/api/sessions/" + sessionId + "/export/participants")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", containsString("text/csv")));
    }

    @Test @Order(23)
    void exportSubmissions() throws Exception {
        mvc.perform(get("/admin/api/rounds/" + roundId + "/export/submissions")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", containsString("text/csv")));
    }

    // ── Unauthorized access ───────────────────────────

    @Test @Order(30)
    void sessionsEndpoint_requiresAdmin() throws Exception {
        // JSON endpoints answer 401 (not an HTML login redirect) so the admin page can react
        mvc.perform(get("/admin/api/sessions"))
            .andExpect(status().isUnauthorized());
    }

    // ── Archive and delete ────────────────────────────

    @Test @Order(40)
    void archiveSession() throws Exception {
        mvc.perform(post("/admin/api/sessions/" + sessionId + "/archive")
                .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test @Order(41)
    void getArchivedSessions() throws Exception {
        mvc.perform(get("/admin/api/sessions/archived")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }
}
