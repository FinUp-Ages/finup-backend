package br.com.finup.dto;

import br.com.finup.model.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Pergunta publicada; o contrato não inclui conteúdo de rascunho. */
@Schema(description = "Pergunta publicada da trilha de estudos")
public record StudyTrackQuestionResponse(
    @Schema(description = "Identificador único da pergunta") UUID id,
    @Schema(description = "Tipo da pergunta", example = "MULTIPLE_CHOICE") QuestionType type,
    @Schema(
            description =
                "Conteúdo publicado da pergunta. Inclui a resposta correta para validação pelo app.")
        Map<String, Object> published,
    @Schema(description = "Data da última publicação") Instant publishedAt) {}
