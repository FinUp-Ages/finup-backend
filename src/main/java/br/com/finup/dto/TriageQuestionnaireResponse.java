package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Questionário de triagem versionado: o app só renderiza, e devolve a versão no envio. */
@Schema(description = "Questionário de triagem")
public record TriageQuestionnaireResponse(
    @Schema(description = "Versão do questionário; o POST precisa enviar a mesma", example = "1")
        int version,
    List<TriageQuestionResponse> questions) {}
