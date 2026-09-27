package com.lifeplanner;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskCrudTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerAndGetToken(String email) throws Exception {

        MvcResult result =
                mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "email":"%s",
                                          "displayName":"Task Test",
                                          "password":"averysecurepassword"
                                        }
                                        """.formatted(email)))
                        .andExpect(status().isCreated())
                        .andReturn();

        JsonNode json =
                objectMapper.readTree(
                        result.getResponse().getContentAsString());

        return json.get("token").asText();
    }

    @Test
    void tasksRequireAuthentication() throws Exception {

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void newUserHasNoTasks() throws Exception {

        String token =
                registerAndGetToken(
                        "tasks-" + System.nanoTime() + "@example.com");

        mockMvc.perform(
                get("/api/tasks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void canCreateTask() throws Exception {

        String token =
                registerAndGetToken(
                        "create-" + System.nanoTime() + "@example.com");

        mockMvc.perform(
                post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Study Java",
                                  "description":"Prepare for interview",
                                  "category":"Study",
                                  "durationMinutes":60,
                                  "priority":"HIGH",
                                  "flexibility":"FLEXIBLE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Study Java"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.durationMinutes").value(60))
                .andExpect(jsonPath("$.priority").value("HIGH"));
    }

    @Test
    void canCompleteTask() throws Exception {

        String token =
                registerAndGetToken(
                        "complete-" + System.nanoTime() + "@example.com");

        MvcResult result =
                mockMvc.perform(
                        post("/api/tasks")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "title":"Complete project",
                                          "durationMinutes":30
                                        }
                                        """))
                        .andExpect(status().isOk())
                        .andReturn();

        long taskId =
                objectMapper
                        .readTree(result.getResponse().getContentAsString())
                        .get("id")
                        .asLong();

        mockMvc.perform(
                post("/api/tasks/" + taskId + "/complete")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").exists());
    }

    @Test
    void canSkipTask() throws Exception {

        String token =
                registerAndGetToken(
                        "skip-" + System.nanoTime() + "@example.com");

        MvcResult result =
                mockMvc.perform(
                        post("/api/tasks")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "title":"Optional task"
                                        }
                                        """))
                        .andExpect(status().isOk())
                        .andReturn();

        long taskId =
                objectMapper
                        .readTree(result.getResponse().getContentAsString())
                        .get("id")
                        .asLong();

        mockMvc.perform(
                post("/api/tasks/" + taskId + "/skip")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SKIPPED"));
    }

    @Test
    void canDeleteTask() throws Exception {

        String token =
                registerAndGetToken(
                        "delete-" + System.nanoTime() + "@example.com");

        MvcResult result =
                mockMvc.perform(
                        post("/api/tasks")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "title":"Delete me"
                                        }
                                        """))
                        .andExpect(status().isOk())
                        .andReturn();

        long taskId =
                objectMapper
                        .readTree(result.getResponse().getContentAsString())
                        .get("id")
                        .asLong();

        mockMvc.perform(
                delete("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(
                get("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void oneUserCannotAccessAnotherUsersTask() throws Exception {

        String tokenA =
                registerAndGetToken(
                        "owner-" + System.nanoTime() + "@example.com");

        String tokenB =
                registerAndGetToken(
                        "other-" + System.nanoTime() + "@example.com");

        MvcResult result =
                mockMvc.perform(
                        post("/api/tasks")
                                .header("Authorization", "Bearer " + tokenA)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "title":"Private task"
                                        }
                                        """))
                        .andExpect(status().isOk())
                        .andReturn();

        long taskId =
                objectMapper
                        .readTree(result.getResponse().getContentAsString())
                        .get("id")
                        .asLong();

        mockMvc.perform(
                get("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }
}