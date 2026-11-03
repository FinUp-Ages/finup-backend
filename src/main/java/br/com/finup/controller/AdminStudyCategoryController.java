package br.com.finup.controller;

import br.com.finup.dto.StudyCategoryRequest;
import br.com.finup.dto.StudyCategoryResponse;
import br.com.finup.service.StudyCategoryService;
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
import org.springframework.http.MediaType;
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

@RestController
@RequestMapping("/api/v1/admin/study-tracks/categories")
@Tag(name = "Admin Study Tracks", description = "Gerenciamento de categorias da trilha de estudos")
public class AdminStudyCategoryController {

  private final StudyCategoryService studyCategoryService;

  public AdminStudyCategoryController(StudyCategoryService studyCategoryService) {
    this.studyCategoryService = studyCategoryService;
  }

  @GetMapping
  @Operation(summary = "Lista as categorias da trilha de estudos")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Categorias retornadas"),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente, inválido ou expirado",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Usuário fora do grupo admin",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  public ResponseEntity<List<StudyCategoryResponse>> list() {
    return ResponseEntity.ok(studyCategoryService.listCategories());
  }

  @PostMapping
  @Operation(summary = "Cria uma categoria da trilha de estudos")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Categoria criada"),
    @ApiResponse(
        responseCode = "400",
        description = "Nome ausente, vazio ou campos inválidos",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente, inválido ou expirado",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Usuário fora do grupo admin",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  public ResponseEntity<StudyCategoryResponse> create(
      @Valid @RequestBody StudyCategoryRequest request) {
    StudyCategoryResponse response = studyCategoryService.create(request);
    return ResponseEntity.created(
            URI.create("/api/v1/admin/study-tracks/categories/" + response.id()))
        .body(response);
  }

  @PutMapping("/{id}")
  @Operation(summary = "Atualiza uma categoria da trilha de estudos")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Categoria atualizada"),
    @ApiResponse(
        responseCode = "400",
        description = "Nome ausente, vazio, campos inválidos ou id fora do formato UUID",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente, inválido ou expirado",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Usuário fora do grupo admin",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Categoria não encontrada",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  public ResponseEntity<StudyCategoryResponse> update(
      @PathVariable UUID id, @Valid @RequestBody StudyCategoryRequest request) {
    return ResponseEntity.ok(studyCategoryService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Remove uma categoria sem perguntas")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Categoria removida"),
    @ApiResponse(
        responseCode = "400",
        description = "Id fora do formato UUID",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente, inválido ou expirado",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Usuário fora do grupo admin",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Categoria não encontrada",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description = "Categoria possui perguntas",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    studyCategoryService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
