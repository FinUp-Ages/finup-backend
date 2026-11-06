package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

/** Últimas respostas da triagem, para pré-preencher o questionário ao refazê-lo. */
@Schema(description = "Últimas respostas da triagem")
public record TriageAnswersResponse(
    @Schema(description = "Versão do questionário em que foram respondidas") int version,
    @Schema(description = "Respostas por id de pergunta") Map<String, Object> answers,
    @Schema(description = "Instante do envio, em UTC") Instant completedAt) {}
