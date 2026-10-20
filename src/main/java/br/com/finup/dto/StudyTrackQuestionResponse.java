package br.com.finup.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Pergunta publicada; o contrato não inclui conteúdo de rascunho. */
@Schema(description = "Pergunta publicada da trilha de estudos")
public record StudyTrackQuestionResponse(
    @Schema(description = "Identificador único da pergunta") UUID id,
    @Schema(description = "Conteúdo publicado da pergunta") JsonNode published) {}
