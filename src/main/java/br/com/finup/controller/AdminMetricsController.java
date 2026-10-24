package br.com.finup.controller;

import br.com.finup.dto.MetricsSummaryResponse;
import br.com.finup.dto.UserGrowthResponse;
import br.com.finup.service.AdminMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Metricas de usuarios do dashboard admin.
 *
 * <p>So admin chega aqui: o {@code SecurityConfig} exige o grupo {@code admin} do Cognito em {@code
 * /api/v1/admin/metrics/**} antes de o request alcancar este controller, entao ele nao confere
 * perfil. Tambem nao depende de usuario: as metricas sao da base inteira, nao de quem chama.
 *
 * <p>{@code period} e {@code months} chegam como texto de proposito: com tipo enum ou inteiro, o
 * Spring recusaria valor invalido com uma mensagem generica. Quem valida e o service, que responde
 * 400 dizendo o que e aceito.
 */
@RestController
@RequestMapping("/api/v1/admin/metrics")
@Tag(name = "Admin Metrics", description = "Metricas de usuarios do dashboard admin")
public class AdminMetricsController {

  private final AdminMetricsService adminMetricsService;

  public AdminMetricsController(AdminMetricsService adminMetricsService) {
    this.adminMetricsService = adminMetricsService;
  }

  @GetMapping("/summary")
  @Operation(
      summary = "Usuarios ativos e novos no periodo, comparados com o periodo anterior",
      description =
          "Ativo e quem registrou ao menos uma transacao no periodo (ocorrencias geradas por"
              + " recorrencia nao contam). O periodo anterior tem o mesmo tamanho e termina onde o"
              + " atual comeca. changePct tem 1 casa decimal e vale 0.0 quando o periodo anterior"
              + " e zero. Exige o grupo admin do Cognito.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Resumo calculado"),
    @ApiResponse(
        responseCode = "400",
        description = "period ausente ou diferente de 7d, 30d e 90d",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente, invalido ou expirado",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Usuario autenticado fora do grupo admin",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public MetricsSummaryResponse summary(
      @Parameter(
              description = "Tamanho do periodo, em dias",
              required = true,
              schema = @Schema(allowableValues = {"7d", "30d", "90d"}),
              example = "30d")
          @RequestParam(required = false)
          String period) {
    return adminMetricsService.summary(period);
  }

  @GetMapping("/user-growth")
  @Operation(
      summary = "Cadastros por mes e crescimento da base de usuarios",
      description =
          "Devolve um ponto por mes, do mais antigo ao mais recente, terminando no mes corrente"
              + " (parcial). Meses sem cadastro entram com newUsers 0. Os meses seguem o fuso de"
              + " Brasilia. Exige o grupo admin do Cognito.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Serie calculada"),
    @ApiResponse(
        responseCode = "400",
        description = "months nao numerico, ou fora de 1 a 24",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente, invalido ou expirado",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Usuario autenticado fora do grupo admin",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public UserGrowthResponse userGrowth(
      @Parameter(
              description = "Quantidade de meses na serie, contando o mes corrente",
              schema = @Schema(type = "integer", defaultValue = "6", minimum = "1", maximum = "24"),
              example = "6")
          @RequestParam(required = false)
          String months) {
    return adminMetricsService.userGrowth(months);
  }
}
