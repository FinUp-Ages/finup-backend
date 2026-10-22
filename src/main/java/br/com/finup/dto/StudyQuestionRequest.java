package br.com.finup.dto;

import br.com.finup.model.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Corpo do POST e do PUT: somente conteúdo do rascunho, conforme a task 86e3m95y3. */
@Schema(description = "Conteúdo do rascunho. Os campos obrigatórios dependem do tipo da pergunta.")
public record StudyQuestionRequest(
    @NotNull(message = "Informe o tipo da pergunta.") QuestionType type,
    @Schema(description = "Obrigatório para FILL_BLANK e LEARNING") String title,
    @Schema(description = "Obrigatório para FILL_BLANK e MULTIPLE_CHOICE") String statement,
    @Schema(description = "FILL_BLANK: pelo menos uma resposta não vazia") List<String> answers,
    @Schema(description = "MULTIPLE_CHOICE: alternativas com pelo menos uma correta")
        List<Option> options,
    @Schema(description = "Explicação opcional para FILL_BLANK e MULTIPLE_CHOICE")
        String explanation,
    @Schema(description = "LEARNING: conteúdo obrigatório em Markdown") String body) {

  @Schema(name = "StudyQuestionOption", description = "Alternativa de uma pergunta objetiva")
  public record Option(String text, Boolean correct) {}
}
