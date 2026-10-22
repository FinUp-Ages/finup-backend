package br.com.finup.dto;

import br.com.finup.model.QuestionStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Contrato C6 administrativo. Os campos publicados devem aparecer mesmo quando nulos. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record StudyQuestionResponse(
    UUID id,
    @Schema(description = "Derivado da existência e da igualdade do conteúdo publicado")
        QuestionStatus status,
    Map<String, Object> draft,
    @Schema(nullable = true) Map<String, Object> published,
    Instant updatedAt,
    @Schema(nullable = true) Instant publishedAt) {}
