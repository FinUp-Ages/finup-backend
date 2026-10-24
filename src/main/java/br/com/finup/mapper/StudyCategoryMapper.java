package br.com.finup.mapper;

import br.com.finup.dto.StudyCategoryResponse;
import br.com.finup.model.QuestionStatus;
import br.com.finup.model.StudyCategory;
import br.com.finup.model.StudyQuestion;
import java.util.Collection;
import org.springframework.stereotype.Component;

/**
 * Mapeador de categorias da trilha de estudos (Contrato C6).
 *
 * <p>Consolida as contagens de perguntas por categoria conforme o critério definido:
 *
 * <ul>
 *   <li>{@code publishedCount}: soma perguntas com versão no ar (PUBLISHED e
 *       PUBLISHED_WITH_CHANGES).
 *   <li>{@code draftCount}: soma perguntas com edições pendentes (DRAFT e PUBLISHED_WITH_CHANGES).
 * </ul>
 */
@Component
public class StudyCategoryMapper {

  public StudyCategoryResponse toResponse(
      StudyCategory category, long draftCount, long publishedCount) {
    if (category == null) {
      return null;
    }
    return new StudyCategoryResponse(
        category.getId(),
        category.getName(),
        category.getDescription(),
        draftCount,
        publishedCount,
        category.getCreatedAt(),
        category.getUpdatedAt());
  }

  public StudyCategoryResponse toResponse(
      StudyCategory category, Collection<StudyQuestion> questions) {
    if (category == null) {
      return null;
    }
    long publishedCount = 0;
    long draftCount = 0;

    if (questions != null) {
      for (StudyQuestion question : questions) {
        QuestionStatus status = question.getStatus();
        if (status == QuestionStatus.PUBLISHED || status == QuestionStatus.PUBLISHED_WITH_CHANGES) {
          publishedCount++;
        }
        if (status == QuestionStatus.DRAFT || status == QuestionStatus.PUBLISHED_WITH_CHANGES) {
          draftCount++;
        }
      }
    }

    return toResponse(category, draftCount, publishedCount);
  }
}
