package br.com.finup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.model.QuestionType;
import br.com.finup.model.StudyCategory;
import br.com.finup.model.StudyQuestion;
import br.com.finup.repository.StudyCategoryRepository;
import br.com.finup.repository.StudyQuestionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * HTTP, segurança, service e persistência reais. Só o decoder JWT é simulado, sem AWS ou rede. A
 * URL configurável permite repetir os testes em um PostgreSQL exclusivo para testes.
 */
@SpringBootTest(
    properties = {
      "spring.config.import=",
      "spring.datasource.url=${study-question-test-db-url:jdbc:h2:mem:study-questions;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
      "spring.datasource.driver-class-name=${study-question-test-db-driver:org.h2.Driver}",
      "spring.datasource.username=${study-question-test-db-user:sa}",
      "spring.datasource.password=${study-question-test-db-password:}",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "finup.cognito.user-pool-id=us-east-1_TestPool",
      "finup.cognito.client-id=test-client",
      "finup.cors.allowed-origins=http://localhost:5173"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudyQuestionIntegrationTests {

  private static final String ROOT = "/api/v1/admin/study-tracks";
  private static final String LEARNING =
      "{\"type\":\"LEARNING\",\"title\":\"Reserva\",\"body\":\"## Conteúdo\\n\\n**Poupar**\"}";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private StudyCategoryRepository categories;
  @Autowired private StudyQuestionRepository questions;
  @Autowired private EntityManager entityManager;
  @MockitoBean private JwtDecoder jwtDecoder;

  private StudyCategory category;

  @BeforeEach
  void setUp() {
    category = categories.saveAndFlush(new StudyCategory("Reserva", "Conceitos financeiros"));
    when(jwtDecoder.decode("admin")).thenReturn(token("admin", List.of("admin")));
  }

  @ParameterizedTest
  @MethodSource("contents")
  void createsListsAndUpdatesDraftForEveryType(String json) throws Exception {
    JsonNode created = create(json);
    UUID id = UUID.fromString(created.get("id").asText());
    assertThat(created.size()).isEqualTo(6);
    assertThat(created.get("status").asText()).isEqualTo("DRAFT");
    assertThat(created.has("published")).isTrue();
    assertThat(created.get("published").isNull()).isTrue();
    assertThat(created.has("publishedAt")).isTrue();
    assertThat(created.get("publishedAt").isNull()).isTrue();
    assertThat(Instant.parse(created.get("updatedAt").asText())).isNotNull();
    assertThat(created.get("draft")).isEqualTo(objectMapper.readTree(json));

    entityManager.clear();
    assertThat(questions.findById(id).orElseThrow().getDraft()).isEqualTo(asMap(json));
    mvc.perform(admin(get(categoryQuestions())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].id").value(id.toString()))
        .andExpect(jsonPath("$[0].status").value("DRAFT"));

    String edited =
        json.replace("Reserva", "Reserva atualizada")
            .replace("Qual opção?", "Qual alternativa?")
            .replace("Rende ___", "Rendimento ___");
    mvc.perform(
            admin(put(questionPath(id))).contentType(MediaType.APPLICATION_JSON).content(edited))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.published").value(nullValue()))
        .andExpect(jsonPath("$.publishedAt").value(nullValue()));
    entityManager.clear();
    assertThat(questions.findById(id).orElseThrow().getDraft()).isEqualTo(asMap(edited));
  }

  static Stream<String> contents() {
    return Stream.of(
        "{\"type\":\"FILL_BLANK\",\"title\":\"Reserva\",\"statement\":\"Rende ___\",\"answers\":[\"100%\"],\"explanation\":\"\"}",
        "{\"type\":\"MULTIPLE_CHOICE\",\"statement\":\"Qual opção?\",\"options\":[{\"text\":\"A\",\"correct\":true},{\"text\":\"B\",\"correct\":false}],\"explanation\":\"Explicação\"}",
        LEARNING);
  }

  @Test
  void editingPublishedContentPreservesPublicationAndRestoringContentRestoresStatus()
      throws Exception {
    Map<String, Object> published = asMap(LEARNING);
    Instant publishedAt = Instant.parse("2026-10-07T12:00:00Z");
    StudyQuestion saved =
        questions.saveAndFlush(
            new StudyQuestion(category, QuestionType.LEARNING, published, published, publishedAt));
    UUID id = saved.getId();
    Instant previousUpdatedAt = saved.getUpdatedAt();
    entityManager.clear();

    JsonNode result =
        objectMapper.readTree(
            mvc.perform(
                    admin(put(questionPath(id)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LEARNING.replace("Poupar", "Investir")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED_WITH_CHANGES"))
                .andExpect(jsonPath("$.publishedAt").value(publishedAt.toString()))
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(result.get("published")).isEqualTo(objectMapper.readTree(LEARNING));
    Instant updatedAt = Instant.parse(result.get("updatedAt").asText());
    assertThat(updatedAt).isAfter(previousUpdatedAt);
    entityManager.clear();
    StudyQuestion reloaded = questions.findById(id).orElseThrow();
    assertThat(reloaded.getPublished()).isEqualTo(published);
    assertThat(reloaded.getPublishedAt()).isEqualTo(publishedAt);
    assertThat(reloaded.getUpdatedAt()).isCloseTo(updatedAt, within(1, ChronoUnit.MICROS));

    mvc.perform(
            admin(put(questionPath(id))).contentType(MediaType.APPLICATION_JSON).content(LEARNING))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PUBLISHED"));
    mvc.perform(
            admin(put(questionPath(id))).contentType(MediaType.APPLICATION_JSON).content(LEARNING))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PUBLISHED"));
  }

  @Test
  void listingIncludesEveryStatusAndDoesNotLeakAnotherCategory() throws Exception {
    Map<String, Object> content = asMap(LEARNING);
    questions.saveAndFlush(new StudyQuestion(category, QuestionType.LEARNING, content, null, null));
    questions.saveAndFlush(
        new StudyQuestion(category, QuestionType.LEARNING, content, content, Instant.now()));
    Map<String, Object> changed = asMap(LEARNING.replace("Poupar", "Investir"));
    questions.saveAndFlush(
        new StudyQuestion(category, QuestionType.LEARNING, changed, content, Instant.now()));
    StudyCategory other = categories.saveAndFlush(new StudyCategory("Outra", null));
    questions.saveAndFlush(new StudyQuestion(other, QuestionType.LEARNING, content, null, null));
    entityManager.clear();

    mvc.perform(admin(get(categoryQuestions())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(3)))
        .andExpect(jsonPath("$[0].status").value("DRAFT"))
        .andExpect(jsonPath("$[1].status").value("PUBLISHED"))
        .andExpect(jsonPath("$[2].status").value("PUBLISHED_WITH_CHANGES"));
  }

  @Test
  void existingEmptyCategoryReturnsAnEmptyArray() throws Exception {
    mvc.perform(admin(get(categoryQuestions())))
        .andExpect(status().isOk())
        .andExpect(content().json("[]", true));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void deletingRemovesTheWholeQuestionIncludingPublication(boolean published) throws Exception {
    Map<String, Object> content = asMap(LEARNING);
    StudyQuestion saved =
        questions.saveAndFlush(
            new StudyQuestion(
                category,
                QuestionType.LEARNING,
                content,
                published ? content : null,
                published ? Instant.now() : null));
    UUID id = saved.getId();
    mvc.perform(admin(delete(questionPath(id))))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
    entityManager.clear();
    assertThat(questions.findById(id)).isEmpty();
    assertThat(categories.existsById(category.getId())).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"POST", "GET", "PUT", "DELETE"})
  void nonexistentResourcesReturn404(String method) throws Exception {
    String path =
        List.of("POST", "GET").contains(method)
            ? ROOT + "/categories/" + UUID.randomUUID() + "/questions"
            : questionPath(UUID.randomUUID());
    mvc.perform(
            admin(request(HttpMethod.valueOf(method), path))
                .contentType(MediaType.APPLICATION_JSON)
                .content(LEARNING))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.traceId").exists());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"type\":\"UNKNOWN\"}",
        "{\"type\":12}",
        "{",
        "null",
        "{\"type\":\"FILL_BLANK\",\"title\":\"CDB\",\"statement\":\"Rende ___\",\"answers\":[]}",
        "{\"type\":\"FILL_BLANK\",\"title\":\"CDB\",\"statement\":\"Rende ___\",\"answers\":[\"  \"]}",
        "{\"type\":\"MULTIPLE_CHOICE\",\"statement\":\"Qual?\",\"options\":[{\"text\":\"A\",\"correct\":false}]}",
        "{\"type\":\"LEARNING\",\"title\":\"Reserva\"}"
      })
  void invalidPostAndPutReturn400WithoutChangingStoredContent(String json) throws Exception {
    UUID id = UUID.fromString(create(LEARNING).get("id").asText());
    mvc.perform(
            admin(post(categoryQuestions())).contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    mvc.perform(admin(put(questionPath(id))).contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    entityManager.clear();
    assertThat(questions.findByCategoryId(category.getId())).hasSize(1);
    assertThat(questions.findById(id).orElseThrow().getDraft()).isEqualTo(asMap(LEARNING));
  }

  @Test
  void metadataInThePayloadCannotPublishOrChangeTheStatus() throws Exception {
    String json =
        LEARNING.substring(0, LEARNING.length() - 1)
            + ",\"status\":\"PUBLISHED\",\"published\":{\"body\":\"injetado\"},\"publishedAt\":\"2026-10-07T12:00:00Z\"}";
    JsonNode created = create(json);
    assertThat(created.get("status").asText()).isEqualTo("DRAFT");
    assertThat(created.get("published").isNull()).isTrue();
    assertThat(created.get("draft")).isEqualTo(objectMapper.readTree(LEARNING));
  }

  @Test
  void changingTheQuestionTypeReturns400AndPreservesTheStoredDraft() throws Exception {
    UUID id = UUID.fromString(create(LEARNING).get("id").asText());
    String differentType =
        "{\"type\":\"FILL_BLANK\",\"title\":\"Reserva\",\"statement\":\"Rende ___\",\"answers\":[\"100%\"]}";
    mvc.perform(
            admin(put(questionPath(id)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(differentType))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("O tipo da pergunta não pode ser alterado."));
    entityManager.clear();
    StudyQuestion stored = questions.findById(id).orElseThrow();
    assertThat(stored.getType()).isEqualTo(QuestionType.LEARNING);
    assertThat(stored.getDraft()).isEqualTo(asMap(LEARNING));
  }

  @ParameterizedTest
  @ValueSource(strings = {"POST", "GET", "PUT", "DELETE"})
  void malformedIdsReturn400(String method) throws Exception {
    String path =
        List.of("POST", "GET").contains(method)
            ? ROOT + "/categories/invalid-id/questions"
            : ROOT + "/questions/invalid-id";
    mvc.perform(
            admin(request(HttpMethod.valueOf(method), path))
                .contentType(MediaType.APPLICATION_JSON)
                .content(LEARNING))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @ParameterizedTest
  @MethodSource("unauthorizedRequests")
  void allMethodsRequireAnAdminToken(String method, String credential, int expectedStatus)
      throws Exception {
    if (credential.equals("invalid") || credential.equals("expired")) {
      when(jwtDecoder.decode(credential)).thenThrow(new BadJwtException("Token recusado"));
    } else if (!credential.equals("missing")) {
      when(jwtDecoder.decode(credential))
          .thenReturn(
              token(
                  credential,
                  credential.equals("uppercase") ? List.of("ADMIN") : List.of("clientes")));
    }
    String path =
        List.of("POST", "GET").contains(method)
            ? categoryQuestions()
            : questionPath(UUID.randomUUID());
    MockHttpServletRequestBuilder call =
        request(HttpMethod.valueOf(method), path)
            .contentType(MediaType.APPLICATION_JSON)
            .content(LEARNING);
    if (!credential.equals("missing")) {
      call.header("Authorization", "Bearer " + credential);
    }
    mvc.perform(call)
        .andExpect(status().is(expectedStatus))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.traceId").exists());
  }

  static Stream<Arguments> unauthorizedRequests() {
    return Stream.of("POST", "GET", "PUT", "DELETE")
        .flatMap(
            method ->
                Stream.of("missing", "invalid", "expired", "user", "uppercase")
                    .map(
                        credential ->
                            Arguments.of(
                                method,
                                credential,
                                List.of("user", "uppercase").contains(credential) ? 403 : 401)));
  }

  @Test
  void openApiDocumentsTheFourOperationsAndThreePayloads() throws Exception {
    JsonNode api =
        objectMapper.readTree(
            mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    JsonNode paths = api.get("paths");
    for (String method : List.of("get", "post", "put", "delete")) {
      String path =
          List.of("get", "post").contains(method)
              ? ROOT + "/categories/{categoryId}/questions"
              : ROOT + "/questions/{id}";
      JsonNode operation = paths.get(path).get(method);
      assertThat(operation).isNotNull();
      for (String code : List.of("400", "401", "403", "404")) {
        assertThat(operation.get("responses").has(code)).isTrue();
      }
    }
    JsonNode examples =
        paths
            .get(ROOT + "/categories/{categoryId}/questions")
            .get("post")
            .get("requestBody")
            .get("content")
            .get("application/json")
            .get("examples");
    assertThat(examples.has("FILL_BLANK")).isTrue();
    assertThat(examples.has("MULTIPLE_CHOICE")).isTrue();
    assertThat(examples.has("LEARNING")).isTrue();
  }

  private JsonNode create(String json) throws Exception {
    return objectMapper.readTree(
        mvc.perform(
                admin(post(categoryQuestions()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private Map<String, Object> asMap(String json) throws Exception {
    return objectMapper.readValue(json, new TypeReference<>() {});
  }

  private String categoryQuestions() {
    return ROOT + "/categories/" + category.getId() + "/questions";
  }

  private static String questionPath(UUID id) {
    return ROOT + "/questions/" + id;
  }

  private static MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder call) {
    return call.header("Authorization", "Bearer admin");
  }

  private static Jwt token(String value, List<String> groups) {
    Instant now = Instant.now();
    return Jwt.withTokenValue(value)
        .header("alg", "RS256")
        .subject("admin-test")
        .claim("cognito:groups", groups)
        .issuedAt(now.minusSeconds(60))
        .expiresAt(now.plusSeconds(3600))
        .build();
  }
}
