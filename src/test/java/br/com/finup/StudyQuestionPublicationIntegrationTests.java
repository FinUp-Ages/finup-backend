package br.com.finup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.dto.StudyCategoryResponse;
import br.com.finup.model.QuestionStatus;
import br.com.finup.model.QuestionType;
import br.com.finup.model.StudyCategory;
import br.com.finup.model.StudyQuestion;
import br.com.finup.repository.StudyCategoryRepository;
import br.com.finup.repository.StudyQuestionRepository;
import br.com.finup.service.StudyCategoryService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publicação, despublicação e descarte de edições, de ponta a ponta: filtro de segurança com os
 * grupos do Cognito, controller, service, JPA e banco reais. Só o decoder JWT é simulado, sem AWS
 * ou rede. A URL configurável permite repetir em um PostgreSQL exclusivo para testes (mesmas
 * propriedades do {@code StudyQuestionIntegrationTests}).
 *
 * <p>O que cada cenário confere, além do status HTTP: o status derivado devolvido, o que ficou
 * gravado depois de limpar o contexto de persistência e as contagens da categoria.
 */
@SpringBootTest(
    properties = {
      "spring.config.import=",
      "spring.datasource.url=${study-question-test-db-url:jdbc:h2:mem:study-question-publication;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
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
class StudyQuestionPublicationIntegrationTests {

  private static final String ROOT = "/api/v1/admin/study-tracks";
  private static final String LEARNING =
      "{\"type\":\"LEARNING\",\"title\":\"Reserva\",\"body\":\"## Texto original\"}";
  private static final String LEARNING_EDITED =
      "{\"type\":\"LEARNING\",\"title\":\"Reserva\",\"body\":\"## Texto editado\"}";
  private static final String FILL_BLANK =
      "{\"type\":\"FILL_BLANK\",\"title\":\"CDB\",\"statement\":\"Rende ___\",\"answers\":[\"100%\"]}";
  private static final String MULTIPLE_CHOICE =
      "{\"type\":\"MULTIPLE_CHOICE\",\"statement\":\"Qual?\",\"options\":"
          + "[{\"text\":\"A\",\"correct\":true},{\"text\":\"B\",\"correct\":false}],"
          + "\"explanation\":\"Porque sim.\"}";

  /** Sem corpo: o que sobra de um rascunho que nao passa na validacao do tipo. */
  private static final String INVALID_LEARNING = "{\"type\":\"LEARNING\",\"title\":\"So titulo\"}";

  private static final Instant LONG_AGO = Instant.parse("2026-10-01T12:00:00Z");

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private StudyCategoryRepository categories;
  @Autowired private StudyQuestionRepository questions;
  @Autowired private StudyCategoryService categoryService;
  @Autowired private EntityManager entityManager;
  @MockitoBean private JwtDecoder jwtDecoder;

  private StudyCategory category;

  @BeforeEach
  void setUp() {
    category = categories.saveAndFlush(new StudyCategory("Reserva", "Conceitos financeiros"));
    when(jwtDecoder.decode("admin")).thenReturn(token("admin", List.of("admin")));
    when(jwtDecoder.decode("comum")).thenReturn(token("comum", List.of("clientes")));
  }

  // ------------------------------------------------------- ciclo de vida completo

  @Test
  @DisplayName("ciclo completo: status e contagens da categoria acompanham cada acao")
  void fullLifecycleKeepsStatusAndCategoryCountsInSync() throws Exception {
    JsonNode created = create(LEARNING);
    UUID id = UUID.fromString(created.get("id").asText());
    assertThat(created.get("status").asText()).isEqualTo("DRAFT");
    assertCounts(1, 0);

    JsonNode published = send(post(publishPath(id)), 200);
    assertThat(published.get("status").asText()).isEqualTo("PUBLISHED");
    assertThat(published.get("published")).isEqualTo(objectMapper.readTree(LEARNING));
    assertThat(published.get("draft")).isEqualTo(published.get("published"));
    Instant firstPublishedAt = Instant.parse(published.get("publishedAt").asText());
    assertCounts(0, 1);

    JsonNode edited = update(id, LEARNING_EDITED);
    assertThat(edited.get("status").asText()).isEqualTo("PUBLISHED_WITH_CHANGES");
    assertCounts(1, 1);

    JsonNode discarded = send(delete(draftPath(id)), 200);
    assertThat(discarded.get("status").asText()).isEqualTo("PUBLISHED");
    assertThat(discarded.get("draft")).isEqualTo(objectMapper.readTree(LEARNING));
    assertThat(discarded.get("published")).isEqualTo(objectMapper.readTree(LEARNING));
    assertThat(Instant.parse(discarded.get("publishedAt").asText())).isEqualTo(firstPublishedAt);
    assertCounts(0, 1);

    update(id, LEARNING_EDITED);
    JsonNode republished = send(post(publishPath(id)), 200);
    assertThat(republished.get("status").asText()).isEqualTo("PUBLISHED");
    assertThat(republished.get("published")).isEqualTo(objectMapper.readTree(LEARNING_EDITED));
    assertThat(Instant.parse(republished.get("publishedAt").asText()))
        .isAfterOrEqualTo(firstPublishedAt);
    assertCounts(0, 1);

    JsonNode unpublished = send(post(unpublishPath(id)), 200);
    assertThat(unpublished.get("status").asText()).isEqualTo("DRAFT");
    assertThat(unpublished.get("published").isNull()).isTrue();
    assertThat(unpublished.get("publishedAt").isNull()).isTrue();
    assertThat(unpublished.get("draft")).isEqualTo(objectMapper.readTree(LEARNING_EDITED));
    assertCounts(1, 0);

    entityManager.clear();
    StudyQuestion stored = questions.findById(id).orElseThrow();
    assertThat(stored.getStatus()).isEqualTo(QuestionStatus.DRAFT);
    assertThat(stored.getPublished()).isNull();
    assertThat(stored.getPublishedAt()).isNull();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("validContents")
  @DisplayName("publica o rascunho valido de cada tipo")
  void publishesEveryType(String name, String json) throws Exception {
    UUID id = UUID.fromString(create(json).get("id").asText());

    JsonNode published = send(post(publishPath(id)), 200);

    assertThat(published.get("status").asText()).isEqualTo("PUBLISHED");
    assertThat(published.get("published")).isEqualTo(objectMapper.readTree(json));
    entityManager.clear();
    StudyQuestion stored = questions.findById(id).orElseThrow();
    assertThat(stored.getPublished()).isEqualTo(stored.getDraft()).isEqualTo(asMap(json));
    assertThat(stored.getPublishedAt()).isNotNull();
  }

  static Stream<Arguments> validContents() {
    return Stream.of(
        Arguments.of("FILL_BLANK", FILL_BLANK),
        Arguments.of("MULTIPLE_CHOICE", MULTIPLE_CHOICE),
        Arguments.of("LEARNING", LEARNING));
  }

  // ------------------------------------------------------------- publicar: 400

  @Test
  @DisplayName("rascunho invalido nao publica, responde 400 e nao altera nada")
  void invalidDraftReturns400AndPublishesNothing() throws Exception {
    UUID id = persist(QuestionType.LEARNING, INVALID_LEARNING, null, null);
    assertCounts(1, 0);

    mvc.perform(admin(post(publishPath(id))))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").value(containsString("não foi publicado")))
        .andExpect(jsonPath("$.detail").value(containsString("conteúdo educativo")))
        .andExpect(jsonPath("$.traceId").exists());

    entityManager.clear();
    StudyQuestion stored = questions.findById(id).orElseThrow();
    assertThat(stored.getPublished()).isNull();
    assertThat(stored.getPublishedAt()).isNull();
    assertThat(stored.getStatus()).isEqualTo(QuestionStatus.DRAFT);
    assertCounts(1, 0);
  }

  @Test
  @DisplayName("rascunho invalido de pergunta ja publicada mantem a versao publicada no ar")
  void invalidDraftKeepsThePublishedVersionLive() throws Exception {
    UUID id = persist(QuestionType.LEARNING, INVALID_LEARNING, LEARNING, LONG_AGO);
    assertCounts(1, 1);

    mvc.perform(admin(post(publishPath(id)))).andExpect(status().isBadRequest());

    entityManager.clear();
    StudyQuestion stored = questions.findById(id).orElseThrow();
    assertThat(stored.getPublished()).isEqualTo(asMap(LEARNING));
    assertThat(stored.getPublishedAt()).isEqualTo(LONG_AGO);
    assertThat(stored.getStatus()).isEqualTo(QuestionStatus.PUBLISHED_WITH_CHANGES);
    assertCounts(1, 1);
  }

  @Test
  @DisplayName("rascunho no formato antigo do seed (content) nao publica")
  void legacyDraftFormatIsRejected() throws Exception {
    UUID id =
        persist(QuestionType.LEARNING, "{\"type\":\"LEARNING\",\"content\":\"texto\"}", null, null);

    mvc.perform(admin(post(publishPath(id))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(containsString("não foi publicado")));
  }

  // ------------------------------------------------- descartar sem publicada: 409

  @Test
  @DisplayName("descartar sem versao publicada responde 409 e preserva o rascunho")
  void discardWithoutPublishedVersionReturns409() throws Exception {
    UUID id = persist(QuestionType.LEARNING, LEARNING_EDITED, null, null);

    mvc.perform(admin(delete(draftPath(id))))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.detail").value(containsString("não tem versão publicada")));

    entityManager.clear();
    assertThat(questions.findById(id).orElseThrow().getDraft()).isEqualTo(asMap(LEARNING_EDITED));
    assertCounts(1, 0);
  }

  // --------------------------------------------------------------- sem efeito

  @Test
  @DisplayName("publicar o que ja esta publicado e sem edicao nao muda nada, nem publishedAt")
  void publishingAnUnchangedQuestionKeepsPublishedAt() throws Exception {
    UUID id = persist(QuestionType.LEARNING, LEARNING, LEARNING, LONG_AGO);

    mvc.perform(admin(post(publishPath(id))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PUBLISHED"))
        .andExpect(jsonPath("$.publishedAt").value(LONG_AGO.toString()));

    entityManager.clear();
    assertThat(questions.findById(id).orElseThrow().getPublishedAt()).isEqualTo(LONG_AGO);
  }

  @Test
  @DisplayName("despublicar o que nunca foi publicado responde 200 e segue em DRAFT")
  void unpublishingADraftIsANoOp() throws Exception {
    UUID id = persist(QuestionType.LEARNING, LEARNING, null, null);

    mvc.perform(admin(post(unpublishPath(id))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.published").value(nullValue()));
    assertCounts(1, 0);
  }

  @Test
  @DisplayName("descartar sem edicao pendente responde 200 e nao muda nada")
  void discardingWithoutPendingChangesIsANoOp() throws Exception {
    UUID id = persist(QuestionType.LEARNING, LEARNING, LEARNING, LONG_AGO);

    mvc.perform(admin(delete(draftPath(id))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PUBLISHED"))
        .andExpect(jsonPath("$.publishedAt").value(LONG_AGO.toString()));
  }

  @Test
  @DisplayName("despublicar com edicao pendente volta a DRAFT e guarda a edicao")
  void unpublishingKeepsPendingEdits() throws Exception {
    UUID id = persist(QuestionType.LEARNING, LEARNING_EDITED, LEARNING, LONG_AGO);
    assertCounts(1, 1);

    mvc.perform(admin(post(unpublishPath(id))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.draft.body").value("## Texto editado"));
    assertCounts(1, 0);
  }

  // ------------------------------------------------------------- 404 e 400 de id

  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("actions")
  @DisplayName("pergunta inexistente responde 404 em problem+json")
  void unknownQuestionReturns404(HttpMethod method, String suffix) throws Exception {
    mvc.perform(admin(request(method, UUID.randomUUID(), suffix)))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value("Pergunta da trilha não encontrada."));
  }

  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("actions")
  @DisplayName("id que nao e UUID responde 400")
  void malformedIdReturns400(HttpMethod method, String suffix) throws Exception {
    MockHttpServletRequestBuilder call =
        method == HttpMethod.DELETE
            ? delete(ROOT + "/questions/nao-e-uuid" + suffix)
            : post(ROOT + "/questions/nao-e-uuid" + suffix);
    mvc.perform(admin(call)).andExpect(status().isBadRequest());
  }

  // --------------------------------------------------------------- 401 e 403

  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("actions")
  @DisplayName("sem token responde 401 e nao altera a pergunta")
  void withoutTokenReturns401(HttpMethod method, String suffix) throws Exception {
    UUID id = persist(QuestionType.LEARNING, LEARNING, null, null);

    mvc.perform(request(method, id, suffix))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

    assertStillDraft(id);
  }

  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("actions")
  @DisplayName("usuario fora do grupo admin recebe 403 e a pergunta nao e alterada")
  void nonAdminReturns403(HttpMethod method, String suffix) throws Exception {
    UUID id = persist(QuestionType.LEARNING, LEARNING, null, null);

    mvc.perform(request(method, id, suffix).header("Authorization", "Bearer comum"))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(403));

    assertStillDraft(id);
  }

  static Stream<Arguments> actions() {
    return Stream.of(
        Arguments.of(HttpMethod.POST, "/publish"),
        Arguments.of(HttpMethod.POST, "/unpublish"),
        Arguments.of(HttpMethod.DELETE, "/draft"));
  }

  // ----------------------------------------------------------------- Swagger

  @Test
  @DisplayName("o Swagger documenta as tres rotas, com o 409 do descarte e o 400 da publicacao")
  void openApiDocumentsThePublicationRoutes() throws Exception {
    JsonNode paths =
        objectMapper
            .readTree(
                mvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("paths");

    JsonNode publish = paths.get(ROOT + "/questions/{id}/publish").get("post");
    JsonNode unpublish = paths.get(ROOT + "/questions/{id}/unpublish").get("post");
    JsonNode discard = paths.get(ROOT + "/questions/{id}/draft").get("delete");
    assertThat(publish).isNotNull();
    assertThat(unpublish).isNotNull();
    assertThat(discard).isNotNull();
    assertThat(publish.get("responses").has("400")).isTrue();
    assertThat(discard.get("responses").has("409")).isTrue();
    for (JsonNode operation : List.of(publish, unpublish, discard)) {
      assertThat(operation.get("responses").has("200")).isTrue();
      assertThat(operation.get("responses").has("401")).isTrue();
      assertThat(operation.get("responses").has("403")).isTrue();
      assertThat(operation.get("responses").has("404")).isTrue();
    }
  }

  // ----------------------------------------------------------------- helpers

  /** Grava direto, sem passar pela API de escrita: e a unica forma de ter um rascunho invalido. */
  private UUID persist(QuestionType type, String draft, String published, Instant publishedAt)
      throws Exception {
    StudyQuestion saved =
        questions.saveAndFlush(
            new StudyQuestion(
                category,
                type,
                asMap(draft),
                published == null ? null : asMap(published),
                publishedAt));
    entityManager.clear();
    return saved.getId();
  }

  private void assertStillDraft(UUID id) {
    entityManager.clear();
    StudyQuestion stored = questions.findById(id).orElseThrow();
    assertThat(stored.getStatus()).isEqualTo(QuestionStatus.DRAFT);
    assertThat(stored.getPublished()).isNull();
    assertThat(stored.getPublishedAt()).isNull();
  }

  /** As contagens vem do mesmo caminho que a API de categorias usa, a partir do banco. */
  private void assertCounts(long draftCount, long publishedCount) {
    entityManager.flush();
    entityManager.clear();
    StudyCategoryResponse counts = categoryService.getCategoryById(category.getId());
    assertThat(counts.draftCount()).as("draftCount").isEqualTo(draftCount);
    assertThat(counts.publishedCount()).as("publishedCount").isEqualTo(publishedCount);
  }

  private JsonNode create(String json) throws Exception {
    return objectMapper.readTree(
        mvc.perform(
                admin(post(ROOT + "/categories/" + category.getId() + "/questions"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode update(UUID id, String json) throws Exception {
    return objectMapper.readTree(
        mvc.perform(
                admin(put(ROOT + "/questions/" + id))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode send(MockHttpServletRequestBuilder call, int expectedStatus) throws Exception {
    return objectMapper.readTree(
        mvc.perform(admin(call))
            .andExpect(status().is(expectedStatus))
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private Map<String, Object> asMap(String json) throws Exception {
    return objectMapper.readValue(json, new TypeReference<>() {});
  }

  private static MockHttpServletRequestBuilder request(HttpMethod method, UUID id, String suffix) {
    String path = ROOT + "/questions/" + id + suffix;
    return method == HttpMethod.DELETE ? delete(path) : post(path);
  }

  private static String publishPath(UUID id) {
    return ROOT + "/questions/" + id + "/publish";
  }

  private static String unpublishPath(UUID id) {
    return ROOT + "/questions/" + id + "/unpublish";
  }

  private static String draftPath(UUID id) {
    return ROOT + "/questions/" + id + "/draft";
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
