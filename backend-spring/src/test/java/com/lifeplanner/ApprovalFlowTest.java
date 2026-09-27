package com.lifeplanner;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/**
 * End-to-end checks over the approval gate.
 *
 * NOTE: {@code proposalRequiresPlanner} expects the planner service on
 * localhost:8001. The rest run without it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApprovalFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private String registerAndGetToken(String email) throws Exception {
        String body = """
                {"email":"%s","displayName":"Test User","password":"averysecurepassword"}
                """.formatted(email);

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("token").asText();
    }

    @Test
    void registrationIssuesAToken() throws Exception {
        String token = registerAndGetToken("first@example.com");
        org.assertj.core.api.Assertions.assertThat(token).isNotBlank();
    }

    @Test
    void passwordIsNeverReturned() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"secret@example.com","displayName":"S",
                                 "password":"averysecurepassword"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void duplicateEmailIsRejectedCleanly() throws Exception {
        registerAndGetToken("dupe@example.com");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"dupe@example.com","displayName":"D",
                                 "password":"averysecurepassword"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void shortPasswordIsRejectedWithAReadableMessage() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"weak@example.com","displayName":"W","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    void scheduleRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/schedule/today"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oneUserCannotSeeAnotherUsersSchedule() throws Exception {
        String tokenA = registerAndGetToken("a@example.com");
        String tokenB = registerAndGetToken("b@example.com");

        mockMvc.perform(get("/api/habits").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/habits").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void approvingAnUnknownProposalIsNotFound() throws Exception {
        String token = registerAndGetToken("ghost@example.com");
        mockMvc.perform(post("/api/schedule/proposals/999999/approve")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void nothingIsScheduledBeforeApproval() throws Exception {
        String token = registerAndGetToken("clean@example.com");
        mockMvc.perform(get("/api/schedule/today").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
