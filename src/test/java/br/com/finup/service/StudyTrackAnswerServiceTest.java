package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.finup.dto.StudyTrackAnswersRequest;
import br.com.finup.dto.StudyTrackAnswersResponse;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudyTrackAnswerServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private StudyCategoryRepository categoryRepository;
  @Mock private StudyQuestionRepository questionRepository;
  @Mock private StudyTrackAnswerRepository answerRepository;

  private StudyTrackAnswerService service;
  private User user;
  private StudyCategory category;
  private StudyQuestion multipleChoice;
  private StudyQuestion fillBlank;

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
    multipleChoice =
        new StudyQuestion(
            UUID.randomUUID(),
            category,
            QuestionType.MULTIPLE_CHOICE,
            Map.of(),
            Map.of(
                "type",
                "MULTIPLE_CHOICE",
                "statement",
                "Qual?",
                "options",
                List.of(
                    Map.of("text", "A", "correct", false), Map.of("text", "B", "correct", true))),
            Instant.now());
    fillBlank =
        new StudyQuestion(
            UUID.randomUUID(),
            category,
            QuestionType.FILL_BLANK,
            Map.of(),
            Map.of("type", "FILL_BLANK", "answers", List.of("liquidez")),
            Instant.now());
    when(userRepository.findByCognitoId("sub")).thenReturn(Optional.of(user));
    org.mockito.Mockito.lenient()
        .when(categoryRepository.existsById(category.getId()))
        .thenReturn(true);
    when(questionRepository.findByCategoryIdAndPublishedIsNotNullOrderByCreatedAtAsc(
            category.getId()))
        .thenReturn(List.of(multipleChoice, fillBlank));
    org.mockito.Mockito.lenient()
        .when(answerRepository.save(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void correctsPublishedContentAndCalculatesTwoStarsAtFiftyPercent() {
    StudyTrackAnswersResponse response =
        service.submit(
            new AuthenticatedIdentity("sub", null, null),
            new StudyTrackAnswersRequest(
                category.getId(),
                List.of(
                    new StudyTrackAnswersRequest.Answer(multipleChoice.getId(), "A"),
                    new StudyTrackAnswersRequest.Answer(fillBlank.getId(), " LIQUIDEZ "))));

    assertThat(response.stars()).isEqualTo(2);
    assertThat(response.correctCount()).isEqualTo(1);
    assertThat(response.gradableCount()).isEqualTo(2);
    var saved = ArgumentCaptor.forClass(br.com.finup.model.StudyTrackAnswer.class);
    verify(answerRepository, org.mockito.Mockito.times(2)).save(saved.capture());
    assertThat(saved.getAllValues())
        .extracting(br.com.finup.model.StudyTrackAnswer::getCorrect)
        .containsExactly(false, true);
  }

  @Test
  void calculatesProgressOnlyFromAuthenticatedUserAnswers() {
    StudyTrackAnswer wrong =
        new StudyTrackAnswer(
            user.getId(),
            category.getId(),
            multipleChoice.getId(),
            "A",
            false,
            2,
            Instant.parse("2026-11-06T12:00:00Z"));
    StudyTrackAnswer right =
        new StudyTrackAnswer(
            user.getId(),
            category.getId(),
            fillBlank.getId(),
            "liquidez",
            true,
            2,
            Instant.parse("2026-11-06T12:00:00Z"));
    when(categoryRepository.findAllByOrderByNameAsc()).thenReturn(List.of(category));
    when(answerRepository.findByUserIdOrderBySubmittedAtAsc(user.getId()))
        .thenReturn(List.of(wrong, right));

    var progress = service.findProgress(new AuthenticatedIdentity("sub", null, null));

    assertThat(progress.categories()).hasSize(1);
    assertThat(progress.categories().getFirst().percentage()).isEqualTo(50.0);
    assertThat(progress.categories().getFirst().stars()).isEqualTo(2);
    assertThat(progress.total().correctAnswers()).isEqualTo(1);
    org.mockito.Mockito.verify(answerRepository).findByUserIdOrderBySubmittedAtAsc(user.getId());
  }
}
