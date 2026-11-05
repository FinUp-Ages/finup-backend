package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/** Uma pergunta do questionário de triagem, como o app a renderiza. */
@Schema(description = "Pergunta do questionário de triagem")
public record TriageQuestionResponse(
    @Schema(description = "Identificador estável da pergunta; é a chave da resposta no POST")
        String id,
    @Schema(description = "Texto exibido ao usuário") String text,
    @Schema(description = "Tipo de resposta esperada") Type type,
    @Schema(description = "Obrigatória sempre, ou só quando requiredWhen é satisfeita")
        boolean required,
    @Schema(description = "Opções, nas perguntas SINGLE_CHOICE e MULTIPLE_CHOICE")
        List<Option> options,
    @Schema(description = "Menor valor aceito, nas perguntas NUMBER") BigDecimal min,
    @Schema(description = "Maior valor aceito, nas perguntas NUMBER") BigDecimal max,
    @Schema(
            description =
                "Casas decimais aceitas, nas perguntas NUMBER: 0 para contagens, 2 para R$")
        Integer decimals,
    @Schema(description = "Quando preenchido, a pergunta só é cobrada se a condição for verdadeira")
        RequiredWhen requiredWhen) {

  public enum Type {
    SINGLE_CHOICE,
    MULTIPLE_CHOICE,
    NUMBER,
    BOOLEAN
  }

  /** Uma opção de resposta: {@code value} é o que o app envia, {@code label} o que exibe. */
  public record Option(String value, String label) {}

  /**
   * Condição de obrigatoriedade: a pergunta {@code questionId} foi respondida com {@code equals}.
   */
  public record RequiredWhen(String questionId, boolean equals) {}
}
