package br.com.finup.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * Envio do questionário. {@code answers} é um mapa {@code id da pergunta -> valor}: texto em
 * SINGLE_CHOICE, lista de textos em MULTIPLE_CHOICE, número em NUMBER e booleano em BOOLEAN. O
 * valor chega cru ({@link JsonNode}) porque o tipo certo depende da pergunta, que só o validador
 * conhece.
 */
@Schema(description = "Respostas do questionário de triagem")
public record SubmitTriageRequest(
    @Schema(description = "Versão do questionário respondido", example = "1")
        @NotNull(message = "é obrigatória")
        Integer version,
    @Schema(
            description = "Respostas por id de pergunta",
            example = "{\"INCOME_TYPE\":\"FIXED\",\"HAS_DEBT\":false}")
        @NotEmpty(message = "é obrigatório")
        @Size(max = SubmitTriageRequest.MAX_ANSWERS, message = "tem respostas demais")
        Map<String, JsonNode> answers) {

  /** Teto de respostas por envio: folga sobre as perguntas do questionário, não o seu tamanho. */
  public static final int MAX_ANSWERS = 50;
}
