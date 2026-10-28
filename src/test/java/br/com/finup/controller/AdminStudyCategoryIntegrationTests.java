package br.com.finup.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.model.QuestionType;
import br.com.finup.model.StudyCategory;
import br.com.finup.model.StudyQuestion;
import br.com.finup.repository.StudyCategoryRepository;
import br.com.finup.repository.StudyQuestionRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD administrativo de categorias da trilha com HTTP, segurança, service e persistência reais (H2
 * em modo PostgreSQL). Só o decoder JWT é simulado, com um usuário do grupo admin.
 */
@SpringBootTest(
    properties = {
      "spring.config.import=",
      "spring.datasource.url=jdbc:h2:mem:admin-study-categories;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "finup.cognito.user-pool-id=us-east-1_TestPool",
      "finup.cognito.client-id=test-client",
      "finup.cognito.admin-client-id=test-admin-client",
      "finup.cors.allowed-origins=http://localhost:5173"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminStudyCategoryIntegrationTests {

  private static final String ROOT = "/api/v1/admin/study-tracks/categories";
  private static final String TOKEN = "admin-token";
  private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

  private static final Map<String, Object> PUBLISHED_CONTENT =
      Map.of("type", "LEARNING", "title", "CDI", "body", "Versão publicada");

  private static final Map<String, Object> DRAFT_CONTENT =
      Map.of("type", "LEARNING", "title", "CDI", "body", "Rascunho em edição");

  @Autowired private MockMvc mvc;
  @Autowired private StudyCategoryRepository categories;
  @Autowired private StudyQuestionRepository questions;
  @MockitoBean private JwtDecoder jwtDecoder;

  @BeforeEach
  void authenticateAsAdmin() {
    when(jwtDecoder.decode(TOKEN)).thenReturn(adminToken());
  }

  @Test
  @DisplayName("GET e PUT devolvem draftCount e publishedCount calculados no banco")
  void listReturnsCountsComputedFromQuestions() throws Exception {
    StudyCategory reserve = categories.save(new StudyCategory("Reserva", "Conceitos de reserva"));
    StudyCategory cdb = categories.save(new StudyCategory("CDB", null));
    // DRAFT: só draft; PUBLISHED: draft igual ao published; PUBLISHED_WITH_CHANGES: diferentes.
    questions.save(new StudyQuestion(reserve, QuestionType.LEARNING, DRAFT_CONTENT, null, null));
    questions.save(
        new StudyQuestion(
            reserve, QuestionType.LEARNING, PUBLISHED_CONTENT, PUBLISHED_CONTENT, NOW));
    questions.save(
        new StudyQuestion(reserve, QuestionType.LEARNING, DRAFT_CONTENT, PUBLISHED_CONTENT, NOW));
    questions.flush();

    mvc.perform(asAdmin(get(ROOT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].id").value(cdb.getId().toString()))
        .andExpect(jsonPath("$[0].name").value("CDB"))
        .andExpect(jsonPath("$[0].description").value(""))
        .andExpect(jsonPath("$[0].draftCount").value(0))
        .andExpect(jsonPath("$[0].publishedCount").value(0))
        .andExpect(jsonPath("$[1].name").value("Reserva"))
        .andExpect(jsonPath("$[1].draftCount").value(2))
        .andExpect(jsonPath("$[1].publishedCount").value(2))
        .andExpect(jsonPath("$[1].createdAt", notNullValue()))
        .andExpect(jsonPath("$[1].updatedAt", notNullValue()));

    // O PUT usa a contagem de uma categoria só, que precisa dar o mesmo resultado da lista.
    mvc.perform(
            asAdmin(
                put(ROOT + "/" + reserve.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Reserva de emergência\"}")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.draftCount").value(2))
        .andExpect(jsonPath("$.publishedCount").value(2));
  }

  @Test
  @DisplayName("POST cria com trim e Location, PUT edita e DELETE remove a categoria do banco")
  void crudPersistsCategory() throws Exception {
    MockHttpServletResponse created =
        mvc.perform(
                asAdmin(
                    post(ROOT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"name\":\"  Tesouro  \",\"description\":\" Títulos públicos \"}")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Tesouro"))
            .andExpect(jsonPath("$.description").value("Títulos públicos"))
            .andExpect(jsonPath("$.draftCount").value(0))
            .andExpect(jsonPath("$.publishedCount").value(0))
            .andExpect(jsonPath("$.createdAt", notNullValue()))
            .andReturn()
            .getResponse();
    UUID id = UUID.fromString(JsonPath.read(created.getContentAsString(), "$.id"));
    assertThat(created.getHeader(HttpHeaders.LOCATION)).isEqualTo(ROOT + "/" + id);
    assertThat(categories.findById(id))
        .get()
        .extracting(StudyCategory::getName)
        .isEqualTo("Tesouro");

    mvc.perform(
            asAdmin(
                put(ROOT + "/" + id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Tesouro Direto\"}")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.name").value("Tesouro Direto"))
        .andExpect(jsonPath("$.description").value(""));

    mvc.perform(asAdmin(delete(ROOT + "/" + id))).andExpect(status().isNoContent());

    assertThat(categories.existsById(id)).isFalse();
  }

  @Test
  @DisplayName("DELETE de categoria com perguntas devolve 409 e mantém a categoria")
  void deleteWithQuestionsReturns409() throws Exception {
    StudyCategory category = categories.save(new StudyCategory("Reserva", null));
    questions.saveAndFlush(
        new StudyQuestion(category, QuestionType.LEARNING, DRAFT_CONTENT, null, null));

    mvc.perform(asAdmin(delete(ROOT + "/" + category.getId())))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "A categoria possui perguntas e não pode ser removida. Remova as perguntas"
                        + " antes."));

    assertThat(categories.existsById(category.getId())).isTrue();
  }

  @Test
  @DisplayName("PUT e DELETE de categoria inexistente devolvem 404")
  void unknownCategoryReturns404() throws Exception {
    UUID id = UUID.randomUUID();

    mvc.perform(
            asAdmin(
                put(ROOT + "/" + id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"CDB\"}")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));

    mvc.perform(asAdmin(delete(ROOT + "/" + id)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  @DisplayName("name ausente, vazio ou longo demais devolve 400 sem gravar nada")
  void invalidNameReturns400() throws Exception {
    String tooLong = "a".repeat(101);
    for (String json : List.of("{}", "{\"name\":\"   \"}", "{\"name\":\"" + tooLong + "\"}")) {
      mvc.perform(asAdmin(post(ROOT).contentType(MediaType.APPLICATION_JSON).content(json)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.fields[0].field").value("name"));
    }

    StudyCategory category = categories.saveAndFlush(new StudyCategory("CDB", null));
    mvc.perform(
            asAdmin(
                put(ROOT + "/" + category.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"\"}")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields[0].field").value("name"));

    assertThat(categories.findAll()).extracting(StudyCategory::getName).containsExactly("CDB");
  }

  @Test
  @DisplayName("id fora do formato UUID devolve 400")
  void invalidIdReturns400() throws Exception {
    mvc.perform(asAdmin(delete(ROOT + "/nao-e-uuid"))).andExpect(status().isBadRequest());
  }

  private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
    return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
  }

  private Jwt adminToken() {
    return Jwt.withTokenValue(TOKEN)
        .header("alg", "RS256")
        .subject("admin-sub")
        .issuedAt(NOW.minusSeconds(60))
        .expiresAt(Instant.now().plusSeconds(3600))
        .claim("cognito:groups", List.of("admin"))
        .build();
  }
}
