package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import java.util.UUID;

/** Pergunta publicada; o contrato não inclui conteúdo de rascunho. */
@Schema(description = "Pergunta publicada da trilha de estudos")
public record StudyTrackQuestionResponse(
    @Schema(description = "Identificador único da pergunta") UUID id,
    @Schema(
            description =
                "Conteúdo publicado, no formato da seção 4 do contrato C6, com a resposta correta")
        Map<String, Object> published) {}
