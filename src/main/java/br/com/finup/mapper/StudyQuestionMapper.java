package br.com.finup.mapper;

import br.com.finup.dto.StudyQuestionRequest;
import br.com.finup.dto.StudyQuestionResponse;
import br.com.finup.model.StudyQuestion;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converte conteúdo validado em JSON, sem incluir campos de outros tipos ou metadados. */
public final class StudyQuestionMapper {

  private StudyQuestionMapper() {}

  public static Map<String, Object> toDraft(StudyQuestionRequest request) {
    Map<String, Object> content = new LinkedHashMap<>();
    content.put("type", request.type().name());
    switch (request.type()) {
      case FILL_BLANK -> {
        content.put("title", request.title());
        content.put("statement", request.statement());
        content.put("answers", List.copyOf(request.answers()));
        if (request.explanation() != null) {
          content.put("explanation", request.explanation());
        }
      }
      case MULTIPLE_CHOICE -> {
        content.put("statement", request.statement());
        content.put(
            "options",
            request.options().stream()
                .map(option -> Map.of("text", option.text(), "correct", option.correct()))
                .toList());
        if (request.explanation() != null) {
          content.put("explanation", request.explanation());
        }
      }
      case LEARNING -> {
        content.put("title", request.title());
        content.put("body", request.body());
      }
    }
    return content;
  }

  public static StudyQuestionResponse toResponse(StudyQuestion question) {
    return new StudyQuestionResponse(
        question.getId(),
        question.getStatus(),
        question.getDraft(),
        question.getPublished(),
        question.getUpdatedAt(),
        question.getPublishedAt());
  }
}
