package br.com.finup.controller;

import br.com.finup.dto.AdminMeResponse;
import br.com.finup.mapper.UserMapper;
import br.com.finup.security.AuthenticatedIdentity;
import br.com.finup.security.AuthenticatedIdentityResolver;
import br.com.finup.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Identidade de quem esta logado no painel administrativo.
 *
 * <p>Nao confere papel aqui: quem garante que so administrador chega a {@code /api/v1/admin/**} e o
 * {@code SecurityConfig}, a partir do grupo {@code admin} do Cognito.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "Acesso ao painel administrativo")
public class AdminController {

  private final UserService userService;
  private final AuthenticatedIdentityResolver authenticatedIdentityResolver;

  public AdminController(
      UserService userService, AuthenticatedIdentityResolver authenticatedIdentityResolver) {
    this.userService = userService;
    this.authenticatedIdentityResolver = authenticatedIdentityResolver;
  }

  @GetMapping("/me")
  @Operation(
      summary = "Devolve o administrador autenticado",
      description =
          "E o endpoint que o painel chama apos o login, para confirmar que quem entrou e"
              + " administrador. Exige o access token de um usuario do grupo admin do Cognito.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Administrador autenticado"),
    @ApiResponse(
        responseCode = "401",
        description = "Token de acesso ausente, invalido ou expirado",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Token valido, mas o usuario nao esta no grupo admin",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Administrador ainda sem usuario local (POST /api/v1/users nao foi feito)",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  public AdminMeResponse me() {
    AuthenticatedIdentity identity = authenticatedIdentityResolver.resolveCurrent();
    return UserMapper.toAdminMeResponse(userService.findByAuthenticatedIdentity(identity));
  }
}
