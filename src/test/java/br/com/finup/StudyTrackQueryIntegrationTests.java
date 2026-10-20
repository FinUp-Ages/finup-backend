package br.com.finup;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.model.QuestionType;
import br.com.finup.model.StudyCategory;
import br.com.finup.model.StudyQuestion;
import br.com.finup.repository.StudyCategoryRepository;
import br.com.finup.repository.StudyQuestionRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:study-track;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
@AutoConfigureMockMvc
@ActiveProfiles("mock-auth")
class StudyTrackQueryIntegrationTests {

  private static final String MOCK_SUB = "study-track-test-user";
  private static final String MOCK_EMAIL = "study-track@example.com";

  @Autowired private MockMvc mockMvc;

  @Autowired private StudyCategoryRepository categoryRepository;

  @Autowired private StudyQuestionRepository questionRepository;

  private StudyCategory category;

  @BeforeEach
  void setUp() {
    questionRepository.deleteAll();
    categoryRepository.deleteAll();
    category = categoryRepository.saveAndFlush(new StudyCategory("Investimentos", "Renda fixa"));

    questionRepository.saveAndFlush(
        new StudyQuestion(
            category,
            QuestionType.MULTIPLE_CHOICE,
            Map.of("statement", "Rascunho inédito", "correctOptionId", "D"),
            Map.of("statement", "Pergunta publicada", "correctOptionId", "B"),
            Instant.now()));
    questionRepository.saveAndFlush(
        new StudyQuestion(
            category,
            QuestionType.FILL_BLANK,
            Map.of("statement", "Pergunta ainda em rascunho"),
            null,
            null));
    questionRepository.saveAndFlush(
        new StudyQuestion(
            category,
            QuestionType.LEARNING,
            Map.of("title", "Versão editada em rascunho"),
            Map.of("title", "Versão publicada original"),
            Instant.now()));
    Map<String, Object> publishedContent = Map.of("title", "Conteúdo publicado sem alterações");
    questionRepository.saveAndFlush(
        new StudyQuestion(
            category, QuestionType.LEARNING, publishedContent, publishedContent, Instant.now()));
  }

  @Test
  @DisplayName(
      "usuário autenticado recebe categorias e somente conteúdo publicado, inclusive sem draft pendente")
  void returnsCategoriesAndPublishedQuestionsWithoutDraft() throws Exception {
    var authenticatedRequest =
        get("/api/v1/study-tracks/categories")
            .header("X-Mock-Cognito-Sub", MOCK_SUB)
            .header("X-Mock-Cognito-Email", MOCK_EMAIL);

    mockMvc
        .perform(authenticatedRequest)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(category.getId().toString()))
        .andExpect(jsonPath("$[0].name").value("Investimentos"))
        .andExpect(jsonPath("$[0].publishedCount").value(3))
        .andExpect(jsonPath("$[0].draftCount").doesNotExist());

    mockMvc
        .perform(
            get("/api/v1/study-tracks/categories/{id}/questions", category.getId())
                .header("X-Mock-Cognito-Sub", MOCK_SUB)
                .header("X-Mock-Cognito-Email", MOCK_EMAIL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].published.statement").value("Pergunta publicada"))
        .andExpect(jsonPath("$[0].published.correctOptionId").value("B"))
        .andExpect(jsonPath("$[1].published.title").value("Versão publicada original"))
        .andExpect(jsonPath("$[2].published.title").value("Conteúdo publicado sem alterações"))
        .andExpect(jsonPath("$[0].draft").doesNotExist())
        .andExpect(jsonPath("$[1].draft").doesNotExist())
        .andExpect(jsonPath("$[2].draft").doesNotExist())
        .andExpect(content().string(not(org.hamcrest.Matchers.containsString("Rascunho"))))
        .andExpect(content().string(not(org.hamcrest.Matchers.containsString("Versão editada"))));
  }

  @Test
  @DisplayName("sem identidade autenticada as rotas da trilha respondem 401")
  void rejectsRequestsWithoutAuthenticatedIdentity() throws Exception {
    mockMvc.perform(get("/api/v1/study-tracks/categories")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("categoria inexistente responde 404")
  void returnsNotFoundForUnknownCategory() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/study-tracks/categories/{id}/questions", UUID.randomUUID())
                .header("X-Mock-Cognito-Sub", MOCK_SUB)
                .header("X-Mock-Cognito-Email", MOCK_EMAIL))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }
}
