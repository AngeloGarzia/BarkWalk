package com.barkwalk.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.barkwalk.support.PostgresTestcontainers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RoleAccessIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = PostgresTestcontainers.create();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        PostgresTestcontainers.register(registry, POSTGRES);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicWalks_okWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/walks")).andExpect(status().isOk());
    }

    @Test
    void adminDashboard_unauthorizedWithoutAuth() throws Exception {
        // Anonymous → 401 (authenticationEntryPoint), not 403.
        mockMvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    void meProfile_unauthorizedWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/me/profile")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void adminDashboard_forbiddenForUser() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminDashboard_okForAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void adminSettings_forbiddenForUser() throws Exception {
        mockMvc.perform(get("/api/admin/settings")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void adminSettingsUpdate_forbiddenForUser() throws Exception {
        mockMvc.perform(put("/api/admin/settings/cancellation_free_hours")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"48\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminSettings_readableAndUpdatableForAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key=='cancellation_free_hours')]").exists());

        mockMvc.perform(put("/api/admin/settings/cancellation_free_hours")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"48\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("cancellation_free_hours"))
                .andExpect(jsonPath("$.value").value("48"));
    }
}
