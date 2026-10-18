package com.bea.servicedesk;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** API-level tests through MockMvc against an in-memory H2 database. */
@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  private String token;

  @BeforeEach
  void register() throws Exception {
    String email = "user" + UUID.randomUUID() + "@example.com";
    String response =
        mvc.perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"Student\",\"email\":\""
                            + email
                            + "\",\"password\":\"SafePass123!\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    token = mapper.readTree(response).get("token").asText();
  }

  @Test
  void invalidEmailRejected() throws Exception {
    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"A\",\"email\":\"invalid\",\"password\":\"Password123\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void incidentCreated() throws Exception {
    mvc.perform(
            post("/api/incidents")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\"Printer offline\",\"description\":\"No"
                        + " printing\",\"category\":\"Hardware\",\"priority\":\"HIGH\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("OPEN"));
  }

  @Test
  void incidentRequiresTitle() throws Exception {
    mvc.perform(
            post("/api/incidents")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\"\",\"description\":\"No"
                        + " printing\",\"category\":\"Hardware\",\"priority\":\"HIGH\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void unauthenticatedListRejected() throws Exception {
    mvc.perform(get("/api/incidents")).andExpect(status().isForbidden());
  }

  @Test
  void userCannotAccessAdminReport() throws Exception {
    mvc.perform(get("/api/reports/summary").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void userCanListOwnIncidents() throws Exception {
    mvc.perform(get("/api/incidents").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }
}
