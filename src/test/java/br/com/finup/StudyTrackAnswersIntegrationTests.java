package br.com.finup;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.model.QuestionType;
import br.com.finup.model.StudyCategory;
import br.com.finup.model.StudyQuestion;
import br.com.finup.model.StudyTrackAnswer;
import br.com.finup.model.User;
import br.com.finup.repository.StudyCategoryRepository;
import br.com.finup.repository.StudyQuestionRepository;
import br.com.finup.repository.StudyTrackAnswerRepository;
import br.com.finup.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Respostas e progresso por nível da trilha com HTTP, segurança, service e persistência reais. Só o
 * decoder JWT é simulado, com um usuário comum.
 */
@SpringBootTest(
    properties = {
      "spring.config.import=",
      "spring.datasource.url=jdbc:h2:mem:study-track-answers;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
class StudyTrackAnswersIntegrationTests {

  private static final String ANSWERS = "/api/v1/study-tracks/answers";
  private static final String PROGRESS = "/api/v1/study-tracks/progress";

  private static final Map<String, Object> MULTIPLE_CHOICE =
      Map.of(
          "type",
          "MULTIPLE_CHOICE",
          "statement",
          "Quantos meses de reserva?",
          "options",
          List.of(
              Map.of("text", "1 a 2 meses", "correct", false),
              Map.of("text", "3 a 6 meses", "correct", true)));

  private static final Map<String, Object> FILL_BLANK =
      Map.of(
          "type",
          "FILL_BLANK",
          "title",
          "Onde",
          "statement",
          "Alta ___.",
          "answers",
          List.of("liquidez"));

  private static final Map<String, Object> LEARNING =
      Map.of("type", "LEARNING", "title", "CDI", "body", "Texto");

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository users;
  @Autowired private StudyCategoryRepository categories;
  @Autowired private StudyQuestionRepository questions;
  @Autowired private StudyTrackAnswerRepository answers;
  @MockitoBean private JwtDecoder jwtDecoder;

  private StudyCategory reserve;
  private StudyQuestion level1;
  private StudyQuestion level2;
  private StudyQuestion level3;
  private StudyQuestion draft;

  @BeforeEach
  void setUp() {
    users.save(User.createFromCognitoIdentity("app-user", "Ana", "ana@example.com"));
    reserve = categories.save(new StudyCategory("Reserva", null));
    Instant now = Instant.now();
    level1 =
        questions.save(
            createdAt(
                new StudyQuestion(
                    reserve, QuestionType.MULTIPLE_CHOICE, MULTIPLE_CHOICE, MULTIPLE_CHOICE, now),
                now.minusSeconds(30)));
    level2 =
        questions.save(
            createdAt(
                new StudyQuestion(reserve, QuestionType.FILL_BLANK, FILL_BLANK, FILL_BLANK, now),
                now.minusSeconds(20)));
    level3 =
        questions.save(
            createdAt(
                new StudyQuestion(reserve, QuestionType.LEARNING, LEARNING, LEARNING, now),
                now.minusSeconds(10)));
    draft =
        questions.save(new StudyQuestion(reserve, QuestionType.FILL_BLANK, FILL_BLANK, null, null));
    questions.flush();
    when(jwtDecoder.decode("user")).thenReturn(userToken());
  }

  @Test
  void userWithoutAnswersHasOnlyTheFirstLevelReached() throws Exception {
    mvc.perform(user(get(PROGRESS)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categories[0].totalQuestions").value(3))
        .andExpect(jsonPath("$.categories[0].answeredQuestions").value(0))
        .andExpect(jsonPath("$.categories[0].currentLevel").value(1))
        .andExpect(jsonPath("$.categories[0].completionPercentage").value(0.0))
        .andExpect(jsonPath("$.categories[0]", hasKey("stars")))
        .andExpect(jsonPath("$.categories[0].stars").value(nullValue()))
        .andExpect(jsonPath("$.categories[0].levels[0]", hasKey("correct")))
        .andExpect(jsonPath("$.categories[0].levels", hasSize(3)))
        .andExpect(jsonPath("$.total.completionPercentage").value(0.0));
  }

  @Test
  void levelsFollowTheOrderOfThePublishedQuestions() throws Exception {
    String body =
        mvc.perform(user(get("/api/v1/study-tracks/categories/" + reserve.getId() + "/questions")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> order = JsonPath.read(body, "$[*].id");

    progress()
        .andExpect(jsonPath("$.categories[0].levels[0].questionId").value(order.get(0)))
        .andExpect(jsonPath("$.categories[0].levels[1].questionId").value(order.get(1)))
        .andExpect(jsonPath("$.categories[0].levels[2].questionId").value(order.get(2)));
  }

  @Test
  void partialAnswerIsSavedAndAdvancesTheCurrentLevel() throws Exception {
    send(Map.of(level(1), "3 a 6 meses"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stars").value(3))
        .andExpect(jsonPath("$.correctCount").value(1))
        .andExpect(jsonPath("$.gradableCount").value(1))
        .andExpect(jsonPath("$.answers", hasSize(1)));

    progress()
        .andExpect(jsonPath("$.categories[0].answeredQuestions").value(1))
        .andExpect(jsonPath("$.categories[0].currentLevel").value(2))
        .andExpect(jsonPath("$.categories[0].levels[0].completed").value(true))
        .andExpect(jsonPath("$.categories[0].levels[0].correct").value(true))
        .andExpect(jsonPath("$.categories[0].levels[0].stars").value(3))
        .andExpect(jsonPath("$.categories[0].levels[1].completed").value(false));

    send(Map.of(level(2), "errado"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stars").value(2));

    progress()
        .andExpect(jsonPath("$.categories[0].currentLevel").value(3))
        .andExpect(jsonPath("$.categories[0].levels[1].stars").value(1))
        .andExpect(jsonPath("$.categories[0].percentage").value(50.0));
  }

  @Test
  void fullBatchStillCompletesTheCategory() throws Exception {
    send(answersFor(level(1), "1 a 2 meses", level(2), " LIQUIDEZ ", level(3), null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stars").value(2))
        .andExpect(jsonPath("$.answers", hasSize(3)));

    progress()
        .andExpect(jsonPath("$.categories[0].answeredQuestions").value(3))
        .andExpect(jsonPath("$.categories[0].currentLevel").value(3))
        .andExpect(jsonPath("$.categories[0].completionPercentage").value(100.0))
        .andExpect(jsonPath("$.categories[0].levels[2]", hasKey("stars")))
        .andExpect(jsonPath("$.categories[0].levels[2].stars").value(nullValue()));
  }

  @Test
  void rejectsDraftQuestionAndDuplicatesWithoutSavingAnything() throws Exception {
    send(answersFor(level(1), "3 a 6 meses", draft.getId(), "liquidez"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
    mvc.perform(
            user(
                post(ANSWERS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"categoryId": "%s", "answers": [
                          {"questionId": "%s", "answer": "a"},
                          {"questionId": "%s", "answer": "b"}]}
                        """
                            .formatted(reserve.getId(), level(1), level(1)))))
        .andExpect(status().isBadRequest());

    progress().andExpect(jsonPath("$.categories[0].answeredQuestions").value(0));
  }

  @Test
  void unknownCategoryReturns404AndAuthenticationIsRequired() throws Exception {
    mvc.perform(
            user(
                post(ANSWERS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"categoryId": "%s", "answers": [{"questionId": "%s", "answer": "a"}]}
                        """
                            .formatted(UUID.randomUUID(), level(1)))))
        .andExpect(status().isNotFound());
    mvc.perform(get(PROGRESS)).andExpect(status().isUnauthorized());
    mvc.perform(post(ANSWERS).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void unpublishedQuestionLeavesTheProgress() throws Exception {
    send(answersFor(level(1), "3 a 6 meses", level(2), "liquidez")).andExpect(status().isOk());
    level1.setPublished(null);
    level1.setPublishedAt(null);
    questions.saveAndFlush(level1);

    progress()
        .andExpect(jsonPath("$.categories[0].totalQuestions").value(2))
        .andExpect(jsonPath("$.categories[0].answeredQuestions").value(1))
        .andExpect(jsonPath("$.categories[0].currentLevel").value(2))
        .andExpect(jsonPath("$.categories[0].levels[0].questionId").value(level(2).toString()));
  }

  @Test
  void answersOfAnotherUserAreNotCounted() throws Exception {
    User other = users.save(User.createFromCognitoIdentity("other", "Bia", "bia@example.com"));
    answers.saveAndFlush(
        new StudyTrackAnswer(
            other.getId(), reserve.getId(), level(1), "3 a 6 meses", true, 3, Instant.now()));

    progress()
        .andExpect(jsonPath("$.categories[0].answeredQuestions").value(0))
        .andExpect(jsonPath("$.categories[0].currentLevel").value(1));
  }

  /** O construtor usa o relógio atual; fixar o instante deixa a ordem dos níveis determinística. */
  private static StudyQuestion createdAt(StudyQuestion question, Instant instant) {
    ReflectionTestUtils.setField(question, "createdAt", instant);
    return question;
  }

  private UUID level(int number) {
    return switch (number) {
      case 1 -> level1.getId();
      case 2 -> level2.getId();
      default -> level3.getId();
    };
  }

  private static Map<UUID, String> answersFor(Object... pairs) {
    Map<UUID, String> result = new java.util.LinkedHashMap<>();
    for (int index = 0; index < pairs.length; index += 2) {
      result.put((UUID) pairs[index], (String) pairs[index + 1]);
    }
    return result;
  }

  private ResultActions send(Map<UUID, String> sent) throws Exception {
    String items =
        sent.entrySet().stream()
            .map(
                entry ->
                    "{\"questionId\": \"%s\", \"answer\": %s}"
                        .formatted(
                            entry.getKey(),
                            entry.getValue() == null ? "null" : "\"" + entry.getValue() + "\""))
            .reduce((first, second) -> first + "," + second)
            .orElse("");
    return mvc.perform(
        user(
            post(ANSWERS)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"categoryId\": \"%s\", \"answers\": [%s]}"
                        .formatted(reserve.getId(), items))));
  }

  private ResultActions progress() throws Exception {
    return mvc.perform(user(get(PROGRESS))).andExpect(status().isOk());
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
