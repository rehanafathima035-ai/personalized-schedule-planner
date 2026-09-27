package com.lifeplanner;


import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StreakAnalyticsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerAndGetToken(String email) throws Exception {

        String body = """
                {"email":"%s","displayName":"Analytics Test","password":"averysecurepassword"}
                """.formatted(email);

        MvcResult result = mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/auth/register")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json =
                objectMapper.readTree(
                        result.getResponse().getContentAsString());

        return json.get("token").asText();
    }

    @Test
    void analyticsRequiresAuthentication() throws Exception {

        mockMvc.perform(
                get("/api/analytics")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-30"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void completionHistoryRequiresAuthentication() throws Exception {

        mockMvc.perform(
                get("/api/completion-history")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-30"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void prayerStreakRequiresAuthentication() throws Exception {

        mockMvc.perform(get("/api/prayers/streak"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void analyticsReturnsDateRangeAndZeroValuesForNewUser() throws Exception {

        String token =
                registerAndGetToken(
                        "analytics-" + System.nanoTime() + "@example.com");

        mockMvc.perform(
                get("/api/analytics")
                        .header("Authorization", "Bearer " + token)
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-09-01"))
                .andExpect(jsonPath("$.to").value("2026-09-30"))
                .andExpect(jsonPath("$.habitCompleted").value(0))
                .andExpect(jsonPath("$.habitTotal").value(0))
                .andExpect(jsonPath("$.prayerCompleted").value(0))
                .andExpect(jsonPath("$.prayerTotal").value(0))
                .andExpect(jsonPath("$.totalCompleted").value(0))
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.completionPercentage").value(0.0));
    }

    @Test
    void completionHistoryReturnsEmptyListsForNewUser() throws Exception {

        String token =
                registerAndGetToken(
                        "history-" + System.nanoTime() + "@example.com");

        mockMvc.perform(
                get("/api/completion-history")
                        .header("Authorization", "Bearer " + token)
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-09-01"))
                .andExpect(jsonPath("$.to").value("2026-09-30"))
                .andExpect(jsonPath("$.habits").isArray())
                .andExpect(jsonPath("$.prayers").isArray())
                .andExpect(jsonPath("$.habits.length()").value(0))
                .andExpect(jsonPath("$.prayers.length()").value(0));
    }

    @Test
    void prayerStreakReturnsZeroForNewUser() throws Exception {

        String token =
                registerAndGetToken(
                        "prayer-" + System.nanoTime() + "@example.com");

        mockMvc.perform(
                get("/api/prayers/streak")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreak").value(0))
                .andExpect(jsonPath("$.bestStreak").value(0));
    }

    @Test
    void habitStreakReturnsZeroForUnknownHabit() throws Exception {

        String token =
                registerAndGetToken(
                        "habit-" + System.nanoTime() + "@example.com");

        mockMvc.perform(
                get("/api/habits/999999/streak")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}