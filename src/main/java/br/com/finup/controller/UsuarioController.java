package br.com.finup.controller;

import br.com.finup.dto.CadastrarUsuarioRequest;
import br.com.finup.dto.UsuarioResponse;
import br.com.finup.mapper.UsuarioMapper;
import br.com.finup.model.Usuario;
import br.com.finup.service.UsuarioService;
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
 * nao existe {@code try/catch} aqui. O service lanca excecao de negocio e o
 * {@code ApiExceptionHandler} devolve o RFC 7807.
 *
 * <p>Versao no caminho ({@code /api/v1}) desde o primeiro endpoint: adicionar versionamento depois
 * que web e mobile ja consomem a API custa muito mais caro.
 */
@RestController
@RequestMapping("/api/v1/usuarios")
@Tag(name = "Usuarios", description = "Cadastro e consulta de usuarios")
public class UsuarioController {

  private final UsuarioService usuarioService;

  public UsuarioController(UsuarioService usuarioService) {
    this.usuarioService = usuarioService;
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
  public ResponseEntity<UsuarioResponse> cadastrar(
      @Valid @RequestBody CadastrarUsuarioRequest requisicao) {

    Usuario usuario = usuarioService.cadastrar(requisicao.nome(), requisicao.email());
    UsuarioResponse resposta = UsuarioMapper.paraResposta(usuario);

    // 201 com Location apontando para o recurso criado: e o que o padrao HTTP espera de um POST
    // que cria, e o que permite ao cliente seguir direto para o GET.
    URI local = URI.create("/api/v1/usuarios/%s".formatted(resposta.id()));
    return ResponseEntity.created(local).body(resposta);
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
  public UsuarioResponse buscarPorId(@PathVariable UUID id) {
    return UsuarioMapper.paraResposta(usuarioService.buscarPorId(id));
  }

  @GetMapping
  @Operation(summary = "Lista os usuarios cadastrados")
  public List<UsuarioResponse> listar() {
    return UsuarioMapper.paraRespostas(usuarioService.listar());
  }
}
