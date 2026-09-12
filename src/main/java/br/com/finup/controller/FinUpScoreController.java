package br.com.finup.controller;

import br.com.finup.dto.FinUpScoreResponse;
import br.com.finup.model.FinUpScoreResult;
import br.com.finup.service.FinUpScoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recalculo do FinUp Score (regra em {@code docs/finup-score.md}). Sem endpoints de escrita de
 * transacao/divida/meta/investimento ainda, este e o gatilho manual ate eles existirem.
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/finup-score")
@Tag(name = "FinUp Score", description = "Calculo da saude financeira do usuario")
public class FinUpScoreController {

  private final FinUpScoreService finUpScoreService;

  public FinUpScoreController(FinUpScoreService finUpScoreService) {
    this.finUpScoreService = finUpScoreService;
  }

  @PostMapping("/recalculate")
  @Operation(summary = "Recalcula e persiste o FinUp Score do usuario")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Recalculo processado (score computado ou dado insuficiente)"),
    @ApiResponse(
        responseCode = "404",
        description = "Usuario inexistente",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public FinUpScoreResponse recalculate(@PathVariable UUID userId) {
    FinUpScoreResult result = finUpScoreService.recalculate(userId);
    return switch (result) {
      case FinUpScoreResult.Computed computed ->
          new FinUpScoreResponse(
              userId, "COMPUTED", computed.score(), "Score recalculado com sucesso.");
      case FinUpScoreResult.InsufficientData insufficient ->
          new FinUpScoreResponse(userId, "INSUFFICIENT_DATA", null, insufficient.reason());
    };
  }
}
