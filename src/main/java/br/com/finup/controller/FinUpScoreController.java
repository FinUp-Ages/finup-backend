package br.com.finup.controller;

import br.com.finup.dto.FinUpScoreResponse;
import br.com.finup.dto.FinUpScoreStatus;
import br.com.finup.model.FinUpScoreResult;
import br.com.finup.security.AuthenticatedIdentity;
import br.com.finup.security.AuthenticatedIdentityResolver;
import br.com.finup.service.FinUpScoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recalculo do FinUp Score (regra em {@code docs/finup-score.md}). Sem endpoints de escrita de
 * transacao/divida/meta/investimento ainda, este e o gatilho manual ate eles existirem.
 *
 * <p>Segue o mesmo padrao do {@link UserController}: a identidade vem de {@link
 * AuthenticatedIdentityResolver}, nunca de um identificador informado pelo cliente.
 */
@RestController
@RequestMapping("/api/v1/users/me/finup-score")
@Tag(name = "FinUp Score", description = "Calculo da saude financeira do usuario")
public class FinUpScoreController {

  private final FinUpScoreService finUpScoreService;
  private final AuthenticatedIdentityResolver authenticatedIdentityResolver;

  public FinUpScoreController(
      FinUpScoreService finUpScoreService,
      AuthenticatedIdentityResolver authenticatedIdentityResolver) {
    this.finUpScoreService = finUpScoreService;
    this.authenticatedIdentityResolver = authenticatedIdentityResolver;
  }

  @PostMapping("/recalculate")
  @Operation(summary = "Recalcula e persiste o FinUp Score do usuario autenticado")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Recalculo processado (score computado ou dado insuficiente)"),
    @ApiResponse(
        responseCode = "401",
        description = "Identidade autenticada ausente ou incompleta",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Usuario ainda nao criado (Etapa 1 nao foi feita)",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public FinUpScoreResponse recalculate() {
    AuthenticatedIdentity identity = authenticatedIdentityResolver.resolveCurrent();
    FinUpScoreResult result = finUpScoreService.recalculate(identity);
    return switch (result) {
      case FinUpScoreResult.Computed computed ->
          new FinUpScoreResponse(
              FinUpScoreStatus.COMPUTED, computed.score(), "Score recalculado com sucesso.");
      case FinUpScoreResult.InsufficientData insufficient ->
          new FinUpScoreResponse(FinUpScoreStatus.INSUFFICIENT_DATA, null, insufficient.reason());
    };
  }
}
