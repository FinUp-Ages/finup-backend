package br.com.finup;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.model.QuestionType;
import br.com.finup.model.StudyCategory;
import br.com.finup.model.StudyQuestion;
import br.com.finup.repository.StudyCategoryRepository;
import br.com.finup.repository.StudyQuestionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leitura da trilha pelo app com HTTP, segurança, service e persistência reais. Só o decoder JWT é
 * simulado, com um usuário comum (sem grupo admin).
 */
@SpringBootTest(
    properties = {
      "spring.config.import=",
      "spring.datasource.url=jdbc:h2:mem:study-tracks;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "finup.cognito.user-pool-id=us-east-1_TestPool",
      "finup.cognito.client-id=test-client",
      "finup.cors.allowed-origins=http://localhost:5173"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudyTrackIntegrationTests {

  private static final String ROOT = "/api/v1/study-tracks/categories";

  private static final Map<String, Object> MULTIPLE_CHOICE =
      Map.of(
          "type",
          "MULTIPLE_CHOICE",
          "statement",
          "Quantos meses de reserva?",
          "options",
          List.of(
              Map.of("text", "1 a 2 meses", "correct", false),
              Map.of("text", "3 a 6 meses", "correct", true)),
          "explanation",
          "Entre 3 e 6 meses de despesas essenciais.");

  private static final Map<String, Object> FILL_BLANK =
      Map.of(
          "type",
          "FILL_BLANK",
          "title",
          "Onde aplicar",
          "statement",
          "A reserva deve ter alta ___.",
          "answers",
          List.of("liquidez"),
          "explanation",
          "");

  private static final Map<String, Object> LEARNING_PUBLISHED =
      Map.of("type", "LEARNING", "title", "CDI", "body", "Versão publicada");

  private static final Map<String, Object> LEARNING_DRAFT =
      Map.of("type", "LEARNING", "title", "CDI", "body", "Rascunho em edição");

  @Autowired private MockMvc mvc;
  @Autowired private StudyCategoryRepository categories;
  @Autowired private StudyQuestionRepository questions;
  @MockitoBean private JwtDecoder jwtDecoder;

  private StudyCategory reserve;
  private StudyCategory investments;
  private StudyCategory empty;
  private StudyQuestion published;
  private StudyQuestion publishedWithChanges;

  @BeforeEach
  void setUp() {
    reserve = categories.save(new StudyCategory("Reserva", "Conceitos de reserva"));
    investments = categories.save(new StudyCategory("Investimentos", null));
    empty = categories.save(new StudyCategory("Sem perguntas", null));
    Instant now = Instant.now();
    published =
        questions.save(
            new StudyQuestion(
                reserve, QuestionType.MULTIPLE_CHOICE, MULTIPLE_CHOICE, MULTIPLE_CHOICE, now));
    publishedWithChanges =
        questions.save(
            new StudyQuestion(
                reserve, QuestionType.LEARNING, LEARNING_DRAFT, LEARNING_PUBLISHED, now));
    questions.save(new StudyQuestion(reserve, QuestionType.FILL_BLANK, FILL_BLANK, null, null));
    questions.save(new StudyQuestion(investments, QuestionType.FILL_BLANK, FILL_BLANK, null, null));
    questions.flush();
    when(jwtDecoder.decode("user")).thenReturn(userToken());
  }

  @Test
  void listsCategoriesByNameWithPublishedCountForCommonUser() throws Exception {
    mvc.perform(user(get(ROOT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(3)))
        .andExpect(jsonPath("$[0].name").value("Investimentos"))
        .andExpect(jsonPath("$[0].publishedCount").value(0))
        .andExpect(jsonPath("$[1].id").value(reserve.getId().toString()))
        .andExpect(jsonPath("$[1].name").value("Reserva"))
        .andExpect(jsonPath("$[1].description").value("Conceitos de reserva"))
        .andExpect(jsonPath("$[1].publishedCount").value(2))
        .andExpect(jsonPath("$[2].name").value("Sem perguntas"))
        .andExpect(jsonPath("$[2].publishedCount").value(0));
  }

  @Test
  void listsOnlyPublishedQuestionsWithPublishedContentAndCorrectAnswer() throws Exception {
    mvc.perform(user(get(questionsOf(reserve.getId()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(
            jsonPath("$[*].id")
                .value(
                    containsInAnyOrder(
                        published.getId().toString(), publishedWithChanges.getId().toString())))
        .andExpect(
            jsonPath(byId(published) + ".published.statement").value("Quantos meses de reserva?"))
        .andExpect(jsonPath(byId(published) + ".published.options[1].correct").value(true))
        .andExpect(
            jsonPath(byId(publishedWithChanges) + ".published.body").value("Versão publicada"))
        .andExpect(jsonPath("$[*].draft").isEmpty())
        .andExpect(jsonPath("$[*].status").isEmpty());
  }

  @Test
  void categoryWithOnlyDraftQuestionsReturnsEmptyList() throws Exception {
    mvc.perform(user(get(questionsOf(investments.getId()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
    mvc.perform(user(get(questionsOf(empty.getId()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void unknownCategoryReturns404() throws Exception {
    mvc.perform(user(get(questionsOf(UUID.randomUUID()))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  void requiresAuthentication() throws Exception {
    mvc.perform(get(ROOT)).andExpect(status().isUnauthorized());
    mvc.perform(get(questionsOf(reserve.getId()))).andExpect(status().isUnauthorized());
  }

  private static String byId(StudyQuestion question) {
    return "$[?(@.id == '" + question.getId() + "')]";
  }

  private static String questionsOf(UUID categoryId) {
    return ROOT + "/" + categoryId + "/questions";
  }

  private static MockHttpServletRequestBuilder user(MockHttpServletRequestBuilder call) {
    return call.header("Authorization", "Bearer user");
  }

  private static Jwt userToken() {
    Instant now = Instant.now();
    return Jwt.withTokenValue("user")
        .header("alg", "RS256")
        .subject("app-user")
        .issuedAt(now.minusSeconds(60))
        .expiresAt(now.plusSeconds(3600))
        .build();
  }
}
