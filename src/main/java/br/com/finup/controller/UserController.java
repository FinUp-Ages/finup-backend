package br.com.finup.controller;

import br.com.finup.dto.UpdateUserAdditionalInfoRequest;
import br.com.finup.dto.UserResponse;
import br.com.finup.mapper.UserMapper;
import br.com.finup.model.User;
import br.com.finup.security.AuthenticatedIdentity;
import br.com.finup.security.AuthenticatedIdentityResolver;
import br.com.finup.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Etapa 1 do cadastro de usuario: cria o registro local a partir da identidade ja autenticada pelo
 * Cognito. Nao ha corpo de requisicao — nome, e-mail e o identificador da identidade vem de {@link
 * AuthenticatedIdentityResolver}, nunca de um DTO preenchido pelo cliente.
 *
 * <p>O que o controller faz: resolve a identidade, delega ao service, converte para DTO e escolhe o
 * codigo de status. So isso — regra de negocio e acesso a dados ficam fora daqui, e nao existe
 * {@code try/catch}: o service lanca excecao de negocio e o {@code ApiExceptionHandler} devolve o
 * RFC 7807.
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Cadastro e consulta de usuarios")
public class UserController {

  private final UserService userService;
  private final AuthenticatedIdentityResolver authenticatedIdentityResolver;

  public UserController(
      UserService userService, AuthenticatedIdentityResolver authenticatedIdentityResolver) {
    this.userService = userService;
    this.authenticatedIdentityResolver = authenticatedIdentityResolver;
  }

  @PostMapping
  @Operation(
      summary = "Cria o usuario local a partir da identidade autenticada pelo Cognito",
      description =
          "Nao recebe corpo. A identidade (sub, nome, e-mail) vem da requisicao autenticada.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Usuario criado"),
    @ApiResponse(
        responseCode = "401",
        description = "Identidade autenticada ausente ou incompleta",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description = "Ja existe usuario para esta identidade ou e-mail",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public ResponseEntity<UserResponse> create() {
    AuthenticatedIdentity identity = authenticatedIdentityResolver.resolveCurrent();
    User user = userService.createFromAuthenticatedIdentity(identity);
    UserResponse response = UserMapper.toResponse(user);

    // 201 com Location apontando para o recurso criado: e o que o padrao HTTP espera de um POST
    // que cria, e o que permite ao cliente seguir direto para o GET.
    URI location = URI.create("/api/v1/users/%s".formatted(response.id()));
    return ResponseEntity.created(location).body(response);
  }

  @PatchMapping("/me/additional-info")
  @Operation(
      summary = "Cadastra ou atualiza as informacoes complementares do usuario autenticado",
      description =
          "Etapa 2 do cadastro. So funciona depois da Etapa 1 (POST /api/v1/users). Cada campo do"
              + " corpo e opcional: so o que vier preenchido e atualizado.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Informacoes atualizadas"),
    @ApiResponse(
        responseCode = "400",
        description = "Campos invalidos",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Identidade autenticada ausente ou incompleta",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Usuario ainda nao criado (Etapa 1 nao foi feita)",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public UserResponse updateAdditionalInfo(
      @Valid @RequestBody UpdateUserAdditionalInfoRequest request) {
    AuthenticatedIdentity identity = authenticatedIdentityResolver.resolveCurrent();
    User user =
        userService.updateAdditionalInfo(
            identity, request.birthDate(), request.monthlyIncome(), request.financialProfile());
    return UserMapper.toResponse(user);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Busca um usuario pelo identificador")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Usuario encontrado"),
    @ApiResponse(
        responseCode = "404",
        description = "Usuario inexistente",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public UserResponse findById(@PathVariable UUID id) {
    return UserMapper.toResponse(userService.findById(id));
  }

  @GetMapping
  @Operation(summary = "Lista os usuarios cadastrados")
  public List<UserResponse> findAll() {
    return UserMapper.toResponses(userService.findAll());
  }
}
