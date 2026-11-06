package br.com.finup.controller;

import br.com.finup.dto.AdminUserResponse;
import br.com.finup.dto.PageResponse;
import br.com.finup.service.AdminUserService;
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
 * Listagem de usuarios do painel administrativo.
 *
 * <p>So admin chega aqui: o {@code SecurityConfig} exige o grupo {@code admin} do Cognito em {@code
 * /api/v1/admin/**} antes de o request alcancar este controller, entao ele nao confere perfil.
 *
 * <p>{@code page}, {@code size} e {@code status} chegam como texto de proposito: com tipo inteiro
 * ou enum, o Spring recusaria valor invalido com uma mensagem generica. Quem valida e o service,
 * que responde 400 dizendo o que e aceito.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin Users", description = "Listagem de usuarios do painel administrativo")
public class AdminUserController {

  private final AdminUserService adminUserService;

  public AdminUserController(AdminUserService adminUserService) {
    this.adminUserService = adminUserService;
  }

  @GetMapping
  @Operation(
      summary = "Lista usuarios paginada, com filtro de status e busca por nome ou e-mail",
      description =
          "status=ACTIVE e quem acessou o app (GET /api/v1/users/me) nos ultimos 7 dias;"
              + " INACTIVE e o restante, inclusive quem nunca acessou. search casa parcialmente"
              + " contra nome ou e-mail, sem diferenciar maiusculas de minusculas. Exige o grupo"
              + " admin do Cognito.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Pagina de usuarios"),
    @ApiResponse(
        responseCode = "400",
        description = "status fora de ACTIVE/INACTIVE, ou page/size invalidos",
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
  public PageResponse<AdminUserResponse> list(
      @Parameter(
              description = "Pagina, baseada em zero",
              schema = @Schema(type = "integer", defaultValue = "0", minimum = "0"),
              example = "0")
          @RequestParam(required = false)
          String page,
      @Parameter(
              description = "Tamanho da pagina",
              schema =
                  @Schema(type = "integer", defaultValue = "20", minimum = "1", maximum = "100"),
              example = "20")
          @RequestParam(required = false)
          String size,
      @Parameter(
              description = "Filtra por status; ausente devolve todos",
              schema = @Schema(allowableValues = {"ACTIVE", "INACTIVE"}))
          @RequestParam(required = false)
          String status,
      @Parameter(
              description =
                  "Busca parcial em nome ou e-mail, sem diferenciar maiusculas de minusculas;"
                      + " ausente ou em branco nao filtra")
          @RequestParam(required = false)
          String search) {
    return adminUserService.listUsers(page, size, status, search);
  }
}
