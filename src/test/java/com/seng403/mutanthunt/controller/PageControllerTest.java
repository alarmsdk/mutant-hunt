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
class PageControllerTest {

    @Autowired MockMvc mvc;

    @Test
    void studentPage() throws Exception {
        mvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(view().name("student"));
    }

    @Test
    void boardPage() throws Exception {
        mvc.perform(get("/board"))
            .andExpect(status().isOk())
            .andExpect(view().name("board"));
    }

    @Test
    void adminLoginPage() throws Exception {
        mvc.perform(get("/admin/login"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin-login"));
    }

    @Test
    void adminPage_requiresAuth() throws Exception {
        mvc.perform(get("/admin"))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    void adminPage_withAuth() throws Exception {
        mvc.perform(get("/admin")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(view().name("admin"));
    }
}
