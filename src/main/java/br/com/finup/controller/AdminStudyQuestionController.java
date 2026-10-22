package br.com.finup.controller;

import br.com.finup.dto.StudyQuestionRequest;
import br.com.finup.dto.StudyQuestionResponse;
import br.com.finup.service.StudyQuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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

/** CRUD de perguntas da trilha, restrito ao grupo admin do Cognito. */
@RestController
@RequestMapping("/api/v1/admin/study-tracks")
@Tag(name = "Admin Study Questions", description = "Perguntas da trilha de estudos — contrato C6")
@ApiResponses({
  @ApiResponse(
      responseCode = "400",
      description = "Tipo, conteúdo ou UUID inválido",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class))),
  @ApiResponse(
      responseCode = "401",
      description = "Token ausente ou inválido",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class))),
  @ApiResponse(
      responseCode = "403",
      description = "Usuário fora do grupo admin",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class))),
  @ApiResponse(
      responseCode = "404",
      description = "Categoria ou pergunta não encontrada",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
})
public class AdminStudyQuestionController {

  private final StudyQuestionService service;

  public AdminStudyQuestionController(StudyQuestionService service) {
    this.service = service;
  }

  @PostMapping("/categories/{categoryId}/questions")
  @Operation(
      summary = "Cria uma pergunta em DRAFT",
      description =
          "Grava somente o rascunho; published e publishedAt são nulos. Exige grupo admin.")
  @ApiResponse(responseCode = "201", description = "Pergunta criada")
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      required = true,
      content =
          @Content(
              schema = @Schema(implementation = StudyQuestionRequest.class),
              examples = {
                @ExampleObject(
                    name = "FILL_BLANK",
                    value =
                        "{\"type\":\"FILL_BLANK\",\"title\":\"CDB\",\"statement\":\"O CDB rende ___ do CDI\",\"answers\":[\"100%\"],\"explanation\":\"Rendimento do CDB.\"}"),
                @ExampleObject(
                    name = "MULTIPLE_CHOICE",
                    value =
                        "{\"type\":\"MULTIPLE_CHOICE\",\"statement\":\"Qual investimento tem liquidez diária?\",\"options\":[{\"text\":\"CDB com liquidez diária\",\"correct\":true},{\"text\":\"Imóvel\",\"correct\":false}],\"explanation\":\"O CDB permite resgate diário.\"}"),
                @ExampleObject(
                    name = "LEARNING",
                    value =
                        "{\"type\":\"LEARNING\",\"title\":\"Reserva\",\"body\":\"## Reserva de emergência\"}")
              }))
  public ResponseEntity<StudyQuestionResponse> create(
      @PathVariable UUID categoryId, @Valid @RequestBody StudyQuestionRequest request) {
    StudyQuestionResponse response = service.create(categoryId, request);
    return ResponseEntity.created(
            URI.create("/api/v1/admin/study-tracks/questions/" + response.id()))
        .body(response);
  }

  @GetMapping("/categories/{categoryId}/questions")
  @Operation(
      summary = "Lista todas as perguntas de uma categoria",
      description =
          "Inclui rascunho, publicação e status derivado, por ordem de criação. Categoria vazia retorna [].")
  @ApiResponse(responseCode = "200", description = "Perguntas da categoria")
  public List<StudyQuestionResponse> findByCategory(@PathVariable UUID categoryId) {
    return service.findByCategory(categoryId);
  }

  @PutMapping("/questions/{id}")
  @Operation(
      summary = "Substitui somente o rascunho de uma pergunta",
      description =
          "Aceita o mesmo corpo do POST, mantendo o tipo original. Nunca altera published ou publishedAt. O status é derivado pela igualdade de draft e published.")
  @ApiResponse(responseCode = "200", description = "Rascunho atualizado")
  public StudyQuestionResponse update(
      @PathVariable UUID id, @Valid @RequestBody StudyQuestionRequest request) {
    return service.update(id, request);
  }

  @DeleteMapping("/questions/{id}")
  @Operation(
      summary = "Remove a pergunta inteira",
      description = "Remove também a versão publicada, se houver.")
  @ApiResponse(responseCode = "204", description = "Pergunta removida", content = @Content)
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
