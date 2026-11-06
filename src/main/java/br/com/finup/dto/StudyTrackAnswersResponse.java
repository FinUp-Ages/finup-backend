package br.com.finup.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StudyTrackAnswersResponse(
    UUID categoryId, Integer stars, int correctCount, int gradableCount, List<Answer> answers) {
  public record Answer(UUID questionId, String answer, Boolean correct, Instant submittedAt) {}
}
