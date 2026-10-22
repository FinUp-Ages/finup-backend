package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.finup.dto.StudyQuestionRequest;
import br.com.finup.dto.StudyQuestionResponse;
import br.com.finup.exception.InvalidStudyQuestionException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.QuestionStatus;
import br.com.finup.model.QuestionType;
import br.com.finup.model.StudyCategory;
import br.com.finup.model.StudyQuestion;
import br.com.finup.repository.StudyCategoryRepository;
import br.com.finup.repository.StudyQuestionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudyQuestionServiceTest {

  @Mock private StudyCategoryRepository categories;
  @Mock private StudyQuestionRepository questions;
  private StudyQuestionService service;

  @BeforeEach
  void setUp() {
    service = new StudyQuestionService(categories, questions, new StudyQuestionValidator());
  }

  @Test
  void replacesDraftWithoutMutatingThePublishedMapEvenWhenTheyWereShared() {
    UUID id = UUID.randomUUID();
    Map<String, Object> original =
        Map.of("type", "LEARNING", "title", "Reserva", "body", "Original");
    Instant publishedAt = Instant.parse("2026-10-07T12:00:00Z");
    StudyQuestion question =
        new StudyQuestion(
            new StudyCategory("CDB", null), QuestionType.LEARNING, original, original, publishedAt);
    when(questions.findById(id)).thenReturn(Optional.of(question));
    when(questions.saveAndFlush(question)).thenReturn(question);

    StudyQuestionResponse response = service.update(id, learning("Novo conteúdo"));

    assertThat(response.status()).isEqualTo(QuestionStatus.PUBLISHED_WITH_CHANGES);
    assertThat(response.draft()).containsEntry("body", "Novo conteúdo");
    assertThat(response.published()).isSameAs(original).containsEntry("body", "Original");
    assertThat(response.publishedAt()).isEqualTo(publishedAt);
  }

  @Test
  void identicalContentRemainsPublished() {
    UUID id = UUID.randomUUID();
    Map<String, Object> original =
        Map.of("type", "LEARNING", "title", "Reserva", "body", "Original");
    StudyQuestion question =
        new StudyQuestion(
            new StudyCategory("CDB", null),
            QuestionType.LEARNING,
            original,
            original,
            Instant.now());
    when(questions.findById(id)).thenReturn(Optional.of(question));
    when(questions.saveAndFlush(question)).thenReturn(question);

    assertThat(service.update(id, learning("Original")).status())
        .isEqualTo(QuestionStatus.PUBLISHED);
  }

  @Test
  void invalidUpdateDoesNotChangeTheDraftOrWriteToTheRepository() {
    UUID id = UUID.randomUUID();
    Map<String, Object> original = Map.of("title", "Reserva", "body", "Original");
    StudyQuestion question =
        new StudyQuestion(
            new StudyCategory("CDB", null), QuestionType.LEARNING, original, null, null);
    when(questions.findById(id)).thenReturn(Optional.of(question));

    assertThatThrownBy(() -> service.update(id, learning(" ")))
        .isInstanceOf(InvalidStudyQuestionException.class);
    assertThat(question.getDraft()).isSameAs(original);
    verify(questions, never()).saveAndFlush(any());
  }

  @Test
  void rejectsChangingTypeAndPreservesOriginalContent() {
    UUID id = UUID.randomUUID();
    Map<String, Object> original = Map.of("statement", "Rende ___", "answers", List.of("100%"));
    StudyQuestion question =
        new StudyQuestion(
            new StudyCategory("CDB", null), QuestionType.FILL_BLANK, original, null, null);
    when(questions.findById(id)).thenReturn(Optional.of(question));

    assertThatThrownBy(() -> service.update(id, learning("Texto")))
        .isInstanceOf(InvalidStudyQuestionException.class);
    assertThat(question.getDraft()).isSameAs(original);
    assertThat(question.getType()).isEqualTo(QuestionType.FILL_BLANK);
    verify(questions, never()).saveAndFlush(any());
  }

  @Test
  void missingCategoryDoesNotCreateAnything() {
    UUID id = UUID.randomUUID();
    when(categories.findById(id)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.create(id, learning("Texto")))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(questions, never()).saveAndFlush(any());
  }

  @Test
  void missingCategoryDoesNotLookLikeAnEmptyList() {
    UUID id = UUID.randomUUID();
    when(categories.existsById(id)).thenReturn(false);
    assertThatThrownBy(() -> service.findByCategory(id))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(questions, never()).findByCategoryIdOrderByCreatedAtAsc(any());
  }

  @Test
  void deletingMissingQuestionReturnsNotFound() {
    UUID id = UUID.randomUUID();
    when(questions.findById(id)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.delete(id)).isInstanceOf(ResourceNotFoundException.class);
    verify(questions, never()).delete(any(StudyQuestion.class));
  }

  private static StudyQuestionRequest learning(String body) {
    return new StudyQuestionRequest(QuestionType.LEARNING, "Reserva", null, null, null, null, body);
  }
}
