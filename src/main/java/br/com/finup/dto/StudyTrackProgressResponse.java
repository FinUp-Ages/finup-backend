package br.com.finup.dto;

import java.util.List;
import java.util.UUID;

public record StudyTrackProgressResponse(List<CategoryProgress> categories, Progress total) {
  public record CategoryProgress(
      UUID categoryId,
      int totalQuestions,
      int answeredQuestions,
      int correctAnswers,
      int gradableQuestions,
      Integer stars,
      double percentage) {}

  public record Progress(
      int totalQuestions,
      int answeredQuestions,
      int correctAnswers,
      int gradableQuestions,
      Integer stars,
      double percentage) {}
}
