package br.com.finup.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record StudyTrackAnswersRequest(
    @NotNull UUID categoryId, @NotEmpty List<@Valid Answer> answers) {
  public record Answer(@NotNull UUID questionId, String answer) {}
}
