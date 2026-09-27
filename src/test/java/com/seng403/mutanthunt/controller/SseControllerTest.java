package com.seng403.mutanthunt.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SseControllerTest {

    @Autowired MockMvc mvc;

    @Test
    void boardStream() throws Exception {
        mvc.perform(get("/sse/board"))
            .andExpect(status().isOk());
    }

    @Test
    void adminStream_requiresAuth() throws Exception {
        mvc.perform(get("/sse/admin"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminStream_withAuth() throws Exception {
        mvc.perform(get("/sse/admin")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk());
    }

    @Test
    void studentStream_withoutCookie() throws Exception {
        mvc.perform(get("/sse/student"))
            .andExpect(status().isOk());
    }
}
