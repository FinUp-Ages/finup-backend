package br.com.finup.controller;

import br.com.finup.dto.RegisterUserRequest;
import br.com.finup.dto.UserResponse;
import br.com.finup.mapper.UserMapper;
import br.com.finup.model.User;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exemplo de referencia da camada HTTP. Copie a estrutura desta classe ao criar um recurso novo.
 *
 * <p>O que o controller faz: recebe, valida formato com {@code @Valid}, delega ao service, converte
 * para DTO e escolhe o codigo de status. So isso.
 *
 * <p>O que ele <strong>nao</strong> faz: regra de negocio, acesso a dados e tratamento de erro —
 * nao existe {@code try/catch} aqui. O service lanca excecao de negocio e o {@code
 * ApiExceptionHandler} devolve o RFC 7807.
 *
 * <p>Versao no caminho ({@code /api/v1}) desde o primeiro endpoint: adicionar versionamento depois
 * que web e mobile ja consomem a API custa muito mais caro.
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Cadastro e consulta de usuarios")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @PostMapping
  @Operation(summary = "Cadastra um usuario")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Usuario cadastrado"),
    @ApiResponse(
        responseCode = "400",
        description = "Campos invalidos",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description = "E-mail ja cadastrado",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
    User user = userService.register(request.name(), request.email());
    UserResponse response = UserMapper.toResponse(user);

    // 201 com Location apontando para o recurso criado: e o que o padrao HTTP espera de um POST
    // que cria, e o que permite ao cliente seguir direto para o GET.
    URI location = URI.create("/api/v1/users/%s".formatted(response.id()));
    return ResponseEntity.created(location).body(response);
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
