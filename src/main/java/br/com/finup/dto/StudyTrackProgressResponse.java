package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * Progresso do usuário na trilha (Contrato C6). Só contam as respostas de perguntas publicadas
 * hoje; cada pergunta publicada é um nível, na ordem de {@code GET /categories/{id}/questions}.
 */
@Schema(description = "Progresso do usuário autenticado na trilha de estudos")
public record StudyTrackProgressResponse(List<CategoryProgress> categories, Progress total) {

  @Schema(description = "Progresso em uma categoria")
  public record CategoryProgress(
      UUID categoryId,
      @Schema(description = "Perguntas publicadas (níveis) da categoria") int totalQuestions,
      @Schema(description = "Níveis concluídos: perguntas publicadas com resposta do usuário")
          int answeredQuestions,
      int correctAnswers,
      @Schema(description = "Respostas corrigíveis (LEARNING não entra)") int gradableQuestions,
      @Schema(
              description = "Estrelas da categoria, pelo percentual de acertos; null sem nota",
              nullable = true)
          Integer stars,
      @Schema(description = "Percentual de ACERTOS sobre as respostas corrigíveis (0 a 100)")
          double percentage,
      @Schema(description = "Percentual de AVANÇO: níveis concluídos sobre o total (0 a 100)")
          double completionPercentage,
      @Schema(
              description =
                  "Nível atual: o primeiro não concluído na ordem da trilha (1 sem respostas), o"
                      + " total quando todos estão concluídos e 0 sem perguntas. Níveis até ele"
                      + " estão alcançados.")
          int currentLevel,
      @Schema(description = "Um item por pergunta publicada, na ordem da trilha")
          List<LevelProgress> levels) {}

  @Schema(description = "Situação de um nível (pergunta publicada)")
  public record LevelProgress(
      @Schema(description = "Número do nível, a partir de 1") int level,
      UUID questionId,
      @Schema(description = "O usuário já respondeu (ou leu, se LEARNING) esta pergunta")
          boolean completed,
      @Schema(
              description = "Acerto da última resposta; null se LEARNING ou não respondida",
              nullable = true)
          Boolean correct,
      @Schema(
              description = "Estrelas do nível: 3 no acerto, 1 no erro, null sem nota",
              nullable = true)
          Integer stars) {}

  @Schema(description = "Progresso somado de todas as categorias")
  public record Progress(
      int totalQuestions,
      int answeredQuestions,
      int correctAnswers,
      int gradableQuestions,
      @Schema(nullable = true) Integer stars,
      @Schema(description = "Percentual de ACERTOS (0 a 100)") double percentage,
      @Schema(description = "Percentual de AVANÇO (0 a 100)") double completionPercentage) {}
}
