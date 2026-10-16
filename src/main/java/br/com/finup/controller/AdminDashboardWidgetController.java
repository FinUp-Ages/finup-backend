package br.com.finup.controller;

import br.com.finup.dto.DashboardWidgetsRequest;
import br.com.finup.dto.DashboardWidgetsResponse;
import br.com.finup.security.AuthenticatedIdentity;
import br.com.finup.security.AuthenticatedIdentityResolver;
import br.com.finup.service.DashboardWidgetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Configuração dos widgets do dashboard por admin.
 *
 * <p>A identidade vem sempre do {@link AuthenticatedIdentityResolver}. Só administrador chega aqui:
 * o {@code SecurityConfig} exige o grupo {@code admin} do Cognito em {@code /api/v1/admin/**}.
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard/widgets")
@Tag(name = "Admin Dashboard", description = "Widgets configuráveis do dashboard")
public class AdminDashboardWidgetController {

  private final DashboardWidgetService dashboardWidgetService;
  private final AuthenticatedIdentityResolver authenticatedIdentityResolver;

  public AdminDashboardWidgetController(
      DashboardWidgetService dashboardWidgetService,
      AuthenticatedIdentityResolver authenticatedIdentityResolver) {
    this.dashboardWidgetService = dashboardWidgetService;
    this.authenticatedIdentityResolver = authenticatedIdentityResolver;
  }

  @GetMapping
  @Operation(summary = "Devolve a configuração de widgets do admin autenticado")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Configuração (lista vazia se não houver)"),
    @ApiResponse(
        responseCode = "401",
        description = "Identidade autenticada ausente ou incompleta",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Usuário fora do grupo admin",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Usuário não encontrado",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public ResponseEntity<DashboardWidgetsResponse> find() {
    AuthenticatedIdentity identity = authenticatedIdentityResolver.resolveCurrent();
    return ResponseEntity.ok(dashboardWidgetService.find(identity));
  }

  @PutMapping
  @Operation(summary = "Substitui a configuração de widgets do admin autenticado")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Configuração gravada"),
    @ApiResponse(
        responseCode = "400",
        description = "Campos inválidos",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Identidade autenticada ausente ou incompleta",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Usuário fora do grupo admin",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Usuário não encontrado",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description = "Widget ou posição repetidos na lista",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public ResponseEntity<DashboardWidgetsResponse> replace(
      @Valid @RequestBody DashboardWidgetsRequest request) {
    AuthenticatedIdentity identity = authenticatedIdentityResolver.resolveCurrent();
    return ResponseEntity.ok(dashboardWidgetService.replace(identity, request));
  }
}
