package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.finup.dto.StudyTrackAnswersRequest;
import br.com.finup.dto.StudyTrackAnswersResponse;
import br.com.finup.dto.StudyTrackProgressResponse;
import br.com.finup.dto.StudyTrackProgressResponse.CategoryProgress;
import br.com.finup.dto.StudyTrackProgressResponse.LevelProgress;
import br.com.finup.exception.InvalidStudyTrackAnswersException;
import br.com.finup.model.QuestionType;
import br.com.finup.model.StudyCategory;
import br.com.finup.model.StudyQuestion;
import br.com.finup.model.StudyTrackAnswer;
import br.com.finup.model.User;
import br.com.finup.repository.StudyCategoryRepository;
import br.com.finup.repository.StudyQuestionRepository;
import br.com.finup.repository.StudyTrackAnswerRepository;
import br.com.finup.repository.UserRepository;
import br.com.finup.security.AuthenticatedIdentity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudyTrackAnswerServiceTest {

  private static final AuthenticatedIdentity IDENTITY =
      new AuthenticatedIdentity("sub", null, null);

  @Mock private UserRepository userRepository;
  @Mock private StudyCategoryRepository categoryRepository;
  @Mock private StudyQuestionRepository questionRepository;
  @Mock private StudyTrackAnswerRepository answerRepository;

  private StudyTrackAnswerService service;
  private User user;
  private StudyCategory category;
  private StudyCategory single;
  private StudyCategory empty;
  private StudyQuestion multipleChoice;
  private StudyQuestion fillBlank;
  private StudyQuestion learning;
  private StudyQuestion singleQuestion;
  private final List<StudyQuestion> published = new ArrayList<>();

  /** Respostas gravadas, no lugar da tabela: o service lê o que acabou de gravar. */
  private final List<StudyTrackAnswer> stored = new ArrayList<>();

  @BeforeEach
  void setUp() {
    service =
        new StudyTrackAnswerService(
            userRepository,
            categoryRepository,
            questionRepository,
            answerRepository,
            Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC));
    user = User.createFromCognitoIdentity("sub", "Ana", "ana@example.com");
    category = new StudyCategory(UUID.randomUUID(), "Reserva", null);
    single = new StudyCategory(UUID.randomUUID(), "Uma pergunta", null);
    empty = new StudyCategory(UUID.randomUUID(), "Vazia", null);
    multipleChoice =
        question(
            category,
            QuestionType.MULTIPLE_CHOICE,
            Map.of(
                "type",
                "MULTIPLE_CHOICE",
                "statement",
                "Qual?",
                "options",
                List.of(
                    Map.of("text", "A", "correct", false), Map.of("text", "B", "correct", true))));
    fillBlank =
        question(
            category,
            QuestionType.FILL_BLANK,
            Map.of("type", "FILL_BLANK", "answers", List.of("liquidez")));
    learning =
        question(
            category,
            QuestionType.LEARNING,
            Map.of("type", "LEARNING", "title", "CDI", "body", ""));
    singleQuestion =
        question(
            single,
            QuestionType.FILL_BLANK,
            Map.of("type", "FILL_BLANK", "answers", List.of("selic")));
    published.addAll(List.of(multipleChoice, fillBlank, learning, singleQuestion));

    when(userRepository.findByCognitoId("sub")).thenReturn(Optional.of(user));
    lenient().when(categoryRepository.existsById(any())).thenReturn(true);
    lenient()
        .when(categoryRepository.findAllByOrderByNameAsc())
        .thenReturn(List.of(category, single, empty));
    lenient()
        .when(
            questionRepository.findByCategoryIdAndPublishedIsNotNullOrderByCreatedAtAscIdAsc(any()))
        .thenAnswer(
            invocation ->
                published.stream()
                    .filter(q -> q.getCategory().getId().equals(invocation.getArgument(0)))
                    .toList());
    lenient()
        .when(questionRepository.findByPublishedIsNotNullOrderByCreatedAtAscIdAsc())
        .thenAnswer(invocation -> List.copyOf(published));
    lenient()
        .when(answerRepository.save(any()))
        .thenAnswer(
            invocation -> {
              stored.add(invocation.getArgument(0));
              return invocation.getArgument(0);
            });
    lenient()
        .when(answerRepository.findByUserIdAndQuestionId(any(), any()))
        .thenAnswer(
            invocation ->
                stored.stream()
                    .filter(answer -> Objects.equals(answer.getUserId(), invocation.getArgument(0)))
                    .filter(answer -> answer.getQuestionId().equals(invocation.getArgument(1)))
                    .findFirst());
    lenient()
        .when(answerRepository.findByUserIdAndCategoryIdOrderByQuestionId(any(), any()))
        .thenAnswer(
            invocation ->
                stored.stream()
                    .filter(answer -> Objects.equals(answer.getUserId(), invocation.getArgument(0)))
                    .filter(answer -> answer.getCategoryId().equals(invocation.getArgument(1)))
                    .toList());
    lenient()
        .when(answerRepository.findByUserIdOrderBySubmittedAtAsc(any()))
        .thenAnswer(
            invocation ->
                stored.stream()
                    .filter(answer -> Objects.equals(answer.getUserId(), invocation.getArgument(0)))
                    .toList());
  }

  @Test
  void fullBatchStillWorksAndCalculatesCategoryStarsAtFiftyPercent() {
    StudyTrackAnswersResponse response =
        submit(
            category,
            answer(multipleChoice, "A"),
            answer(fillBlank, " LIQUIDEZ "),
            answer(learning, null));

    assertThat(response.stars()).isEqualTo(2);
    assertThat(response.correctCount()).isEqualTo(1);
    assertThat(response.gradableCount()).isEqualTo(2);
    assertThat(response.answers()).hasSize(3);
    assertThat(stored).extracting(StudyTrackAnswer::getCorrect).containsExactly(false, true, null);
    assertThat(stored).extracting(StudyTrackAnswer::getStars).containsExactly(1, 3, null);
  }

  @Test
  void acceptsPartialBatchAndRecalculatesOverAllCurrentAnswers() {
    StudyTrackAnswersResponse first = submit(category, answer(multipleChoice, "B"));

    assertThat(first.stars()).isEqualTo(3);
    assertThat(first.correctCount()).isEqualTo(1);
    assertThat(first.gradableCount()).isEqualTo(1);
    assertThat(first.answers()).hasSize(1);

    StudyTrackAnswersResponse second = submit(category, answer(fillBlank, "errado"));

    assertThat(second.stars()).isEqualTo(2);
    assertThat(second.correctCount()).isEqualTo(1);
    assertThat(second.gradableCount()).isEqualTo(2);
    assertThat(second.answers())
        .extracting(StudyTrackAnswersResponse.Answer::questionId)
        .containsExactlyInAnyOrder(multipleChoice.getId(), fillBlank.getId());
  }

  @Test
  void answeringAgainReplacesThePreviousAnswer() {
    submit(category, answer(multipleChoice, "A"));
    StudyTrackAnswersResponse response = submit(category, answer(multipleChoice, "B"));

    assertThat(stored).hasSize(1);
    assertThat(stored.getFirst().getCorrect()).isTrue();
    assertThat(stored.getFirst().getStars()).isEqualTo(3);
    assertThat(response.stars()).isEqualTo(3);
  }

  @Test
  void rejectsQuestionSentTwiceWithoutSavingAnything() {
    assertThatThrownBy(
            () -> submit(category, answer(multipleChoice, "A"), answer(multipleChoice, "B")))
        .isInstanceOf(InvalidStudyTrackAnswersException.class)
        .hasMessage("A pergunta foi enviada mais de uma vez.");
    verify(answerRepository, never()).save(any());
  }

  @Test
  void rejectsQuestionFromAnotherCategoryOrNotPublishedWithoutSavingAnything() {
    UUID unpublished = UUID.randomUUID();
    assertThatThrownBy(
            () ->
                submit(
                    category,
                    answer(multipleChoice, "B"),
                    new StudyTrackAnswersRequest.Answer(unpublished, "x")))
        .isInstanceOf(InvalidStudyTrackAnswersException.class)
        .hasMessage("O lote só pode conter perguntas publicadas da categoria.");
    assertThatThrownBy(() -> submit(category, answer(singleQuestion, "selic")))
        .isInstanceOf(InvalidStudyTrackAnswersException.class);
    verify(answerRepository, never()).save(any());
  }

  @Test
  void userWithoutAnswersHasFirstLevelReachedAndNothingCompleted() {
    StudyTrackProgressResponse progress = service.findProgress(IDENTITY);

    CategoryProgress reserve = progressOf(progress, category);
    assertThat(reserve.totalQuestions()).isEqualTo(3);
    assertThat(reserve.answeredQuestions()).isZero();
    assertThat(reserve.currentLevel()).isEqualTo(1);
    assertThat(reserve.completionPercentage()).isZero();
    assertThat(reserve.percentage()).isZero();
    assertThat(reserve.stars()).isNull();
    assertThat(reserve.levels())
        .extracting(LevelProgress::level, LevelProgress::questionId, LevelProgress::completed)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(1, multipleChoice.getId(), false),
            org.assertj.core.groups.Tuple.tuple(2, fillBlank.getId(), false),
            org.assertj.core.groups.Tuple.tuple(3, learning.getId(), false));
    assertThat(reserve.levels()).extracting(LevelProgress::stars).containsOnlyNulls();
    assertThat(progress.total().completionPercentage()).isZero();
  }

  @Test
  void partialProgressPointsToFirstPendingLevelWithStarsPerLevel() {
    submit(category, answer(multipleChoice, "A"), answer(fillBlank, "liquidez"));

    CategoryProgress reserve = progressOf(service.findProgress(IDENTITY), category);

    assertThat(reserve.answeredQuestions()).isEqualTo(2);
    assertThat(reserve.currentLevel()).isEqualTo(3);
    assertThat(reserve.completionPercentage()).isEqualTo(200.0 / 3);
    assertThat(reserve.percentage()).isEqualTo(50.0);
    assertThat(reserve.stars()).isEqualTo(2);
    assertThat(reserve.levels())
        .extracting(LevelProgress::completed)
        .containsExactly(true, true, false);
    assertThat(reserve.levels())
        .extracting(LevelProgress::correct)
        .containsExactly(false, true, null);
    assertThat(reserve.levels()).extracting(LevelProgress::stars).containsExactly(1, 3, null);
  }

  @Test
  void answerOutOfOrderDoesNotSkipThePendingFirstLevel() {
    submit(category, answer(fillBlank, "liquidez"));

    CategoryProgress reserve = progressOf(service.findProgress(IDENTITY), category);

    assertThat(reserve.currentLevel()).isEqualTo(1);
    assertThat(reserve.answeredQuestions()).isEqualTo(1);
    assertThat(reserve.levels().get(1).completed()).isTrue();
  }

  @Test
  void completedCategoryReachesLastLevelWithFullCompletion() {
    submit(category, answer(multipleChoice, "B"), answer(fillBlank, "liquidez"));
    submit(category, answer(learning, null));

    CategoryProgress reserve = progressOf(service.findProgress(IDENTITY), category);

    assertThat(reserve.currentLevel()).isEqualTo(3);
    assertThat(reserve.completionPercentage()).isEqualTo(100.0);
    assertThat(reserve.percentage()).isEqualTo(100.0);
    assertThat(reserve.stars()).isEqualTo(3);
    LevelProgress read = reserve.levels().get(2);
    assertThat(read.completed()).isTrue();
    assertThat(read.correct()).isNull();
    assertThat(read.stars()).isNull();
  }

  @Test
  void categoryWithOneQuestionGoesFromZeroToFullCompletion() {
    CategoryProgress before = progressOf(service.findProgress(IDENTITY), single);
    assertThat(before.currentLevel()).isEqualTo(1);
    assertThat(before.completionPercentage()).isZero();

    submit(single, answer(singleQuestion, "Selic"));

    CategoryProgress after = progressOf(service.findProgress(IDENTITY), single);
    assertThat(after.currentLevel()).isEqualTo(1);
    assertThat(after.completionPercentage()).isEqualTo(100.0);
    assertThat(after.levels()).extracting(LevelProgress::stars).containsExactly(3);
  }

  @Test
  void categoryWithoutPublishedQuestionsHasNoLevels() {
    CategoryProgress none = progressOf(service.findProgress(IDENTITY), empty);

    assertThat(none.totalQuestions()).isZero();
    assertThat(none.currentLevel()).isZero();
    assertThat(none.completionPercentage()).isZero();
    assertThat(none.levels()).isEmpty();
  }

  @Test
  void answersOfUnpublishedQuestionsLeaveProgressAndStars() {
    submit(category, answer(multipleChoice, "A"), answer(fillBlank, "liquidez"));
    published.remove(multipleChoice);

    StudyTrackProgressResponse progress = service.findProgress(IDENTITY);
    CategoryProgress reserve = progressOf(progress, category);

    assertThat(reserve.totalQuestions()).isEqualTo(2);
    assertThat(reserve.answeredQuestions()).isEqualTo(1);
    assertThat(reserve.correctAnswers()).isEqualTo(1);
    assertThat(reserve.gradableQuestions()).isEqualTo(1);
    assertThat(reserve.stars()).isEqualTo(3);
    assertThat(reserve.currentLevel()).isEqualTo(2);
    assertThat(progress.total().answeredQuestions()).isEqualTo(1);

    StudyTrackAnswersResponse next = submit(category, answer(learning, null));
    assertThat(next.answers())
        .extracting(StudyTrackAnswersResponse.Answer::questionId)
        .doesNotContain(multipleChoice.getId());
    assertThat(next.gradableCount()).isEqualTo(1);
  }

  @Test
  void questionPublishedAfterCompletionBecomesTheCurrentLevel() {
    submit(
        category,
        answer(multipleChoice, "B"),
        answer(fillBlank, "liquidez"),
        answer(learning, null));
    StudyQuestion added =
        question(
            category,
            QuestionType.LEARNING,
            Map.of("type", "LEARNING", "title", "Novo", "body", ""));
    published.add(added);

    CategoryProgress reserve = progressOf(service.findProgress(IDENTITY), category);

    assertThat(reserve.totalQuestions()).isEqualTo(4);
    assertThat(reserve.currentLevel()).isEqualTo(4);
    assertThat(reserve.completionPercentage()).isEqualTo(75.0);
    assertThat(reserve.levels().get(3).questionId()).isEqualTo(added.getId());
  }

  @Test
  void totalAddsAllCategoriesAndOnlyReadsTheAuthenticatedUser() {
    submit(category, answer(multipleChoice, "B"));
    submit(single, answer(singleQuestion, "errado"));

    StudyTrackProgressResponse progress = service.findProgress(IDENTITY);

    assertThat(progress.categories()).hasSize(3);
    assertThat(progress.total().totalQuestions()).isEqualTo(4);
    assertThat(progress.total().answeredQuestions()).isEqualTo(2);
    assertThat(progress.total().correctAnswers()).isEqualTo(1);
    assertThat(progress.total().percentage()).isEqualTo(50.0);
    assertThat(progress.total().completionPercentage()).isEqualTo(50.0);
    assertThat(progress.total().stars()).isEqualTo(2);
    verify(answerRepository).findByUserIdOrderBySubmittedAtAsc(user.getId());
  }

  private StudyTrackAnswersResponse submit(
      StudyCategory target, StudyTrackAnswersRequest.Answer... answers) {
    return service.submit(
        IDENTITY, new StudyTrackAnswersRequest(target.getId(), Arrays.asList(answers)));
  }

  private static StudyTrackAnswersRequest.Answer answer(StudyQuestion question, String value) {
    return new StudyTrackAnswersRequest.Answer(question.getId(), value);
  }

  private static StudyQuestion question(
      StudyCategory owner, QuestionType type, Map<String, Object> content) {
    return new StudyQuestion(UUID.randomUUID(), owner, type, Map.of(), content, Instant.now());
  }

  private static CategoryProgress progressOf(
      StudyTrackProgressResponse progress, StudyCategory target) {
    return progress.categories().stream()
        .filter(item -> item.categoryId().equals(target.getId()))
        .findFirst()
        .orElseThrow();
  }
}
