package br.com.finup.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.model.StudyCategory;
import br.com.finup.repository.StudyCategoryRepository;
import br.com.finup.repository.StudyQuestionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(
    properties = {
      "finup.cognito.user-pool-id=us-east-1_TestPool",
      "finup.cognito.client-id=test-client",
      "finup.cognito.admin-client-id=test-admin-client",
      "finup.cors.allowed-origins=http://localhost:5173"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminStudyCategoryIntegrationTests {

  private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private JwtDecoder jwtDecoder;
  @MockitoBean private StudyCategoryRepository categoryRepository;
  @MockitoBean private StudyQuestionRepository questionRepository;

  @Test
  @DisplayName("CRUD administrativo integra segurança, controller e service")
  void crudUsesAdminRouteContract() throws Exception {
    UUID id = UUID.randomUUID();
    StudyCategory category = new StudyCategory(id, "CDB", null);
    when(jwtDecoder.decode("admin-token")).thenReturn(adminToken());
    when(categoryRepository.findAllByOrderByNameAsc()).thenReturn(List.of(category));
    when(questionRepository.countAggregatedByCategoryId(id)).thenReturn(counts(2, 1));
    when(categoryRepository.save(any(StudyCategory.class))).thenReturn(category);
    when(categoryRepository.findById(id)).thenReturn(Optional.of(category));
    when(questionRepository.existsByCategoryId(id)).thenReturn(false);

    mockMvc
        .perform(withAdmin(get("/api/v1/admin/study-tracks/categories")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("CDB"))
        .andExpect(jsonPath("$[0].description").value(""))
        .andExpect(jsonPath("$[0].draftCount").value(2))
        .andExpect(jsonPath("$[0].publishedCount").value(1));

    mockMvc
        .perform(
            withAdmin(
                post("/api/v1/admin/study-tracks/categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"CDB\"}")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(id.toString()));

    mockMvc
        .perform(
            withAdmin(
                put("/api/v1/admin/study-tracks/categories/" + id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"CDB\"}")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("CDB"));

    mockMvc
        .perform(withAdmin(delete("/api/v1/admin/study-tracks/categories/" + id)))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("categoria com perguntas integra erro 409")
  void deleteWithQuestionsReturns409() throws Exception {
    UUID id = UUID.randomUUID();
    when(jwtDecoder.decode("admin-token")).thenReturn(adminToken());
    when(categoryRepository.findById(id))
        .thenReturn(Optional.of(new StudyCategory(id, "CDB", null)));
    when(questionRepository.existsByCategoryId(id)).thenReturn(true);

    mockMvc
        .perform(withAdmin(delete("/api/v1/admin/study-tracks/categories/" + id)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409));
  }

  @Test
  @DisplayName("categoria inexistente integra erro 404 no PUT e DELETE")
  void unknownCategoryReturns404() throws Exception {
    UUID id = UUID.randomUUID();
    when(jwtDecoder.decode("admin-token")).thenReturn(adminToken());
    when(categoryRepository.findById(id)).thenReturn(Optional.empty());

    mockMvc
        .perform(
            withAdmin(
                put("/api/v1/admin/study-tracks/categories/" + id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"CDB\"}")))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(withAdmin(delete("/api/v1/admin/study-tracks/categories/" + id)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("nome inválido integra erro 400")
  void invalidNameReturns400() throws Exception {
    when(jwtDecoder.decode("admin-token")).thenReturn(adminToken());

    mockMvc
        .perform(
            withAdmin(
                post("/api/v1/admin/study-tracks/categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"   \"}")))
        .andExpect(status().isBadRequest());
  }

  private MockHttpServletRequestBuilder withAdmin(MockHttpServletRequestBuilder request) {
    return request.header("Authorization", "Bearer admin-token");
  }

  private Jwt adminToken() {
    return Jwt.withTokenValue("admin-token")
        .header("alg", "RS256")
        .subject("admin-sub")
        .issuedAt(NOW.minusSeconds(60))
        .expiresAt(NOW.plusSeconds(3600))
        .claim("cognito:groups", List.of("admin"))
        .build();
  }

  private StudyQuestionRepository.CategoryCountProjection counts(
      long draftCount, long publishedCount) {
    return new StudyQuestionRepository.CategoryCountProjection() {
      @Override
      public long getPublishedCount() {
        return publishedCount;
      }

      @Override
      public long getDraftCount() {
        return draftCount;
      }
    };
  }
}
