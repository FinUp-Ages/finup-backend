package br.com.finup.controller;

import br.com.finup.dto.SpendingAlertsResponse;
import br.com.finup.security.AuthenticatedIdentity;
import br.com.finup.security.AuthenticatedIdentityResolver;
import br.com.finup.service.SpendingAlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Controller de insights financeiros inteligentes. */
@RestController
@RequestMapping("/api/v1/insights")
@Tag(name = "Insights", description = "Insights e análises financeiras inteligentes")
public class SpendingAlertController {

  private final SpendingAlertService spendingAlertService;
  private final AuthenticatedIdentityResolver authenticatedIdentityResolver;

  public SpendingAlertController(
      SpendingAlertService spendingAlertService,
      AuthenticatedIdentityResolver authenticatedIdentityResolver) {
    this.spendingAlertService = spendingAlertService;
    this.authenticatedIdentityResolver = authenticatedIdentityResolver;
  }

  @GetMapping("/spending-alerts")
  @Operation(
      summary = "Identifica alertas de gastos de risco no período informado",
      description =
          "Analisa as transações EXPENSE dos últimos N dias (padrão: 30) do usuário autenticado,"
              + " identificando gastos de risco por regras determinísticas e inteligência artificial,"
              + " calculando projeções caso o montante fosse investido a 10% a.a. por 5 anos.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Alertas de gastos de risco calculados com sucesso"),
    @ApiResponse(
        responseCode = "400",
        description = "Parâmetro days inválido (fora do intervalo de 1 a 365)",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Identidade autenticada ausente ou inválida",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Usuário autenticado sem cadastro local",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public ResponseEntity<SpendingAlertsResponse> getSpendingAlerts(
      @Parameter(description = "Quantidade de dias a analisar (padrão: 30)", example = "30")
          @RequestParam(defaultValue = "30")
          int days) {
    AuthenticatedIdentity identity = authenticatedIdentityResolver.resolveCurrent();
    SpendingAlertsResponse response = spendingAlertService.getSpendingAlerts(identity, days);
    return ResponseEntity.ok(response);
  }
}
