package br.com.finup.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.finup.dto.StudyCategoryResponse;
import br.com.finup.model.QuestionType;
import br.com.finup.model.StudyCategory;
import br.com.finup.model.StudyQuestion;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StudyCategoryMapperTest {

  private final StudyCategoryMapper mapper = new StudyCategoryMapper();

  @Test
  @DisplayName("Deve calcular draftCount e publishedCount corretamente para os 3 status derivados")
  void shouldCalculateCountsCorrectlyForDerivedStatuses() {
    StudyCategory category =
        new StudyCategory(UUID.randomUUID(), "Investimentos", "Renda Variável");

    // 1 pergunta DRAFT (published == null) -> conta como draft
    StudyQuestion draftQuestion =
        new StudyQuestion(
            category, QuestionType.FILL_BLANK, Map.of("statement", "Rascunho"), null, null);

    // 1 pergunta PUBLISHED (draft == published) -> conta como published
    StudyQuestion publishedQuestion =
        new StudyQuestion(
            category,
            QuestionType.MULTIPLE_CHOICE,
            Map.of("statement", "No ar"),
            Map.of("statement", "No ar"),
            Instant.now());

    // 1 pergunta PUBLISHED_WITH_CHANGES (draft != published) -> conta como published E como draft
    StudyQuestion publishedWithChangesQuestion =
        new StudyQuestion(
            category,
            QuestionType.LEARNING,
            Map.of("title", "Rascunho editado"),
            Map.of("title", "Versão antiga no ar"),
            Instant.now().minusSeconds(100));

    List<StudyQuestion> questions =
        List.of(draftQuestion, publishedQuestion, publishedWithChangesQuestion);

    StudyCategoryResponse response = mapper.toResponse(category, questions);

    assertThat(response).isNotNull();
    assertThat(response.id()).isEqualTo(category.getId());
    assertThat(response.name()).isEqualTo("Investimentos");
    assertThat(response.description()).isEqualTo("Renda Variável");
    // publishedCount = published (1) + published_with_changes (1) = 2
    assertThat(response.publishedCount()).isEqualTo(2);
    // draftCount = draft (1) + published_with_changes (1) = 2
    assertThat(response.draftCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("Deve retornar contagens zeradas quando lista de perguntas for vazia ou nula")
  void shouldReturnZeroCountsWhenQuestionsAreEmptyOrNull() {
    StudyCategory category = new StudyCategory(UUID.randomUUID(), "Economia", "Macroeconomia");

    StudyCategoryResponse responseEmpty = mapper.toResponse(category, List.of());
    assertThat(responseEmpty.draftCount()).isZero();
    assertThat(responseEmpty.publishedCount()).isZero();

    StudyCategoryResponse responseNull = mapper.toResponse(category, (List<StudyQuestion>) null);
    assertThat(responseNull.draftCount()).isZero();
    assertThat(responseNull.publishedCount()).isZero();

    assertThat(mapper.toResponse(null, List.of())).isNull();
  }
}
