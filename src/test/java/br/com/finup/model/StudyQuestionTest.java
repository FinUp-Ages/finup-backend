package br.com.finup.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StudyQuestionTest {

  @Test
  @DisplayName("Deve retornar status DRAFT quando published for nulo")
  void shouldReturnDraftWhenPublishedIsNull() {
    StudyCategory category = new StudyCategory("Finanças Básicas", "Descrição teste");
    StudyQuestion question =
        new StudyQuestion(
            category,
            QuestionType.FILL_BLANK,
            Map.of("statement", "Rascunho de pergunta"),
            null,
            null);

    assertThat(question.getStatus()).isEqualTo(QuestionStatus.DRAFT);
  }

  @Test
  @DisplayName(
      "Deve retornar status PUBLISHED quando published não for nulo e draft for idêntico a published")
  void shouldReturnPublishedWhenDraftMatchesPublished() {
    StudyCategory category = new StudyCategory("Finanças Básicas", "Descrição teste");
    Map<String, Object> content = Map.of("statement", "Pergunta publicada", "answer", "B");

    StudyQuestion question =
        new StudyQuestion(category, QuestionType.MULTIPLE_CHOICE, content, content, Instant.now());

    assertThat(question.getStatus()).isEqualTo(QuestionStatus.PUBLISHED);
  }

  @Test
  @DisplayName(
      "Deve retornar status PUBLISHED_WITH_CHANGES quando published existir mas draft for diferente")
  void shouldReturnPublishedWithChangesWhenDraftDiffersFromPublished() {
    StudyCategory category = new StudyCategory("Finanças Básicas", "Descrição teste");
    Map<String, Object> publishedContent = Map.of("title", "Versão original");
    Map<String, Object> draftContent = Map.of("title", "Versão editada com novidades");

    StudyQuestion question =
        new StudyQuestion(
            category,
            QuestionType.LEARNING,
            draftContent,
            publishedContent,
            Instant.now().minusSeconds(3600));

    assertThat(question.getStatus()).isEqualTo(QuestionStatus.PUBLISHED_WITH_CHANGES);
  }

  @Test
  @DisplayName("Deve inicializar propriedades corretamente com id e categoria")
  void shouldInitializePropertiesCorrectly() {
    UUID id = UUID.randomUUID();
    StudyCategory category = new StudyCategory(UUID.randomUUID(), "Investimentos", "Renda Fixa");
    Map<String, Object> draft = Map.of("key", "val1");
    Map<String, Object> published = Map.of("key", "val1");
    Instant publishedAt = Instant.now();

    StudyQuestion question =
        new StudyQuestion(
            id, category, QuestionType.MULTIPLE_CHOICE, draft, published, publishedAt);

    assertThat(question.getId()).isEqualTo(id);
    assertThat(question.getCategory()).isEqualTo(category);
    assertThat(question.getType()).isEqualTo(QuestionType.MULTIPLE_CHOICE);
    assertThat(question.getDraft()).isEqualTo(draft);
    assertThat(question.getPublished()).isEqualTo(published);
    assertThat(question.getPublishedAt()).isEqualTo(publishedAt);
    assertThat(question.getCreatedAt()).isNotNull();
    assertThat(question.getUpdatedAt()).isNotNull();
  }
}
