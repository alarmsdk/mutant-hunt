package com.seng403.mutanthunt.controller;

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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StudentApiControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Test @Order(1)
    void join_missingName() throws Exception {
        mvc.perform(post("/api/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("fullName", "", "studentNumber", "1234567890"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("join.error.name.required"));
    }

    @Test @Order(2)
    void join_missingStudentNumber() throws Exception {
        mvc.perform(post("/api/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("fullName", "Alice", "studentNumber", ""))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("join.error.studentId.required"));
    }

    @Test @Order(3)
    void join_nonNumericStudentNumber() throws Exception {
        mvc.perform(post("/api/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("fullName", "Alice", "studentNumber", "abc"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("join.error.studentId.numeric"));
    }

    @Test @Order(4)
    void status_withoutCookie() throws Exception {
        mvc.perform(get("/api/status"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("not_joined"));
    }

    @Test @Order(5)
    void submit_withoutCookie() throws Exception {
        mvc.perform(post("/api/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("list", "1", "target", "1", "expected", "0"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("not_joined"));
    }

    @Test @Order(6)
    void status_invalidCookie() throws Exception {
        mvc.perform(get("/api/status")
                .cookie(new Cookie("player_token", "invalid-token-value")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("not_joined"));
    }

    @Test @Order(7)
    void qr_default() throws Exception {
        mvc.perform(get("/api/qr"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/png"));
    }

    @Test @Order(8)
    void qr_customSize() throws Exception {
        mvc.perform(get("/api/qr").param("size", "200"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/png"));
    }

    @Test @Order(9)
    void qr_clampedMinSize() throws Exception {
        mvc.perform(get("/api/qr").param("size", "10"))
            .andExpect(status().isOk());
    }

    @Test @Order(10)
    void qr_clampedMaxSize() throws Exception {
        mvc.perform(get("/api/qr").param("size", "5000"))
            .andExpect(status().isOk());
    }
}
