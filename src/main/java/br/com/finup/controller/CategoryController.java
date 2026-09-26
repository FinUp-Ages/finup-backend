package br.com.finup.controller;

import br.com.finup.dto.CategoryRequest;
import br.com.finup.dto.CategoryResponse;
import br.com.finup.security.AuthenticatedIdentity;
import br.com.finup.security.AuthenticatedIdentityResolver;
import br.com.finup.service.CategoryService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gerenciamento de categorias do usuário autenticado.
 *
 * <p>Nenhum endpoint recebe userId do cliente — a identidade vem sempre do {@link
 * AuthenticatedIdentityResolver}. Regras de negócio e acesso a dados ficam no service; não existe
 * {@code try/catch} aqui.
 */
@RestController
@RequestMapping("/api/v1/categories")
@Tag(name = "Categories", description = "Gerenciamento de categorias de transações")
public class CategoryController {

  private final CategoryService categoryService;
  private final AuthenticatedIdentityResolver authenticatedIdentityResolver;

  public CategoryController(
      CategoryService categoryService,
      AuthenticatedIdentityResolver authenticatedIdentityResolver) {
    this.categoryService = categoryService;
    this.authenticatedIdentityResolver = authenticatedIdentityResolver;
  }

  @PostMapping
  @Operation(summary = "Cria uma nova categoria para o usuário autenticado")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Categoria criada"),
    @ApiResponse(
        responseCode = "400",
        description = "Campos inválidos",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Identidade autenticada ausente ou incompleta",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Usuário não encontrado",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
    AuthenticatedIdentity identity = authenticatedIdentityResolver.resolveCurrent();
    CategoryResponse response = categoryService.create(identity, request);
    return ResponseEntity.created(URI.create("/api/v1/categories/" + response.id())).body(response);
  }

  @PutMapping("/{id}")
  @Operation(summary = "Atualiza uma categoria do usuário autenticado")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Categoria atualizada"),
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
        description = "Tentativa de editar uma categoria padrão do sistema",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Categoria não encontrada ou não pertence ao usuário",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public ResponseEntity<CategoryResponse> update(
      @PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
    AuthenticatedIdentity identity = authenticatedIdentityResolver.resolveCurrent();
    return ResponseEntity.ok(categoryService.update(identity, id, request));
  }

  @GetMapping
  @Operation(summary = "Lista as categorias disponíveis: as do usuário e as padrão do sistema")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"),
    @ApiResponse(
        responseCode = "401",
        description = "Identidade autenticada ausente ou incompleta",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Usuário não encontrado",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public ResponseEntity<List<CategoryResponse>> findAvailable() {
    AuthenticatedIdentity identity = authenticatedIdentityResolver.resolveCurrent();
    return ResponseEntity.ok(categoryService.findAvailable(identity));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Remove uma categoria do usuário autenticado")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Categoria removida"),
    @ApiResponse(
        responseCode = "401",
        description = "Identidade autenticada ausente ou incompleta",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Tentativa de remover uma categoria padrão do sistema",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Categoria não encontrada ou não pertence ao usuário",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description = "Categoria em uso por uma ou mais transações",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    AuthenticatedIdentity identity = authenticatedIdentityResolver.resolveCurrent();
    categoryService.delete(identity, id);
    return ResponseEntity.noContent().build();
  }
}
